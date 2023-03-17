#!/bin/bash

DIST=${DIST:-focal}

/sysroot/run-in.sh /sysroot/$DIST-amd64 make $*
