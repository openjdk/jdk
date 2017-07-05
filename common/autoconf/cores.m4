#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

AC_DEFUN([CHECK_CORES],
[
    AC_MSG_CHECKING([for number of cores])
    NUM_CORES=1
    FOUND_CORES=no
    
    if test -f /proc/cpuinfo; then
        # Looks like a Linux system
        NUM_CORES=`cat /proc/cpuinfo  | grep -c processor`
        FOUND_CORES=yes
    fi

    if test -x /usr/sbin/psrinfo; then
        # Looks like a Solaris system
        NUM_CORES=`LC_MESSAGES=C /usr/sbin/psrinfo -v | grep -c on-line`
        FOUND_CORES=yes
    fi

    if test -x /usr/sbin/system_profiler; then
        # Looks like a MacOSX system
        NUM_CORES=`/usr/sbin/system_profiler -detailLevel full SPHardwareDataType | grep 'Cores' | awk  '{print [$]5}'`
        FOUND_CORES=yes
    fi

    if test "x$build_os" = xwindows; then
        NUM_CORES=4
    fi

    # For c/c++ code we run twice as many concurrent build
    # jobs than we have cores, otherwise we will stall on io.
    CONCURRENT_BUILD_JOBS=`expr $NUM_CORES \* 2`

    if test "x$FOUND_CORES" = xyes; then
        AC_MSG_RESULT([$NUM_CORES])
    else
        AC_MSG_RESULT([could not detect number of cores, defaulting to 1!])
    fi 

])

AC_DEFUN([CHECK_MEMORY_SIZE],
[
    AC_MSG_CHECKING([for memory size])
    # Default to 1024MB
    MEMORY_SIZE=1024
    FOUND_MEM=no
    
    if test -f /proc/cpuinfo; then
        # Looks like a Linux system
        MEMORY_SIZE=`cat /proc/meminfo | grep MemTotal | awk '{print [$]2}'`
        MEMORY_SIZE=`expr $MEMORY_SIZE / 1024`
        FOUND_MEM=yes
    fi

    if test -x /usr/sbin/prtconf; then
        # Looks like a Solaris system
        MEMORY_SIZE=`/usr/sbin/prtconf | grep "Memory size" | awk '{ print [$]3 }'`
        FOUND_MEM=yes
    fi

    if test -x /usr/sbin/system_profiler; then
        # Looks like a MacOSX system
        MEMORY_SIZE=`/usr/sbin/system_profiler -detailLevel full SPHardwareDataType | grep 'Memory' | awk  '{print [$]2}'`
        MEMORY_SIZE=`expr $MEMORY_SIZE \* 1024`
        FOUND_MEM=yes
    fi

    if test "x$build_os" = xwindows; then
        MEMORY_SIZE=`systeminfo | grep 'Total Physical Memory:' | awk '{ print [$]4 }' | sed 's/,//'`
        FOUND_MEM=yes    
    fi

    if test "x$FOUND_MEM" = xyes; then
        AC_MSG_RESULT([$MEMORY_SIZE MB])
    else
        AC_MSG_RESULT([could not detect memory size defaulting to 1024MB!])
    fi 
])
