#!/bin/bash

DIST=${DIST:-focal}

ARGS=(
    --with-boot-jdk=/rivos/jdk
    --with-zlib=system
    --with-sysroot=/sysroot/$DIST-amd64
    --with-jmh=/workspace/jmh/build/images/jmh
    --with-jtreg=/workspace/jtreg/build/images/jtreg
    --with-hsdis=binutils --with-binutils-src=/workspace/binutils --enable-hsdis-bundling
)

bash configure ${ARGS[@]} $*
