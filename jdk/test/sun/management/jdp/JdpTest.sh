#!/bin/sh -x

# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

# @test
# @bug 7169888
# @compile -XDignore.symbol.file JdpUnitTest.java JdpClient.java JdpDoSomething.java
# @run shell JdpTest.sh --jtreg --no-compile
# @summary No word Failed expected in the test output

_verbose=no
_jtreg=no
_compile=yes

# temporary disable jcmd related tests
# _testsuite="01,02,03,04,05"
_testsuite="01,02,04"

_pwd=`pwd`

_testclasses=".classes"
_testsrc="${_pwd}"
_lockFileName="JdpDoSomething.lck"

_logname=".classes/output.txt"
_last_pid=""

_ip="224.0.23.178"
_port="7095"
_jmxport="4545"

_do_compile(){
    # If the test run without JTReg, we have to compile it by our self
    # Under JTReg see @compile statement above
    # sun.* packages is not included to symbol file lib/ct.sym so we have
    # to ignore it

    if [ ! -d ${_testclasses} ]
    then
	  mkdir -p ${_testclasses}
    fi

    rm -f ${_testclasses}/*.class

    # Compile testcase
    ${COMPILEJAVA}/bin/javac -XDignore.symbol.file -d ${_testclasses} \
                                             JdpUnitTest.java \
                                             JdpDoSomething.java  \
                                             JdpClient.java


    if [ ! -f ${_testclasses}/JdpDoSomething.class -o ! -f ${_testclasses}/JdpClient.class -o ! -f ${_testclasses}/JdpUnitTest.class ]
    then
      echo "ERROR: Can't compile"
      exit 255
    fi
}


_app_start(){

  testappname=$1
  shift

  ${TESTJAVA}/bin/java -server $* -cp ${_testclasses} ${testappname}  >> ${_logname} 2>&1 &
 _last_pid=$!

  npid=`_get_pid`
  if [ "${npid}" = "" ]
  then
     echo "ERROR: Test app not started. Please check machine resources before filing a bug."
     if [ "${_jtreg}" = "yes" ]
     then
       exit 255
     fi
  fi
}

_get_pid(){
    ${TESTJAVA}/bin/jps | sed -n "/Jdp/s/ .*//p"
}

_app_stop(){
   rm ${_lockFileName}

# wait until VM is actually shuts down
  while true
  do
    npid=`_get_pid`
    if [ "${npid}" = "" ]
    then
      break
    fi
    sleep 1
  done
}

_testme(){
  ${TESTJAVA}/bin/java \
  -cp ${_testclasses} \
  $* \
    -Dcom.sun.management.jdp.port=${_port} \
    -Dcom.sun.management.jdp.address=${_ip} \
  JdpClient

}


_jcmd(){
    ${TESTJAVA}/bin/jcmd JdpDoSomething $* > /dev/null 2>/dev/null
}


_echo(){
    echo "$*"
    echo "$*" >> ${_logname}
}

# ============= TESTS ======================================

test_01(){

    _echo "**** Test one ****"

    _app_start JdpUnitTest \
    -Dcom.sun.management.jdp.port=${_port} \
    -Dcom.sun.management.jdp.address=${_ip} \
    -Dcom.sun.management.jdp.pause=5

    res=`_testme`

    case "${res}" in
     OK*)
	_echo "Passed"
     ;;
     *)
	_echo "Failed!"
     ;;
    esac

    _app_stop
}

test_02(){

    _echo "**** Test two ****"

    _app_start JdpDoSomething \
     -Dcom.sun.management.jdp.port=${_port} \
     -Dcom.sun.management.jdp.address=${_ip} \
     -Dcom.sun.management.jdp.pause=5 \
     -Dcom.sun.management.jmxremote.port=${_jmxport} \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false

    res=`_testme`

    case "${res}" in
     OK*)
	_echo "Passed"
     ;;
     *)
	_echo "Failed!"
     ;;
    esac

    _app_stop
}

test_03(){

    _echo "**** Test three ****"

    _app_start JdpDoSomething

    _jcmd  ManagementAgent.start\
                jdp.port=${_port} \
                jdp.address=${_ip} \
                jdp.pause=5 \
                jmxremote.port=${_jmxport} \
                jmxremote.authenticate=false \
                jmxremote.ssl=false

    res=`_testme`

    case "${res}" in
     OK*)
	_echo "Passed"
     ;;
     *)
	_echo "Failed!"
     ;;
    esac

    _app_stop
}

test_04(){

    _echo "**** Test four ****"

    _app_start JdpDoSomething \
     -Dcom.sun.management.jmxremote.autodiscovery=true \
     -Dcom.sun.management.jmxremote.port=${_jmxport} \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false

    res=`_testme`

    case "${res}" in
     OK*)
	_echo "Passed"
     ;;
     *)
	_echo "Failed!"
     ;;
    esac

    _app_stop
}

test_05(){

    _echo "**** Test five ****"

    _app_start JdpDoSomething

    _jcmd  ManagementAgent.start\
                jmxremote.autodiscovery=true \
                jmxremote.port=${_jmxport} \
                jmxremote.authenticate=false \
                jmxremote.ssl=false


    res=`_testme`

    case "${res}" in
     OK*)
	_echo "Passed"
     ;;
     *)
	_echo "Failed!"
     ;;
    esac

    _app_stop
}


# ============= MAIN =======================================

if [ "x${TESTJAVA}" = "x" ]
then
  echo "TESTJAVA env have to be set"
  exit
fi

# COMPILEJAVA variable is set when we test jre
if [ "x${COMPILEJAVA}" = "x" ]
then
   COMPILEJAVA="${TESTJAVA}"
fi


#------------------------------------------------------------------------------
# reading parameters

for parm in "$@"
do
   case $parm in
  --verbose)      _verbose=yes  ;;
  --jtreg)        _jtreg=yes    ;;
  --no-compile)   _compile=no   ;;
  --testsuite=*)  _testsuite=`_echo $parm | sed "s,^--.*=\(.*\),\1,"`  ;;
  *)
     echo "Undefined parameter $parm. Try --help for help"
     exit
   ;;
 esac
done

if [ "${_compile}" = "yes" ]
then
 _do_compile
fi

if [ "${_jtreg}" = "yes" ]
then
 _testclasses=${TESTCLASSES}
 _testsrc=${TESTSRC}
 _logname="output.txt"
fi

# Make sure _tesclasses is absolute path
tt=`echo ${_testclasses} | sed -e 's,/,,'`
if [ "${tt}" = "${_testclasses}" ]
then
  _testclasses="${_pwd}/${_testclasses}"
fi

_policyname="${_testclasses}/policy"

rm -f ${_logname}
rm -f ${_policyname}

if [ -f ${_testsrc}/policy.tpl ]
then

cat ${_testsrc}/policy.tpl | \
     sed -e "s,@_TESTCLASSES@,${_testclasses},g" -e "s,@TESTJAVA@,${TESTJAVA},g" \
 > ${_policyname}

fi

# Local mode tests
for i in `echo ${_testsuite} | sed -e "s/,/ /g"`
do
  test_${i}
done
