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
    if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
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
        if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
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
    
    if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
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
    if test "x$OPENJDK_BUILD_OS" != xwindows; then
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

AC_DEFUN_ONCE([BASIC_INIT],
[
# Save the original command line. This is passed to us by the wrapper configure script.
AC_SUBST(CONFIGURE_COMMAND_LINE)
DATE_WHEN_CONFIGURED=`LANG=C date`
AC_SUBST(DATE_WHEN_CONFIGURED)

# Locate the directory of this script.
SCRIPT="[$]0"
REMOVE_SYMBOLIC_LINKS(SCRIPT)        
AUTOCONF_DIR=`dirname [$]0`
])

AC_DEFUN_ONCE([BASIC_SETUP_PATHS],
[
# Where is the source? It is located two levels above the configure script.
CURDIR="$PWD"
cd "$AUTOCONF_DIR/../.."
SRC_ROOT="`pwd`"
if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
    SRC_ROOT_LENGTH=`pwd|wc -m`
    if test $SRC_ROOT_LENGTH -gt 100; then
        AC_MSG_ERROR([Your base path is too long. It is $SRC_ROOT_LENGTH characters long, but only 100 is supported])
    fi
fi
AC_SUBST(SRC_ROOT)
cd "$CURDIR"

SPACESAFE(SRC_ROOT,[the path to the source root])
SPACESAFE(CURDIR,[the path to the current directory])
])

AC_DEFUN_ONCE([BASIC_SETUP_SEARCHPATH],
[
if test "x$OPENJDK_BUILD_OS" = "xsolaris"; then
    # Add extra search paths on solaris for utilities like ar and as etc...
    PATH="$PATH:/usr/ccs/bin:/usr/sfw/bin:/opt/csw/bin"
fi
])

AC_DEFUN_ONCE([BASIC_SETUP_PATH_SEP],
[
# For cygwin we need cygpath first, since it is used everywhere.
AC_PATH_PROG(CYGPATH, cygpath)
PATH_SEP=":"
if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
    if test "x$CYGPATH" = x; then
        AC_MSG_ERROR([Something is wrong with your cygwin installation since I cannot find cygpath.exe in your path])
    fi
    PATH_SEP=";"
fi
AC_SUBST(PATH_SEP)
])

AC_DEFUN_ONCE([BASIC_SETUP_OUTPUT_DIR],
[

AC_ARG_WITH(conf-name, [AS_HELP_STRING([--with-conf-name],
	[use this as the name of the configuration, overriding the generated default])],
        [ CONF_NAME=${with_conf_name} ])

# Test from where we are running configure, in or outside of src root.
if test "x$CURDIR" = "x$SRC_ROOT" || test "x$CURDIR" = "x$SRC_ROOT/common" || test "x$CURDIR" = "x$SRC_ROOT/common/autoconf" || test "x$CURDIR" = "x$SRC_ROOT/common/makefiles" ; then
    # We are running configure from the src root.
    # Create a default ./build/target-variant-debuglevel output root.
    if test "x${CONF_NAME}" = x; then
        CONF_NAME="${OPENJDK_TARGET_OS}-${OPENJDK_TARGET_CPU}-${JDK_VARIANT}-${ANDED_JVM_VARIANTS}-${DEBUG_LEVEL}"
    fi
    OUTPUT_ROOT="$SRC_ROOT/build/${CONF_NAME}"
    mkdir -p "$OUTPUT_ROOT"
    if test ! -d "$OUTPUT_ROOT"; then
        AC_MSG_ERROR([Could not create build directory $OUTPUT_ROOT])
    fi
else
    # We are running configure from outside of the src dir.
    # Then use the current directory as output dir!
    # If configuration is situated in normal build directory, just use the build
    # directory name as configuration name, otherwise use the complete path.
    if test "x${CONF_NAME}" = x; then
        CONF_NAME=`$ECHO $CURDIR | $SED -e "s!^${SRC_ROOT}/build/!!"`
    fi
    OUTPUT_ROOT="$CURDIR"
fi

SPACESAFE(OUTPUT_ROOT,[the path to the output root])

AC_SUBST(SPEC, $OUTPUT_ROOT/spec.gmk)
AC_SUBST(CONF_NAME, $CONF_NAME)
AC_SUBST(OUTPUT_ROOT, $OUTPUT_ROOT)

# Most of the probed defines are put into config.h
AC_CONFIG_HEADERS([$OUTPUT_ROOT/config.h:$AUTOCONF_DIR/config.h.in])
# The spec.gmk file contains all variables for the make system.
AC_CONFIG_FILES([$OUTPUT_ROOT/spec.gmk:$AUTOCONF_DIR/spec.gmk.in])
# The spec.sh file contains variables for compare{images|-objects}.sh scrips.
AC_CONFIG_FILES([$OUTPUT_ROOT/spec.sh:$AUTOCONF_DIR/spec.sh.in])
# The generated Makefile knows where the spec.gmk is and where the source is.
# You can run make from the OUTPUT_ROOT, or from the top-level Makefile
# which will look for generated configurations
AC_CONFIG_FILES([$OUTPUT_ROOT/Makefile:$AUTOCONF_DIR/Makefile.in])

# Save the arguments given to us
echo "$CONFIGURE_COMMAND_LINE" > $OUTPUT_ROOT/configure-arguments
])

