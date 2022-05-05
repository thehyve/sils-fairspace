package io.fairspace.saturn.services.views;

import org.apache.jena.rdf.model.RDFNode;

import java.util.*;

public class JoinColumnData {
    private final HashMap<String, HashMap<String, HashSet<RDFNode>>> columnValues = new HashMap<>();

    public void addValue(String resourceIri, String columnName, RDFNode value) {
        if(!columnValues.containsKey(resourceIri)) {
            columnValues.put(resourceIri, new HashMap<>());
        }

        if(!columnValues.get(resourceIri).containsKey(columnName)) {
            columnValues.get(resourceIri).put(columnName, new HashSet<>());
        }

        columnValues.get(resourceIri).get(columnName).add(value);
    }

    public List<RDFNode> get(String resourceIri, String column) {
        return columnValues.get(resourceIri).get(column).stream().toList();
    }

    public List<RDFNode> find(String resourceIri, String column) {
        var columns = columnValues.get(resourceIri);
        if(columns != null) {
            var columnValues = columns.get(column);
            if (columnValues != null) {
                return columnValues.stream().toList();
            }
        }

        return new ArrayList<RDFNode>();
    }
}
