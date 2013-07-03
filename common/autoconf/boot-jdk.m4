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

# Execute the check given as argument, and verify the result
# If the Boot JDK was previously found, do nothing
# $1 A command line (typically autoconf macro) to execute
AC_DEFUN([BOOTJDK_DO_CHECK],
[
  if test "x$BOOT_JDK_FOUND" = xno; then
    # Now execute the test
    $1

    # If previous step claimed to have found a JDK, check it to see if it seems to be valid.
    if test "x$BOOT_JDK_FOUND" = xmaybe; then
      # Do we have a bin/java?
      if test ! -x "$BOOT_JDK/bin/java"; then
        AC_MSG_NOTICE([Potential Boot JDK found at $BOOT_JDK did not contain bin/java; ignoring])
        BOOT_JDK_FOUND=no
      else
        # Do we have a bin/javac?
        if test ! -x "$BOOT_JDK/bin/javac"; then
          AC_MSG_NOTICE([Potential Boot JDK found at $BOOT_JDK did not contain bin/javac; ignoring])
          AC_MSG_NOTICE([(This might be an JRE instead of an JDK)])
          BOOT_JDK_FOUND=no
        else 
          # Do we have an rt.jar? (On MacOSX it is called classes.jar)
          if test ! -f "$BOOT_JDK/jre/lib/rt.jar" && test ! -f "$BOOT_JDK/../Classes/classes.jar"; then
            AC_MSG_NOTICE([Potential Boot JDK found at $BOOT_JDK did not contain an rt.jar; ignoring])
            BOOT_JDK_FOUND=no
          else
            # Oh, this is looking good! We probably have found a proper JDK. Is it the correct version?
            BOOT_JDK_VERSION=`"$BOOT_JDK/bin/java" -version 2>&1 | head -n 1`

            # Extra M4 quote needed to protect [] in grep expression.
            [FOUND_VERSION_78=`echo $BOOT_JDK_VERSION | grep  '\"1\.[78]\.'`]
            if test "x$FOUND_VERSION_78" = x; then
              AC_MSG_NOTICE([Potential Boot JDK found at $BOOT_JDK is incorrect JDK version ($BOOT_JDK_VERSION); ignoring])
              AC_MSG_NOTICE([(Your Boot JDK must be version 7 or 8)])
              BOOT_JDK_FOUND=no
            else
              # We're done! :-)
              BOOT_JDK_FOUND=yes
              BASIC_FIXUP_PATH(BOOT_JDK)
              AC_MSG_CHECKING([for Boot JDK])
              AC_MSG_RESULT([$BOOT_JDK])
              AC_MSG_CHECKING([Boot JDK version])
              BOOT_JDK_VERSION=`"$BOOT_JDK/bin/java" -version 2>&1 | $TR '\n\r' '  '`
              AC_MSG_RESULT([$BOOT_JDK_VERSION])
            fi # end check jdk version
          fi # end check rt.jar
        fi # end check javac
      fi # end check java
    fi # end check boot jdk found
  fi
])

# Test: Is bootjdk explicitely set by command line arguments?
AC_DEFUN([BOOTJDK_CHECK_ARGUMENTS],
[
if test "x$with_boot_jdk" != x; then
    BOOT_JDK=$with_boot_jdk
    BOOT_JDK_FOUND=maybe
    AC_MSG_NOTICE([Found potential Boot JDK using configure arguments])
fi
])

# Test: Is bootjdk available from builddeps?
AC_DEFUN([BOOTJDK_CHECK_BUILDDEPS],
[
    BDEPS_CHECK_MODULE(BOOT_JDK, bootjdk, xxx, [BOOT_JDK_FOUND=maybe], [BOOT_JDK_FOUND=no])
])

# Test: Is $JAVA_HOME set?
AC_DEFUN([BOOTJDK_CHECK_JAVA_HOME],
[
    if test "x$JAVA_HOME" != x; then
        JAVA_HOME_PROCESSED="$JAVA_HOME"
        BASIC_FIXUP_PATH(JAVA_HOME_PROCESSED)
        if test ! -d "$JAVA_HOME_PROCESSED"; then
            AC_MSG_NOTICE([Your JAVA_HOME points to a non-existing directory!])
        else
          # Aha, the user has set a JAVA_HOME
          # let us use that as the Boot JDK.
          BOOT_JDK="$JAVA_HOME_PROCESSED"
          BOOT_JDK_FOUND=maybe
          AC_MSG_NOTICE([Found potential Boot JDK using JAVA_HOME])
        fi
    fi
])

