package io.fairspace.saturn.services.views;

import io.fairspace.saturn.config.ViewsConfig;
import io.fairspace.saturn.config.ViewsConfig.ColumnType;
import io.fairspace.saturn.config.ViewsConfig.View;
import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.DavUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.vocabulary.RDF;
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
public class SparqlViewQueryBuilder {
    private final StringBuilder builder = new StringBuilder();
    private final Function<String, View> getView;
    private final String RESOURCES_VIEW;
    private final HashMap<String, List<String>> entityTypes = new HashMap<>();
    private static final HashMap<String, String> parents = DavUtils.getHierarchyItemParents();

    public SparqlViewQueryBuilder(Function<String, View> getView, String resourcesView) {
        this.getView = getView;
        RESOURCES_VIEW = resourcesView;
    }

    public Query getQuery(View view, List<ViewFilter> viewFilters) {
        fetchTypes(view);

        appNamespace("fs", FS.NS);
        appNamespace("rdf", RDF.getURI());
        appNamespace("rdfs", RDFS.getURI());
        appNamespace("sh", SHACL.NS);

        builder.append("\n");

        builder.append("SELECT DISTINCT ?")
                .append(view.name)
                .append("\nWHERE {\n");

        builder.append("?dir fs:linkedEntity ?entity .\n")
                .append("?subdir (fs:belongsTo)+ ?dir .\n")
                .append("?subdir fs:linkedEntity ?")
                .append(view.name)
                .append(" .\n");

//      TODO: add location filters
//      AddLocationFilter(view, builder, viewFilters);

        addFacetFilters(view, viewFilters);

        builder.append("?")
                .append(view.name)
                .append(" a ?type .\nFILTER (?type IN (")
                .append(view.types.stream().map(t -> "<" + t + ">").collect(joining(", ")))
                .append("))\nFILTER NOT EXISTS { ?")
                .append(view.name)
                .append(" fs:dateDeleted ?any }\n}");

        return QueryFactory.create(builder.toString());
    }

    private void appNamespace(String alias, String namespace) {
        builder.append("PREFIX ")
                .append(alias)
                .append(": <")
                .append(namespace)
                .append(">\n");
    }

    private void addFacetFilters(ViewsConfig.View view, List<ViewFilter> filters) {
        var entitiesWithFilter = filters.stream()
                .map(f -> f.field)
                .sorted(comparing(field -> field.contains("_") ? getColumn(field, view.name).priority : 0))
                .map(field -> field.split("_")[0])
                .distinct()
                .toList();

        for (String entity : entitiesWithFilter) {
            var entityFilters = filters.stream()
                    .filter(f -> f.getField().startsWith(entity))
                    .sorted(comparing(f -> f.field.contains("_") ? getColumn(f.field, view.name).priority : 0))
                    .toList();

            var isLinkedEntity = !entity.equals(view.name);
            addSingleFacetFilter(entityFilters, entity, isLinkedEntity, view.types);
        }

        builder.append("\n");
    }

    private void addLocationFilter(ViewsConfig.View view, ArrayList<ViewFilter> filters) {
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

    private void addSingleFacetFilter(List<ViewFilter> filters, String entity, boolean isLinkedEntity, List<String> types) {
        String postfix = "_" + entity;

        for (var filter : filters) {
            builder.append("FILTER EXISTS {\n")
                    .append("?")
                    .append(entity)
                    .append(" ^fs:linkedEntity ?linkedDir")
                    .append(postfix)
                    .append(" .\n");

            if (isLinkedEntity) {
                var entityLocatedTowardRoot = types.stream()
                        .anyMatch((viewType) -> entityLocatedTowardRoot(entityTypes.get(entity).get(0), viewType));

                var searchDirection = entityLocatedTowardRoot ? "^" : "";

                builder.append("?linkedSubdir")
                        .append(postfix)
                        .append(" fs:linkedEntity ?facetEntity")
                        .append(postfix)
                        .append(" .\n")
                        .append("?linkedSubdir")
                        .append(postfix)
                        .append(" (")
                        .append(searchDirection)
                        .append("fs:belongsTo)+ ?linkedDir")
                        .append(postfix)
                        .append(" .\n");
            } else {
                builder.append("?linkedDir")
                        .append(postfix)
                        .append(" fs:linkedEntity ?facetEntity")
                        .append(postfix)
                        .append(" .\n");
            }

            if (entityTypes.get(entity).size() == 1) {
                builder.append("?facetEntity").append(postfix).append(" rdf:type <").append(entityTypes.get(entity).get(0)).append("> .\n");
            } else {
                builder.append("?facetEntity").append(postfix).append(" a ?type .\\nFILTER (?type IN (\")\n")
                        .append(entityTypes.get(entity).stream().map(t -> "<\" + t + \">").collect(joining(", ")))
                        .append("\n");
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
            if (column.sourceClass.isBlank()) {
                throw new RuntimeException("joinColumn " + column.name + " has no 'sourceClass'");
            }

            entityTypes.put(column.sourceClassName, List.of(column.sourceClass));
        });
    }

    /**
     * We have a tree structure of directories. The sparql queries we use need to now in which direction to
     * traverse the 'belongsTo' properties. Either toward the root or in opposite direction (and use a '^' in sparql)
     * <p>
     * Here we determine the position of the target directory, is it between current node and root, or between current
     * node and endnodes.
     */
    private Boolean entityLocatedTowardRoot(String treeLocation, String targetEntity) {
        var parent = parents.get(treeLocation);

        if (parent.isEmpty()) {
            return false;
        }
        if (parent.equals(targetEntity)) {
            return true;
        }

        return entityLocatedTowardRoot(parent, targetEntity);
    }
}