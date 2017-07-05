#!/bin/sh -x
#
# Copyright (c) 2000, 2008, Oracle and/or its affiliates. All rights reserved.
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

#  Generate the reorder data for hotspot.
#
#  Usage:
#
#	sh  reorder.sh  <test_sdk_workspace>  <test_sdk>  <jbb_dir>
#
#	<test_sdk_workspace> is a *built* SDK workspace which contains the
#	reordering tools for the SDK.  This script relies on lib_mcount.so
#	from this workspace.
#
#	<test_sdk> is a working SDK which you can use to run the profiled
#	JVMs in to collect data.  You must be able to write to this SDK.
#
#	<jbb_dir> is a directory containing JBB test jar files and properties
#	which will be used to run the JBB test to provide reordering data
#	for the server VM.
#
#	Profiled builds of the VM are needed (before running this script),
#	build with PROFILE_PRODUCT=1:
#
#		gnumake profiled1 profiled PROFILE_PRODUCT=1
#
#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

test_setup() {

  #   $1 = "client"  or  "server"
  #   $2 = name of reorder file to be generated.

  echo ""
  echo "TEST_SETUP  $1  $2"
  echo ""
  libreldir=${ALT_OUTPUTDIR:-../../../make/solaris-$arch5}/reorder
  libabsdir=${ALT_OUTPUTDIR:-$sdk_ws/make/solaris-$arch5}/reorder
  ( cd $sdk_ws/make/tools/reorder ; gnumake $libreldir/$arch5/libmcount.so )
  if [ "${arch3}" = "i386" ] ; then
	# On Solaris/x86 we need to remove the symbol _mcount from the command
	( cd $sdk_ws/make/tools/reorder ; \
	    gnumake $libreldir/$arch5/remove_mcount )
	echo Remove _mcount from java command.
	$libabsdir/$arch5/remove_mcount $jre/bin/java
  fi
  ( cd $sdk_ws/make/tools/reorder ; gnumake tool_classes )
  ( cd $sdk_ws/make/tools/reorder ; gnumake test_classes )

  tests="Null Exit Hello Sleep IntToString \
	 LoadToolkit LoadFrame LoadJFrame JHello"
  swingset=$sdk/demo/jfc/SwingSet2/SwingSet2.jar
  java=$jre/bin/java
  if [ "X$LP64" != "X" ] ; then
    testjava="$jre/bin/${arch3}/java"
  else
    testjava="$jre/bin/java"
  fi
  mcount=$libabsdir/$arch5/libmcount.so

  if [ ! -x $mcount ] ; then
    echo $mcount is missing!
    exit 1
  fi

  if [ "X$1" = "client" ] ; then
    if [ "X$NO_SHARING" = "X" ] ; then
      echo "Dumping shared file."
      LD_PRELOAD=$mcount \
      JDK_ALTERNATE_VM=jvm_profiled \
  	    $testjava -Xshare:dump -Xint -XX:PermSize=16m -version 2> /dev/null
      shared_client="-Xshare:on"
      echo "Shared file dump completed."
    else
      shared_client="-Xshare:off"
      echo "NO_SHARING defined, not using sharing."
    fi
  else
    echo "Server:  no sharing" 
    shared_server="-Xshare:off"
  fi

  testpath=$libabsdir/classes

  reorder_file=$2
  
  rm -f ${reorder_file}
  rm -f ${reorder_file}_tmp2
  rm -f ${reorder_file}_tmp1

  echo "data = R0x2000;"				> ${reorder_file}
  echo "text = LOAD ?RXO;"				>> ${reorder_file}
  echo ""						>>  ${reorder_file}
  echo ""						>>  ${reorder_file}
}

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

