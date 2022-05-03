package io.fairspace.saturn.services.views;

import io.fairspace.saturn.config.ViewsConfig;
import io.fairspace.saturn.config.ViewsConfig.ColumnType;
import io.fairspace.saturn.config.ViewsConfig.View;
import io.fairspace.saturn.vocabulary.FS;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static io.fairspace.saturn.rdf.ModelUtils.getStringProperty;
import static io.fairspace.saturn.util.ValidationUtils.validateIRI;
import static io.fairspace.saturn.webdav.DavUtils.getHierarchyTree;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.sparql.expr.NodeValue.*;

@Log4j2
public class SparqlViewQueryBuilder {
    private StringBuilder builder;
    private final View view;
    private long limit;
    private long offset;
    private HashSet<String> entityColumnsWithSubquery;
    private final String PLACEHOLDER = " [subquery_column_placeholder] ";
    private HashMap<String, List<String>> entityTypes;

    public SparqlViewQueryBuilder(View view) {
        this.view = view;
        this.limit = -1L;
        this.offset = -1L;
    }

    public SparqlViewQueryBuilder(View view, int page, int size) {
        this.view = view;
        this.limit = (size + 1);
        this.offset = ((page - 1) * size);
    }

    public Query getQuery(List<ViewFilter> viewFilters) {
        builder = new StringBuilder();
        entityColumnsWithSubquery = new HashSet<>();
        entityTypes = new HashMap<>();

        fetchTypes(view); //TODO init types on the class init

        appNamespace("fs", FS.NS);
        appNamespace("rdf", RDF.getURI());
        appNamespace("rdfs", RDFS.getURI());
        appNamespace("sh", SHACL.NS);

        builder.append("\nSELECT DISTINCT ?")
                .append(view.name)
                .append(PLACEHOLDER)
                .append("\nWHERE {\n");

        builder.append("?dir fs:linkedEntity ?").append(view.name)
                .append(" .\n");

        applyHierarchy();
        applyFilters(viewFilters);

        builder.append("{\nSELECT DISTINCT ?")
                .append(view.name)
                .append(" WHERE {\n");
        if (view.types.size() == 1) {
            builder.append("?")
                    .append(view.name)
                    .append(" rdf:type <").append(view.types.get(0)).append("> .\n");
        } else {
            builder.append("?")
                    .append(view.name)
                    .append(" a ?type .\nFILTER (?type IN (")
                    .append(view.types.stream().map(t -> "<" + t + ">").collect(joining(", ")))
                    .append("))\n");
        }
        builder.append("FILTER NOT EXISTS { ?")
                .append(view.name)
                .append(" fs:dateDeleted ?any } .\n}\n");
        if (limit > -1L && offset > -1L) {
            builder.append("LIMIT ").append(limit)
                    .append(" OFFSET ").append(offset).append("\n");
        }
        builder.append("}\n");

        builder.append("}\n");

        String query = builder.toString();
        query = addSubqueryColumns(query);

        return QueryFactory.create(query);
    }

    private void applyHierarchy() {
        ArrayList<List<Resource>> hierarchy = getHierarchyTree();

        int hierarchyIndexDesc = 0;
        for (int i = 0; i < hierarchy.size(); i++) {
            if (hierarchy.get(i).stream().anyMatch(r -> r.getURI().equals(view.types.get(0)))) {
                hierarchyIndexDesc = i;
            }
        }
        ListIterator<List<Resource>> hierarchyIteratorDesc = hierarchy.listIterator(hierarchyIndexDesc);
        String descDirAlias = "dir";
        while (hierarchyIteratorDesc.hasPrevious()) {
            List<Resource> resources = hierarchyIteratorDesc.previous();
            String currentDirAlias = resources.stream()
                    .map(r -> getEntityDirAlias(Objects.requireNonNull(getStringProperty(r, SHACLM.name))))
                    .collect(joining("_"));
            applyHierarchyLevelCriteria(resources, descDirAlias, currentDirAlias, true);
            descDirAlias = currentDirAlias;
        }

        int hierarchyIndexAsc = hierarchyIndexDesc < (hierarchy.size() - 1) ? (hierarchyIndexDesc + 1) : hierarchy.size();
        ListIterator<List<Resource>> hierarchyIteratorAsc = hierarchy.listIterator(hierarchyIndexAsc);
        String ascDirAlias = "dir";
        while (hierarchyIteratorAsc.hasNext()) {
            List<Resource> resources = hierarchyIteratorAsc.next();
            String currentDirAlias = resources.stream()
                    .map(r -> getEntityDirAlias(Objects.requireNonNull(getStringProperty(r, SHACLM.name))))
                    .collect(joining("_"));
            applyHierarchyLevelCriteria(resources, ascDirAlias, currentDirAlias, false);
            ascDirAlias = currentDirAlias;
        }
    }

