#!/bin/sh

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
# @bug 7110104
# @build JMXStartStopTest JMXStartStopDoSomething
# @run shell JMXStartStopTest.sh --jtreg --no-compile
# @summary No word Failed expected in the test output

_server=no
_jtreg=no
_compile=yes
_testsuite="01,02,03,04,05,06,07,08,09,10,11,12,13"
_port_one=50234
_port_two=50235


_testclasses=".classes"
_testsrc=`pwd`

_logname=".classes/output.txt"
_lockFileName="JMXStartStop.lck"

_compile(){

    if [ ! -d ${_testclasses} ]
    then
      mkdir -p ${_testclasses} 
    fi   

    rm -f ${_testclasses}/JMXStartStopTest.class

    # Compile testcase
    ${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${_testclasses} \
	JMXStartStopDoSomething.java JMXStartStopTest.java 

    if [ ! -f ${_testclasses}/JMXStartStopTest.class ]
    then
      echo "ERROR: Can't compile"
      exit -1
    fi
}

_app_start(){
  ${TESTJAVA}/bin/java ${TESTVMOPTS} $* -cp ${_testclasses} JMXStartStopDoSomething  >> ${_logname} 2>&1 &

  x=0
  while [ ! -f ${_lockFileName} ]
  do
     if [ $x -gt 20 ]
     then
        echo "ERROR: Test app not started"
        if [ "${_jtreg}" = "yes" ]
        then
           exit -1
        fi   
     fi    
        
     echo "Waiting JMXStartStopDoSomething to start: $x"
     x=`expr $x + 1`
     sleep 1
  done
}

_get_pid(){
    ${COMPILEJAVA}/bin/jps ${TESTTOOLVMOPTS} | sed -n "/JMXStartStopDoSomething/s/ .*//p"
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

_exit_on_jtreg(){
  # Stop on first failed test under jtreg
  if [ "${_jtreg}" = "yes" ]
  then
      _app_stop
      exit -1
  fi
}

_testme(){
  ${TESTJAVA}/bin/java ${TESTVMOPTS} -cp ${_testclasses} JMXStartStopTest $*
}   

  
_jcmd(){
  ${TESTJAVA}/bin/jcmd ${TESTTOOLVMOPTS} JMXStartStopDoSomething $* > /dev/null 2>/dev/null
} 

_echo(){
    echo "$*"
    echo "$*" >> ${_logname}
}
   
# ============= TESTS ======================================
   
test_01(){
# Run an app with JMX enabled stop it and 
# restart on other port
        
    _echo "**** Test one ****"      

    _app_start  -Dcom.sun.management.jmxremote.port=$1 \
                -Dcom.sun.management.jmxremote.authenticate=false \
                -Dcom.sun.management.jmxremote.ssl=false 

    res1=`_testme $1`

    _jcmd ManagementAgent.stop

    res2=`_testme $1`

    _jcmd ManagementAgent.start jmxremote.port=$2

    res3=`_testme $2`

    if [ "${res1}" = "OK_CONN" -a "${res2}" = "NO_CONN" -a "${res3}" = "OK_CONN" ] 
    then
        _echo "Passed"
    else
        _echo "Failed r1(OK):${res1} r2(NO):${res2} r3(OK):${res3}"
        _exit_on_jtreg
    fi

    _app_stop
}  
   
test_02(){
# Run an app without JMX enabled 
# start JMX by jcmd

    _echo "**** Test two ****"      
    _app_start  

    _jcmd ManagementAgent.start jmxremote.port=$1 jmxremote.authenticate=false jmxremote.ssl=false 

    res1=`_testme $1`

    if [ "${res1}" = "OK_CONN" ] 
    then
        _echo "Passed"
    else
        _echo "Failed r1(OK):${res1}"
        _exit_on_jtreg
    fi
    _app_stop
}   
   
test_03(){
# Run an app without JMX enabled 
# start JMX by jcmd on one port than on other one

    _echo "**** Test three ****"        
    _app_start  

    _jcmd ManagementAgent.start jmxremote.port=$1 jmxremote.authenticate=false jmxremote.ssl=false 

# Second agent shouldn't start
    _jcmd ManagementAgent.start jmxremote.port=$2 jmxremote.authenticate=false jmxremote.ssl=false

# First agent should connect
    res1=`_testme $1`

    if [ "${res1}" = "OK_CONN" ] 
    then
        _echo "Passed $1"
    else
        _echo "Failed r1(NO):${res1}"
        _exit_on_jtreg
    fi

#Second agent shouldn't connect
    res1=`_testme $2`

    if [ "${res1}" = "NO_CONN" ] 
    then
        _echo "Passed $2"
    else
        _echo "Failed r1(OK):${res1}"
        _exit_on_jtreg
    fi

    _app_stop
}   
   
test_04(){
# Run an app without JMX enabled 
# start JMX by jcmd on one port, specify rmi port explicitly

    _echo "**** Test four ****"     
    _app_start  

    _jcmd ManagementAgent.start jmxremote.port=$1 jmxremote.rmi.port=$2 jmxremote.authenticate=false jmxremote.ssl=false 

# First agent should connect
    res1=`_testme $1 $2`

    if [ "${res1}" = "OK_CONN" ] 
    then
        _echo "Passed $1 $2"
    else
        _echo "Failed r1(NO):${res1}"
        _exit_on_jtreg
    fi

    _app_stop
}   

test_05(){
# Run an app without JMX enabled, it will enable local server
# but should leave remote server disabled  

    _echo "**** Test five ****"     
    _app_start  

    _jcmd ManagementAgent.start jmxremote=1 

    # First agent should connect
    res1=`_testme $1`

    if [ "${res1}" = "NO_CONN" ] 
    then
        _echo "Passed $1 $2"
    else
        _echo "Failed r1(OK):${res1}"
        _exit_on_jtreg
    fi

    _app_stop
}   

test_06(){
# Run an app without JMX enabled 
# start JMX by jcmd on one port, specify rmi port explicitly
# attempt to start it again
# 1) with the same port 
# 2) with other port
# 3) attempt to stop it twice
# Check for valid messages in the output    

    _echo "**** Test six ****"      
    _app_start  

    _jcmd ManagementAgent.start jmxremote.port=$1 jmxremote.authenticate=false jmxremote.ssl=false 

    # First agent should connect
    res1=`_testme $1 $2`

    if [ "${res1}" = "OK_CONN" ] 
    then
        _echo "Passed $1 $2"
    else
        _echo "Failed r1(NO):${res1}"
        _exit_on_jtreg
    fi

    _jcmd ManagementAgent.start jmxremote.port=$1 jmxremote.authenticate=false jmxremote.ssl=false 

    _jcmd ManagementAgent.start jmxremote.port=$2 jmxremote.authenticate=false jmxremote.ssl=false 

    _jcmd ManagementAgent.stop

    _jcmd ManagementAgent.stop

    _jcmd ManagementAgent.start jmxremote.port=22 jmxremote.rmi.port=$2 jmxremote.authenticate=false jmxremote.ssl=false 

    _app_stop
}   

test_07(){
# Run an app without JMX enabled, but with some properties set 
# in command line.
# make sure these properties overriden corectly 

    _echo "**** Test seven ****"        

    _app_start   -Dcom.sun.management.jmxremote.authenticate=false \
                 -Dcom.sun.management.jmxremote.ssl=true 

    res1=`_testme $1`

    _jcmd ManagementAgent.start jmxremote.port=$2 jmxremote.authenticate=false jmxremote.ssl=false

    res2=`_testme $2`

    if [ "${res1}" = "NO_CONN" -a "${res2}" = "OK_CONN" ] 
    then
        echo "Passed"
    else
        _echo "Failed r1(NO):${res1} r2(OK):${res2}"
        _exit_on_jtreg
    fi

    _app_stop
}   

test_08(){
# Run an app with JMX enabled and with some properties set 
# in command line.
# stop JMX agent and then start it again with different property values
# make sure these properties overriden corectly 

    _echo "**** Test eight ****"        

    _app_start  -Dcom.sun.management.jmxremote.port=$1 \
                -Dcom.sun.management.jmxremote.authenticate=false \
                -Dcom.sun.management.jmxremote.ssl=true 

    res1=`_testme $1`

    _jcmd ManagementAgent.stop

    res2=`_testme $1`

    _jcmd ManagementAgent.start jmxremote.port=$2 jmxremote.authenticate=false jmxremote.ssl=false

    res3=`_testme $2`

    if [ "${res1}" = "NO_CONN" -a "${res2}" = "NO_CONN" -a "${res3}" = "OK_CONN" ] 
    then
        _echo "Passed"
    else
        _echo "Failed r1(NO):${res1} r2(NO):${res2} r3(OK):${res3}"
        _exit_on_jtreg
    fi
 
    _app_stop
}   

test_09(){
# Run an app with JMX enabled and with some properties set 
# in command line.
# stop JMX agent and then start it again with different property values
# specifing some property in management config file and some of them
# in command line
# make sure these properties overriden corectly 

    _echo "**** Test nine ****"     

    _app_start -Dcom.sun.management.config.file=${_testsrc}/management_cl.properties \
               -Dcom.sun.management.jmxremote.authenticate=false 

    res1=`_testme $1`

    _jcmd ManagementAgent.stop

    res2=`_testme $1`

    _jcmd ManagementAgent.start config.file=${_testsrc}/management_jcmd.properties \
                                jmxremote.authenticate=false jmxremote.port=$2

    res3=`_testme $2`

    if [ "${res1}" = "NO_CONN" -a "${res2}" = "NO_CONN" -a "${res3}" = "OK_CONN" ] 
    then
        _echo "Passed"
    else
        _echo "Failed r1(NO):${res1} r2(NO):${res2} r3(OK):${res3}"
        _exit_on_jtreg
    fi
 
    _app_stop
}   

test_10(){
# Run an app with JMX enabled and with some properties set 
# in command line.
# stop JMX agent and then start it again with different property values
# stop JMX agent again and then start it without property value
# make sure these properties overriden corectly 

    _echo "**** Test ten ****"      

    _app_start  -Dcom.sun.management.jmxremote.port=$1 \
                -Dcom.sun.management.jmxremote.authenticate=false \
                -Dcom.sun.management.jmxremote.ssl=true 

    res1=`_testme $1`

    _jcmd ManagementAgent.stop
    _jcmd ManagementAgent.start jmxremote.ssl=false jmxremote.port=$1


    res2=`_testme $1`

    _jcmd ManagementAgent.stop
    _jcmd ManagementAgent.start jmxremote.port=$1

    res3=`_testme $1`

    if [ "${res1}" = "NO_CONN" -a "${res2}" = "OK_CONN" -a "${res3}" = "NO_CONN" ] 
    then
        _echo "Passed"
    else
        _echo "Failed r1(NO):${res1} r2(OK):${res2} r3(NO):${res3}"
        _exit_on_jtreg
    fi
 
    _app_stop
}   

test_11(){
# Run an app with JMX enabled 
# stop remote agent 
# make sure local agent is not affected

    _echo "**** Test eleven ****"       

    _app_start  -Dcom.sun.management.jmxremote.port=$2 \
                -Dcom.sun.management.jmxremote.authenticate=false \
                -Dcom.sun.management.jmxremote.ssl=false 
      
    res1=`_testme $2`

    _jcmd ManagementAgent.stop

    pid=`${COMPILEJAVA}/bin/jps ${TESTTOOLVMOPTS} | sed -n "/JMXStartStopDoSomething/s/ .*//p"`
    res2=`_testme local ${pid}`

    if [ "${res1}" = "OK_CONN" -a "${res2}" = "OK_CONN" ] 
    then
        _echo "Passed"
    else
        _echo "Failed r1(OK):${res1} r2(OK):${res2}"
        _exit_on_jtreg
    fi
 
    _app_stop
}   

test_12(){
# Run an app with JMX disabled 
# start local agent only

    _echo "**** Test twelve ****"       

    _app_start 
      
    res1=`_testme $1`

    _jcmd ManagementAgent.start_local

    pid=`_get_pid`
    if [ "x${pid}" = "x" ]
    then
        res2="NO_CONN"
    else
        res2=`_testme local ${pid}`
    fi

    if [ "${res1}" = "NO_CONN" -a "${res2}" = "OK_CONN" ] 
    then
        _echo "Passed"
    else
        _echo "Failed r1(NO):${res1} r2(OK):${res2}"
        _exit_on_jtreg
    fi
 
    _app_stop
}   

test_13(){
# Run an app with -javaagent make sure it works as expected - system properties are ignored

    _echo "**** Test thirteen ****"       
			   
    AGENT="${TESTJAVA}/jre/lib/management-agent.jar"
    if [ ! -f ${AGENT} ]
    then
        AGENT="${TESTJAVA}/lib/management-agent.jar"
    fi

    _app_start -javaagent:${AGENT}=com.sun.management.jmxremote.port=$1,com.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false 
      
    res1=`_testme $1`

    if [ "${res1}" = "NO_CONN" ] 
    then
        _echo "Passed"
    else
        _echo "Failed r1(NO):${res1}"
        _exit_on_jtreg
    fi
 
    _app_stop
}   

# ============= MAIN =======================================

if [ "x${TESTJAVA}" = "x" ]
then
  echo "TESTJAVA env have to be set"
  exit
fi

if [ ! -x "${TESTJAVA}/bin/jcmd" ]
then
  echo "${TESTJAVA}/bin/jcmd"
  echo "Doesn't exist or not an executable"
  exit
fi


#------------------------------------------------------------------------------
# reading parameters 

for parm in "$@"  
do
   case $parm in
  --jtreg)        _jtreg=yes    ;;
  --no-compile)   _compile=no   ;;
  --testsuite=*)  _testsuite=`_echo $parm | sed "s,^--.*=\(.*\),\1,"`  ;;
  --port-one=*)   _port_one=`_echo $parm | sed "s,^--.*=\(.*\),\1,"`  ;;
  --port-two=*)   _port_two=`_echo $parm | sed "s,^--.*=\(.*\),\1,"`  ;;
  *) 
     echo "Undefined parameter $parm. Try --help for help" 
     exit 
   ;;
 esac 
done

if [ "${COMPILEJAVA}" = "" ]
then 
  COMPILEJAVA=${TESTJAVA}
fi

if [ ${_compile} = "yes" ]
then
 _compile
fi

if [ ${_jtreg} = "yes" ]
then
 _testclasses=${TESTCLASSES}
 _testsrc=${TESTSRC}
 _logname="JMXStartStopTest_output.txt"
fi

rm -f ${_logname}

# Local mode tests
for i in `echo ${_testsuite} | sed -e "s/,/ /g"`
do
  test_${i} ${_port_one} ${_port_two}
done


