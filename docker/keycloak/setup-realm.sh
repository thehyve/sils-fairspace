cat /tmp/realm-template.json | \
  sed "s|\${KEYCLOAK_REALM}|${KEYCLOAK_REALM}|" | \
  sed "s|\${KEYCLOAK_CLIENT_ID}|${KEYCLOAK_CLIENT_ID}|" | \
  sed "s|\${FAIRSPACE_URL}|${FAIRSPACE_URL}|" | \
  > /tmp/realm-export.json
