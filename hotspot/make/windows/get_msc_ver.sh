#
# Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#  
#

# This shell script echoes "MSC_VER=<munged version of cl>"
# It ignores the micro version component.
# Examples:
# cl version 12.00.8804 returns "MSC_VER=1200"
# cl version 13.10.3077 returns "MSC_VER=1310"
# cl version 14.00.30701 returns "MSC_VER=1399" (OLD_MSSDK version)
# cl version 14.00.40310.41 returns "MSC_VER=1400"
# cl version 15.00.21022.8 returns "MSC_VER=1500"

# Note that we currently do not have a way to set HotSpotMksHome in
# the batch build, but so far this has not seemed to be a problem. The
# reason this environment variable is necessary is that it seems that
# Windows truncates very long PATHs when executing shells like MKS's
# sh, and it has been found that sometimes `which sh` fails.

if [ "x$HotSpotMksHome" != "x" ]; then
 MKS_HOME="$HotSpotMksHome"
else
 SH=`which sh`
 MKS_HOME=`dirname "$SH"`
fi

HEAD="$MKS_HOME/head"
ECHO="$MKS_HOME/echo"
EXPR="$MKS_HOME/expr"
CUT="$MKS_HOME/cut"
SED="$MKS_HOME/sed"

if [ "x$FORCE_MSC_VER" != "x" ]; then
  echo "MSC_VER=$FORCE_MSC_VER"
else
  MSC_VER_RAW=`cl 2>&1 | "$HEAD" -n 1 | "$SED" 's/.*Version[\ ]*\([0-9][0-9.]*\).*/\1/'`
  MSC_VER_MAJOR=`"$ECHO" $MSC_VER_RAW | "$CUT" -d'.' -f1`
  MSC_VER_MINOR=`"$ECHO" $MSC_VER_RAW | "$CUT" -d'.' -f2`
  MSC_VER_MICRO=`"$ECHO" $MSC_VER_RAW | "$CUT" -d'.' -f3`
  if [ "${MSC_VER_MAJOR}" -eq 14 -a "${MSC_VER_MINOR}" -eq 0 -a "${MSC_VER_MICRO}" -eq 30701 ] ; then
    # This said 1400 but it was really more like VS2003 (VC7) in terms of options
    MSC_VER=1399
  else
    MSC_VER=`"$EXPR" $MSC_VER_MAJOR \* 100 + $MSC_VER_MINOR`
  fi
  echo "MSC_VER=$MSC_VER"
  echo "MSC_VER_RAW=$MSC_VER_RAW"
fi

if [ "x$FORCE_LINK_VER" != "x" ]; then
  echo "LINK_VER=$FORCE_LINK_VER"
else
  LINK_VER_RAW=`link 2>&1 | "$HEAD" -n 1 | "$SED" 's/.*Version[\ ]*\([0-9][0-9.]*\).*/\1/'`
  LINK_VER_MAJOR=`"$ECHO" $LINK_VER_RAW | "$CUT" -d'.' -f1`
  LINK_VER_MINOR=`"$ECHO" $LINK_VER_RAW | "$CUT" -d'.' -f2`
  LINK_VER_MICRO=`"$ECHO" $LINK_VER_RAW | "$CUT" -d'.' -f3`
  LINK_VER=`"$EXPR" $LINK_VER_MAJOR \* 100 + $LINK_VER_MINOR`
  echo "LINK_VER=$LINK_VER"
  echo "LINK_VER_RAW=$LINK_VER_RAW"
fi
