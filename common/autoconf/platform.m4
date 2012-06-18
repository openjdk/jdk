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

AC_DEFUN([CHECK_FIND_DELETE],
[
    # Test if find supports -delete
    AC_MSG_CHECKING([if find supports -delete])
    FIND_DELETE="-delete"

    DELETEDIR=`mktemp -d tmp.XXXXXXXXXX` || (echo Could not create temporary directory!; exit $?)

    echo Hejsan > $DELETEDIR/TestIfFindSupportsDelete

    TEST_DELETE=`$FIND "$DELETEDIR" -name TestIfFindSupportsDelete $FIND_DELETE 2>&1`
    if test -f $DELETEDIR/TestIfFindSupportsDelete; then
        # No, it does not.
        rm $DELETEDIR/TestIfFindSupportsDelete
        FIND_DELETE="-exec rm \{\} \+"
        AC_MSG_RESULT([no])    
    else
        AC_MSG_RESULT([yes])    
    fi
    rmdir $DELETEDIR
])

AC_DEFUN([CHECK_NONEMPTY],
[
    # Test that variable $1 is not empty.
    if test "" = "[$]$1"; then AC_MSG_ERROR(Could not find translit($1,A-Z,a-z) !); fi
])

AC_DEFUN([ADD_JVM_ARG_IF_OK],
[
    # Test if $1 is a valid argument to $3 (often is $JAVA passed as $3)
    # If so, then append $1 to $2
    FOUND_WARN=`$3 $1 -version 2>&1 | grep -i warn`
    FOUND_VERSION=`$3 $1 -version 2>&1 | grep " version \""`
    if test "x$FOUND_VERSION" != x && test "x$FOUND_WARN" = x; then
        $2="[$]$2 $1"
    fi
])

AC_DEFUN([WHICHCMD],
[
    # Translate "gcc -E" into "`which gcc` -E" ie
    # extract the full path to the binary and at the
    # same time maintain any arguments passed to it.
    # The command MUST exist in the path, or else!
    tmp="[$]$1"
    car="${tmp%% *}"
    tmp="[$]$1 EOL"
    cdr="${tmp#* }"
    # On windows we want paths without spaces.
    if test "x$BUILD_OS" = "xwindows"; then
        WHICHCMD_SPACESAFE(car)
    else
        # "which" is not portable, but is used here
        # because we know that the command exists!
        car=`which $car`
    fi
    if test "x$cdr" != xEOL; then
        $1="$car ${cdr% *}"
    else
        $1="$car"
    fi
])

AC_DEFUN([SPACESAFE],
[
    # Fail with message $2 if var $1 contains a path with no spaces in it.
    # Unless on Windows, where we can rewrite the path.
    HAS_SPACE=`echo "[$]$1" | grep " "`
    if test "x$HAS_SPACE" != x; then
        if test "x$BUILD_OS" = "xwindows"; then
            $1=`$CYGPATH -s -m -a "[$]$1"`
            $1=`$CYGPATH -u "[$]$1"`            
        else
            AC_MSG_ERROR([You cannot have spaces in $2! "[$]$1"])
        fi
    fi
])

AC_DEFUN([WHICHCMD_SPACESAFE],
[
    # Translate long cygdrive or C:\sdfsf path
    # into a short mixed mode path that has no
    # spaces in it.
    tmp="[$]$1"
    if test "x$BUILD_OS" = "xwindows"; then
        tmp=`$CYGPATH -u "[$]$1"`
        tmp=`which "$tmp"`
        # If file exists with .exe appended, that's the real filename
        # and cygpath needs that to convert to short style path.
        if test -f "${tmp}.exe"; then
           tmp="${tmp}.exe"
        elif test -f "${tmp}.cmd"; then
           tmp="${tmp}.cmd"
        fi
        # Convert to C:/ mixed style path without spaces.
         tmp=`$CYGPATH -s -m "$tmp"`
    fi
    $1="$tmp"
])

