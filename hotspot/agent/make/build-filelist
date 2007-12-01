#!/bin/sh -f

SH=`which sh`
MKS_HOME=`dirname $SH`

CD=cd
FIND=$MKS_HOME/find
SORT=$MKS_HOME/sort

$CD ../src/share/classes; $FIND sun \( -name SCCS -prune \) -o \( -name "*.java" \) -print | $SORT > ../../../make/filelist.txt
