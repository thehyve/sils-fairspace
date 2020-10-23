package io.fairspace.saturn.rdf.search;

import com.datastax.oss.driver.api.core.*;
import lombok.extern.slf4j.*;

@Slf4j
public class ViewStoreClientFactory {
    /**
     * Builds a client for connecting to Cassandra for the metadata views.
     */
    public static CqlSession build() {
        log.debug("Initializing the Cassandra client");
        CqlSession session = CqlSession.builder()
                .withKeyspace(CqlIdentifier.fromCql("fairspace"))
                .build();

        log.info("Cassandra session: {} {}", session.getName(), session.getMetadata().toString());

        return session;
    }
}
