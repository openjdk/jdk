#!/bin/sh

#
# Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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


#############################################################################
#
# Generic build profile.sh for all platforms, works in bash, sh, and ksh.
#
# Copy this file to your own area, and edit it to suit your needs.
#
# Ideally you either won't need to set the ALT_* variables because the
#   build system will find what it needs through system provided paths
#   or environment variables, or you have installed the component in the
#   recommended default path.
#
# If you find yourself forced to set an ALT_* environment variable and
#   suspect we could have figured it out automatically, please let us know.
#
# Most ALT_* directory defaults are based on being in the parent directory in
#    ALT_SLASH_JAVA, so it's possible to create for example a "C:/jdk6"
#    directory, assign that to ALT_SLASH_JAVA, and place all the components
#    in that directory. This could also minimize the ALT_* environment
#    variables you need to set.
#
########
#
# Assumes basic unix utilities are in the PATH already (uname, hostname, etc.).
#
# On Windows, assumes PROCESSOR_IDENTIFIER, VS71COMNTOOLS,
#   SYSTEMROOT (or SystemRoot), COMPUTERNAME (or hostname works), and
#   USERNAME is defined in the environment.
#   This profile does not rely on using vcvars32.bat and 64bit Setup.bat.
#   Uses CYGWIN cygpath to make sure paths are space-free.
#
# The JDK Makefiles may change in the future, making some of these
#   settings unnecessary or redundant.
#
# This is a working example, but may or may not work on all systems.
#
#############################################################################
#
# WARNING: This file will clobber the value of some environment variables.
#
# Sets up these environment variables for doing JDK builds:
#    USERNAME
#    COMPUTERNAME
#    PATH
#    Windows Only:
#      LIB
#      INCLUDE
#      PS1
#      SHELL
#
# Attempts to set these variables for the JDK builds:           
#    ALT_COMPILER_PATH
#    ALT_BOOTDIR
#    ALT_BINARY_PLUGS_PATH
#    ALT_CLOSED_JDK_IMPORT_PATH
#    Windows Only:
#      ALT_UNIXCOMMAND_PATH
#      ALT_MSDEVTOOLS_PATH
#      ALT_DXSDK_PATH
#      ALT_MSVCRT_DLL_PATH
#      ALT_MSVCR71_DLL_PATH
#
#############################################################################
#
# Keep in mind that at this point, we are running in some kind of shell
#   (sh, ksh, or bash). We don't know if it's solaris, linux, or windows 
#   CYGWIN. We need to figure that out.

# Find user name
if [ "${USERNAME}" = "" ] ; then
    USERNAME="${LOGNAME}"
fi
if [ "${USERNAME}" = "" ] ; then
    USERNAME="${USER}"
fi
export USERNAME

# Find machine name
if [ "${COMPUTERNAME}" = "" ] ; then
    COMPUTERNAME="$(hostname)"
fi
export COMPUTERNAME

# Boot jdk
bootjdk=jdk1.6.0
importjdk=jdk1.7.0

# Uses 'uname -s', but only expect SunOS or Linux, assume Windows otherwise.
osname=$(uname -s)
if [ "${osname}" = SunOS ] ; then
  
  # System place where JDK installed images are stored?
  jdk_instances=/usr/jdk/instances

  # Get the Sun Studio compilers (and latest patches for them too)
  if [ "${ALT_COMPILER_PATH}" = "" ] ; then
    ALT_COMPILER_PATH=/opt/SUNWspro/bin
    export ALT_COMPILER_PATH
  fi
  if [ ! -d ${ALT_COMPILER_PATH} ] ; then
    echo "WARNING: Cannot access ALT_COMPILER_PATH=${ALT_COMPILER_PATH}"
  fi
  
  # Place compiler path early in PATH to avoid 'cc' conflicts.
  path4sdk=${ALT_COMPILER_PATH}:/usr/ccs/bin:/usr/ccs/lib:/usr/bin:/bin:/usr/sfw/bin

  # Make sure these are unset
  unset JAVA_HOME
  unset LD_LIBRARY_PATH

  # Build in C locale
  LANG=C
  export LANG
  LC_ALL=C
  export LC_ALL

  umask 002

elif [ "${osname}" = Linux ] ; then
  
  # System place where JDK installed images are stored?
  jdk_instances=/opt/java
    
  # Use compilers from /usr/bin
  path4sdk=/usr/bin:/bin:/usr/sbin:/sbin

  # Make sure these are unset
  unset JAVA_HOME
  unset LD_LIBRARY_PATH

  # Build in C locale
  LANG=C
  export LANG
  LC_ALL=C
  export LC_ALL

  umask 002

