#
# Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

########################################################################
# This file handles detection of the Boot JDK. The Boot JDK detection
# process has been developed as a response to solve a complex real-world
# problem. Initially, it was simple, but it has grown as platform after
# platform, idiosyncracy after idiosyncracy has been supported.
#
# The basic idea is this:
# 1) You need an acceptable *) JDK to use as a Boot JDK
# 2) There are several ways to locate a JDK, that are mostly platform
#    dependent **)
# 3) You can have multiple JDKs installed
# 4) If possible, configure should try to dig out an acceptable JDK
#    automatically, without having to resort to command-line options
#
# *)  acceptable means e.g. JDK7 for building JDK8, a complete JDK (with
#     javac) and not a JRE, etc.
#
# **) On Windows we typically use a well-known path.
#     On MacOSX we typically use the tool java_home.
#     On Linux we typically find javac in the $PATH, and then follow a
#     chain of symlinks that often ends up in a real JDK.
#
# This leads to the code where we check in different ways to locate a
# JDK, and if one is found, check if it is acceptable. If not, we print
# our reasons for rejecting it (useful when debugging non-working
# configure situations) and continue checking the next one.
########################################################################

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
          # Oh, this is looking good! We probably have found a proper JDK. Is it the correct version?
          BOOT_JDK_VERSION=`"$BOOT_JDK/bin/java" -version 2>&1 | head -n 1`

          # Extra M4 quote needed to protect [] in grep expression.
          [FOUND_CORRECT_VERSION=`$ECHO $BOOT_JDK_VERSION | $EGREP '\"9([\.+-].*)?\"|(1\.[89]\.)'`]
          if test "x$FOUND_CORRECT_VERSION" = x; then
            AC_MSG_NOTICE([Potential Boot JDK found at $BOOT_JDK is incorrect JDK version ($BOOT_JDK_VERSION); ignoring])
            AC_MSG_NOTICE([(Your Boot JDK must be version 8 or 9)])
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
# $1: Argument to the java_home binary (optional)
AC_DEFUN([BOOTJDK_CHECK_LIBEXEC_JAVA_HOME],
[
  if test -x /usr/libexec/java_home; then
    BOOT_JDK=`/usr/libexec/java_home $1`
    BOOT_JDK_FOUND=maybe
    AC_MSG_NOTICE([Found potential Boot JDK using /usr/libexec/java_home $1])
  fi
])

# Test: On MacOS X, can we find a boot jdk using /usr/libexec/java_home?
AC_DEFUN([BOOTJDK_CHECK_MACOSX_JAVA_LOCATOR],
[
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    # First check at user selected default
    BOOTJDK_DO_CHECK([BOOTJDK_CHECK_LIBEXEC_JAVA_HOME()])
    # If that did not work out (e.g. too old), try explicit versions instead
    BOOTJDK_DO_CHECK([BOOTJDK_CHECK_LIBEXEC_JAVA_HOME([-v 1.9])])
    BOOTJDK_DO_CHECK([BOOTJDK_CHECK_LIBEXEC_JAVA_HOME([-v 1.8])])
    BOOTJDK_DO_CHECK([BOOTJDK_CHECK_LIBEXEC_JAVA_HOME([-v 1.7])])
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
  # Use user overridden value if available, otherwise locate tool in the Boot JDK.
  BASIC_SETUP_TOOL($1,
    [
      AC_MSG_CHECKING([for $2 in Boot JDK])
      $1=$BOOT_JDK/bin/$2
      if test ! -x [$]$1; then
        AC_MSG_RESULT(not found)
        AC_MSG_NOTICE([Your Boot JDK seems broken. This might be fixed by explicitely setting --with-boot-jdk])
        AC_MSG_ERROR([Could not find $2 in the Boot JDK])
      fi
      AC_MSG_RESULT(ok)
      AC_SUBST($1)
    ])
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

  # Test: On MacOS X, can we find a boot jdk using /usr/libexec/java_home?
  BOOTJDK_DO_CHECK([BOOTJDK_CHECK_MACOSX_JAVA_LOCATOR])

  # Test: Is $JAVA_HOME set?
  BOOTJDK_DO_CHECK([BOOTJDK_CHECK_JAVA_HOME])

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

  AC_SUBST(BOOT_JDK)

  # Setup tools from the Boot JDK.
  BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JAVA, java)
  BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JAVAC, javac)
  BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JAVAH, javah)
  BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JAR, jar)
  BOOTJDK_CHECK_TOOL_IN_BOOTJDK(JARSIGNER, jarsigner)

  # Finally, set some other options...

  # When compiling code to be executed by the Boot JDK, force jdk8 compatibility.
  BOOT_JDK_SOURCETARGET="-source 8 -target 8"
  AC_SUBST(BOOT_JDK_SOURCETARGET)
  AC_SUBST(JAVAC_FLAGS)
])

