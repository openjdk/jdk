#!/bin/bash

DIST=${DIST:-focal}

ARGS=(
    --openjdk-target=riscv64-linux-gnu
    --with-boot-jdk=/rivos/jdk
    --with-build-jdk=/workspace/jdk/build/linux-x86_64-server-release/images/jdk
    # --with-debug-level=slowdebug
    --with-zlib=system
    --with-sysroot=/sysroot/$DIST-riscv64
    --with-jmh=/workspace/jmh/build/images/jmh
    --with-jtreg=/workspace/jtreg/build/images/jtreg
    --with-hsdis=binutils --with-binutils-src=/workspace/binutils --enable-hsdis-bundling
)

/sysroot/run-in.sh /sysroot/$DIST-amd64 bash configure ${ARGS[@]} $*