test_client() {

  # Run each of a set of tests, extract the methods called,
  # append the new functions to the reorder list.
  #   $1 = "client"  or  "server"
  #   $2 = name of reorder file to be generated.

  echo "TEST_CLIENT $1 $2."
  test_setup $1 $2
  echo "TEST_CLIENT $1 $2."

  for f in $tests ; do
    echo Running test $f.
    rm -f ${reorder_file}_tmp1
    echo "# Test $f" >> ${reorder_file}

    echo "Using LD_PRELOAD=$mcount"
    echo $testjava ${shared_client} -classpath $testpath $f

    LD_PRELOAD=$mcount \
    JDK_ALTERNATE_VM=jvm_profiled \
	    $testjava ${shared_client} -classpath $testpath $f 2> ${reorder_file}_tmp1

    echo "Done."
    sed -n -e '/^text:/p' ${reorder_file}_tmp1 > ${reorder_file}_tmp2
    sed -e '/^text:/d' ${reorder_file}_tmp1
    LD_LIBRARY_PATH=$lib/server \
    $java -classpath $testpath Combine ${reorder_file} \
	${reorder_file}_tmp2 \
        > ${reorder_file}_tmp3
    mv ${reorder_file}_tmp3 ${reorder_file}
    rm -f ${reorder_file}_tmp2
    rm -f ${reorder_file}_tmp1
  done

  # Run SwingSet, extract the methods called,
  # append the new functions to the reorder list.

  echo "# SwingSet" >> ${reorder_file}

  echo ""
  echo ""
  echo "When SwingSet has finished drawing, " \
       "you may terminate it (with your mouse)."
  echo "Otherwise, it should be automatically terminated in 3 minutes."
  echo ""
  echo ""

  echo "Using LD_PRELOAD=$mcount, JDK_ALTERNATE=jvm_profiled."
  echo $testjava ${shared_client} -classpath $testpath MaxTime $swingset 60
  LD_PRELOAD=$mcount \
  JDK_ALTERNATE_VM=jvm_profiled \
	  $testjava ${shared_client} -classpath $testpath MaxTime \
		$swingset 60 2> ${reorder_file}_tmp1 

  sed -n -e '/^text:/p' ${reorder_file}_tmp1 > ${reorder_file}_tmp2

  LD_LIBRARY_PATH=$lib/server \
  $java -server -classpath $testpath Combine ${reorder_file} ${reorder_file}_tmp2  \
      > ${reorder_file}_tmp3
  echo mv ${reorder_file}_tmp3 ${reorder_file}
  mv ${reorder_file}_tmp3 ${reorder_file}
  echo rm -f ${reorder_file}_tmp2
  rm -f ${reorder_file}_tmp2
  echo rm -f ${reorder_file}_tmp1
  rm -f ${reorder_file}_tmp1
}

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

test_server() {

  # Run the JBB script, collecting data on the way.
  #   $1 = "client"  or  "server"
  #   $2 = name of reorder file to be generated.

  echo "TEST_SERVER $1 $2."
  test_setup $1 $2
  echo "TEST_SERVER $1 $2."

  echo Running JBB.

  rm -f ${reorder_file}_tmp1
  rm -f ${reorder_file}_tmp2
  heap=200m

  CLASSPATH=jbb.jar:jbb_no_precompile.jar:check.jar:reporter.jar

    ( cd $jbb_dir; LD_PRELOAD=$mcount MCOUNT_ORDER_BY_COUNT=1 \
        JDK_ALTERNATE_VM=jvm_profiled \
        $testjava ${shared_server} -classpath $CLASSPATH -Xms${heap} -Xmx${heap} \
	spec.jbb.JBBmain -propfile SPECjbb.props ) 2> ${reorder_file}_tmp1

  sed -n -e '/^text:/p' ${reorder_file}_tmp1 > ${reorder_file}_tmp2
  sed -e '/^text:/d' ${reorder_file}_tmp1
  cat ${reorder_file}_tmp2		>> ${reorder_file}
  rm -f ${reorder_file}_tmp2
  rm -f ${reorder_file}_tmp1
}

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Rename the old VMs, copy the new in, run the test, and put the
# old one back.

