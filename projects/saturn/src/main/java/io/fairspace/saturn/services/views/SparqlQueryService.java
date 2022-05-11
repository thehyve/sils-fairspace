package io.fairspace.saturn.services.views;

import io.fairspace.saturn.config.Config;
import io.fairspace.saturn.config.ViewsConfig;
import io.fairspace.saturn.config.ViewsConfig.View;
import io.fairspace.saturn.rdf.SparqlUtils;
import io.fairspace.saturn.services.search.FileSearchRequest;
import io.fairspace.saturn.services.search.SearchResultDTO;
import io.fairspace.saturn.vocabulary.FS;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;

import java.util.*;

import static io.fairspace.saturn.rdf.ModelUtils.getResourceProperties;
import static io.fairspace.saturn.util.ValidationUtils.validateIRI;
import static java.time.Instant.ofEpochMilli;
import static java.util.stream.Collectors.*;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createStringLiteral;
import static org.apache.jena.system.Txn.calculateRead;

@Log4j2
public class SparqlQueryService implements QueryService {
    private final Config.Search config;
    private final ViewsConfig searchConfig;
    private final Dataset ds;

    public SparqlQueryService(Config.Search config, ViewsConfig viewsConfig, Dataset ds) {
        this.config = config;
        this.searchConfig = viewsConfig;
        this.ds = ds;
    }

    public ViewPageDTO retrieveViewPage(ViewRequest request) {
        var query = new SparqlViewQueryBuilder(getView(request.getView()))
                .getQuery(request.getFilters());
        log.debug("Executing query:\n{}", query);

        var page = (request.getPage() != null && request.getPage() >= 1) ? request.getPage() : 1;
        var size = (request.getSize() != null && request.getSize() >= 1) ? request.getSize() : 20;
        query.setLimit(size + 1);
        query.setOffset((page - 1) * size);
        log.debug("Query with filters and pagination applied: \n{}", query);

        var selectExecution = QueryExecutionFactory.create(query, ds);
        selectExecution.setTimeout(config.pageRequestTimeout);

        // get sparql results
        return calculateRead(ds, () -> {
            var results = new ArrayList<QuerySolution>();
            var timeout = false;

            try (selectExecution) {
                var rs = selectExecution.execSelect();
                while (rs.hasNext()) {
                    results.add(rs.next());
                }
            } catch (QueryCancelledException e) {
                timeout = true;
            }

            var uniqueIris = new HashSet<Resource>();
            var columnData = new JoinColumnData();
            var hasNext = false;

            // extract iri's and column values
            for (var row : results) {
                var resourceUri = row.getResource(request.getView());

                if(uniqueIris.size() < size) {
                    uniqueIris.add(resourceUri);
                } else {
                    hasNext = true;
                }

                var vars = row.varNames();
                while (vars.hasNext()) {
                    var column = vars.next();
                    if (row.get(column) == null) {
                        continue;
                    }
                    columnData.addValue(resourceUri.getURI(), column, row.get(column));
                }
            }

            var rows = uniqueIris.stream()
                    .map(resource -> fetch(resource, request.getView(), columnData))
                    .collect(toList());

            return ViewPageDTO.builder()
                    .rows(rows)
                    .hasNext(hasNext)
                    .timeout(timeout)
                    .build();
        });
    }

    private Map<String, Set<ValueDTO>> fetch(Resource resource, String viewName, JoinColumnData columnData) {
        var view = getView(viewName);

        var result = new HashMap<String, Set<ValueDTO>>();
        result.put(view.name, Set.of(toValueDTO(resource)));

        for (var c : view.columns) {
            result.put(viewName + "_" + c.name, getValues(resource, c));
        }
        for (var c : view.joinColumns) {
            result.put(c.sourceClassName + "_" + c.name, getValuesJoinColumn(resource, c, columnData));
        }
        for (var j : view.join) {
            var joinView = getView(j.view);

            var prop = createProperty(j.on);
            var refs = j.reverse
                    ? resource.getModel().listResourcesWithProperty(prop, resource).toList()
                    : getResourceProperties(resource, prop);

            for (var colName : j.include) {
                if (colName.equals("id")) {
                    result.put(joinView.name, refs.stream().map(this::toValueDTO).collect(toSet()));
                } else {
                    var col = joinView.columns.stream()
                            .filter(c -> c.name.equals(colName))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Unknown column: " + colName));

                    var values = refs.stream()
                            .flatMap(ref -> getValues(ref, col)
                                    .stream()).
                            collect(toCollection(TreeSet::new));

                    result.put(joinView.name + "_" + colName, values);
                }
            }
        }

        return result;
    }

