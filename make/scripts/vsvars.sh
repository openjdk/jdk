#!/bin/sh

#
# Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# This file should be used to set the Visual Studio environment
#   variables normally set by the vcvars32.bat or vcvars64.bat file or
#   SetEnv.cmd for older SDKs.

# Use cygpath?
isCygwin="`uname -s | grep CYGWIN`"
if [ "${isCygwin}" != "" ] ; then
  cygpath="/usr/bin/cygpath"
  cygpath_short="${cygpath} -m -s"
  cygpath_windows="${cygpath} -w -s"
  cygpath_path="${cygpath} -p"
  pathsep=':'
else
  cygpath="dosname"
  cygpath_short="${cygpath} -s"
  cygpath_windows="${cygpath} -s"
  cygpath_path="echo"
  pathsep=';'
fi

########################################################################
# Error functions
msg() # message
{
  echo "$1" 1>&2
}
error() # message
{
  msg "ERROR: $1"
  exit 1
}
warning() # message
{
  msg "WARNING: $1"
}
envpath() # path
{
  if [ "${cygpath_short}" != "" -a -d "$1" ] ; then
    ${cygpath_short} "$1"
  else
    echo "$1"
  fi
}
########################################################################


# Defaults settings
debug="false"
verbose="false"
shellStyle="sh"
parentCsh="` ps -p ${PPID} 2> /dev/null | grep csh `"
if [ "${parentCsh}" != "" ] ; then
  shellStyle="csh"
fi

set -e

# Check environment first
if [ "${PROGRAMFILES}" != "" ] ; then
  progfiles=`envpath "${PROGRAMFILES}"`
elif [ "${ProgramFiles}" != "" ] ; then
  progfiles=`envpath "${ProgramFiles}"`
elif [ "${SYSTEMDRIVE}" != "" ] ; then
  progfiles=`envpath "${SYSTEMDRIVE}/Program Files"`
elif [ "${SystemDrive}" != "" ] ; then
  progfiles=`envpath "${SystemDrive}/Program Files"`
else
  error "No PROGRAMFILES or SYSTEMDRIVE defined in environment"
fi

# Arch data model
if [ "${PROCESSOR_IDENTIFIER}" != "" ] ; then
  arch=`echo "${PROCESSOR_IDENTIFIER}" | cut -d' ' -f1`
elif [ "${MACHTYPE}" != "" ] ; then
  if [ "`echo ${MACHTYPE} | grep 64`" != "" ] ; then
    # Assume this is X64, not IA64
    arch="x64"
  else
    arch="x86"
  fi
else
 arch="`uname -m`"
fi
if [ "${arch}" = "X86" -o \
     "${arch}" = "386" -o "${arch}" = "i386" -o \
     "${arch}" = "486" -o "${arch}" = "i486" -o \
     "${arch}" = "586" -o "${arch}" = "i586" -o \
     "${arch}" = "686" -o "${arch}" = "i686" -o \
     "${arch}" = "86" ] ; then
  arch="x86"
fi
if [ "${arch}" = "X64"     -o \
     "${arch}" = "8664"    -o "${arch}" = "i8664"   -o \
     "${arch}" = "amd64"   -o "${arch}" = "AMD64"   -o \
     "${arch}" = "EM64T"   -o "${arch}" = "emt64t"  -o \
     "${arch}" = "intel64" -o "${arch}" = "Intel64" -o \
     "${arch}" = "64" ] ; then
  arch="x64"
  binarch64="/amd64"
fi
if [ "${arch}" = "IA64" ] ; then
  arch="ia64"
  binarch64="/ia64"
fi
if [ "${arch}" != "x86" -a "${arch}" != "x64" -a "${arch}" != "ia64" ] ; then
 error "No PROCESSOR_IDENTIFIER or MACHTYPE environment variables and uname -m is not helping"
fi
if [ "${arch}" = "x86" ] ; then
  arch_data_model=32
  progfiles32="${progfiles}"
  progfiles64="${progfiles}"
else
  arch_data_model=64
  progfiles32="${progfiles}"
  if [ "${PROGRAMW6432}" != "" ] ; then
    progfiles64=`envpath "${PROGRAMW6432}"`
  else
    progfiles64=`envpath "C:/Program Files"`
  fi
