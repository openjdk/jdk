#!/bin/sh
#
# @test
# @bug 6332666
# @summary tests the capability of replacing the currency data with user
#     specified currency properties file
# @build PropertiesTest
# @run shell/timeout=600 PropertiesTest.sh

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
echo "TESTCLASSES=${TESTCLASSES}"
echo "CLASSPATH=${CLASSPATH}"

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    PS=":"
    FS="/"
    ;;
  Windows* | CYGWIN* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# Currency dump path #1.  Just dump currencies with the bare JRE

# run
RUNCMD="${TESTJAVA}${FS}bin${FS}java -classpath ${TESTCLASSES} PropertiesTest -d dump1"

echo ${RUNCMD}
${RUNCMD}
result=$?

if [ $result -eq 0 ]
then
  echo "Execution successful"
else
  echo "Execution of the test case failed."
fi

# Currency dump path #2.  Dump currencies using the JRE with replacement currencies

# copy the test properties file
COPIED=0
if [ -w $TESTJAVA ]
then 
  WRITABLEJDK=$TESTJAVA
else
  WRITABLEJDK=.${FS}testjava 
  cp -r $TESTJAVA $WRITABLEJDK
  COPIED=1
fi

if [ -d ${WRITABLEJDK}${FS}jre ]
then
  PROPLOCATION=${WRITABLEJDK}${FS}jre${FS}lib
else
  PROPLOCATION=${WRITABLEJDK}${FS}lib
fi
cp ${TESTSRC}${FS}currency.properties $PROPLOCATION

# run
RUNCMD="${WRITABLEJDK}${FS}bin${FS}java -classpath ${TESTCLASSES} PropertiesTest -d dump2"

echo ${RUNCMD}
${RUNCMD}
result=$?

if [ $result -eq 0 ]
then
  echo "Execution successful"
else
  echo "Execution of the test case failed."
fi

# Now compare the two dump files

RUNCMD="${WRITABLEJDK}${FS}bin${FS}java -classpath ${TESTCLASSES} PropertiesTest -c dump1 dump2"

echo ${RUNCMD}
${RUNCMD}
result=$?

if [ $result -eq 0 ]
then
  echo "Execution successful"
else
  echo "Execution of the test case failed."
fi

# Cleanup
rm -f dump1
rm -f dump2
rm -f ${PROPLOCATION}${FS}currency.properties
if [ $COPIED -eq 1 ]
then
  rm -rf $WRITABLEJDK
fi

exit $result