else

  # System place where JDK installed images are stored?
  jdk_instances="C:"

  # Windows: Differs on CYGWIN and the compiler available.
  #   Also, blanks in pathnames gives make headaches, so anything placed
  #   in any ALT_* variable should be the short windows DOS names.
   
  # Check CYGWIN (should have already been done)
  #   Assumption here is that you are in a shell window via cygwin.
  proc_arch=`echo "$(PROCESSOR_IDENTIFIER)" | expand | cut -d' ' -f1 | sed -e 's@x86@X86@g' -e 's@Intel64@X64@g' -e 's@em64t@X64@g' -e 's@EM64T@X64@g' -e 's@amd64@X64@g' -e 's@AMD64@X64@g' -e 's@ia64@IA64@g'`
  if [ "${proc_arch}" = "X64" ] ; then
    windows_arch=amd64
  else
    windows_arch=i586
  fi
  # We need to check if we are running a CYGWIN shell
  if [ "$(uname -a | fgrep Cygwin)" != "" -a -f /bin/cygpath ] ; then
    # For CYGWIN, uname will have "Cygwin" in it, and /bin/cygpath should exist
    # Utility to convert to short pathnames without spaces
    cygpath="/usr/bin/cygpath -a -m -s"
    # Most unix utilities are in the /usr/bin
    unixcommand_path="/usr/bin"
    # Make the prompt tell you CYGWIN
    export PS1="CYGWIN:${COMPUTERNAME}:${USERNAME}[\!] "
  else
    echo "ERROR: Cannot find CYGWIN on this machine"
    exit 1
  fi
  if [ "${ALT_UNIXCOMMAND_PATH}" != "" ] ; then
    unixcommand_path=${ALT_UNIXCOMMAND_PATH}
  fi
    
  # Default shell
  export SHELL="${unixcommand_path}/sh"

  # Setup path system (verify this is right)
  if [ "${SystemRoot}" != "" ] ; then
    sys_root=$(${cygpath} "${SystemRoot}")
  elif [ "${SYSTEMROOT}" != "" ] ; then
    sys_root=$(${cygpath} "${SYSTEMROOT}")
  else
    sys_root=$(${cygpath} "C:/WINNT")
  fi
  path4sdk="${unixcommand_path};${sys_root}/system32;${sys_root};${sys_root}/System32/Wbem"
  if [ ! -d "${sys_root}" ] ; then
    echo "WARNING: No system root found at: ${sys_root}"
  fi

  # Compiler setup (nasty part)
  #   NOTE: You can use vcvars32.bat to set PATH, LIB, and INCLUDE.
  #   NOTE: CYGWIN has a link.exe too, make sure the compilers are first
  if [ "${windows_arch}" = i586 ] ; then
    # 32bit Windows compiler settings
    # VisualStudio .NET 2003 VC++ 7.1 (VS71COMNTOOLS should be defined)
    vs_root=$(${cygpath} "${VS71COMNTOOLS}/../..")
    # Fill in PATH, LIB, and INCLUDE (unset all others to make sure)
    msdev_root="${vs_root}/Common7/Tools"
    msdevtools_path="${msdev_root}/bin"
    vc7_root="${vs_root}/Vc7"
    compiler_path="${vc7_root}/bin"
    platform_sdk="${vc7_root}/PlatformSDK"
        
    # LIB and INCLUDE must use ; as a separator
    include4sdk="${vc7_root}/atlmfc/include"
    include4sdk="${include4sdk};${vc7_root}/include"
    include4sdk="${include4sdk};${platform_sdk}/include/prerelease"
    include4sdk="${include4sdk};${platform_sdk}/include"
    include4sdk="${include4sdk};${vs_root}/SDK/v1.1/include"
    lib4sdk="${lib4sdk};${vc7_root}/lib"
    lib4sdk="${lib4sdk};${platform_sdk}/lib/prerelease"
    lib4sdk="${lib4sdk};${platform_sdk}/lib"
    lib4sdk="${lib4sdk};${vs_root}/SDK/v1.1/lib"
    # Search path and DLL locating path
    #   WARNING: CYGWIN has a link.exe too, make sure compilers are first
    path4sdk="${vs_root}/Common7/Tools/bin;${path4sdk}"
    path4sdk="${vs_root}/SDK/v1.1/bin;${path4sdk}"
    path4sdk="${vs_root}/Common7/Tools;${path4sdk}"
    path4sdk="${vs_root}/Common7/Tools/bin/prerelease;${path4sdk}"
    path4sdk="${vs_root}/Common7/IDE;${path4sdk}"
    path4sdk="${compiler_path};${path4sdk}"
  elif [ "${windows_arch}" = amd64 ] ; then
    # AMD64 64bit Windows compiler settings
    if [ "${ALT_DEPLOY_MSSDK}" != "" ] ; then
      platform_sdk=${ALT_DEPLOY_MSSDK}
    else
      platform_sdk=$(${cygpath} "C:/Program Files/Microsoft Platform SDK/")
    fi
    if [ "${ALT_COMPILER_PATH}" != "" ] ; then
      compiler_path=${ALT_COMPILER_PATH}
      if [ "${ALT_DEPLOY_MSSDK}" = "" ] ; then
        platform_sdk=${ALT_COMPILER_PATH}/../../../..
      fi
    else
      compiler_path="${platform_sdk}/Bin/win64/x86/AMD64"
    fi
    if [ "${ALT_MSDEVTOOLS_PATH}" != "" ] ; then
      msdevtools_path=${ALT_MSDEVTOOLS_PATH}
    else
      msdevtools_path="${platform_sdk}/Bin/win64/x86/AMD64"
    fi
    msdevtools_path="${compiler_path}"
    # LIB and INCLUDE must use ; as a separator
    include4sdk="${platform_sdk}/Include"
    include4sdk="${include4sdk};${platform_sdk}/Include/crt/sys"
    include4sdk="${include4sdk};${platform_sdk}/Include/mfc"
    include4sdk="${include4sdk};${platform_sdk}/Include/atl"
    include4sdk="${include4sdk};${platform_sdk}/Include/crt"
    lib4sdk="${platform_sdk}/Lib/AMD64"
    lib4sdk="${lib4sdk};${platform_sdk}/Lib/AMD64/atlmfc"
    # Search path and DLL locating path
    #   WARNING: CYGWIN has a link.exe too, make sure compilers are first
    path4sdk="${platform_sdk}/bin;${path4sdk}"
    path4sdk="${compiler_path};${path4sdk}"
  fi
  # Export LIB and INCLUDE
  unset lib
  unset Lib
  LIB="${lib4sdk}"
  export LIB
  unset include
  unset Include
  INCLUDE="${include4sdk}"
  export INCLUDE
    
  # Turn all \\ into /, remove duplicates and trailing /
  slash_path="$(echo ${path4sdk} | sed -e 's@\\\\@/@g' -e 's@//@/@g' -e 's@/$@@' -e 's@/;@;@g')"
  path4sdk="${slash_path}"
   
  # Convert path4sdk to cygwin style
  path4sdk="$(/usr/bin/cygpath -p ${path4sdk})"

