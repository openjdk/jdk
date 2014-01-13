#
#  Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
#  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#


#
# @test Test7110720.sh
# @bug 7110720
# @summary improve VM configuration file loading
# @run shell Test7110720.sh
#

if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

# Jtreg sets TESTVMOPTS which may include -d64 which is
# required to test a 64-bit JVM on some platforms.
# If another test harness still creates HOME/JDK64BIT,
# we can recognise that.

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    FS="/"
    RM=/bin/rm
    CP=/bin/cp
    MV=/bin/mv
    ## for solaris, linux it's HOME
    FILE_LOCATION=$HOME
    if [ -f ${FILE_LOCATION}${FS}JDK64BIT -a ${OS} = "SunOS" ]
    then
        TESTVMOPTS=`cat ${FILE_LOCATION}${FS}JDK64BIT`
    fi
    ;;
  Windows_* )
    FS="\\"
    RM=rm
    CP=cp
    MV=mv
    ;;
  CYGWIN_* )
    FS="/"
    RM=rm
    CP=cp
    MV=mv
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac


JAVA=${TESTJAVA}${FS}bin${FS}java

# Don't test debug builds, they do read the config files:
${JAVA} ${TESTVMOPTS} -version 2>&1 | grep "debug" >/dev/null
if [ "$?" = "0" ]; then
  echo Skipping test for debug build.
  exit 0
fi

ok=yes

$RM -f .hotspot_compiler .hotspotrc

${JAVA} ${TESTVMOPTS} -version 2>&1 | grep "garbage in" >/dev/null
if [ "$?" = "0" ]; then
  echo "FAILED: base case failure"
  exit 1
fi


echo "garbage in, garbage out" > .hotspot_compiler
${JAVA} ${TESTVMOPTS} -version 2>&1 | grep "garbage in" >/dev/null
if [ "$?" = "0" ]; then
  echo "FAILED: .hotspot_compiler was read"
  ok=no
fi

$MV .hotspot_compiler hs_comp.txt
${JAVA} ${TESTVMOPTS} -XX:CompileCommandFile=hs_comp.txt -version 2>&1 | grep "garbage in" >/dev/null
if [ "$?" = "1" ]; then
  echo "FAILED: explicit compiler command file not read"
  ok=no
fi

$RM -f .hotspot_compiler hs_comp.txt

echo "garbage" > .hotspotrc
${JAVA} ${TESTVMOPTS} -version 2>&1 | grep "garbage" >/dev/null
if [ "$?" = "0" ]; then
  echo "FAILED: .hotspotrc was read"
  ok=no
fi

$MV .hotspotrc hs_flags.txt
${JAVA} ${TESTVMOPTS} -XX:Flags=hs_flags.txt -version 2>&1 | grep "garbage" >/dev/null
if [ "$?" = "1" ]; then
  echo "FAILED: explicit flags file not read"
  ok=no
fi

if [ "${ok}" = "no" ]; then 
  echo "Some tests failed."
  exit 1
else 
  echo "Passed"
  exit 0
fi

