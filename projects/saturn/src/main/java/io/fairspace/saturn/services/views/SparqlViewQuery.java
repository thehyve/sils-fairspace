package io.fairspace.saturn.services.views;

import io.fairspace.saturn.config.ViewsConfig;
import io.fairspace.saturn.config.ViewsConfig.ColumnType;
import io.fairspace.saturn.config.ViewsConfig.View;
import io.fairspace.saturn.vocabulary.FS;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.vocabulary.RDFS;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fairspace.saturn.util.ValidationUtils.validateIRI;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.sparql.expr.NodeValue.*;
import static org.apache.jena.sparql.expr.NodeValue.makeDateTime;

@Log4j2
public class SparqlViewQuery {
    private final StringBuilder builder = new StringBuilder();
    private final Function<String, View> getView;
    private final String RESOURCES_VIEW;
    private final Hashtable<String, String> entityTypes = new Hashtable<>();

    public SparqlViewQuery(Function<String, View> getView, String resourcesView) {
        this.getView = getView;
        RESOURCES_VIEW = resourcesView;
    }

    public Query getQuery(View view, List<ViewFilter> viewFilters) {
        FetchTypes(view);

        AppNamespace("fs", FS.NS);
        AppNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        AppNamespace("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        AppNamespace("sils", "https://sils.uva.nl/ontology#");
        AppNamespace("sh", "http://www.w3.org/ns/shacl#");

        builder.append("\n");

        builder.append("SELECT DISTINCT ?")
                .append(view.name)
                .append("\nWHERE {\n");

        builder.append("?dir fs:linkedEntity ?entity .\n")
                .append("?subdir (fs:belongsTo)+ ?dir .\n")
                .append("?subdir fs:linkedEntity ?").append(view.name).append(" .\n");
        if (viewFilters != null) {
            var filters = new ArrayList<>(viewFilters);

//          TODO: add location filters
//          AddLocationFilter(view, builder, filters);

            AddFacetFilters(view, filters);
        }

        builder.append("?")
                .append(view.name)
                .append(" a ?type .\nFILTER (?type IN (")
                .append(view.types.stream().map(t -> "<" + t + ">").collect(joining(", ")))
                .append("))\nFILTER NOT EXISTS { ?")
                .append(view.name)
                .append(" fs:dateDeleted ?any }\n}");

        return QueryFactory.create(builder.toString());
    }

    private void FetchTypes(View view) {
        // TODO: discuss how to handle multiple types with same name
        entityTypes.put(view.name, view.types.get(0));

        view.joinColumns.forEach(column -> entityTypes.put(column.sourceClassName, column.sourceClass));
    }

    private void AppNamespace(String alias, String namespace) {
        builder.append("PREFIX ")
                .append(alias)
                .append(": <")
                .append(namespace)
                .append(">\n");
    }

    private void AddFacetFilters(ViewsConfig.View view, ArrayList<ViewFilter> filters) {
        var entitiesWithFilter = filters.stream()
                .map(f -> f.field)
                .sorted(comparing(field -> field.contains("_") ? getColumn(field, view.name).priority : 0))
                .map(field -> field.split("_")[0])
                .distinct()
                .toList();

        int entityNumber = 1;

        for (String entity : entitiesWithFilter) {
            var entityFilters = filters.stream()
                    .filter(f -> f.getField().startsWith(entity))
                    .sorted(comparing(f -> f.field.contains("_") ? getColumn(f.field, view.name).priority : 0))
                    .toList();

            var isLinkedEntity = !entity.equals(view.name);
            AddFacetFilter(entityFilters, entity, isLinkedEntity, entityNumber);
            entityNumber++;
        }

        builder.append("\n");
    }

    private void AddLocationFilter(ViewsConfig.View view, ArrayList<ViewFilter> filters) {
        filters.stream()
                .filter(f -> f.field.equals("location"))
                .findFirst()
                .ifPresent(locationFilter -> {
                    filters.remove(locationFilter);

                    if (locationFilter.values != null && !locationFilter.values.isEmpty()) {
                        locationFilter.values.forEach(v -> validateIRI(v.toString()));
                        var fileLink = view.join.stream().filter(v -> v.view.equals(RESOURCES_VIEW))
                                .findFirst().orElse(null);
                        if (fileLink != null) {
                            builder.append("FILTER EXISTS {\n")
                                    .append("?file fs:belongsTo* ?location .\n FILTER (?location IN (")
                                    .append(locationFilter.values.stream().map(v -> "<" + v + ">").collect(joining(", ")))
                                    .append("))\n ?file <")
                                    .append(fileLink.on)
                                    .append("> ?")
                                    .append(view.name)
                                    .append(" . \n")
                                    .append("}\n");
                        } else {
                            builder.append("?").append(view.name)
                                    .append(" fs:belongsTo* ?location .\n FILTER (?location IN (")
                                    .append(locationFilter.values.stream().map(v -> "<" + v + ">").collect(joining(", ")))
                                    .append("))\n");
                        }
                    }
                });
    }

    private void AddFacetFilter(List<ViewFilter> filters, String entity, boolean isLinkedEntity, int entityNumber) {
        String postfix = String.format("%3s", entityNumber).replace(' ', '0');

        for (var filter : filters) {
            if (isLinkedEntity) {
                builder.append("?").append(entity).append(" ^fs:linkedEntity ?linkedDir").append(postfix).append(" .\n")
                        .append("{\n")
                        .append("?linkedSubdir").append(postfix).append(" fs:linkedEntity ?facetEntity").append(postfix).append(" .\n")
                        .append("?linkedSubdir").append(postfix).append(" (^fs:belongsTo)+ ?linkedDir").append(postfix).append(" .\n")
                        .append("?facetEntity").append(postfix).append(" rdfs:label ?facetEntityName .\n")
                        .append("?facetEntity").append(postfix).append(" rdf:type <").append(entityTypes.get(entity)).append(">")
                        .append(" .\n");
            } else {
                builder.append("?").append(entity).append(" ^fs:linkedEntity ?linkedDir").append(postfix).append(" .\n")
                        .append("{\n")
                        .append("?linkedDir").append(postfix).append(" fs:linkedEntity ?facetEntity").append(postfix).append(" .\n")
                        .append("?facetEntity").append(postfix).append(" rdfs:label ?facetEntityName .\n")
                        .append("?facetEntity").append(postfix).append(" rdf:type sils:").append(entity)
                        .append(" .\n");
            }

            String condition, property, field;

            if (filter.getField().equals(entity)) {
                field = filter.field + "_id";
                property = RDFS.label.toString();
                condition = toFilterString(filter, ViewsConfig.ColumnType.Identifier, field);
            } else {
                field = filter.field;
                property = getColumn(field, entity).source;
                condition = toFilterString(filter, getColumn(field, entity).type, filter.field);
            }
            builder.append("?facetEntity").append(postfix).append(" <").append(property).append("> ?").append(field).append(postfix).append(" .\n");
            builder.append(condition);
            builder.append("\n");
            builder.append("}");
            builder.append("\n");
        }
    }

    private String toFilterString(ViewFilter filter, ColumnType type, String field) {
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

        return new ElementFilter(expr).toString();
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

    private View.Column getColumn(String name, String viewName) {
        var fieldNameParts = name.split("_");
        if (fieldNameParts.length != 2) {
            throw new IllegalArgumentException("Invalid field: " + name);
        }
        var viewConfig = getView.apply(viewName);
        Optional<View.Column> column = Optional.empty();
        if (viewName.equals(fieldNameParts[0])) {
            column = viewConfig.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldNameParts[1]))
                    .findFirst();
        } else if (!viewConfig.joinColumns.isEmpty()) {
            column = viewConfig.joinColumns.stream()
                    .filter(c -> c.sourceClassName.equalsIgnoreCase(fieldNameParts[0]) && c.name.equalsIgnoreCase(fieldNameParts[1]))
                    .findFirst().map(c -> (View.Column) c);
        }
        return column.orElseThrow(() -> {
            log.error("Unknown column for view {}: {}", viewName, fieldNameParts[1]);
            log.error("Expected one of {}",
                    Stream.concat(viewConfig.columns.stream(), viewConfig.joinColumns.stream()).map(c -> c.name).collect(joining(", ")));
            throw new IllegalArgumentException("Unknown column for view " + viewName + ": " + fieldNameParts[1]);
        });
    }
}