AC_DEFUN([REMOVE_SYMBOLIC_LINKS],
[
    if test "x$BUILD_OS" != xwindows; then
        # Follow a chain of symbolic links. Use readlink
        # where it exists, else fall back to horribly
        # complicated shell code.
        AC_PATH_PROG(READLINK, readlink)
        if test "x$READLINK_TESTED" != yes; then
            # On MacOSX there is a readlink tool with a different
            # purpose than the GNU readlink tool. Check the found readlink.
            ISGNU=`$READLINK --help 2>&1 | grep GNU`
            if test "x$ISGNU" = x; then
                 # A readlink that we do not know how to use.
                 # Are there other non-GNU readlinks out there?
                 READLINK_TESTED=yes
                 READLINK=
            fi
        fi

        if test "x$READLINK" != x; then
            $1=`$READLINK -f [$]$1`
        else
            STARTDIR=$PWD
            COUNTER=0
            DIR=`dirname [$]$1`
            FIL=`basename [$]$1`
            while test $COUNTER -lt 20; do
                ISLINK=`ls -l $DIR/$FIL | grep '\->' | sed -e 's/.*-> \(.*\)/\1/'`
                if test "x$ISLINK" == x; then
                    # This is not a symbolic link! We are done!
                    break
                fi
                # The link might be relative! We have to use cd to travel safely.
                cd $DIR
                cd `dirname $ISLINK`
                DIR=`pwd`
                FIL=`basename $ISLINK`
                let COUNTER=COUNTER+1
            done
            cd $STARTDIR
            $1=$DIR/$FIL
        fi
    fi
])

AC_DEFUN([TESTFOR_PROG_CCACHE],
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
        SETUP_CCACHE_USAGE
    fi    
])

