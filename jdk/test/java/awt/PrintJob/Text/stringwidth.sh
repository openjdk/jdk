#!/bin/ksh -p
#
#   @test      
#   @bug        4692562
#   @summary    Requirement: Windows with printer installed.  It should print with text "Hello World".
#   @compile StringWidth.java
#   @run shell/manual stringwidth.sh
#
OS=`uname`

status=1
checkstatus()
 {
  status=$?
  if [ $status -ne "0" ]; then
    exit "$status"
  fi
 }

# pick up the compiled class files.
if [ -z "${TESTCLASSES}" ]; then
  CP="."
else
  CP="${TESTCLASSES}"
fi


if [ $OS = SunOS -o $OS = Linux ]
then
    exit 0
fi
# Windows

if [ -z "${TESTJAVA}" ] ; then
   JAVA_HOME=../../../../../../build/windows-i586
else
   JAVA_HOME=$TESTJAVA
fi

    $JAVA_HOME/bin/java -cp "${CP}" StringWidth
    checkstatus

exit 0
