#!/bin/sh

STARTDIR=`dirname $0`

if [ "x$SA_JAVA" = "x" ]; then
   SA_JAVA=java
fi

if [ -f $STARTDIR/sa.jar ] ; then
  CP=$STARTDIR/sa.jar
else
  CP=$STARTDIR/../build/classes
fi

# License file for development version of dbx
setenv LM_LICENSE_FILE 7588@extend.eng:/usr/dist/local/config/sparcworks/license.dat:7588@setlicense

$SA_JAVA -Xbootclasspath/p:$CP -Djava.rmi.server.codebase=file:/$CP -Djava.security.policy=$STARTDIR\/grantAll.policy sun.jvm.hotspot.DebugServer $*
