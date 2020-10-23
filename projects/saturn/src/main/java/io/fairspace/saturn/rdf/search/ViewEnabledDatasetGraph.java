package io.fairspace.saturn.rdf.search;

import com.datastax.oss.driver.api.core.*;
import org.apache.jena.query.text.*;
import org.apache.jena.sparql.core.*;

public class ViewEnabledDatasetGraph extends DatasetGraphText {
    public ViewEnabledDatasetGraph(DatasetGraph dsg) {
        this(dsg, ViewStoreClientFactory.build());
    }

    public ViewEnabledDatasetGraph(DatasetGraph dsg, CqlSession session) {
        this(dsg, new TextIndexCassandra(new TextIndexConfig(new AutoEntityDefinition()), session));
    }

    public ViewEnabledDatasetGraph(DatasetGraph dsg, TextIndex index) {
        super(dsg, index, new SingleTripleTextDocProducer(index), true);

        getContext().set(TextQuery.textIndex, index);
    }
}
