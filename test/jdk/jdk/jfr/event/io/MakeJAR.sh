#!/bin/sh

AGENT="$1"

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

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ -z "${COMPILEJAVA}" ]
then
  COMPILEJAVA=${TESTJAVA}
fi

JAVAC="${COMPILEJAVA}/bin/javac -g"
JAR="${COMPILEJAVA}/bin/jar"

cp ${TESTSRC}/${AGENT}.java .
${JAVAC} -cp ${TESTCLASSPATH} ${AGENT}.java

echo "Manifest-Version: 1.0"    >  ${AGENT}.mf
echo Premain-Class: jdk.jfr.event.io.${AGENT} >> ${AGENT}.mf
shift
while [ $# != 0 ] ; do
  echo $1 >> ${AGENT}.mf
  shift
done


${JAR} cvfm ${AGENT}.jar ${AGENT}.mf ${AGENT}*.class
