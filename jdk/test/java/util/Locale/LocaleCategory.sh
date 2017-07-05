#!/bin/sh
#
# @test
# @bug 4700857
# @summary tests for Locale.getDefault(Locale.Category) and 
#    Locale.setDefault(Locale.Category, Locale)
# @build LocaleCategory
# @run shell/timeout=600 LocaleCategory.sh

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
  Windows* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# test user.xxx.display user.xxx.format properties

# run
RUNCMD="${TESTJAVA}${FS}bin${FS}java -classpath ${TESTCLASSES} -Duser.language.display=ja -Duser.language.format=zh LocaleCategory"

echo ${RUNCMD}
${RUNCMD}
result=$?

if [ $result -eq 0 ]
then
  echo "Execution successful"
else
  echo "Execution of the test case failed."
fi

# test user.xxx properties overriding user.xxx.display/format

# run
RUNCMD="${TESTJAVA}${FS}bin${FS}java -classpath ${TESTCLASSES} -Duser.language=en -Duser.language.display=ja -Duser.language.format=zh LocaleCategory"

echo ${RUNCMD}
${RUNCMD}
result=$?

if [ $result -eq 0 ]
then
  echo "Execution successful"
else
  echo "Execution of the test case failed."
fi

exit $result