fi

# VS2010 (VC10)
if [ "${VS100COMNTOOLS}" = "" ] ; then
  VS100COMNTOOLS="${progfiles32}/Microsoft Visual Studio 10.0/Common7/Tools/"
  export VS100COMNTOOLS
fi
vc10Bin32Dir=`envpath "${VS100COMNTOOLS}"`/../../VC/Bin
vc10Bin64Dir="${vc10Bin32Dir}${binarch64}"
vc10vars32Bat="vcvars32.bat"
vc10vars64Bat="vcvars64.bat"

# VS2008 (VC9)
if [ "${VS90COMNTOOLS}" = "" ] ; then
  VS90COMNTOOLS="${progfiles32}/Microsoft Visual Studio 9.0/Common7/Tools/"
  export VS90COMNTOOLS
fi
vc9Bin32Dir=`envpath "${VS90COMNTOOLS}"`/../../VC/Bin
vc9Bin64Dir="${vc9Bin32Dir}"
vc9vars32Bat="vcvars32.bat"
vc9vars64Bat="vcvars64.bat"

# VS2005 (VC8)
if [ "${VS80COMNTOOLS}" = "" ] ; then
  VS80COMNTOOLS="${progfiles32}/Microsoft Visual Studio 8.0/Common7/Tools/"
  export VS80COMNTOOLS
fi
vc8Bin32Dir=`envpath "${VS80COMNTOOLS}"`/../../VC/Bin
vc8Bin64Dir="${progfiles64}/Microsoft Platform SDK"
vc8vars32Bat="vcvars32.bat"
vc8vars64Bat="SetEnv.cmd /X64"

# VS2003 (VC7)
if [ "${VS71COMNTOOLS}" = "" ] ; then
  VS71COMNTOOLS="${progfiles32}/Microsoft Visual Studio .NET 2003/Common7/Tools/"
  export VS71COMNTOOLS
fi
vc7Bin32Dir=`envpath "${VS71COMNTOOLS}"`/../../VC7/Bin
vc7Bin64Dir="${progfiles64}/Microsoft Platform SDK"
vc7vars32Bat="vcvars32.bat"
vc7vars64Bat="SetEnv.cmd /X64"

# Force user to select
vcSelection=""

