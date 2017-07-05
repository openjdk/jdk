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

AC_DEFUN([BPERF_CHECK_CORES],
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

AC_DEFUN([BPERF_CHECK_MEMORY_SIZE],
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

AC_DEFUN_ONCE([BPERF_SETUP_BUILD_CORES],
[
# How many cores do we have on this build system?
AC_ARG_WITH(num-cores, [AS_HELP_STRING([--with-num-cores],
    [number of cores in the build system, e.g. --with-num-cores=8 @<:@probed@:>@])])
if test "x$with_num_cores" = x; then
    # The number of cores were not specified, try to probe them.
    BPERF_CHECK_CORES
else
    NUM_CORES=$with_num_cores
    CONCURRENT_BUILD_JOBS=`expr $NUM_CORES \* 2`
fi
AC_SUBST(NUM_CORES)
AC_SUBST(CONCURRENT_BUILD_JOBS)
])

AC_DEFUN_ONCE([BPERF_SETUP_BUILD_MEMORY],
[
# How much memory do we have on this build system?
AC_ARG_WITH(memory-size, [AS_HELP_STRING([--with-memory-size],
    [memory (in MB) available in the build system, e.g. --with-memory-size=1024 @<:@probed@:>@])])
if test "x$with_memory_size" = x; then
    # The memory size was not specified, try to probe it.
    BPERF_CHECK_MEMORY_SIZE
else
    MEMORY_SIZE=$with_memory_size
fi
AC_SUBST(MEMORY_SIZE)
])

AC_DEFUN([BPERF_SETUP_CCACHE],
[
    AC_ARG_ENABLE([ccache],
	      [AS_HELP_STRING([--disable-ccache],
	      		      [use ccache to speed up recompilations @<:@enabled@:>@])],
              [ENABLE_CCACHE=${enable_ccache}], [ENABLE_CCACHE=yes])
    if test "x$ENABLE_CCACHE" = xyes; then
        AC_PATH_PROG(CCACHE, ccache)
    else
        AC_MSG_CHECKING([for ccache])
        AC_MSG_RESULT([explicitly disabled])    
        CCACHE=
    fi    
    AC_SUBST(CCACHE)

    AC_ARG_WITH([ccache-dir],
	      [AS_HELP_STRING([--with-ccache-dir],
	      		      [where to store ccache files @<:@~/.ccache@:>@])])

    if test "x$with_ccache_dir" != x; then
        # When using a non home ccache directory, assume the use is to share ccache files
        # with other users. Thus change the umask.
        SET_CCACHE_DIR="CCACHE_DIR=$with_ccache_dir CCACHE_UMASK=002"
    fi
    CCACHE_FOUND=""
    if test "x$CCACHE" != x; then
        BPERF_SETUP_CCACHE_USAGE
    fi    
])

AC_DEFUN([BPERF_SETUP_CCACHE_USAGE],
[
    if test "x$CCACHE" != x; then
        CCACHE_FOUND="true"
        # Only use ccache if it is 3.1.4 or later, which supports
        # precompiled headers.
        AC_MSG_CHECKING([if ccache supports precompiled headers])
        HAS_GOOD_CCACHE=`($CCACHE --version | head -n 1 | grep -E 3.1.@<:@456789@:>@) 2> /dev/null`
        if test "x$HAS_GOOD_CCACHE" = x; then
            AC_MSG_RESULT([no, disabling ccache])
            CCACHE=
        else
            AC_MSG_RESULT([yes])
            AC_MSG_CHECKING([if C-compiler supports ccache precompiled headers])
            PUSHED_FLAGS="$CXXFLAGS"
            CXXFLAGS="-fpch-preprocess $CXXFLAGS"
            AC_COMPILE_IFELSE([AC_LANG_PROGRAM([], [])], [CC_KNOWS_CCACHE_TRICK=yes], [CC_KNOWS_CCACHE_TRICK=no])
            CXXFLAGS="$PUSHED_FLAGS"
            if test "x$CC_KNOWS_CCACHE_TRICK" = xyes; then
                AC_MSG_RESULT([yes])
            else
                AC_MSG_RESULT([no, disabling ccaching of precompiled headers])
                CCACHE=
            fi
        fi
    fi

    if test "x$CCACHE" != x; then
        CCACHE_SLOPPINESS=time_macros
        CCACHE="CCACHE_COMPRESS=1 $SET_CCACHE_DIR CCACHE_SLOPPINESS=$CCACHE_SLOPPINESS $CCACHE"
        CCACHE_FLAGS=-fpch-preprocess

        if test "x$SET_CCACHE_DIR" != x; then
            mkdir -p $CCACHE_DIR > /dev/null 2>&1
	    chmod a+rwxs $CCACHE_DIR > /dev/null 2>&1
        fi
    fi
])

AC_DEFUN_ONCE([BPERF_SETUP_PRECOMPILED_HEADERS],
[
       
###############################################################################
#
# Can the C/C++ compiler use precompiled headers?
#
AC_ARG_ENABLE([precompiled-headers], [AS_HELP_STRING([--disable-precompiled-headers],
	[use precompiled headers when compiling C++ @<:@enabled@:>@])],
    [ENABLE_PRECOMPH=${enable_precompiled-headers}], [ENABLE_PRECOMPH=yes])

USE_PRECOMPILED_HEADER=1
if test "x$ENABLE_PRECOMPH" = xno; then
    USE_PRECOMPILED_HEADER=0
fi

if test "x$ENABLE_PRECOMPH" = xyes; then
    # Check that the compiler actually supports precomp headers.
    if test "x$GCC" = xyes; then
         AC_MSG_CHECKING([that precompiled headers work])         
         echo "int alfa();" > conftest.h
         $CXX -x c++-header conftest.h -o conftest.hpp.gch
         if test ! -f conftest.hpp.gch; then
             echo Precompiled header is not working!
             USE_PRECOMPILED_HEADER=0
             AC_MSG_RESULT([no])        
         else
             AC_MSG_RESULT([yes])
         fi
         rm -f conftest.h
    fi
fi

AC_SUBST(USE_PRECOMPILED_HEADER)
])


AC_DEFUN_ONCE([BPERF_SETUP_SMART_JAVAC],
[
AC_ARG_WITH(server-java, [AS_HELP_STRING([--with-server-java],
	[use this java binary for running the javac background server and other long running java tasks in the build process,
     e.g. ---with-server-java="/opt/jrockit/bin/java -server"])])

if test "x$with_server_java" != x; then
    SERVER_JAVA="$with_server_java"
    FOUND_VERSION=`$SERVER_JAVA -version 2>&1 | grep " version \""`
    if test "x$FOUND_VERSION" = x; then
        AC_MSG_ERROR([Could not execute server java: $SERVER_JAVA])
    fi
else
    SERVER_JAVA=""
    # Hotspot specific options.
    ADD_JVM_ARG_IF_OK([-XX:+UseParallelOldGC],SERVER_JAVA,[$JAVA])
    ADD_JVM_ARG_IF_OK([-verbosegc],SERVER_JAVA,[$JAVA])
    # JRockit specific options.
    ADD_JVM_ARG_IF_OK([-Xverbose:gc],SERVER_JAVA,[$JAVA])
    SERVER_JAVA="$JAVA $SERVER_JAVA"
fi                    
AC_SUBST(SERVER_JAVA)

AC_MSG_CHECKING([whether to use shared server for javac])
AC_ARG_ENABLE([javac-server], [AS_HELP_STRING([--enable-javac-server],
	[enable the shared javac server during the build process @<:@disabled@:>@])],
	[ENABLE_JAVAC_SERVER="${enableval}"], [ENABLE_JAVAC_SERVER='no'])
AC_MSG_RESULT([$ENABLE_JAVAC_SERVER])
if test "x$ENABLE_JAVAC_SERVER" = xyes; then
    JAVAC_USE_REMOTE=true
    JAVAC_SERVERS="$OUTPUT_ROOT/javacservers"
else
    JAVAC_USE_REMOTE=false
    JAVAC_SERVERS=
fi
AC_SUBST(JAVAC_USE_REMOTE)
AC_SUBST(JAVAC_SERVERS)

AC_ARG_WITH(javac-server-cores, [AS_HELP_STRING([--with-javac-server-cores],
	[use at most this number of concurrent threads on the javac server @<:@probed@:>@])])
if test "x$with_javac_server_cores" != x; then
    JAVAC_SERVER_CORES="$with_javac_server_cores"
else
    if test "$NUM_CORES" -gt 16; then
        # We set this arbitrary limit because we want to limit the heap
        # size of the javac server.
        # In the future we will make the javac compilers in the server
        # share more and more state, thus enabling us to use more and
        # more concurrent threads in the server.
        JAVAC_SERVER_CORES="16"
    else
        JAVAC_SERVER_CORES="$NUM_CORES"
    fi

    if test "$MEMORY_SIZE" -gt "17000"; then
        MAX_HEAP_MEM=10000
        ADD_JVM_ARG_IF_OK([-d64],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xms10G -Xmx10G],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn2G],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "10000"; then
        MAX_HEAP_MEM=6000
        ADD_JVM_ARG_IF_OK([-d64],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xms6G -Xmx6G],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn1G],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "5000"; then
        MAX_HEAP_MEM=3000
        ADD_JVM_ARG_IF_OK([-d64],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xms1G -Xmx3G],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn256M],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "3800"; then
        MAX_HEAP_MEM=2500
        ADD_JVM_ARG_IF_OK([-Xms1G -Xmx2500M],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn256M],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "1900"; then
        MAX_HEAP_MEM=1200
        ADD_JVM_ARG_IF_OK([-Xms700M -Xmx1200M],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn256M],SERVER_JAVA,[$SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "1000"; then
        MAX_HEAP_MEM=900
        ADD_JVM_ARG_IF_OK([-Xms400M -Xmx900M],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn128M],SERVER_JAVA,[$SERVER_JAVA])
    else
        MAX_HEAP_MEM=512
        ADD_JVM_ARG_IF_OK([-Xms256M -Xmx512M],SERVER_JAVA,[$SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xmn128M],SERVER_JAVA,[$SERVER_JAVA])
    fi

    MAX_COMPILERS_IN_HEAP=`expr $MAX_HEAP_MEM / 501`
    if test "$JAVAC_SERVER_CORES" -gt "$MAX_COMPILERS_IN_HEAP"; then
        AC_MSG_CHECKING([if number of server cores must be reduced])
        JAVAC_SERVER_CORES="$MAX_COMPILERS_IN_HEAP"
        AC_MSG_RESULT([yes, to $JAVAC_SERVER_CORES with max heap size $MAX_HEAP_MEM MB])
    fi
fi                    
AC_SUBST(JAVAC_SERVER_CORES)

AC_MSG_CHECKING([whether to track dependencies between Java packages])
AC_ARG_ENABLE([javac-deps], [AS_HELP_STRING([--enable-javac-deps],
	[enable the dependency tracking between Java packages @<:@disabled@:>@])],
	[ENABLE_JAVAC_DEPS="${enableval}"], [ENABLE_JAVAC_DEPS='no'])
AC_MSG_RESULT([$ENABLE_JAVAC_DEPS])
if test "x$ENABLE_JAVAC_DEPS" = xyes; then
    JAVAC_USE_DEPS=true
else
    JAVAC_USE_DEPS=false
fi
AC_SUBST(JAVAC_USE_DEPS)

AC_MSG_CHECKING([whether to use multiple cores for javac compilation])
AC_ARG_ENABLE([javac-multi-core], [AS_HELP_STRING([--enable-javac-multi-core],
	[compile Java packages concurrently @<:@disabled@:>@])],
	[ENABLE_JAVAC_MULTICORE="${enableval}"], [ENABLE_JAVAC_MULTICORE='no'])
AC_MSG_RESULT([$ENABLE_JAVAC_MULTICORE])
if test "x$ENABLE_JAVAC_MULTICORE" = xyes; then
    JAVAC_USE_MODE=MULTI_CORE_CONCURRENT
else
    JAVAC_USE_MODE=SINGLE_THREADED_BATCH
    if test "x$ENABLE_JAVAC_DEPS" = xyes; then
        AC_MSG_WARN([Dependency tracking is not supported with single threaded batch compiles of Java source roots. Please add --disable-javac-deps to your configure options.])
        AC_MSG_WARN([Disabling dependency tracking for you now.])
        JAVAC_USE_DEPS=false
    fi
    if test "x$ENABLE_JAVAC_SERVER" = xyes; then
        AC_MSG_WARN([The javac server will not be used since single threaded batch compiles are run within their own JVM. Please add --disable-javac-server to your configure options.])
        AC_MSG_WARN([Disabling javac server for you now.])
        JAVAC_USE_REMOTE=false
    fi
fi
AC_SUBST(JAVAC_USE_MODE)

AC_MSG_CHECKING([whether to use sjavac])
AC_ARG_ENABLE([sjavac], [AS_HELP_STRING([--enable-sjavac],
	[use sjavac to do fast incremental compiles @<:@disabled@:>@])],
	[ENABLE_SJAVAC="${enableval}"], [ENABLE_SJAVAC='no'])
AC_MSG_RESULT([$ENABLE_SJAVAC])
AC_SUBST(ENABLE_SJAVAC)

])