AC_DEFUN_ONCE([BASIC_SETUP_LOGGING],
[
# Setup default logging of stdout and stderr to build.log in the output root.
BUILD_LOG='$(OUTPUT_ROOT)/build.log'
BUILD_LOG_PREVIOUS='$(OUTPUT_ROOT)/build.log.old'
BUILD_LOG_WRAPPER='$(SH) $(SRC_ROOT)/common/bin/logger.sh $(BUILD_LOG)'
AC_SUBST(BUILD_LOG)
AC_SUBST(BUILD_LOG_PREVIOUS)
AC_SUBST(BUILD_LOG_WRAPPER)
])


#%%% Simple tools %%%

AC_DEFUN([BASIC_CHECK_FIND_DELETE],
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

AC_DEFUN_ONCE([BASIC_SETUP_TOOLS],
[
# Start with tools that do not need have cross compilation support
# and can be expected to be found in the default PATH. These tools are
# used by configure. Nor are these tools expected to be found in the
# devkit from the builddeps server either, since they are
# needed to download the devkit. 
AC_PROG_AWK
CHECK_NONEMPTY(AWK)
AC_PATH_PROG(CAT, cat)
CHECK_NONEMPTY(CAT)
AC_PATH_PROG(CHMOD, chmod)
CHECK_NONEMPTY(CHMOD)
AC_PATH_PROG(CP, cp)
CHECK_NONEMPTY(CP)
AC_PATH_PROG(CPIO, cpio)
CHECK_NONEMPTY(CPIO)
AC_PATH_PROG(CUT, cut)
CHECK_NONEMPTY(CUT)
AC_PATH_PROG(DATE, date)
CHECK_NONEMPTY(DATE)
AC_PATH_PROG(DF, df)
CHECK_NONEMPTY(DF)
AC_PATH_PROG(DIFF, diff)
CHECK_NONEMPTY(DIFF)
# Warning echo is really, really unportable!!!!! Different
# behaviour in bash and dash and in a lot of other shells!
# Use printf for serious work! 
AC_PATH_PROG(ECHO, echo)
CHECK_NONEMPTY(ECHO)
AC_PROG_EGREP
CHECK_NONEMPTY(EGREP)
AC_PROG_FGREP
CHECK_NONEMPTY(FGREP)

AC_PATH_PROG(FIND, find)
CHECK_NONEMPTY(FIND)
BASIC_CHECK_FIND_DELETE
AC_SUBST(FIND_DELETE)

AC_PROG_GREP
CHECK_NONEMPTY(GREP)
AC_PATH_PROG(HEAD, head)
CHECK_NONEMPTY(HEAD)
AC_PATH_PROG(LN, ln)
CHECK_NONEMPTY(LN)
AC_PATH_PROG(LS, ls)
CHECK_NONEMPTY(LS)
AC_PATH_PROGS(MAKE, [gmake make])
CHECK_NONEMPTY(MAKE)
MAKE_VERSION=`$MAKE --version | head -n 1 | grep '3.8[[12346789]]'`
if test "x$MAKE_VERSION" = x; then
    AC_MSG_ERROR([You must use GNU make 3.81 or newer! Please put it in the path, or add e.g. MAKE=/opt/gmake3.81/make as argument to configure.])
fi
AC_PATH_PROG(MKDIR, mkdir)
CHECK_NONEMPTY(MKDIR)
AC_PATH_PROG(MV, mv)
CHECK_NONEMPTY(MV)
AC_PATH_PROGS(NAWK, [nawk gawk awk])
CHECK_NONEMPTY(NAWK)
AC_PATH_PROG(PRINTF, printf)
CHECK_NONEMPTY(PRINTF)
AC_PATH_PROG(THEPWDCMD, pwd)
AC_PATH_PROG(RM, rm)
CHECK_NONEMPTY(RM)
RM="$RM -f"
AC_PROG_SED
CHECK_NONEMPTY(SED)
AC_PATH_PROG(SH, sh)
CHECK_NONEMPTY(SH)
AC_PATH_PROG(SORT, sort)
CHECK_NONEMPTY(SORT)
AC_PATH_PROG(TAR, tar)
CHECK_NONEMPTY(TAR)
AC_PATH_PROG(TAIL, tail)
CHECK_NONEMPTY(TAIL)
AC_PATH_PROG(TEE, tee)
CHECK_NONEMPTY(TEE)
AC_PATH_PROG(TR, tr)
CHECK_NONEMPTY(TR)
AC_PATH_PROG(TOUCH, touch)
CHECK_NONEMPTY(TOUCH)
AC_PATH_PROG(WC, wc)
CHECK_NONEMPTY(WC)
AC_PATH_PROG(XARGS, xargs)
CHECK_NONEMPTY(XARGS)
AC_PATH_PROG(ZIP, zip)
CHECK_NONEMPTY(ZIP)
AC_PATH_PROG(UNZIP, unzip)
CHECK_NONEMPTY(UNZIP)
AC_PATH_PROG(LDD, ldd)
if test "x$LDD" = "x"; then
    # List shared lib dependencies is used for
    # debug output and checking for forbidden dependencies.
    # We can build without it.
    LDD="true"
fi
AC_PATH_PROG(OTOOL, otool)
if test "x$OTOOL" = "x"; then
   OTOOL="true"
fi
AC_PATH_PROG(READELF, readelf)
AC_PATH_PROG(EXPR, expr)
CHECK_NONEMPTY(EXPR)
AC_PATH_PROG(FILE, file)
CHECK_NONEMPTY(FILE)
AC_PATH_PROG(HG, hg)
])



AC_DEFUN_ONCE([BASIC_COMPILE_UNCYGDRIVE],
[
# When using cygwin, we need a wrapper binary that renames
# /cygdrive/c/ arguments into c:/ arguments and peeks into
# @files and rewrites these too! This wrapper binary is
# called uncygdrive.exe.
UNCYGDRIVE=
if test "x$OPENJDK_BUILD_OS" = xwindows; then
    AC_MSG_CHECKING([if uncygdrive can be created])
    UNCYGDRIVE_SRC=`$CYGPATH -m $SRC_ROOT/common/src/uncygdrive.c`
    rm -f $OUTPUT_ROOT/uncygdrive*
    UNCYGDRIVE=`$CYGPATH -m $OUTPUT_ROOT/uncygdrive.exe`
    cd $OUTPUT_ROOT
    $CC $UNCYGDRIVE_SRC /Fe$UNCYGDRIVE > $OUTPUT_ROOT/uncygdrive1.log 2>&1
    cd $CURDIR

    if test ! -x $OUTPUT_ROOT/uncygdrive.exe; then 
        AC_MSG_RESULT([no])
        cat $OUTPUT_ROOT/uncygdrive1.log
        AC_MSG_ERROR([Could not create $OUTPUT_ROOT/uncygdrive.exe])
    fi
    AC_MSG_RESULT([$UNCYGDRIVE])
    AC_MSG_CHECKING([if uncygdrive.exe works])
    cd $OUTPUT_ROOT
    $UNCYGDRIVE $CC $SRC_ROOT/common/src/uncygdrive.c /Fe$OUTPUT_ROOT/uncygdrive2.exe > $OUTPUT_ROOT/uncygdrive2.log 2>&1 
    cd $CURDIR
    if test ! -x $OUTPUT_ROOT/uncygdrive2.exe; then 
        AC_MSG_RESULT([no])
        cat $OUTPUT_ROOT/uncygdrive2.log
        AC_MSG_ERROR([Uncygdrive did not work!])
    fi
    AC_MSG_RESULT([yes])
    rm -f $OUTPUT_ROOT/uncygdrive?.??? $OUTPUT_ROOT/uncygdrive.obj
fi

AC_SUBST(UNCYGDRIVE)
])


# Check if build directory is on local disk.
# Argument 1: directory to test
# Argument 2: what to do if it is on local disk
# Argument 3: what to do otherwise (remote disk or failure)
AC_DEFUN([BASIC_CHECK_DIR_ON_LOCAL_DISK],
[
	# df -l lists only local disks; if the given directory is not found then
	# a non-zero exit code is given
	if $DF -l $1 > /dev/null 2>&1; then
          $2
        else
          $3
        fi
])

AC_DEFUN_ONCE([BASIC_TEST_USABILITY_ISSUES],
[

AC_MSG_CHECKING([if build directory is on local disk])
BASIC_CHECK_DIR_ON_LOCAL_DISK($OUTPUT_ROOT,
  [OUTPUT_DIR_IS_LOCAL="yes"],
  [OUTPUT_DIR_IS_LOCAL="no"])
AC_MSG_RESULT($OUTPUT_DIR_IS_LOCAL)

# Check if the user has any old-style ALT_ variables set.
FOUND_ALT_VARIABLES=`env | grep ^ALT_`

# Before generating output files, test if they exist. If they do, this is a reconfigure.
# Since we can't properly handle the dependencies for this, warn the user about the situation
if test -e $OUTPUT_ROOT/spec.gmk; then
  IS_RECONFIGURE=yes
else
  IS_RECONFIGURE=no
fi

if test -e $SRC_ROOT/build/.hide-configure-performance-hints; then
  HIDE_PERFORMANCE_HINTS=yes
else
  HIDE_PERFORMANCE_HINTS=no
  # Hide it the next time around...
  $TOUCH $SRC_ROOT/build/.hide-configure-performance-hints > /dev/null 2>&1
fi

])