AC_DEFUN([SETUP_CCACHE_USAGE],
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

AC_DEFUN([EXTRACT_HOST_AND_BUILD_AND_LEGACY_VARS],
[
    # Expects $host_os $host_cpu $build_os and $build_cpu
    # and $with_data_model to have been setup!
    #
    # Translate the standard triplet(quadruplet) definition
    # of the host/build system into
    # HOST_OS=aix,bsd,hpux,linux,macosx,solaris,windows
    # HOST_OS_FAMILY=bsd,gnu,sysv,win32,wince
    # HOST_OS_API=posix,winapi
    # 
    # HOST_CPU=ia32,x64,sparc,sparcv9,arm,arm64,ppc,ppc64
    # HOST_CPU_ARCH=x86,sparc,pcc,arm
    # HOST_CPU_BITS=32,64
    # HOST_CPU_ENDIAN=big,little
    #
    # The same values are setup for BUILD_...
    # 
    # And the legacy variables, for controlling the old makefiles.
    # LEGACY_HOST_CPU1=i586,amd64/x86_64,sparc,sparcv9,arm,arm64...
    # LEGACY_HOST_CPU2=i386,amd64,sparc,sparcv9,arm,arm64...
    # LEGACY_HOST_CPU3=sparcv9,amd64 (but only on solaris)
    # LEGACY_HOST_OS_API=solaris,windows
    #
    # We also copy the autoconf trip/quadruplet
    # verbatim to HOST and BUILD
    AC_SUBST(HOST, ${host})
    AC_SUBST(BUILD, ${build})
    
    EXTRACT_VARS_FROM_OS_TO(HOST,$host_os)
    EXTRACT_VARS_FROM_CPU_TO(HOST,$host_cpu)

    EXTRACT_VARS_FROM_OS_TO(BUILD,$build_os)
    EXTRACT_VARS_FROM_CPU_TO(BUILD,$build_cpu)

    if test "x$HOST_OS" != xsolaris; then
        LEGACY_HOST_CPU3=""
        LEGACY_BUILD_CPU3=""
    fi

    # On MacOSX and MacOSX only, we have a different name for the x64 CPU in ARCH (LEGACY_HOST_CPU1) ...
    if test "x$HOST_OS" = xmacosx && test "x$HOST_CPU" = xx64; then
        LEGACY_HOST_CPU1="x86_64"
    fi

    SET_RELEASE_FILE_OS_VALUES()
])

AC_DEFUN([EXTRACT_VARS_FROM_OS_TO],
[
    EXTRACT_VARS_FROM_OS($2)
    $1_OS="$VAR_OS"
    $1_OS_FAMILY="$VAR_OS_FAMILY"
    $1_OS_API="$VAR_OS_API"

    AC_SUBST($1_OS)
    AC_SUBST($1_OS_FAMILY)
    AC_SUBST($1_OS_API)

    if test "x$$1_OS_API" = xposix; then
        LEGACY_$1_OS_API="solaris"
    fi
    if test "x$$1_OS_API" = xwinapi; then
        LEGACY_$1_OS_API="windows"
    fi
    AC_SUBST(LEGACY_$1_OS_API)    
])

AC_DEFUN([EXTRACT_VARS_FROM_CPU_TO],
[
    EXTRACT_VARS_FROM_CPU($2)
    $1_CPU="$VAR_CPU"
    $1_CPU_ARCH="$VAR_CPU_ARCH"
    $1_CPU_BITS="$VAR_CPU_BITS"
    $1_CPU_ENDIAN="$VAR_CPU_ENDIAN"

    AC_SUBST($1_CPU)
    AC_SUBST($1_CPU_ARCH)
    AC_SUBST($1_CPU_BITS)
    AC_SUBST($1_CPU_ENDIAN)
    
    # Also store the legacy naming of the cpu.
    # Ie i586 and amd64 instead of ia32 and x64
    LEGACY_$1_CPU1="$VAR_LEGACY_CPU"
    AC_SUBST(LEGACY_$1_CPU1)

    # And the second legacy naming of the cpu.
    # Ie i386 and amd64 instead of ia32 and x64.
    LEGACY_$1_CPU2="$LEGACY_$1_CPU1"
    if test "x$LEGACY_$1_CPU1" = xi586; then 
        LEGACY_$1_CPU2=i386
    fi
    AC_SUBST(LEGACY_$1_CPU2)

    # And the third legacy naming of the cpu.
    # Ie only amd64 or sparcv9, used for the ISA_DIR on Solaris.
    LEGACY_$1_CPU3=""
    if test "x$$1_CPU" = xx64; then 
        LEGACY_$1_CPU3=amd64
    fi
    if test "x$$1_CPU" = xsparcv9; then 
        LEGACY_$1_CPU3=sparvc9
    fi
    AC_SUBST(LEGACY_$1_CPU3)
])

AC_DEFUN([EXTRACT_VARS_FROM_CPU],
[
  # First argument is the cpu name from the trip/quad
  case "$1" in
    x86_64)
      VAR_CPU=x64
      VAR_CPU_ARCH=x86
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=little
      VAR_LEGACY_CPU=amd64
      ;;
    i?86)
      VAR_CPU=ia32
      VAR_CPU_ARCH=x86
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=little
      VAR_LEGACY_CPU=i586
      ;;
    alpha*)
      VAR_CPU=alpha
      VAR_CPU_ARCH=alpha
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=alpha
      ;;
    arm*)
      VAR_CPU=arm
      VAR_CPU_ARCH=arm
      VAR_CPU_BITS=3264
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=arm
      ;;
    mips)
      VAR_CPU=mips
      VAR_CPU_ARCH=mips
      VAR_CPU_BITS=woot
      VAR_CPU_ENDIAN=woot
      VAR_LEGACY_CPU=mips
       ;;
    mipsel)
      VAR_CPU=mipsel
      VAR_CPU_ARCH=mips
      VAR_CPU_BITS=woot
      VAR_CPU_ENDIAN=woot
      VAR_LEGACY_CPU=mipsel
       ;;
    powerpc)
      VAR_CPU=ppc
      VAR_CPU_ARCH=ppc
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=ppc
       ;;
    powerpc64)
      VAR_CPU=ppc64
      VAR_CPU_ARCH=ppc
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=32
      VAR_LEGACY_CPU=ppc64
       ;;
    sparc)
      VAR_CPU=sparc
      VAR_CPU_ARCH=sparc
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=sparc
       ;;
    sparc64)
      VAR_CPU=sparcv9
      VAR_CPU_ARCH=sparc
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=big
      VAR_LEGACY_CPU=sparc_sparcv9
       ;;
    s390)
      VAR_CPU=s390
      VAR_CPU_ARCH=s390
      VAR_CPU_BITS=32
      VAR_CPU_ENDIAN=woot
      VAR_LEGACY_CPU=s390
      VAR_LEGACY_CPU=s390
       ;;
    s390x)
      VAR_CPU=s390x
      VAR_CPU_ARCH=s390
      VAR_CPU_BITS=64
      VAR_CPU_ENDIAN=woot
      VAR_LEGACY_CPU=s390x
       ;;
    *)
      AC_MSG_ERROR([unsupported cpu $1])
      ;;
  esac

  # Workaround cygwin not knowing about 64 bit.
  if test "x$VAR_OS" = "xwindows"; then
      if test "x$PROCESSOR_IDENTIFIER" != "x"; then
          PROC_ARCH=`echo $PROCESSOR_IDENTIFIER | $CUT -f1 -d' '`
          case "$PROC_ARCH" in
            intel64|Intel64|INTEL64|em64t|EM64T|amd64|AMD64|8664|x86_64)
              VAR_CPU=x64
              VAR_CPU_BITS=64
              VAR_LEGACY_CPU=amd64
              ;;
          esac
      fi
  fi

  if test "x$VAR_CPU_ARCH" = "xx86"; then
      if test "x$with_data_model" = "x64"; then
          VAR_CPU=x64
          VAR_CPU_BITS=64
          VAR_LEGACY_CPU=amd64
      fi
      if test "x$with_data_model" = "x32"; then
          VAR_CPU=ia32
          VAR_CPU_BITS=32
          VAR_LEGACY_CPU=i586
      fi
  fi 
])

