#!/bin/sh -f

SH=`which sh`
MKS_HOME=`dirname $SH`

CD=cd
FIND=$MKS_HOME/find
SED=$MKS_HOME/sed
SORT=$MKS_HOME/sort

$CD ../src/share/classes; $FIND sun/jvm/hotspot com/sun/java/swing -type d -print | $SED -e 's/\//./g' | $SORT > ../../../make/pkglist.txt