# Test: Is there a java or javac in the PATH, which is a symlink to the JDK?
AC_DEFUN([BOOTJDK_CHECK_JAVA_IN_PATH_IS_SYMLINK],
[
    AC_PATH_PROG(JAVAC_CHECK, javac)
    AC_PATH_PROG(JAVA_CHECK, java)
    BINARY="$JAVAC_CHECK"
    if test "x$JAVAC_CHECK" = x; then
        BINARY="$JAVA_CHECK"
    fi
    if test "x$BINARY" != x; then
        # So there is a java(c) binary, it might be part of a JDK.
        # Lets find the JDK/JRE directory by following symbolic links.
        # Linux/GNU systems often have links from /usr/bin/java to 
        # /etc/alternatives/java to the real JDK binary.
        BASIC_REMOVE_SYMBOLIC_LINKS(BINARY)
        BOOT_JDK=`dirname "$BINARY"`
        BOOT_JDK=`cd "$BOOT_JDK/.."; pwd`
        if test -x "$BOOT_JDK/bin/javac" && test -x "$BOOT_JDK/bin/java"; then
            # Looks like we found ourselves an JDK
            BOOT_JDK_FOUND=maybe
            AC_MSG_NOTICE([Found potential Boot JDK using java(c) in PATH])
        fi
    fi
])

# Test: Is there a /usr/libexec/java_home? (Typically on MacOSX)
AC_DEFUN([BOOTJDK_CHECK_LIBEXEC_JAVA_HOME],
[
    if test -x /usr/libexec/java_home; then
        BOOT_JDK=`/usr/libexec/java_home`
        BOOT_JDK_FOUND=maybe
        AC_MSG_NOTICE([Found potential Boot JDK using /usr/libexec/java_home])
    fi
])

# Look for a jdk in the given path. If there are multiple, try to select the newest.
# If found, set BOOT_JDK and BOOT_JDK_FOUND.
# $1 = Path to directory containing jdk installations.
# $2 = String to append to the found JDK directory to get the proper JDK home
AC_DEFUN([BOOTJDK_FIND_BEST_JDK_IN_DIRECTORY],
[
  BOOT_JDK_PREFIX="$1"
  BOOT_JDK_SUFFIX="$2"
  ALL_JDKS_FOUND=`$LS "$BOOT_JDK_PREFIX" 2> /dev/null | $SORT -r`
  if test "x$ALL_JDKS_FOUND" != x; then
    for JDK_TO_TRY in $ALL_JDKS_FOUND ; do
      BOOTJDK_DO_CHECK([
        BOOT_JDK="${BOOT_JDK_PREFIX}/${JDK_TO_TRY}${BOOT_JDK_SUFFIX}"
        if test -d "$BOOT_JDK"; then
          BOOT_JDK_FOUND=maybe
          AC_MSG_NOTICE([Found potential Boot JDK using well-known locations (in $BOOT_JDK_PREFIX/$JDK_TO_TRY)])
        fi
      ])
    done
  fi
])

# Call BOOTJDK_FIND_BEST_JDK_IN_DIRECTORY, but use the given
# environmental variable as base for where to look.
# $1 Name of an environmal variable, assumed to point to the Program Files directory.
AC_DEFUN([BOOTJDK_FIND_BEST_JDK_IN_WINDOWS_VIRTUAL_DIRECTORY],
[
  if test "x[$]$1" != x; then
    VIRTUAL_DIR="[$]$1/Java"
    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(VIRTUAL_DIR)
    BOOTJDK_FIND_BEST_JDK_IN_DIRECTORY($VIRTUAL_DIR)
  fi
])

