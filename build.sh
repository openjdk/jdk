#!/bin/bash
if command -v scl_source >/dev/null; then
  set +e
  source scl_source enable devtoolset-10
fi

set -eux

bash configure \
  --without-version-pre \
  --without-version-opt \
  --with-version-build=8 \
  --with-vendor-version-string="(ByteOpenJDK)" \
  --with-vendor-name=ByteOpenJDK \
  --with-debug-level=release \
  --with-native-debug-symbols=none

make JOBS="${RESOURCE_CPU_LIMIT_ESTIMATE:-8}" all

ln -sf "$(find . -path "*-release/images/jdk")" output

arch=$(objdump="$(command -v objdump)" && objdump --file-headers "$objdump" | awk -F '[:,]+[[:space:]]+' '$1 == "architecture" { print $2 }')
case "$arch" in
'i386:x86-64')
  arch='x86_64'
  ;;
'aarch64')
  arch='aarch64'
  ;;
*)
  echo >&2 "error: unsupported architecture: '$arch'"
  exit 1
  ;;
esac

libserver=$(find . -path "*/images/jdk/lib")
wget -O zlib.tar.gz "https://luban-source.byted.org/repository/scm/api/v1/download_latest/?name=sys/ste/zlib&arch=${arch}"
mkdir -p zlib
tar -xzvf zlib.tar.gz -C zlib
mv -f zlib/lib/libz.so.1* "${libserver}/"
rm -rf zlib.tar.gz zlib/*

if [ ${arch} == "x86_64" ]; then
wget -O snappy-java.tar.gz "https://luban-source.byted.org/repository/scm/api/v1/download_latest/?name=sys/byteopenjdk/snappy_java&arch=x86_64"
mkdir -p snappy-java
tar -xzvf snappy-java.tar.gz -C snappy-java
mv -f snappy-java/libsnappyjava.so "output/lib/server/libsnappyjava.so"
rm -rf snappy-java
rm snappy-java.tar.gz
wget -O snappy-java.tar.gz "https://luban-source.byted.org/repository/scm/sys.byteopenjdk.snappy_java_1.0.0.32.tar.gz"
mkdir -p snappy-java
mkdir output/lib/server/debian8
tar -xzvf snappy-java.tar.gz -C snappy-java
mv -f snappy-java/libsnappyjava.so "output/lib/server/debian8/libsnappyjava.so"
rm -rf snappy-java
fi
