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

# Fixes paths on windows to be mixed mode short.
AC_DEFUN([BOOTJDK_WIN_FIX_PATH],
[
    if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
        AC_PATH_PROG(CYGPATH, cygpath)
        tmp="[$]$1"
        # Convert to C:/ mixed style path without spaces.
        tmp=`$CYGPATH -s -m "$tmp"`
        $1="$tmp"
    fi
])

AC_DEFUN([BOOTJDK_MISSING_ERROR],
[
    AC_MSG_NOTICE([This might be fixed by explicitely setting --with-boot-jdk])
    AC_MSG_ERROR([Cannot continue])
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
                    
if test "x$with_boot_jdk" != x; then
    BOOT_JDK=$with_boot_jdk
    BOOT_JDK_FOUND=yes
fi
if test "x$BOOT_JDK_FOUND" = xno; then
    BDEPS_CHECK_MODULE(BOOT_JDK, boot-jdk, xxx, [BOOT_JDK_FOUND=yes], [BOOT_JDK_FOUND=no])
fi

if test "x$BOOT_JDK_FOUND" = xno; then
    if test "x$JAVA_HOME" != x; then
        if test ! -d "$JAVA_HOME"; then
            AC_MSG_NOTICE([Your JAVA_HOME points to a non-existing directory!])
            BOOTJDK_MISSING_ERROR
        fi
        # Aha, the user has set a JAVA_HOME
        # let us use that as the Boot JDK.
        BOOT_JDK="$JAVA_HOME"
        BOOT_JDK_FOUND=yes
        # To be on the safe side, lets check that it is a JDK.
        if test -x "$BOOT_JDK/bin/javac" && test -x "$BOOT_JDK/bin/java"; then
            JAVAC="$BOOT_JDK/bin/javac"
            JAVA="$BOOT_JDK/bin/java"
            BOOT_JDK_FOUND=yes
        else
            AC_MSG_NOTICE([Your JAVA_HOME points to a JRE! The build needs a JDK! Please point JAVA_HOME to a JDK. JAVA_HOME=[$]JAVA_HOME])
            BOOTJDK_MISSING_ERROR
        fi            
    fi
fi

if test "x$BOOT_JDK_FOUND" = xno; then
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
	WHICHCMD_SPACESAFE(BINARY,[path to javac])
        REMOVE_SYMBOLIC_LINKS(BINARY)
        BOOT_JDK=`dirname $BINARY`
        BOOT_JDK=`cd $BOOT_JDK/..; pwd`
        if test -x $BOOT_JDK/bin/javac && test -x $BOOT_JDK/bin/java; then
            JAVAC=$BOOT_JDK/bin/javac
            JAVA=$BOOT_JDK/bin/java
            BOOT_JDK_FOUND=yes
        fi
    fi
fi

if test "x$BOOT_JDK_FOUND" = xno; then
    # Try the MacOSX way.
    if test -x /usr/libexec/java_home; then
        BOOT_JDK=`/usr/libexec/java_home`
        if test -x $BOOT_JDK/bin/javac && test -x $BOOT_JDK/bin/java; then
            JAVAC=$BOOT_JDK/bin/javac
            JAVA=$BOOT_JDK/bin/java
            BOOT_JDK_FOUND=yes
        fi
    fi
fi

if test "x$BOOT_JDK_FOUND" = xno; then
    AC_PATH_PROG(JAVA_CHECK, java)
    if test "x$JAVA_CHECK" != x; then
        # There is a java in the path. But apparently we have not found a javac 
        # in the path, since that would have been tested earlier.
        if test "x$OPENJDK_TARGET_OS" = xwindows; then
            # Now if this is a windows platform. The default installation of a JDK
            # actually puts the JRE in the path and keeps the JDK out of the path!
            # Go look in the default installation location.
            BOOT_JDK=/cygdrive/c/Program\ Files/Java/`ls /cygdrive/c/Program\ Files/Java | grep jdk | sort -r | head --lines 1`
            if test -d "$BOOT_JDK"; then
                BOOT_JDK_FOUND=yes
            fi
        fi
        if test "x$BOOT_JDK_FOUND" = xno; then
            HELP_MSG_MISSING_DEPENDENCY([openjdk])
            AC_MSG_NOTICE([Found a JRE, not not a JDK! Please remove the JRE from your path and put a JDK there instead. $HELP_MSG])
            BOOTJDK_MISSING_ERROR
        fi
    else
        HELP_MSG_MISSING_DEPENDENCY([openjdk])
        AC_MSG_NOTICE([Could not find a JDK. $HELP_MSG])
        BOOTJDK_MISSING_ERROR
    fi
fi

BOOTJDK_WIN_FIX_PATH(BOOT_JDK)

# Now see if we can find the rt.jar, or its nearest equivalent.
BOOT_RTJAR="$BOOT_JDK/jre/lib/rt.jar"
SPACESAFE(BOOT_RTJAR,[the path to the Boot JDK rt.jar (or nearest equivalent)])

BOOT_TOOLSJAR="$BOOT_JDK/lib/tools.jar"
SPACESAFE(BOOT_TOOLSJAR,[the path to the Boot JDK tools.jar (or nearest equivalent)])

if test ! -f $BOOT_RTJAR; then
    # On MacOSX it is called classes.jar
    BOOT_RTJAR=$BOOT_JDK/../Classes/classes.jar
    if test ! -f $BOOT_RTJAR; then
        AC_MSG_NOTICE([Cannot find the rt.jar or its equivalent!])
        AC_MSG_NOTICE([This typically means that configure failed to automatically find a suitable Boot JDK])
        BOOTJDK_MISSING_ERROR
    fi
    # Remove the .. 
    BOOT_RTJAR="`cd ${BOOT_RTJAR%/*} && pwd`/${BOOT_RTJAR##*/}"
    # The tools.jar is part of classes.jar
    BOOT_TOOLSJAR="$BOOT_RTJAR"
fi

AC_SUBST(BOOT_JDK)
AC_SUBST(BOOT_RTJAR)
AC_SUBST(BOOT_TOOLSJAR)
AC_MSG_CHECKING([for Boot JDK])
AC_MSG_RESULT([$BOOT_JDK])
AC_MSG_CHECKING([for Boot rt.jar])
AC_MSG_RESULT([$BOOT_RTJAR])
AC_MSG_CHECKING([for Boot tools.jar])
AC_MSG_RESULT([$BOOT_TOOLSJAR])

# Use the java tool from the Boot JDK.
AC_MSG_CHECKING([for java in Boot JDK])
JAVA=$BOOT_JDK/bin/java
if test ! -x $JAVA; then
    AC_MSG_NOTICE([Could not find a working java])
    BOOTJDK_MISSING_ERROR
fi
BOOT_JDK_VERSION=`$JAVA -version 2>&1 | head -n 1`
AC_MSG_RESULT([yes $BOOT_JDK_VERSION])
AC_SUBST(JAVA)

# Extra M4 quote needed to protect [] in grep expression.
[FOUND_VERSION_78=`echo $BOOT_JDK_VERSION | grep  '\"1\.[78]\.'`]
if test "x$FOUND_VERSION_78" = x; then
    HELP_MSG_MISSING_DEPENDENCY([openjdk])
    AC_MSG_NOTICE([Your boot-jdk must be version 7 or 8. $HELP_MSG])
    BOOTJDK_MISSING_ERROR
fi

# When compiling code to be executed by the Boot JDK, force jdk7 compatibility.
BOOT_JDK_SOURCETARGET="-source 7 -target 7"
AC_SUBST(BOOT_JDK_SOURCETARGET)

# Use the javac tool from the Boot JDK.
AC_MSG_CHECKING([for javac in Boot JDK])
JAVAC=$BOOT_JDK/bin/javac
if test ! -x $JAVAC; then
    AC_MSG_ERROR([Could not find a working javac])
fi
AC_MSG_RESULT(yes)
AC_SUBST(JAVAC)
AC_SUBST(JAVAC_FLAGS)

# Use the javah tool from the Boot JDK.
AC_MSG_CHECKING([for javah in Boot JDK])
JAVAH=$BOOT_JDK/bin/javah
if test ! -x $JAVAH; then
    AC_MSG_NOTICE([Could not find a working javah])
    BOOTJDK_MISSING_ERROR
fi
AC_MSG_RESULT(yes)
AC_SUBST(JAVAH)

# Use the jar tool from the Boot JDK.
AC_MSG_CHECKING([for jar in Boot JDK])
JAR=$BOOT_JDK/bin/jar
if test ! -x $JAR; then
    AC_MSG_NOTICE([Could not find a working jar])
    BOOTJDK_MISSING_ERROR
fi
AC_SUBST(JAR)
AC_MSG_RESULT(yes)

# Use the rmic tool from the Boot JDK.
AC_MSG_CHECKING([for rmic in Boot JDK])
RMIC=$BOOT_JDK/bin/rmic
if test ! -x $RMIC; then
    AC_MSG_NOTICE([Could not find a working rmic])
    BOOTJDK_MISSING_ERROR
fi
AC_SUBST(RMIC)
AC_MSG_RESULT(yes)

# Use the native2ascii tool from the Boot JDK.
AC_MSG_CHECKING([for native2ascii in Boot JDK])
NATIVE2ASCII=$BOOT_JDK/bin/native2ascii
if test ! -x $NATIVE2ASCII; then
    AC_MSG_NOTICE([Could not find a working native2ascii])
    BOOTJDK_MISSING_ERROR
fi
AC_MSG_RESULT(yes)
AC_SUBST(NATIVE2ASCII)
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
    if test "x$OPENJDK_TARGET_OS" = "xmacosx"; then
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
