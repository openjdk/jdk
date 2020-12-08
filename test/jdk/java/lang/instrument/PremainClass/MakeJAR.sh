#!/bin/sh

PKG="$1"
AGENT="$2"
OTHER="$3"
shift 3

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTSRC=${TESTSRC}"

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTJAVA=${TESTJAVA}"

if [ "${COMPILEJAVA}" = "" ]
then
  COMPILEJAVA="${TESTJAVA}"
fi
echo "COMPILEJAVA=${COMPILEJAVA}"

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTCLASSES=${TESTCLASSES}"

JAR="${COMPILEJAVA}/bin/jar"

echo "Manifest-Version: 1.0"        >  ${AGENT}.mf
echo Premain-Class: ${PKG}.${AGENT} >> ${AGENT}.mf
while [ $# != 0 ] ; do
  echo $1 >> ${AGENT}.mf
  shift
done

${JAR} ${TESTTOOLVMOPTS} cvfm ${AGENT}.jar ${AGENT}.mf \
       ${TESTCLASSES}/${PKG}/${AGENT}*.class ${TESTSRC}/${OTHER}*.java