AC_DEFUN_ONCE([BOOTJDK_SETUP_BOOT_JDK_ARGUMENTS],
[
  ##############################################################################
  #
  # Specify jvm options for anything that is run with the Boot JDK.
  # Not all JVM:s accept the same arguments on the command line.
  #
  AC_ARG_WITH(boot-jdk-jvmargs, [AS_HELP_STRING([--with-boot-jdk-jvmargs],
  [specify JVM arguments to be passed to all java invocations of boot JDK, overriding the default values,
  e.g --with-boot-jdk-jvmargs="-Xmx8G -enableassertions"])])

  AC_MSG_CHECKING([flags for boot jdk java command] )

  # Disable special log output when a debug build is used as Boot JDK...
  ADD_JVM_ARG_IF_OK([-XX:-PrintVMOptions -XX:-UnlockDiagnosticVMOptions -XX:-LogVMOutput],boot_jdk_jvmargs,[$JAVA])

  # Apply user provided options.
  ADD_JVM_ARG_IF_OK([$with_boot_jdk_jvmargs],boot_jdk_jvmargs,[$JAVA])

  AC_MSG_RESULT([$boot_jdk_jvmargs])

  # For now, general JAVA_FLAGS are the same as the boot jdk jvmargs
  JAVA_FLAGS=$boot_jdk_jvmargs
  AC_SUBST(JAVA_FLAGS)


  AC_MSG_CHECKING([flags for boot jdk java command for big workloads])

  # Starting amount of heap memory.
  ADD_JVM_ARG_IF_OK([-Xms64M],boot_jdk_jvmargs_big,[$JAVA])

  # Maximum amount of heap memory.
  # Maximum stack size.
  JVM_MAX_HEAP=`expr $MEMORY_SIZE / 2`
  if test "x$BUILD_NUM_BITS" = x32; then
    if test "$JVM_MAX_HEAP" -gt "1100"; then
      JVM_MAX_HEAP=1100
    elif test "$JVM_MAX_HEAP" -lt "512"; then
      JVM_MAX_HEAP=512
    fi
    STACK_SIZE=768
  else
    # Running Javac on a JVM on a 64-bit machine, takes more space since 64-bit
    # pointers are used. Apparently, we need to increase the heap and stack
    # space for the jvm. More specifically, when running javac to build huge
    # jdk batch
    if test "$JVM_MAX_HEAP" -gt "1600"; then
      JVM_MAX_HEAP=1600
    elif test "$JVM_MAX_HEAP" -lt "512"; then
      JVM_MAX_HEAP=512
    fi
    STACK_SIZE=1536
  fi
  ADD_JVM_ARG_IF_OK([-Xmx${JVM_MAX_HEAP}M],boot_jdk_jvmargs_big,[$JAVA])
  ADD_JVM_ARG_IF_OK([-XX:ThreadStackSize=$STACK_SIZE],boot_jdk_jvmargs_big,[$JAVA])

  AC_MSG_RESULT([$boot_jdk_jvmargs_big])

  JAVA_FLAGS_BIG=$boot_jdk_jvmargs_big
  AC_SUBST(JAVA_FLAGS_BIG)


  AC_MSG_CHECKING([flags for boot jdk java command for small workloads])

  # Use serial gc for small short lived tools if possible
  ADD_JVM_ARG_IF_OK([-XX:+UseSerialGC],boot_jdk_jvmargs_small,[$JAVA])
  ADD_JVM_ARG_IF_OK([-Xms32M],boot_jdk_jvmargs_small,[$JAVA])
  ADD_JVM_ARG_IF_OK([-Xmx512M],boot_jdk_jvmargs_small,[$JAVA])

  AC_MSG_RESULT([$boot_jdk_jvmargs_small])

  JAVA_FLAGS_SMALL=$boot_jdk_jvmargs_small
  AC_SUBST(JAVA_FLAGS_SMALL)

  JAVA_TOOL_FLAGS_SMALL=""
  for f in $JAVA_FLAGS_SMALL; do
    JAVA_TOOL_FLAGS_SMALL="$JAVA_TOOL_FLAGS_SMALL -J$f"
  done
  AC_SUBST(JAVA_TOOL_FLAGS_SMALL)
])
