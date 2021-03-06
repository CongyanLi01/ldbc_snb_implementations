package com.ldbc.snb.janusgraph.importer;

import com.ldbc.snb.janusgraph.importer.utils.LoadingStats;
import org.janusgraph.core.JanusGraphTransaction;
import org.janusgraph.core.JanusGraphVertex;
import org.janusgraph.core.SchemaViolationException;
import org.janusgraph.graphdb.database.StandardJanusGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Function;

/**
 * Created by aprat on 9/06/17.
 */
public class VertexLoadingTask extends LoadingTask {

    public StandardJanusGraph graph = null;
    public WorkLoadSchema schema = null;
    public String vertexLabel = null;
    private Logger logger = LoggerFactory.getLogger("org.janusgraph");
    private Function<String, Object> parsers[];
    private String propertyNames[];
    private LoadingStats stats;
    private long numLoaded = 0;
    private long numPropertiesLoaded = 0;

    JanusGraphTransaction transaction;

    public VertexLoadingTask(StandardJanusGraph graph, WorkLoadSchema schema, String vertexLabel, LoadingStats stats, String header, String[] rows, int numRows) {
        super(header,rows, numRows);
        this.graph = graph;
        this.schema = schema;
        this.vertexLabel = vertexLabel;
        this.stats = stats;
    }

    @Override
    protected void validateHeader(String[] header) throws SchemaViolationException {
        Set<String> props = schema.getVertexProperties().get(vertexLabel);
        if (props == null)
            throw new SchemaViolationException("No properties found for the vertex label " + vertexLabel);

        for (String col : header) {
            if (!props.contains(col)) {
                throw new SchemaViolationException("Unknown property for vertex Type " + vertexLabel
                        + ", found " + col + " expected " + props);
            }

            String colName = col;
            if(col.compareTo("id") == 0) {
                colName = vertexLabel + "." + col;
            }
            if (schema.getPropertyClass(colName) == null)
                throw new SchemaViolationException("Class definition missing for " + vertexLabel + "." + col);
        }

        Class[] classes = new Class[header.length];
        for (int i = 0; i < header.length; i++) {
            if(header[i].compareTo("id") == 0) {
                classes[i] = schema.getPropertyClass(vertexLabel+"."+header[i]);
            } else {
                classes[i] = schema.getPropertyClass(header[i]);
            }
        }

        // Obtaining parsers for the fields and property names
        parsers = new Function[header.length];
        propertyNames = new String[header.length];
        for (int i = 0; i < header.length; ++i) {
            parsers[i] = Parsers.getParser(classes[i]);
            propertyNames[i] = "";
            if(header[i].compareTo("id") == 0) {
                propertyNames[i] = vertexLabel + "." + header[i];
            } else {
                propertyNames[i] = header[i];
            }
        }

        transaction = graph.newThreadBoundTransaction()  ;
    }

    @Override
    protected void parseRow(String[] row) {
        JanusGraphVertex vertex = transaction.addVertex(vertexLabel);
        for (int i = 0; i < row.length; ++i) {
            Object value = parsers[i].apply(row[i]);
            vertex.property(propertyNames[i], value);
            numPropertiesLoaded++;
        }
        numLoaded++;
    }

    @Override
    protected void afterRows() {
        transaction.commit();
        stats.addVertices(numLoaded);
        stats.addProperties(numPropertiesLoaded);
    }

}
