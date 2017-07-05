#!/bin/ksh -p

#
# Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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
#

#
#   @test       PrivateTransportTest.sh
#   @bug        6225664 6220618
#   @summary    Test for when private transport library outside jdk
#   @author     Kelly O'Hair
#
#   @run compile -g HelloWorld.java
#   @run shell PrivateTransportTest.sh
#

# Beginning of subroutines:
status=1

#Call this from anywhere to fail the test with an error message
# usage: fail "reason why the test failed"
fail() 
 { echo "The test failed :-("
   echo "$*" 1>&2
   echo "exit status was $status"
   exit $status
 } #end of fail()

#Call this from anywhere to pass the test with a message
# usage: pass "reason why the test passed if applicable"
pass() 
 { echo "The test passed!!!"
   echo "$*" 1>&2
   exit 0
 } #end of pass()

# end of subroutines

# The beginning of the script proper

TARGETCLASS="HelloWorld"
if [ -z "${TESTJAVA}" ] ; then
   # TESTJAVA is not set, so the test is running stand-alone.
   # TESTJAVA holds the path to the root directory of the build of the JDK
   # to be tested.  That is, any java files run explicitly in this shell
   # should use TESTJAVA in the path to the java interpreter.
   # So, we'll set this to the JDK spec'd on the command line.  If none
   # is given on the command line, tell the user that and use a default.
   # THIS IS THE JDK BEING TESTED.
   if [ -n "$1" ] ; then
          TESTJAVA=$1
      else
	  TESTJAVA=$JAVA_HOME
   fi
   TESTSRC=.
   TESTCLASSES=.
   #Deal with .class files:
   ${TESTJAVA}/bin/javac -d ${TESTCLASSES} \
            -classpath "${TESTCLASSES}" -g \
            ${TARGETCLASS}.java
fi
#
echo "JDK under test is: $TESTJAVA"

# Get flags being sent to debuggee
DEBUGGEEFLAGS=
if [ -r $TESTCLASSES/@debuggeeVMOptions ] ; then
   DEBUGGEEFLAGS=`cat $TESTCLASSES/@debuggeeVMOptions`
elif [ -r $TESTCLASSES/../@debuggeeVMOptions ] ; then
   DEBUGGEEFLAGS=`cat $TESTCLASSES/../@debuggeeVMOptions`
fi

# Figure out what the libarch path is
os=`uname -s`

jreloc=${TESTJAVA}/jre
if [ ! -d ${jreloc} ] ; then
    jreloc=${TESTJAVA}
fi

libdir=${TESTCLASSES}

is_windows=false
is_cygwin=false
case `uname -s` in 
  SunOS)
    libarch=`uname -p`
    d64=`echo "${DEBUGGEEFLAGS}" | fgrep -- -d64`
    case `uname -p` in
      sparc)
	if [ "${d64}" != "" ] ; then
	    libarch=sparcv9
	fi
        ;;
      i386)
        if [ "${d64}" != "" ] ; then
	    libarch=amd64
	fi
        ;;
      *)
        echo "FAILURE:  Unknown uname -p: " `uname -p`
        exit 1
        ;;
    esac
    libloc=${jreloc}/lib/${libarch}
    ;;
  Linux)
    xx=`find ${jreloc}/lib -name libdt_socket.so`
    libloc=`dirname ${xx}`
    ;;
  Darwin)
    libloc=${jreloc}/lib
    ;;
  Windows*)
    is_windows=true
    libloc=${jreloc}/bin
    sep=';'
    ;;
  CYGWIN*)
    is_windows=true
    is_cygwin=true
    libloc=${jreloc}/bin
    sep=':'

    # This is going onto PATH and cygwin needs the form
    # /cygdrive/j/x..... for that.
    libdir=`cygpath -u "$TESTCLASSES"`
    ;;
  *)
    echo "FAILURE:  Unknown uname -s: " `uname -s`
    exit 1
    ;;
esac

# Create private transport library
echo "Setup private transport library by copying an existing one and renaming"
private_transport=private_dt_socket
if [ -f ${libloc}/dt_socket.dll ] ; then
    fullpath=${libdir}/${private_transport}.dll
    rm -f ${fullpath}
    echo cp ${libloc}/dt_socket.dll ${fullpath}
    cp ${libloc}/dt_socket.dll ${fullpath}
    # make sure we can find libraries in current directory
    PATH="${PATH}${sep}${libdir}"
    export PATH
    echo PATH=${PATH}
elif [ -f ${libloc}/libdt_socket.dylib ]; then
    fullpath=${libdir}/lib${private_transport}.dylib
    rm -f ${fullpath}
    echo cp ${libloc}/libdt_socket.dylib ${fullpath}
    cp ${libloc}/libdt_socket.dylib ${fullpath}
    # make sure we can find libraries in current directory
    if [ "${LD_LIBRARY_PATH}" = "" ] ; then
        LD_LIBRARY_PATH=${libdir}
    else
        LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${libdir}
    fi
    export LD_LIBRARY_PATH
    echo LD_LIBRARY_PATH=${LD_LIBRARY_PATH}
elif [ -f ${libloc}/libdt_socket.so ] ; then
    fullpath=${libdir}/lib${private_transport}.so
    rm -f ${fullpath}
    echo cp ${libloc}/libdt_socket.so ${fullpath}
    cp ${libloc}/libdt_socket.so ${fullpath}
    # make sure we can find libraries in current directory
    if [ "${LD_LIBRARY_PATH}" = "" ] ; then
        LD_LIBRARY_PATH=${libdir}
    else
        LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${libdir}
    fi
    export LD_LIBRARY_PATH
    echo LD_LIBRARY_PATH=${LD_LIBRARY_PATH}
else 
    echo "cannot find dt_socket in ${libloc} for ${private_transport}"
    fail "cannot find dt_socket in ${libloc} for ${private_transport}"
fi

#
CP="-classpath \"${TESTCLASSES}\""
#
if [ "$is_windows" = "true" ]; then
    if [ "$is_cygwin" = "true" ]; then
        win_fullpath=`cygpath -m "$fullpath" \
            | sed -e 's#/#\\\\\\\\#g' -e 's/\.dll//'`
    else
        win_fullpath=`echo "$fullpath" \
            | sed -e 's#/#\\\\\\\\#g' -e 's/\.dll//'`
    fi
    DEBUGGEEFLAGS="$DEBUGGEEFLAGS -agentlib:jdwp=transport=${win_fullpath},server=y,suspend=n"
else
    DEBUGGEEFLAGS="$DEBUGGEEFLAGS -agentlib:jdwp=transport=${private_transport},server=y,suspend=n"
fi
               
echo ${TESTJAVA}/bin/java ${DEBUGGEEFLAGS} ${CP} ${TARGETCLASS}
eval ${TESTJAVA}/bin/java ${DEBUGGEEFLAGS} ${CP} ${TARGETCLASS}
status=$?
echo "test status for ${DEBUGGERFLAGS} was: $status"
if [ $status -ne 0 ] ; then 
    fail "unspecified test failure"
    exit 1
fi

pass "found private transport library"
exit 0

