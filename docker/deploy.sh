#!/usr/bin/env bash

root=$(realpath $(dirname "${0}"))

echo "(1/4) Building the Mercury frontend application..."
pushd "${root}/../projects/mercury"
yarn install
yarn build
popd

echo "(2/4) Building the Pluto proxy..."
pushd "${root}/../projects/pluto"
./gradlew clean assemble -x test
test -e build/mercury && rm -r build/mercury
cp -r ../mercury/build build/mercury
popd

echo "(3/4) Building the Saturn backend..."
pushd "${root}/../projects/saturn"
./gradlew clean build -x test
popd

pushd "${root}"

echo "(4/4) Building the images..."
(docker build fairspace-ssl-proxy -t fairspace-ssl-proxy-local:latest &&
docker build ../projects/pluto -t pluto-local:latest &&
docker build ../projects/saturn -t saturn-local:latest) || {
  echo "Build failed."
  popd
  exit 1
}

docker-compose -f docker-compose.yml -f keycloak-docker-compose.yml -f ssl-proxy-docker-compose.yml up -d

popd