AC_DEFUN([EXTRACT_VARS_FROM_OS],
[
  case "$1" in
    *linux*)
      VAR_OS=linux
      VAR_OS_API=posix
      VAR_OS_FAMILY=gnu
      ;;
    *solaris*)
      VAR_OS=solaris
      VAR_OS_API=posix
      VAR_OS_FAMILY=sysv
      ;;
    *darwin*)
      VAR_OS=macosx
      VAR_OS_API=posix
      VAR_OS_FAMILY=bsd
      ;;
    *bsd*)
      VAR_OS=bsd
      VAR_OS_API=posix
      VAR_OS_FAMILY=bsd
      ;;
    *cygwin*|*windows*)
      VAR_OS=windows
      VAR_OS_API=winapi
      VAR_OS_FAMILY=windows
      ;;
    *)
      AC_MSG_ERROR([unsupported host operating system $1])
      ;;
  esac
])

AC_DEFUN([CHECK_COMPILER_VERSION],
[
    # Test the compilers that their versions are new enough.
#    AC_MSG_CHECKING([version of GCC])
    gcc_ver=`${CC} -dumpversion`
    gcc_major_ver=`echo ${gcc_ver}|cut -d'.' -f1`
    gcc_minor_ver=`echo ${gcc_ver}|cut -d'.' -f2`
#    AM_CONDITIONAL(GCC_OLD, test ! ${gcc_major_ver} -ge 4 -a ${gcc_minor_ver} -ge 3)
#    AC_MSG_RESULT([${gcc_ver} (major version ${gcc_major_ver}, minor version ${gcc_minor_ver})])
]) 

# Fixes paths on windows hosts to be mixed mode short.
AC_DEFUN([WIN_FIX_PATH],
[
    if test "x$BUILD_OS" = "xwindows"; then
        AC_PATH_PROG(CYGPATH, cygpath)
        tmp="[$]$1"
        # Convert to C:/ mixed style path without spaces.
        tmp=`$CYGPATH -s -m "$tmp"`
        $1="$tmp"
    fi
])

AC_DEFUN([SET_RELEASE_FILE_OS_VALUES],
[
    if test "x$HOST_OS" = "xsolaris"; then
       REQUIRED_OS_NAME=SunOS
       REQUIRED_OS_VERSION=5.10
    fi
    if test "x$HOST_OS" = "xlinux"; then
       REQUIRED_OS_NAME=Linux
       REQUIRED_OS_VERSION=2.6
    fi
    if test "x$HOST_OS" = "xwindows"; then
        REQUIRED_OS_NAME=Windows
        REQUIRED_OS_VERSION=5.1
    fi
    if test "x$HOST_OS" = "xmacosx"; then
        REQUIRED_OS_NAME=Darwin
        REQUIRED_OS_VERSION=11.2
    fi

    AC_SUBST(REQUIRED_OS_NAME)
    AC_SUBST(REQUIRED_OS_VERSION)
])