# Test: Is there a JDK installed in default, well-known locations?
AC_DEFUN([BOOTJDK_CHECK_WELL_KNOWN_LOCATIONS],
[
  if test "x$OPENJDK_TARGET_OS" = xwindows; then
    BOOTJDK_DO_CHECK([BOOTJDK_FIND_BEST_JDK_IN_WINDOWS_VIRTUAL_DIRECTORY([ProgramW6432])])
    BOOTJDK_DO_CHECK([BOOTJDK_FIND_BEST_JDK_IN_WINDOWS_VIRTUAL_DIRECTORY([PROGRAMW6432])])
    BOOTJDK_DO_CHECK([BOOTJDK_FIND_BEST_JDK_IN_WINDOWS_VIRTUAL_DIRECTORY([PROGRAMFILES])])
    BOOTJDK_DO_CHECK([BOOTJDK_FIND_BEST_JDK_IN_WINDOWS_VIRTUAL_DIRECTORY([ProgramFiles])])
    BOOTJDK_DO_CHECK([BOOTJDK_FIND_BEST_JDK_IN_DIRECTORY([/cygdrive/c/Program Files/Java])])
  elif test "x$OPENJDK_TARGET_OS" = xmacosx; then
    BOOTJDK_DO_CHECK([BOOTJDK_FIND_BEST_JDK_IN_DIRECTORY([/Library/Java/JavaVirtualMachines],[/Contents/Home])])
    BOOTJDK_DO_CHECK([BOOTJDK_FIND_BEST_JDK_IN_DIRECTORY([/System/Library/Java/JavaVirtualMachines],[/Contents/Home])])
  elif test "x$OPENJDK_TARGET_OS" = xlinux; then
    BOOTJDK_DO_CHECK([BOOTJDK_FIND_BEST_JDK_IN_DIRECTORY([/usr/lib/jvm])])
  fi
])

# Check that a command-line tool in the Boot JDK is correct
# $1 = name of variable to assign
# $2 = name of binary
AC_DEFUN([BOOTJDK_CHECK_TOOL_IN_BOOTJDK],
[
  AC_MSG_CHECKING([for $2 in Boot JDK])
  $1=$BOOT_JDK/bin/$2
  if test ! -x [$]$1; then
      AC_MSG_RESULT(not found)
      AC_MSG_NOTICE([Your Boot JDK seems broken. This might be fixed by explicitely setting --with-boot-jdk])
      AC_MSG_ERROR([Could not find $2 in the Boot JDK])
  fi
  AC_MSG_RESULT(ok)
])

###############################################################################
#
# We need a Boot JDK to bootstrap the build. 
#

AC_DEFUN_ONCE([BOOTJDK_SETUP_BOOT_JDK],
[
BOOT_JDK_FOUND=no
AC_ARG_WITH(boot-jdk, [AS_HELP_STRING([--with-boot-jdk],
    [path to Boot JDK (used to bootstrap build) @<:@probed@:>@])])

# We look for the Boot JDK through various means, going from more certain to
# more of a guess-work. After each test, BOOT_JDK_FOUND is set to "yes" if
# we detected something (if so, the path to the jdk is in BOOT_JDK). But we 
# must check if this is indeed valid; otherwise we'll continue looking.

# Test: Is bootjdk explicitely set by command line arguments?
BOOTJDK_DO_CHECK([BOOTJDK_CHECK_ARGUMENTS])
if test "x$with_boot_jdk" != x && test "x$BOOT_JDK_FOUND" = xno; then
  # Having specified an argument which is incorrect will produce an instant failure;
  # we should not go on looking
  AC_MSG_ERROR([The path given by --with-boot-jdk does not contain a valid Boot JDK])
fi

# Test: Is bootjdk available from builddeps?
BOOTJDK_DO_CHECK([BOOTJDK_CHECK_BUILDDEPS])

# Test: Is $JAVA_HOME set?
BOOTJDK_DO_CHECK([BOOTJDK_CHECK_JAVA_HOME])

# Test: Is there a /usr/libexec/java_home? (Typically on MacOSX)
BOOTJDK_DO_CHECK([BOOTJDK_CHECK_LIBEXEC_JAVA_HOME])

# Test: Is there a java or javac in the PATH, which is a symlink to the JDK?
BOOTJDK_DO_CHECK([BOOTJDK_CHECK_JAVA_IN_PATH_IS_SYMLINK])

# Test: Is there a JDK installed in default, well-known locations?
BOOTJDK_DO_CHECK([BOOTJDK_CHECK_WELL_KNOWN_LOCATIONS])

# If we haven't found anything yet, we've truly lost. Give up.
if test "x$BOOT_JDK_FOUND" = xno; then
  HELP_MSG_MISSING_DEPENDENCY([openjdk])
  AC_MSG_NOTICE([Could not find a valid Boot JDK. $HELP_MSG])
  AC_MSG_NOTICE([This might be fixed by explicitely setting --with-boot-jdk])
  AC_MSG_ERROR([Cannot continue])
fi

# Setup proper paths for what we found
BOOT_RTJAR="$BOOT_JDK/jre/lib/rt.jar"
if test ! -f "$BOOT_RTJAR"; then
    # On MacOSX it is called classes.jar
    BOOT_RTJAR="$BOOT_JDK/../Classes/classes.jar"
    if test -f "$BOOT_RTJAR"; then
      # Remove the .. 
      BOOT_RTJAR="`cd ${BOOT_RTJAR%/*} && pwd`/${BOOT_RTJAR##*/}"
    fi
fi
BOOT_TOOLSJAR="$BOOT_JDK/lib/tools.jar"
BOOT_JDK="$BOOT_JDK"
AC_SUBST(BOOT_RTJAR)
AC_SUBST(BOOT_TOOLSJAR)
AC_SUBST(BOOT_JDK)

# Setup tools from the Boot JDK.
BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JAVA,java)
BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JAVAC,javac)
BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JAVAH,javah)
BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JAVAP,javap)
BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JAR,jar)
BOOTJDK_CHECK_TOOL_IN_BOOTJDK(RMIC,rmic)
BOOTJDK_CHECK_TOOL_IN_BOOTJDK(NATIVE2ASCII,native2ascii)

