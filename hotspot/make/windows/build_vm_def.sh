#
# Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# This shell script builds a vm.def file for the current VM variant.
# The .def file exports vtbl symbols which allow the Serviceability
# Agent to run on Windows. See make/windows/projectfiles/*/vm.def
# for more information.
#
# The script expects to be executed in the directory containing all of
# the object files.

# Note that we currently do not have a way to set HotSpotMksHome in
# the batch build, but so far this has not seemed to be a problem. The
# reason this environment variable is necessary is that it seems that
# Windows truncates very long PATHs when executing shells like MKS's
# sh, and it has been found that sometimes `which sh` fails.
if [ "x$HOTSPOTMKSHOME" != "x" ]; then
 MKS_HOME="$HOTSPOTMKSHOME"
else
 SH=`which sh`
 MKS_HOME=`dirname "$SH"`
fi

echo "EXPORTS" > vm1.def

AWK="$MKS_HOME/awk.exe"
GREP="$MKS_HOME/grep.exe"
SORT="$MKS_HOME/sort.exe"
UNIQ="$MKS_HOME/uniq.exe"
CAT="$MKS_HOME/cat.exe"
RM="$MKS_HOME/rm.exe"
DUMPBIN="link.exe /dump"

# When called from IDE the first param should contain the link version, otherwise may be nill
if [ "x$1" != "x" ]; then
LINK_VER="$1"
fi

if [ "x$LINK_VER" != "x800" -a  "x$LINK_VER" != "x900" ]; then
$DUMPBIN /symbols *.obj | "$GREP" "??_7.*@@6B@" | "$GREP" -v "type_info" | "$AWK" '{print $7}' | "$SORT" | "$UNIQ" > vm2.def
else
# Can't use pipes when calling cl.exe or link.exe from IDE. Using transit file vm3.def
$DUMPBIN /OUT:vm3.def /symbols *.obj 
"$CAT" vm3.def | "$GREP" "??_7.*@@6B@" | "$GREP" -v "type_info" | "$AWK" '{print $7}' | "$SORT" | "$UNIQ" > vm2.def
"$RM" -f vm3.def
fi

"$CAT" vm1.def vm2.def > vm.def
"$RM" -f vm1.def vm2.def
