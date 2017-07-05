#!/bin/ksh
#
# Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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


# This jdk must be hopper or better; it must have the 
# SA connectors in VirtualMachineManagerImpl.
jdk=/java/re/jdk/1.4.1/promoted/latest/binaries/solaris-sparc
#jdk=/net/mmm/export/mmm/jdk1.4fcs.sa

doUsage()
{
    cat <<EOF
    Run sagclient.class using Serviceability Agent to talk to a corefile/pid/debugserver.
    Usage:  runsa.sh [-jdk <jdk-pathname>] [-jdb] [ -jdbx ] [ -d64 ] [ -remote ] [ pid | corefile | debugserver ]

    -jdk means to use that jdk.  Default is 1.4.1/latest.
    -jdbx means to run it under jdbx
    -jdb means to connect using jdb instead of the sagclient program.
    -remote debugserver means you want to connect to a remote debug server

    The corefile must have been produced by the same java as is running SA.

EOF
}

if [ $# = 0 ] ; then
    doUsage
    exit 1
fi

# License file for development version of dbx
#LM_LICENSE_FILE=7588@extend.eng:/usr/dist/local/config/sparcworks/license.dat:7588@setlicense
#export LM_LICENSE_FILE

do=
args=
theClass=sagclient
javaArgs=

while [ $# != 0 ] ; do
    case $1 in
      -vv)
        set -x
        ;;
     -jdk)
        jdk=$2
        shift
        ;;
     -jdbx)
        do=jdbx
        ;;
     -jdb)
        do=jdb
        ;;
     -help | help)
        doUsage
        exit
        ;;
     -d64) 
        d64=-d64
        ;;
     -remote)
        shift 
        args="$1"
        do=remote
        ;;
     -*)
        javaArgs="$javaArgs $1"
        ;;
     *)
        echo "$1" | grep -s '^[0-9]*$' > /dev/null
        if [ $? = 0 ] ; then
            # it is a pid
            args="$args $1"
        else
            # It is a core.        
            # We have to pass the name of the program that produced the
            # core, and the core file itself.
            args="$jdk/bin/java $1"
        fi
        ;;
   esac
   shift
done

if [ -z "$jdk" ] ; then
    error "--Error: runsa.sh:  Must specify -jdk <jdk-pathname>."
    error "         Do runsa.sh -help for more info"
    exit 1
fi

set -x
#setenv USE_LIBPROC_DEBUGGER "-Dsun.jvm.hotspot.debugger.useProcDebugger -Djava.library.path=$saprocdir"

# If jjh makes this, then the classes are in .../build/agent.
# if someone else does, they are in  .
classesDir=../../../../../../build/agent
if [ ! -r $classesDir ] ; then
    classesDir=.
    if [ ! -r $classesDir ] ; then
        echo "-- Error: runsa.sh can't find the SA classes"
        exit 1
    fi
fi
#javacp="/net/mmm/export/mmm/ws/sabaseline/build/solaris/solaris_sparc_compiler1/generated/sa-jdi.jar:$classesDir:$jdk/lib/tools.jar:$jdk/classes:./workdir"

javacp="$jdk/lib/sa-jdi.jar:$classesDir:$jdk/lib/tools.jar:$jdk/classes:./workdir"


extraArgs="-showversion $javaArgs"
#extraArgs="-DdbxSvcAgentDSOPathName=/net/mmm/export/mmm/ws/m/b2/sa/src/os/solaris/agent/64bit/libsvc_agent_dbx.so $extraArgs"
#extraArgs="-DdbxSvcAgentDSOPathName=/net/jano.eng/export/disk05/hotspot/sa/solaris/sparcv9/lib/libsvc_agent_dbx.so $extraArgs"

mkdir -p workdir
if [ sagclient.java -nt ./workdir/sagclient.class ] ; then
    $jdk/bin/javac -d ./workdir -classpath $javacp sagclient.java
    if [ $? != 0 ] ; then
        exit 1
    fi
fi
if [ sagdoit.java -nt ./workdir/sagdoit.class ] ; then
    $jdk/bin/javac -d ./workdir -classpath $javacp sagdoit.java
    if [ $? != 0 ] ; then
        exit 1
    fi
fi

if [ "$do" = jdbx ] ; then
    set -x
    dbx=/net/sparcworks.eng/export/set/sparcworks2/dbx_70_nightly/dev/buildbin/Derived-sparc-S2-opt/bin/dbx

    # Have to do this export for jdbx to work.  -cp and -classpath don't work.
    CLASSPATH=$javacp
    export CLASSPATH
    #extraArgs="-Djava.class.path=$mhs/../sa/build/agent sun.jvm.hotspot.HSDB $*"
    jvm_invocation="$jdk/bin/java -Xdebug \
               -Dsun.boot.class.path=$jdk/classes \
               $extraArgs"
    #export jvm_invocation
    
    JAVASRCPATH=$mhs/../sa/src/share/vm/agent
    export JAVASRCPATH

    #operand is pathname of .class file, eg ./jj.class.
    echo run $args
    clss=`echo $theClass | sed -e 's@\.@/@'`
    if [ -r ./workdir/$clss.class ] ; then
        # kludge for running sagclient
        $dbx  ./workdir/$clss.class
    else
        # kludge for running HSDB
        $dbx  $mhs/../sa/build/agent/$clss.class
    fi
elif [ "$do" = jdb ] ; then
    # This hasn't been tested.
    $jdk/bin/jdb -J-Xbootclasspath/a:$classesDir -connect sun.jvm.hotspot.jdi.SACoreAttachingConnector:core=sagcore
elif [ "$do" = remote ] ; then
    $jdk/bin/java $d64 -Djava.class.path=$javacp $extraArgs $theClass $args
else
    $jdk/bin/java $d64 -Djava.class.path=$javacp $extraArgs $theClass $args

fi
