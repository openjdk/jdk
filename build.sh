#!/bin/bash
if command -v scl_source >/dev/null; then
  set +e
  source scl_source enable devtoolset-10
fi

set -eux

bash configure \
  --without-version-pre \
  --without-version-opt \
  --with-version-build=31 \
  --with-vendor-version-string="(ByteOpenJDK)" \
  --with-vendor-name=ByteOpenJDK \
  --with-debug-level=release \
  --with-native-debug-symbols=none

make JOBS="${RESOURCE_CPU_LIMIT_ESTIMATE:-8}" all

ln -sf "$(find . -path "*-release/images/jdk")" output
