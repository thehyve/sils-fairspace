#!/bin/bash
set -e

root="$PWD"

echo 'Building the ssl proxy image'
$BUILD_SCRIPTS_DIR/docker/build.sh

cd "$root"
