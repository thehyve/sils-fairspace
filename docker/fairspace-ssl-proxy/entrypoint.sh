#!/bin/sh
set -e

mkdir -p /etc/nginx/sites-enabled

if [[ ! -z ${FAIRSPACE_HOSTNAME} ]]; then
cat > /etc/nginx/sites-enabled/fairspace.conf <<EndOfMessage
   server {
     listen 443 ssl;
     server_name           ${FAIRSPACE_HOSTNAME};
     ssl_certificate       /etc/nginx/server.pem;
     ssl_certificate_key   /etc/nginx/server.key;
     index                 index.html;
     location / {
       proxy_pass            http://172.17.0.1:9080/;
       proxy_read_timeout    90s;
       proxy_connect_timeout 90s;
       proxy_send_timeout    90s;
       proxy_set_header      X-Real-IP \$remote_addr;
       proxy_set_header      X-Forwarded-For \$proxy_add_x_forwarded_for;
       proxy_set_header      X-Forwarded-Proto \$scheme;
       proxy_set_header      Host \$host;
       proxy_set_header      Proxy "";
       proxy_redirect        default;
     }
   }
EndOfMessage
cat > /etc/nginx/sites-enabled/fw-fairspace.conf <<EndOfMessage
   server {
     listen 80;
     server_name ${FAIRSPACE_HOSTNAME};
     return 301 https://${FAIRSPACE_HOSTNAME}\$request_uri;
   }
EndOfMessage
fi
if [[ ! -z ${KEYCLOAK_HOSTNAME} ]]; then
cat > /etc/nginx/sites-enabled/keycloak.conf <<EndOfMessage
   server {
     listen 443 ssl;
     server_name           ${KEYCLOAK_HOSTNAME};
     ssl_certificate       /etc/nginx/server.pem;
     ssl_certificate_key   /etc/nginx/server.key;
     index                 index.html;
     location / {
       proxy_pass            http://172.17.0.1:8080/;
       proxy_read_timeout    90s;
       proxy_connect_timeout 90s;
       proxy_send_timeout    90s;
       proxy_set_header      X-Real-IP \$remote_addr;
       proxy_set_header      X-Forwarded-For \$proxy_add_x_forwarded_for;
       proxy_set_header      X-Forwarded-Proto \$scheme;
       proxy_set_header      Host \$host;
       proxy_redirect        default;
     }
   }
EndOfMessage
cat > /etc/nginx/sites-enabled/fw-keycloak.conf <<EndOfMessage
   server {
     listen 80;
     server_name ${KEYCLOAK_HOSTNAME};
     return 301 https://${KEYCLOAK_HOSTNAME}\$request_uri;
   }
EndOfMessage
fi

sync

unset KEYCLOAK_HOSTNAME
unset FAIRSPACE_HOSTNAME

exec nginx -g 'daemon off;'
