#!/bin/sh
set -e

if [ "$USE_DOCKERCOMPOSE" = true ] ; then
cat > "application.yaml" <<EndOfMessage
---
server.port: 9080
pluto:
  domains:
    - ${FAIRSPACE_URL}
  downstreamServiceHealthUrl: http://fairspace-saturn:8090/api/health/
  oauth2:
    base-url: ${KEYCLOAK_SERVER_URL}
    realm: ${KEYCLOAK_REALM}

management:
  endpoint:
    health:
      probes:
        enabled: true

logging:
  level:
    root: ${PLUTO_LOGLEVEL}

server:
  error:
    whitelabel:
      enabled: false
  max-http-header-size: 65535

security:
  oidc:
    redirect-after-logout-url: ${FAIRSPACE_URL}/login
    clientId: ${KEYCLOAK_CLIENT_ID}
    clientSecret: "${KEYCLOAK_CLIENT_SECRET}"

zuul:
  retryable: false
  host:
    connect-timeout-millis: ${PLUTO_CONNECT_TIMEOUT_MILLIS}
    socket-timeout-millis: ${PLUTO_SOCKET_TIMEOUT_MILLIS}
  routes:
    saturn:
      path: /api/**
      url: http://fairspace-saturn:8090
      strip-prefix: false
  add-proxy-headers: false
EndOfMessage

CERTS_PATH="/opt/extra_certs.pem"

[[ -f "${CERTS_PATH}" ]] && \
  keytool -delete -alias certificate-alias -keystore "${JAVA_HOME}/lib/security/cacerts" -storepass changeit || true
  keytool -import -trustcacerts -file "${CERTS_PATH}" -alias certificate-alias -keystore "${JAVA_HOME}/lib/security/cacerts" -storepass changeit -noprompt
fi

/opt/pluto-boot-*/bin/pluto
