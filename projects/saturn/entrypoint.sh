#!/bin/sh
set -e

if [ "$USE_DOCKERCOMPOSE" = true ] ; then
cat > "application.yaml" <<EndOfMessage
---
port: 8090
publicUrl: ${FAIRSPACE_URL}
jena:
  metadataBaseIRI: ${FAIRSPACE_URL}/iri/
  datasetPath: "/data/saturn/db"
  storeParams:
    tdb.file_mode: "mapped"
    tdb.block_size: 8192
    tdb.block_read_cache_size: 5000
    tdb.block_write_cache_size: 1000
    tdb.node2nodeid_cache_size: 200000
    tdb.nodeid2node_cache_size: 750000
    tdb.node_miss_cache_size: 1000
    tdb.nodetable: "nodes"
    tdb.triple_index_primary: "SPO"
    tdb.triple_indexes:
      - "SPO"
      - "POS"
      - "OSP"
    tdb.quad_index_primary: "GSPO"
    tdb.quad_indexes:
      - "GSPO"
      - "GPOS"
      - "GOSP"
      - "POSG"
      - "OSPG"
      - "SPOG"
    tdb.prefixtable: "prefixes"
    tdb.prefix_index_primary: "GPU"
    tdb.prefix_indexes:
      - "GPU"
  transactionLogPath: "/data/saturn/files/log"
  bulkTransactions: true
auth:
  authServerUrl: ${KEYCLOAK_SERVER_URL}/auth/
  realm: ${KEYCLOAK_REALM}
  clientId: ${KEYCLOAK_CLIENT_ID}
  clientSecret: ${KEYCLOAK_CLIENT_SECRET}
  enableBasicAuth: true
  superAdminUser: organisation-admin
webDAV:
  blobStorePath: "/data/saturn/files/blobs"
viewDatabase:
  enabled: true
  url: jdbc:postgresql://fairspace-postgres:5432/fairspace
services: {}
features: []
EndOfMessage

CERTS_PATH="/opt/extra_certs.pem"

[[ -f "${CERTS_PATH}" ]] && \
  keytool -delete -alias certificate-alias -keystore "${JAVA_HOME}/lib/security/cacerts" -storepass changeit || true
  keytool -import -trustcacerts -file "${CERTS_PATH}" -alias certificate-alias -keystore "${JAVA_HOME}/lib/security/cacerts" -storepass changeit -noprompt
fi

/opt/saturn-*/bin/saturn