    private void applyHierarchyLevelCriteria(List<Resource> resources, String nextDirAlias, String currentDirAlias, boolean descOrder) {
        builder.append("?").append(nextDirAlias).append(descOrder ? " " : " ^").append("fs:belongsTo ")
                .append("?").append(currentDirAlias).append(" .\n");

        if (resources.size() == 1) {
            builder.append("?")
                    .append(currentDirAlias)
                    .append(" fs:linkedEntityType <")
                    .append(resources.get(0).getURI())
                    .append("> .\n");
        } else {
            builder.append("?")
                    .append(currentDirAlias)
                    .append(" fs:linkedEntityType ?linkedEntityType .\nFILTER (?linkedEntityType IN (")
                    .append(resources.stream().map(r -> "<" + r.getURI() + ">").collect(joining(", ")))
                    .append("))\n");
        }
    }

    private void applyFilters(List<ViewFilter> viewFilters) {
        addLocationFilter(viewFilters);
        createSubqueriesForFacetFilters(viewFilters);
        createSubqueriesForOptionalFilters(viewFilters);
    }

    private String getEntityDirAlias(String entityName) {
        return entityName.toLowerCase().replaceAll("\\s+", "") + "Dir";
    }

    private void appNamespace(String alias, String namespace) {
        builder.append("PREFIX ")
                .append(alias)
                .append(": <")
                .append(namespace)
                .append(">\n");
    }

    private void createSubqueriesForFacetFilters(List<ViewFilter> filters) {
        var entitiesWithFilter = filters.stream()
                .map(f -> f.field)
                .sorted(comparing(field -> field.contains("_") ? getColumn(field).priority : 0))
                .map(field -> field.split("_")[0])
                .distinct()
                .toList();

        entitiesWithFilter.forEach(entity -> {
            var entityFilters = filters.stream()
                    .filter(f -> f.getField().startsWith(entity))
                    .sorted(comparing(f -> f.field.contains("_") ? getColumn(f.field).priority : 0))
                    .toList();
            for (var filter : entityFilters) {
                addSingleFilter(filter, entity, filter.getField());
            }
        });
        builder.append("\n");
    }

    private void createSubqueriesForOptionalFilters(List<ViewFilter> filters) {
        for (var jc : view.joinColumns) {
            var name = jc.sourceClassName + "_" + jc.name;
            if (filters.stream().anyMatch(f -> f.field.equals(name))) {
                continue;
            }
            builder.append("OPTIONAL\n");
            addSingleFilter(null, jc.sourceClassName, name);
        }
    }

    private void addLocationFilter(List<ViewFilter> filters) {
        filters.stream()
                .filter(f -> f.field.equals("location"))
                .findFirst()
                .ifPresent(locationFilter -> {
                    filters.remove(locationFilter);
                    if (locationFilter.values != null && !locationFilter.values.isEmpty()) {
                        locationFilter.values.forEach(v -> validateIRI(v.toString()));
                        builder.append("?dir fs:belongsTo* ?location .\n FILTER (?location IN (")
                                .append(locationFilter.values.stream().map(v -> "<" + v + ">").collect(joining(", ")))
                                .append("))\n");
                    }
                });
    }