    public List<SearchResultDTO> searchFiles(FileSearchRequest request) {
        var query = getSearchForFilesQuery(request.getParentIRI());
        var binding = new QuerySolutionMap();
        binding.add("regexQuery", createStringLiteral(SparqlUtils.getQueryRegex(request.getQuery())));
        return SparqlUtils.getByQuery(query, binding, ds);
    }

    private Set<ValueDTO> getValues(Resource resource, View.Column column) {
        return new TreeSet<>(resource.listProperties(createProperty(column.source))
                .mapWith(Statement::getObject)
                .mapWith(this::toValueDTO)
                .toSet());
    }

    private Set<ValueDTO> getValuesJoinColumn(Resource resource, View.JoinColumn jc, JoinColumnData columnData) {
        return columnData.find(resource.getURI(), jc.sourceClassName + "_" + jc.name)
                .stream().map(this::toValueDTO)
                .collect(toCollection(TreeSet::new));
    }

    private View getView(String viewName) {
        return searchConfig.views
                .stream()
                .filter(v -> v.name.equals(viewName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown view: " + viewName));
    }

    private ValueDTO toValueDTO(RDFNode node) {
        if (node.isLiteral()) {
            var value = node.asLiteral().getValue();
            if (value instanceof XSDDateTime) {
                value = ofEpochMilli(((XSDDateTime) value).asCalendar().getTimeInMillis());
            }
            return new ValueDTO(value.toString(), value);
        }
        var resource = node.asResource();
        var label = resource.listProperties(RDFS.label)
                .nextOptional()
                .map(Statement::getString)
                .orElseGet(resource::getLocalName);

        return new ValueDTO(label, resource.getURI());
    }

    private Query getSearchForFilesQuery(String parentIRI) {
        var builder = new StringBuilder("PREFIX fs: <")
                .append(FS.NS)
                .append(">\nPREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n\n")
                .append("SELECT ?id ?label ?comment ?type\n")
                .append("WHERE {\n");

        if (parentIRI != null && !parentIRI.trim().isEmpty()) {
            validateIRI(parentIRI);
            builder.append("?id fs:belongsTo* <").append(parentIRI).append("> .\n");
        }

        builder.append("?id rdfs:label ?label ; a ?type .\n")
                .append("FILTER (?type in (fs:File, fs:Directory, fs:Collection))\n")
                .append("OPTIONAL { ?id rdfs:comment ?comment }\n")
                .append("FILTER NOT EXISTS { ?id fs:dateDeleted ?anydate }\n")
                .append("FILTER (regex(?label, ?regexQuery, \"i\") || regex(?comment, ?regexQuery, \"i\"))\n")
                .append("}\nLIMIT 10000");

        return QueryFactory.create(builder.toString());
    }

    public CountDTO count(CountRequest request) {
        var query = new SparqlViewQueryBuilder(getView(request.getView()))
                .getQuery(request.getFilters());

        log.debug("Querying the total number of matches: \n{}", query);

        var execution = QueryExecutionFactory.create(query, ds);
        execution.setTimeout(config.countRequestTimeout);

        return calculateRead(ds, () -> {
            long count = 0;
            try (execution) {
                for (var it = execution.execSelect(); it.hasNext(); it.next()) {
                    count++;
                }
                return new CountDTO(count, false);
            } catch (QueryCancelledException e) {
                return new CountDTO(count, true);
            }
        });
    }
}