# Parse options
usage="Usage: $0 [-help] [-debug] [-v] [-c] [-s] [-p] [-v10] [-v9] [-v8] [-v7] [-32] [-64]"
while [ $# -gt 0 ] ; do
  if [ "$1" = "-help" ] ; then
    msg "${usage}"
    msg "  -help    Print out this help message"
    msg "  -debug   Print out extra env variables to help debug this script"
    msg "  -v       Verbose output warns about missing directories"
    msg "  -c       Print out csh style output"
    msg "  -s       Print out sh style output"
    msg "  -p       Print out properties style output"
    msg "  -v10     Use Visual Studio 10 VS2010"
    msg "  -v9      Use Visual Studio 9 VS2008"
    msg "  -v8      Use Visual Studio 8 VS2005"
    msg "  -v7      Use Visual Studio 7 VS2003"
    msg "  -32      Force 32bit"
    msg "  -64      Force 64bit"
    exit 0
  elif [ "$1" = "-debug" ] ; then
    debug="true"
    shift
  elif [ "$1" = "-v" ] ; then
    verbose="true"
    shift
  elif [ "$1" = "-c" ] ; then
    shellStyle="csh"
    shift
  elif [ "$1" = "-s" ] ; then
    shellStyle="sh"
    shift
  elif [ "$1" = "-p" ] ; then
    shellStyle="props"
    shift
  elif [ "$1" = "-v10" ] ; then
    vcBin32Dir="${vc10Bin32Dir}"
    vcBin64Dir="${vc10Bin64Dir}"
    vcvars32Bat="${vc10vars32Bat}"
    vcvars64Bat="${vc10vars64Bat}"
    vcSelection="10"
    shift
  elif [ "$1" = "-v9" ] ; then
    vcBin32Dir="${vc9Bin32Dir}"
    vcBin64Dir="${vc9Bin64Dir}"
    vcvars32Bat="${vc9vars32Bat}"
    vcvars64Bat="${vc9vars64Bat}"
    vcSelection="9"
    shift
  elif [ "$1" = "-v8" ] ; then
    vcBin32Dir="${vc8Bin32Dir}"
    vcBin64Dir="${vc8Bin64Dir}"
    vcvars32Bat="${vc8vars32Bat}"
    vcvars64Bat="${vc8vars64Bat}"
    vcSelection="8"
    shift
  elif [ "$1" = "-v7" ] ; then
    vcBin32Dir="${vc7Bin32Dir}"
    vcBin64Dir="${vc7Bin64Dir}"
    vcvars32Bat="${vc7vars32Bat}"
    vcvars64Bat="${vc7vars64Bat}"
    vcSelection="7"
    shift
  elif [ "$1" = "-32" ] ; then
    arch_data_model=32
    shift
  elif [ "$1" = "-64" ] ; then
    arch_data_model=64
    shift
  else
    msg "${usage}"
    error "Unknown option: $1"
  fi
done

# Need to pick
if [ "${vcSelection}" = "" ] ; then
  msg "${usage}"
  error "You must pick the version"
fi

# Which vcvars bat file to run
if [ "${arch_data_model}" = "32" ] ; then
  vcBinDir="${vcBin32Dir}"
  vcvarsBat="${vcvars32Bat}"
fi
if [ "${arch_data_model}" = "64" ] ; then
  vcBinDir="${vcBin64Dir}"
  vcvarsBat="${vcvars64Bat}"
fi

# Do not allow any error returns
set -e

# Different systems have different awk's
if [ -f /usr/bin/nawk ] ; then
  awk="nawk"
elif [ -f /usr/bin/gawk ] ; then
  awk="gawk"
else
  awk="awk"
fi

if [ "${verbose}" = "true" ] ; then
  echo "# Welcome to verbose mode"
  set -x
fi

if [ "${debug}" = "true" ] ; then
  echo "# Welcome to debug mode"
  set -x
fi

# Temp file area
tmp="/tmp/vsvars.$$"
rm -f -r ${tmp}
mkdir -p ${tmp}

# Check paths
checkPaths() # var path sep
{
  set -e
  sep="$3"
  checklist="${tmp}/checklist"
  printf "%s\n" "$2" | \
    sed -e 's@\\@/@g' | \
    sed -e 's@//@/@g' | \
    ${awk} -F"${sep}" '{for(i=1;i<=NF;i++){printf "%s\n",$i;}}'  \
      > ${checklist}
  cat ${checklist} | while read orig; do
    if [ "${orig}" != "" ] ; then
      if [ ! -d "${orig}" ] ; then
        warning "Directory in $1 does not exist: ${orig}"
      fi
    fi
  done
}

# Remove all duplicate entries
removeDeadDups() # string sep
{
  set -e
  sep="$2"
  pathlist="${tmp}/pathlist"
  printf "%s\n" "$1" | \
    sed -e 's@\\@/@g' | \
    sed -e 's@//@/@g' | \
    ${awk} -F"${sep}" '{for(i=1;i<=NF;i++){printf "%s\n",$i;}}'  \
      > ${pathlist}
  upaths="${tmp}/upaths"
  cat ${pathlist} | while read orig; do
    p="${orig}"
    if [ "${cygpath_short}" != "" ] ; then
      if [ "${p}" != "" ] ; then
        if [ -d "${p}" ] ; then
          short=`${cygpath_short} "${p}"`
          if [ "${short}" != "" -a -d "${short}" ] ; then
            p=`${cygpath} "${short}"`
          fi
          echo "${p}" >> ${upaths}
        fi
      fi
    fi
  done
  newpaths=""
  for i in  `cat ${upaths}` ; do
    # For some reason, \r characters can get into this
    i=`echo "${i}" | tr -d '\r' | sed -e 's@/$@@'`
    if [ "${newpaths}" = "" ] ; then
      newpaths="${i}"
    else
      newpaths="${newpaths}${sep}${i}"
    fi
  done
  printf "%s\n" "${newpaths}" | \
    ${awk} -F"${sep}" \
       '{a[$1];printf "%s",$1;for(i=2;i<=NF;i++){if(!($i in a)){a[$i];printf "%s%s",FS,$i;}};printf "\n";}'
}

# Create bat file to process Visual Studio vcvars*.bat files
createBat() # batfile bindir command
{
  bat="$1"
  bindir="$2"
  command="$3"
  stdout="${bat}.stdout"
  rm -f ${bat} ${stdout}
  echo "Output from: ${command}" > ${stdout}
  bdir=`envpath "${bindir}"`
  cat > ${bat} << EOF  
REM Pick the right vcvars bat file
REM Empty these out so we only get the additions we want
set INCLUDE=
set LIB=
set LIBPATH=
set MSVCDIR=
set MSSdk=
set Mstools=
set DevEnvDir=
set VCINSTALLDIR=
set VSINSTALLDIR=
set WindowsSdkDir=
REM Run the vcvars bat file, send all output to stderr
call `${cygpath_windows} ${bdir}`\\${command} > `${cygpath_windows} "${stdout}"`
REM Echo out env var settings
echo VS_VS71COMNTOOLS="%VS71COMNTOOLS%"
echo export VS_VS71COMNTOOLS
echo VS_VS80COMNTOOLS="%VS80COMNTOOLS%"
echo export VS_VS80COMNTOOLS
echo VS_VS90COMNTOOLS="%VS90COMNTOOLS%"
echo export VS_VS90COMNTOOLS
echo VS_VS100COMNTOOLS="%VS100COMNTOOLS%"
echo export VS_VS100COMNTOOLS
echo VS_VCINSTALLDIR="%VCINSTALLDIR%"
echo export VS_VCINSTALLDIR
echo VS_VSINSTALLDIR="%VSINSTALLDIR%"
echo export VS_VSINSTALLDIR
echo VS_DEVENVDIR="%DevEnvDir%"
echo export VS_DEVENVDIR
echo VS_MSVCDIR="%MSVCDIR%"
echo export VS_MSVCDIR
echo VS_MSSDK="%MSSdk%"
echo export VS_MSSDK
echo VS_MSTOOLS="%Mstools%"
echo export VS_MSTOOLS
echo VS_WINDOWSSDKDIR="%WindowsSdkDir%"
echo export VS_WINDOWSSDKDIR
echo VS_INCLUDE="%INCLUDE%"
echo export VS_INCLUDE
echo VS_LIB="%LIB%"
echo export VS_LIB
echo VS_LIBPATH="%LIBPATH%"
echo export VS_LIBPATH
echo VS_WPATH="%PATH%"
echo export VS_WPATH
EOF
  chmod a+x ${bat}
}

# Create env file
createEnv() # batfile envfile
{
  rm -f ${1}.stdout
  cmd.exe /Q /C `${cygpath_short} $1` | \
    sed -e 's@\\@/@g' | \
    sed -e 's@//@/@g' > $2
  if [ -f "${1}.stdout" ] ; then
    cat ${1}.stdout 1>&2
  fi
  chmod a+x $2
}

printEnv() # name pname vsname val
{
  name="$1"
  pname="$2"
  vsname="$3"
  val="$4"
  if [ "${val}" != "" ] ; then
    if [ "${shellStyle}" = "csh" ] ; then
      if [ "${debug}" = "true" ] ; then
        echo "setenv ${vsname} \"${val}\";"
      fi
      echo "setenv ${name} \"${val}\";"
    elif [ "${shellStyle}" = "sh" ] ; then
      if [ "${debug}" = "true" ] ; then
        echo "${vsname}=\"${val}\";"
        echo "export ${vsname};"
      fi
      echo "${name}=\"${val}\";"
      echo "export ${name};"
    elif [ "${shellStyle}" = "props" ] ; then
      echo "vs.${pname}=${val}"
    fi
  fi
}

#############################################################################

# Get Visual Studio settings
if [ "${cygpath}" != "" ] ; then

  # Create bat file to run
  batfile="${tmp}/vs-to-env.bat"
  if [ ! -d "${vcBinDir}" ] ; then
    error "Does not exist: ${vcBinDir}"
  elif [ "${vcvarsBat}" = "" ] ; then
    error "No vcvars script: ${vcvarsBat}"
  else
    createBat "${batfile}" "${vcBinDir}" "${vcvarsBat}"
  fi

  # Run bat file to create environment variable settings
  envfile="${tmp}/env.sh"
  createEnv "${batfile}" "${envfile}"

  # Read in the VS_* settings
  . ${envfile}

  # Derive unix style path, save old, and define new (remove dups)
  VS_UPATH=`${cygpath_path} "${VS_WPATH}"`
  export VS_UPATH
  VS_OPATH=`printf "%s" "${PATH}" | sed -e 's@\\\\@/@g'`
  export VS_OPATH
  VS_PATH=`removeDeadDups "${VS_UPATH}${pathsep}${VS_OPATH}" "${pathsep}"`
  export VS_PATH

fi

# Adjustments due to differences in vcvars*bat files
if [ "${VS_MSVCDIR}" = "" ] ; then
  VS_MSVCDIR="${VS_VCINSTALLDIR}"
fi
if [ "${VS_DEVENVDIR}" = "" ] ; then
  VS_DEVENVDIR="${VS_VSINSTALLDIR}"
fi

# Print env settings
#        env           vs.prop       vs env           value
#        -------       -------       ----------       -----
printEnv INCLUDE       include       VS_INCLUDE       "${VS_INCLUDE}"
printEnv LIB           lib           VS_LIB           "${VS_LIB}"
printEnv LIBPATH       libpath       VS_LIBPATH       "${VS_LIBPATH}"
if [ "${debug}" = "true" ] ; then
  printEnv UPATH         upath         VS_UPATH         "${VS_UPATH}"
  printEnv WPATH         wpath         VS_WPATH         "${VS_WPATH}"
  printEnv OPATH         opath         VS_OPATH         "${VS_OPATH}"
fi
printEnv PATH          path          VS_PATH          "${VS_PATH}"
printEnv VCINSTALLDIR  vcinstalldir  VS_VCINSTALLDIR  "${VS_VCINSTALLDIR}"
printEnv VSINSTALLDIR  vsinstalldir  VS_VSINSTALLDIR  "${VS_VSINSTALLDIR}"
printEnv MSVCDIR       msvcdir       VS_MSVCDIR       "${VS_MSVCDIR}"
printEnv MSSDK         mssdk         VS_MSSDK         "${VS_MSSDK}"
printEnv MSTOOLS       mstools       VS_MSTOOLS       "${VS_MSTOOLS}"
printEnv DEVENVDIR     devenvdir     VS_DEVENVDIR     "${VS_DEVENVDIR}"
printEnv WINDOWSSDKDIR windowssdkdir VS_WINDOWSSDKDIR "${VS_WINDOWSSDKDIR}"
if [ "${vcSelection}" = "10" ] ; then
  printEnv VS100COMNTOOLS vs100comntools VS_VS100COMNTOOLS "${VS_VS100COMNTOOLS}"
elif [ "${vcSelection}" = "9" ] ; then
  printEnv VS90COMNTOOLS vs90comntools VS_VS90COMNTOOLS "${VS_VS90COMNTOOLS}"
elif [ "${vcSelection}" = "7" ] ; then
  printEnv VS71COMNTOOLS vs71comntools VS_VS71COMNTOOLS "${VS_VS71COMNTOOLS}"
elif [ "${vcSelection}" = "8" ] ; then
  printEnv VS80COMNTOOLS vs80comntools VS_VS80COMNTOOLS "${VS_VS80COMNTOOLS}"
fi

# Check final settings
if [ "${verbose}" = "true" ] ; then
  checkPaths "Windows PATH" "${VS_WPATH}" ";"
  checkPaths LIB "${VS_LIB}" ";"
  checkPaths INCLUDE "${VS_INCLUDE}" ";"
  checkPaths PATH "${VS_PATH}" "${pathsep}"
fi

# Remove all temp files
rm -f -r ${tmp}

exit 0