fi

# Get the previous JDK to be used to bootstrap the build
if [ "${ALT_BOOTDIR}" = "" ] ; then
  ALT_BOOTDIR=${jdk_instances}/${bootjdk}
  export ALT_BOOTDIR
fi
if [ ! -d ${ALT_BOOTDIR} ] ; then
  echo "WARNING: Cannot access ALT_BOOTDIR=${ALT_BOOTDIR}"
fi

# Get the import JDK to be used to get hotspot VM if not built
if [ "${ALT_JDK_IMPORT_PATH}" = "" -a -d ${jdk_instances}/${importjdk} ] ; then
  ALT_JDK_IMPORT_PATH=${jdk_instances}/${importjdk}
  export ALT_JDK_IMPORT_PATH
fi

# Get the latest JDK binary plugs or build to import pre-built binaries
if [ "${ALT_BINARY_PLUGS_PATH}" = "" ] ; then
  binplugs=${jdk_instances}/openjdk-binary-plugs
  jdkplugs=${jdk_instances}/${importjdk}
  if [ -d ${binplugs} ] ; then
    ALT_BINARY_PLUGS_PATH=${binplugs}
    export ALT_BINARY_PLUGS_PATH
  elif [  "${ALT_CLOSED_JDK_IMPORT_PATH}" = "" -a -d ${jdkplugs} ] ; then
    ALT_CLOSED_JDK_IMPORT_PATH=${jdkplugs}
    export ALT_CLOSED_JDK_IMPORT_PATH
  fi
  if [ "${ALT_BINARY_PLUGS_PATH}" = "" ] ; then
    echo "WARNING: Missing ALT_BINARY_PLUGS_PATH: ${binplugs}"
  fi
fi
if [ "${ALT_BINARY_PLUGS_PATH}" != "" -a ! -d "${ALT_BINARY_PLUGS_PATH}" ] ; then
  echo "WARNING: Cannot access ALT_BINARY_PLUGS_PATH=${ALT_BINARY_PLUGS_PATH}"
fi
if [ "${ALT_CLOSED_JDK_IMPORT_PATH}" != "" -a ! -d "${ALT_CLOSED_JDK_IMPORT_PATH}" ] ; then
  echo "WARNING: Cannot access ALT_CLOSED_JDK_IMPORT_PATH=${ALT_CLOSED_JDK_IMPORT_PATH}"
fi

# Export PATH setting
PATH="${path4sdk}"
export PATH