    private void addSingleFilter(ViewFilter filter, String entity, String filterField) {
        String condition, property, field;

        if (filterField.equals(entity)) {
            field = filterField + "_id";
            property = RDFS.label.toString();
            condition = toFilterString(filter, ViewsConfig.ColumnType.Identifier, field);
        } else {
            field = filterField;
            property = getColumn(field).source;
            condition = toFilterString(filter, getColumn(field).type, field);
        }

        builder.append("{\n");

        if (!entity.equals(view.name)) {
            builder.append("?")
                    .append(getEntityDirAlias(entity))
                    .append(" fs:linkedEntity ?")
                    .append(entity)
                    .append(" .\n");
            if (entityTypes.get(entity).size() == 1) {
                builder.append("?").append(entity)
                        .append(" a <")
                        .append(entityTypes.get(entity).get(0))
                        .append("> .\n");
            } else {
                builder.append("?").append(entity)
                        .append(" a ?type .\\nFILTER (?type IN (\")\n")
                        .append(entityTypes.get(entity).stream().map(t -> "<\" + t + \">").collect(joining(", ")))
                        .append("\n");
            }
        }

        entityColumnsWithSubquery.add(field);
        builder.append("?").append(entity)
                .append(" <")
                .append(property)
                .append("> ?")
                .append(field)
                .append(" .\n")
                .append(condition)
                .append("}\n");
    }

    private String toFilterString(ViewFilter filter, ColumnType type, String field) {
        // we can ignore the filter if we want to retrieve all column values for displaying in the View screen
        if (filter == null) {
            return "";
        }

        var variable = new ExprVar(field);

        Expr expr;
        if (filter.min != null && filter.max != null) {
            expr = new E_LogicalAnd(new E_GreaterThanOrEqual(variable, toNodeValue(filter.min, type)), new E_LessThanOrEqual(variable, toNodeValue(filter.max, type)));
        } else if (filter.min != null) {
            expr = new E_GreaterThanOrEqual(variable, toNodeValue(filter.min, type));
        } else if (filter.max != null) {
            expr = new E_LessThanOrEqual(variable, toNodeValue(filter.max, type));
        } else if (filter.values != null && !filter.values.isEmpty()) {
            List<Expr> values = filter.values.stream()
                    .map(o -> toNodeValue(o, type))
                    .collect(toList());
            expr = new E_OneOf(variable, new ExprList(values));
        } else if (filter.prefix != null && !filter.prefix.isBlank()) {
            expr = new E_StrStartsWith(new E_StrLowerCase(variable), makeString(filter.prefix.trim().toLowerCase()));
        } else {
            return null;
        }

        return new ElementFilter(expr).toString().concat("\n");
    }

    private String addSubqueryColumns(String query) {
        String subQueryColumns = entityColumnsWithSubquery.stream()
                .reduce("", (partialString, element) -> partialString + " ?" + element);

        return query.replace(PLACEHOLDER, subQueryColumns);
    }

    private NodeValue toNodeValue(Object o, ColumnType type) {
        return switch (type) {
            case Identifier, Term, TermSet -> makeNode(createURI(o.toString()));
            case Text, Set -> makeString(o.toString());
            case Number -> makeDecimal(o.toString());
            case Date -> makeDateTime(convertDateValue(o.toString()));
        };
    }

    private Calendar convertDateValue(String value) {
        var calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Instant.parse(value).toEpochMilli());
        return calendar;
    }

    private View.Column getColumn(String name) {
        var fieldNameParts = name.split("_");
        if (fieldNameParts.length != 2) {
            throw new IllegalArgumentException("Invalid field: " + name);
        }
        Optional<View.Column> column = Optional.empty();
        if (fieldNameParts[0].equals(view.name)) {
            column = view.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldNameParts[1]))
                    .findFirst();
        } else if (!view.joinColumns.isEmpty()) {
            column = view.joinColumns.stream()
                    .filter(c -> c.sourceClassName.equalsIgnoreCase(fieldNameParts[0]) && c.name.equalsIgnoreCase(fieldNameParts[1]))
                    .findFirst().map(c -> (View.Column) c);
        }

        return column.orElseThrow(() -> {
            log.error("Unknown column for view {}: {}", view.name, fieldNameParts[1]);
            log.error("Expected one of {}",
                    Stream.concat(view.columns.stream(), view.joinColumns.stream()).map(c -> c.name).collect(joining(", ")));
            throw new IllegalArgumentException("Unknown column for view " + view.name + ": " + fieldNameParts[1]);
        });
    }

    /**
     * The view contains join columns and view columns.
     * <p>
     * Both have type information, extract here for later usage, because client doesn't send it.
     */
    private void fetchTypes(View view) {
        if (view.types == null || view.types.isEmpty()) {
            throw new RuntimeException("View " + view.name + " has empty 'types' list");
        }

        entityTypes.put(view.name, view.types);

        view.joinColumns.forEach(column -> {
            entityTypes.put(column.sourceClassName, List.of(column.sourceClass));
        });
    }
}