copy_and_test() {

  #   $1 = "client"  or  "server"
  #   $2 = name of reorder file to be generated.
  #   $3 = profiled jvm to copy in

  echo "COPY_AND_TEST ($1, $2, $3)."
  #   $2 = name of reorder file to be generated.
  #   $3 = profiled jvm to copy in

  rm -rf $lib/jvm_profiled
  mkdir $lib/jvm_profiled
  cp $3 $lib/jvm_profiled
  test_$1 $1 $2
  rm -rf $lib/jvm_profiled
}

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


# Check arguments:

if [ $# != 3 ] ; then
  echo ""
  echo "Usage:"
  echo "   sh  reorder.sh  <test_sdk_workspace>  <test_sdk>  <jbb_dir>"
  echo ""
  exit 1
fi

sdk_ws=$1
if [ ! -r $sdk_ws/make/tools/reorder/Makefile ] ; then
  echo ""
  echo "test workspace "$sdk_ws" does not contain the reordering tools."
  echo ""
  exit 1
fi

sdk=$2
jre=$sdk/jre

# Set up architecture names as needed by various components.
# Why couldn't we just use x86 for everything?

# Arch name as used in JRE runtime	(eg. i386):
#   .../jre/lib/${arch3}/server
arch3=`uname -p`

# Arch name as used in Hotspot build:	(eg. i486)
#   /export/hotspot/make/solaris/solaris_${arch4}_compiler1
arch4=$arch3

# Arch name as used in SDK build	(eg. i586):
#   /export/tiger/make/solaris-${arch3}
arch5=$arch3

# Tweak for 64-bit sparc builds.  At least they all agree.
if [ $arch3 = sparc -a "X$LP64" != "X" ] ; then
  arch3=sparcv9
  arch4=sparcv9
  arch5=sparcv9
fi

# Tweak for 64-bit i386 == amd64 builds.  At least they all agree.
if [ $arch3 = i386 -a "X$LP64" != "X" ] ; then
  arch3=amd64
  arch4=amd64
  arch5=amd64
fi

# Tweak for x86 builds. All different.
if [ $arch3 = i386 ] ; then
  arch4=i486
  arch5=i586
fi

lib=$jre/lib/$arch3
if [ ! -r $jre/lib/rt.jar ] ; then
  echo ""
  echo "test SDK "$sdk" is not a suitable SDK."
  echo ""
  exit 1
fi

jbb_dir=$3
if [ ! -r $jbb_dir/jbb.jar ] ; then
  echo ""
  echo "jbb.jar not present in $jbb_dir"
  echo ""
  exit 1
fi


# Were profiled VMs built?

if [ "X$LP64" != "X" ] ; then
  if [ ! -r solaris_${arch4}_compiler2/profiled/libjvm.so ] ; then
    echo ""
    echo "Profiled builds of compiler2 are needed first."
    echo ' -- build with  "make profiled PROFILE_PRODUCT=1" -- '
    echo "<solaris_${arch4}_compiler2/profiled/libjvm.so>"
    exit 1
  fi
else
  if [    ! -r solaris_${arch4}_compiler1/profiled/libjvm.so  \
       -o ! -r solaris_${arch4}_compiler2/profiled/libjvm.so ] ; then
    echo ""
    echo "Profiled builds of compiler1 and compiler2 are needed first."
    echo ' -- build with  "make profiled{,1} PROFILE_PRODUCT=1" -- '
    exit 1
  fi
fi


# Compiler1 - not supported in 64-bit (b69 java launcher rejects it).

if [ "X$LP64" = "X" ] ; then
  #gnumake profiled1
  echo Using profiled client VM.
  echo
  copy_and_test client \
                reorder_COMPILER1_$arch4 \
                solaris_${arch4}_compiler1/profiled/libjvm.so
fi

#gnumake profiled
echo Using profiled server VM.
echo
copy_and_test server \
              reorder_COMPILER2_$arch4 \
              solaris_${arch4}_compiler2/profiled/libjvm.so
