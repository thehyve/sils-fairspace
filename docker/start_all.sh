#!/usr/bin/env bash

root=$(realpath $(dirname "${0}"))

pushd "${root}"
docker-compose -f docker-compose.yml -f keycloak-docker-compose.yml -f ssl-proxy-docker-compose.yml up -d
popd
