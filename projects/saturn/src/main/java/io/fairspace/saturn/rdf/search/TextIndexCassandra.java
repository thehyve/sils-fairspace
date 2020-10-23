package io.fairspace.saturn.rdf.search;

import com.datastax.oss.driver.api.core.*;
import org.apache.jena.graph.Node;
import org.apache.jena.query.text.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.core.*;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.action.index.*;
import org.elasticsearch.action.update.*;
import org.elasticsearch.client.*;
import org.elasticsearch.index.query.*;
import org.elasticsearch.script.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;

import static com.google.common.collect.Iterables.*;
import static java.lang.Thread.*;
import static org.apache.jena.query.text.TextQueryFuncs.*;
import static org.elasticsearch.common.xcontent.XContentFactory.*;

/**
 * Cassandra implementation of {@link TextIndex}
 */
public class TextIndexCassandra implements TextIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextIndexCassandra.class);

    private final EntityDefinition docDef;
    private final CqlSession session;
    private final List<UpdateRequest> updates = new ArrayList<>();

    public TextIndexCassandra(TextIndexConfig config, CqlSession session) {
        this.docDef = config.getEntDef();
        this.session = session;
    }

    @Override
    public Map<String, Node> get(String uri) {
        // Not used
        throw new UnsupportedOperationException("TextIndex::get");
    }

    @Override
    public List<TextHit> query(Node property, String qs, String graphURI, String lang, int limit) {
        throw new UnsupportedOperationException("TextIndex::query");
    }

    @Override
    public List<TextHit> query(Node property, String qs, String graphURI, String lang) {
        throw new UnsupportedOperationException("TextIndex::query");
    }

    @Override
    public List<TextHit> query(Node property, String qs, String graphURI, String lang, int limit, String highlight) {
        return query(property, qs, graphURI, lang, limit);
    }

    @Override
    public List<TextHit> query(List<Resource> props, String qs, String graphURI, String lang, int limit, String highlight) {
        return query((String) null, props, qs, graphURI, lang, limit, highlight);
    }

    @Override
    public List<TextHit> query(Node subj, List<Resource> props, String qs, String graphURI, String lang, int limit, String highlight) {
        var subjectUri = subj == null || Var.isVar(subj) || !subj.isURI() ? null : subj.getURI();
        return query(subjectUri, props, qs, graphURI, lang, limit, highlight);
    }

    @Override
    public List<TextHit> query(String uri, List<Resource> props, String qs, String graphURI, String lang, int limit, String highlight) {
        var property = props == null || props.isEmpty() ? null : props.get(0).asNode();
        return query(property, qs, graphURI, lang, limit);
    }

    @Override
    public EntityDefinition getDocDef() {
        return docDef;
    }

    @Override
    public void prepareCommit() {
    }

    @Override
    public void commit() {
    }

    @Override
    public void rollback() {
        updates.clear();
    }

    @Override
    public void close() {
        updates.clear();
    }

    /**
     * Update an Entity. Since we are doing Upserts in add entity anyway, we simply call {@link #addEntity(Entity)}
     * method that takes care of updating the Entity as well.
     *
     * @param entity the entity to update.
     */
    @Override
    public void updateEntity(Entity entity) {
        //Since Add entity also updates the indexed document in case it already exists,
        // we can simply call the addEntity from here.
        addEntity(entity);
    }


    /**
     * Add or update an Entity to the view store.
     *
     * @param entity the entity to add or update
     */
    @Override
    public void addEntity(Entity entity) {
        LOGGER.trace("Adding/Updating the entity {} in Cassandra", entity.getId());
    }

    /**
     * Delete the value of the entity from the existing document, if any.
     * The document itself will never get deleted. Only the value will get deleted.
     *
     * @param entity entity whose value needs to be deleted
     */
    @Override
    public void deleteEntity(Entity entity) {

    }
}
