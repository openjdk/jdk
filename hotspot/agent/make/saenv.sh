#!/bin/sh
#
# Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# This file sets common environment variables for all SA scripts

OS=`uname`
STARTDIR=`dirname $0`
ARCH=`uname -m`

if [ "x$SA_JAVA" = "x" ]; then
   SA_JAVA=java
fi

if [ "$OS" = "Linux" ]; then
   if [ "$ARCH" = "ia64" ] ; then
     SA_LIBPATH=$STARTDIR/../src/os/linux/ia64:$STARTDIR/linux/ia64
     OPTIONS="-Dsa.library.path=$SA_LIBPATH"
     CPU=ia64
   elif [ "$ARCH" = "x86_64" ] ; then 
     SA_LIBPATH=$STARTDIR/../src/os/linux/amd64:$STARTDIR/linux/amd64
     OPTIONS="-Dsa.library.path=$SA_LIBPATH"
     CPU=amd64
   else
     SA_LIBPATH=$STARTDIR/../src/os/linux/i386:$STARTDIR/linux/i386
     OPTIONS="-Dsa.library.path=$SA_LIBPATH"
     CPU=i386
   fi
else
   LD_AUDIT_32=$STARTDIR/../src/os/solaris/proc/`uname -p`/libsaproc_audit.so
   export LD_AUDIT_32
   SA_LIBPATH=$STARTDIR/../src/os/solaris/proc/`uname -p`:$STARTDIR/solaris/`uname -p`
   OPTIONS="-Dsa.library.path=$SA_LIBPATH -Dsun.jvm.hotspot.debugger.useProcDebugger"
   CPU=sparc
fi

if [ "x$SA_DISABLE_VERS_CHK" != "x" ]; then
   OPTIONS="-Dsun.jvm.hotspot.runtime.VM.disableVersionCheck ${OPTIONS}"
fi


SA_CLASSPATH=$STARTDIR/../build/classes:$STARTDIR/../src/share/lib/js.jar:$STARTDIR/sa.jar:$STARTDIR/lib/js.jar

OPTIONS="-Djava.system.class.loader=sun.jvm.hotspot.SALauncherLoader ${OPTIONS}"

SA_JAVA_CMD="$SA_PREFIX_CMD $SA_JAVA -showversion ${OPTIONS} -cp $SA_CLASSPATH $SA_OPTIONS"