# Finally, set some other options...

# When compiling code to be executed by the Boot JDK, force jdk7 compatibility.
BOOT_JDK_SOURCETARGET="-source 7 -target 7"
AC_SUBST(BOOT_JDK_SOURCETARGET)
AC_SUBST(JAVAC_FLAGS)
])

AC_DEFUN_ONCE([BOOTJDK_SETUP_BOOT_JDK_ARGUMENTS],
[
##############################################################################
#
# Specify options for anything that is run with the Boot JDK.
#
AC_ARG_WITH(boot-jdk-jvmargs, [AS_HELP_STRING([--with-boot-jdk-jvmargs],
	[specify JVM arguments to be passed to all invocations of the Boot JDK, overriding the default values,
     e.g --with-boot-jdk-jvmargs="-Xmx8G -enableassertions"])])

if test "x$with_boot_jdk_jvmargs" = x; then
    # Not all JVM:s accept the same arguments on the command line.
    # OpenJDK specific increase in thread stack for JDK build,
    # well more specifically, when running javac.
    if test "x$BUILD_NUM_BITS" = x32; then
       STACK_SIZE=768
    else
       # Running Javac on a JVM on a 64-bit machine, the stack takes more space
       # since 64-bit pointers are pushed on the stach. Apparently, we need
       # to increase the stack space when javacing the JDK....
       STACK_SIZE=1536
    fi

    # Minimum amount of heap memory.
    ADD_JVM_ARG_IF_OK([-Xms64M],boot_jdk_jvmargs,[$JAVA])
    if test "x$OPENJDK_TARGET_OS" = "xmacosx" || test "x$OPENJDK_TARGET_CPU" = "xppc64" ; then
        # Why does macosx need more heap? Its the huge JDK batch.
        ADD_JVM_ARG_IF_OK([-Xmx1600M],boot_jdk_jvmargs,[$JAVA])
    else
        ADD_JVM_ARG_IF_OK([-Xmx1100M],boot_jdk_jvmargs,[$JAVA])
    fi
    # When is adding -client something that speeds up the JVM?
    # ADD_JVM_ARG_IF_OK([-client],boot_jdk_jvmargs,[$JAVA])
    ADD_JVM_ARG_IF_OK([-XX:PermSize=32m],boot_jdk_jvmargs,[$JAVA])
    ADD_JVM_ARG_IF_OK([-XX:MaxPermSize=160m],boot_jdk_jvmargs,[$JAVA])
    ADD_JVM_ARG_IF_OK([-XX:ThreadStackSize=$STACK_SIZE],boot_jdk_jvmargs,[$JAVA])
    # Disable special log output when a debug build is used as Boot JDK...
    ADD_JVM_ARG_IF_OK([-XX:-PrintVMOptions -XX:-UnlockDiagnosticVMOptions -XX:-LogVMOutput],boot_jdk_jvmargs,[$JAVA])
fi

AC_SUBST(BOOT_JDK_JVMARGS, $boot_jdk_jvmargs)
])
