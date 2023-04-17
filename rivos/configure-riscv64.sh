#!/bin/bash

ARGS=(
    --openjdk-target=riscv64-linux-gnu
    --with-boot-jdk=/rivos/jdk
    --with-build-jdk=/rivos/jdk
    # --with-debug-level=slowdebug
    --with-zlib=system
    --with-sysroot=/sysroot/focal-riscv64
    --with-jmh=/workspace/jmh/build/images/jmh
    --with-jtreg=/workspace/jtreg/build/images/jtreg
    --with-hsdis=binutils --with-binutils-src=/workspace/binutils --enable-hsdis-bundling
)

bash configure ${ARGS[@]} $*
