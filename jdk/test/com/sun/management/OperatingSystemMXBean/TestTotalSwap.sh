#
# Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
# @test
# @summary  Tests MM getTotalSwapSpaceSize() api.
# @author   Swamy V
# @bug      6252770
#
# @run build GetTotalSwapSpaceSize
# @run shell TestTotalSwap.sh
#

#
# This test tests the actual swap size on linux and solaris.
# On windows this is just a sanity check and correct size should
# be checked manually:
#
# Windows NT/XP/2000:
#   1. Run Start->Accessories->System Tools->System Information.
#   2. The value (reported in Kbytes) is in the "Page File Space" entry
# Windows 98/ME:
#   Unknown.
#


#set -x

#Set appropriate jdk
#

if [ ! -z "${TESTJAVA}" ] ; then
     jdk="$TESTJAVA"
else
     echo "--Error: TESTJAVA must be defined as the pathname of a jdk to test."
     exit 1
fi

runOne()
{
   echo "runOne $@"
   $TESTJAVA/bin/java -classpath $TESTCLASSES $@  || exit 3
}

solaris_swap_size()
{
   total_swap=0
   for i in `/usr/sbin/swap -l |  awk  '{print $4}' | grep -v blocks`
   do
      # swap -l returns size in blocks of 512 bytes.
      total_swap=`expr $i \* 512 + $total_swap`
   done
}

# Test GetTotalSwapSpaceSize if we are running on Unix
total_swap=0
case `uname -s` in
     SunOS )
       solaris_swap_size
       runOne GetTotalSwapSpaceSize $total_swap 
       ;;
     Linux )
       total_swap=`free -b | grep -i swap | awk '{print $2}'`
       runOne GetTotalSwapSpaceSize $total_swap 
       ;;
     Darwin )
       # $ sysctl -n vm.swapusage 
       # total = 8192.00M  used = 7471.11M  free = 720.89M  (encrypted)
       swap=`/usr/sbin/sysctl -n vm.swapusage | awk '{ print $3 }' | awk -F . '{ print $1 }'` || exit 2
       total_swap=`expr $swap \* 1024 \* 1024` || exit 2
       runOne GetTotalSwapSpaceSize $total_swap
       ;;
    * )
       runOne GetTotalSwapSpaceSize "sanity-only"
       ;;
esac

exit 0

