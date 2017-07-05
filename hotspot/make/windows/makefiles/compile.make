#
# Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# Generic compiler settings
CPP=cl.exe

# CPP Flags: (these vary slightly from VC6->VS2003->VS2005 compilers)
#   /nologo   Supress copyright message at every cl.exe startup
#   /W3       Warning level 3
#   /Zi       Include debugging information
#   /WX       Treat any warning error as a fatal error
#   /MD       Use dynamic multi-threaded runtime (msvcrt.dll or msvc*NN.dll)
#   /MTd      Use static multi-threaded runtime debug versions
#   /O1       Optimize for size (/Os), skips /Oi
#   /O2       Optimize for speed (/Ot), adds /Oi to /O1
#   /Ox       Old "all optimizations flag" for VC6 (in /O1)
#   /Oy       Use frame pointer register as GP reg (in /Ox and /O1)
#   /GF       Merge string constants and put in read-only memory (in /O1)
#   /Gy       Func level link (in /O1, allows for link-time func ordering)
#   /Gs       Inserts stack probes (in /O1)
#   /GS       Inserts security stack checks in some functions (VS2005 default)
#   /Oi       Use intrinsics (in /O2)
#   /Od       Disable all optimizations
#
# NOTE: Normally following any of the above with a '-' will turn off that flag
#
# 6655385: For VS2003/2005 we now specify /Oy- (disable frame pointer
# omission.)  This has little to no effect on performance while vastly
# improving the quality of crash log stack traces involving jvm.dll.

# These are always used in all compiles
CPP_FLAGS=/nologo /W3 /WX

# Let's add debug information always too.
CPP_FLAGS=$(CPP_FLAGS) /Zi

# Based on BUILDARCH we add some flags and select the default compiler name
!if "$(BUILDARCH)" == "ia64"
MACHINE=IA64
DEFAULT_COMPILER_NAME=VS2003
CPP_FLAGS=$(CPP_FLAGS) /D "CC_INTERP" /D "_LP64" /D "IA64"
!endif

!if "$(BUILDARCH)" == "amd64"
MACHINE=AMD64
DEFAULT_COMPILER_NAME=VS2005
CPP_FLAGS=$(CPP_FLAGS) /D "_LP64" /D "AMD64"
LP64=1
!endif

!if "$(BUILDARCH)" == "i486"
MACHINE=I386
DEFAULT_COMPILER_NAME=VS2003
CPP_FLAGS=$(CPP_FLAGS) /D "IA32"
!endif

# Sanity check, this is the default if not amd64, ia64, or i486
!ifndef DEFAULT_COMPILER_NAME
CPP=ARCH_ERROR
!endif

# MSC_VER is a 4 digit number that tells us what compiler is being used
#    and is generated when the local.make file is created by build.make
#    via the script get_msc_ver.sh
#
#    If MSC_VER is set, it overrides the above default setting.
#    But it should be set.
#    Possible values:
#      1200 is for VC6
#      1300 and 1310 is VS2003 or VC7
#      1399 is our fake number for the VS2005 compiler that really isn't 1400
#      1400 is for VS2005
#      1500 is for VS2008
#    Do not confuse this MSC_VER with the predefined macro _MSC_VER that the
#    compiler provides, when MSC_VER==1399, _MSC_VER will be 1400.
#    Normally they are the same, but a pre-release of the VS2005 compilers
#    in the Windows 64bit Platform SDK said it was 1400 when it was really
#    closer to VS2003 in terms of option spellings, so we use 1399 for that
#    1400 version that really isn't 1400.
#    See the file get_msc_ver.sh for more info.
!if "x$(MSC_VER)" == "x"
COMPILER_NAME=$(DEFAULT_COMPILER_NAME)
!else
!if "$(MSC_VER)" == "1200"
COMPILER_NAME=VC6
!endif
!if "$(MSC_VER)" == "1300"
COMPILER_NAME=VS2003
!endif
!if "$(MSC_VER)" == "1310"
COMPILER_NAME=VS2003
!endif
!if "$(MSC_VER)" == "1399"
# Compiler might say 1400, but if it's 14.00.30701, it isn't really VS2005
COMPILER_NAME=VS2003
!endif
!if "$(MSC_VER)" == "1400"
COMPILER_NAME=VS2005
!endif
!if "$(MSC_VER)" == "1500"
COMPILER_NAME=VS2008
!endif
!endif

# Add what version of the compiler we think this is to the compile line
CPP_FLAGS=$(CPP_FLAGS) /D "MSC_VER=$(MSC_VER)"

# By default, we do not want to use the debug version of the msvcrt.dll file
#   but if MFC_DEBUG is defined in the environment it will be used.
MS_RUNTIME_OPTION = /MD
!if "$(MFC_DEBUG)" == "true"
MS_RUNTIME_OPTION = /MTd /D "_DEBUG"
!endif

# Always add the _STATIC_CPPLIB flag
STATIC_CPPLIB_OPTION = /D _STATIC_CPPLIB
MS_RUNTIME_OPTION = $(MS_RUNTIME_OPTION) $(STATIC_CPPLIB_OPTION)
CPP_FLAGS=$(CPP_FLAGS) $(MS_RUNTIME_OPTION)

# How /GX option is spelled
GX_OPTION = /GX

# Optimization settings for various versions of the compilers and types of
#    builds. Three basic sets of settings: product, fastdebug, and debug.
#    These get added into CPP_FLAGS as needed by other makefiles.
!if "$(COMPILER_NAME)" == "VC6"
PRODUCT_OPT_OPTION   = /Ox /Os /Gy /GF
FASTDEBUG_OPT_OPTION = /Ox /Os /Gy /GF
DEBUG_OPT_OPTION     = /Od
!endif

!if "$(COMPILER_NAME)" == "VS2003"
PRODUCT_OPT_OPTION   = /O2 /Oy-
FASTDEBUG_OPT_OPTION = /O2 /Oy-
DEBUG_OPT_OPTION     = /Od
!endif

!if "$(COMPILER_NAME)" == "VS2005"
PRODUCT_OPT_OPTION   = /O2 /Oy-
FASTDEBUG_OPT_OPTION = /O2 /Oy-
DEBUG_OPT_OPTION     = /Od
GX_OPTION = /EHsc
# This VS2005 compiler has /GS as a default and requires bufferoverflowU.lib 
#    on the link command line, otherwise we get missing __security_check_cookie
#    externals at link time. Even with /GS-, you need bufferoverflowU.lib.
#    NOTE: Currently we decided to not use /GS-
BUFFEROVERFLOWLIB = bufferoverflowU.lib
LINK_FLAGS = /manifest $(LINK_FLAGS) $(BUFFEROVERFLOWLIB)
# Manifest Tool - used in VS2005 and later to adjust manifests stored
# as resources inside build artifacts.
MT=mt.exe
!endif

!if "$(COMPILER_NAME)" == "VS2008"
PRODUCT_OPT_OPTION   = /O2 /Oy-
FASTDEBUG_OPT_OPTION = /O2 /Oy-
DEBUG_OPT_OPTION     = /Od
GX_OPTION = /EHsc
LINK_FLAGS = /manifest $(LINK_FLAGS)
# Manifest Tool - used in VS2005 and later to adjust manifests stored
# as resources inside build artifacts.
MT=mt.exe
!endif

# Compile for space above time.
!if "$(Variant)" == "kernel"
PRODUCT_OPT_OPTION   = /O1 /Oy-
FASTDEBUG_OPT_OPTION = /O1 /Oy-
DEBUG_OPT_OPTION     = /Od
!endif

# If NO_OPTIMIZATIONS is defined in the environment, turn everything off
!ifdef NO_OPTIMIZATIONS
PRODUCT_OPT_OPTION   = $(DEBUG_OPT_OPTION)
FASTDEBUG_OPT_OPTION = $(DEBUG_OPT_OPTION)
!endif

# Generic linker settings
LINK=link.exe
LINK_FLAGS= $(LINK_FLAGS) kernel32.lib user32.lib gdi32.lib winspool.lib \
 comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib \
 uuid.lib Wsock32.lib winmm.lib /nologo /machine:$(MACHINE) /opt:REF \
 /opt:ICF,8 /map /debug

# Resource compiler settings
RC=rc.exe
RC_FLAGS=/D "HS_VER=$(HS_VER)" \
	 /D "HS_DOTVER=$(HS_DOTVER)" \
	 /D "HS_BUILD_ID=$(HS_BUILD_ID)" \
	 /D "JDK_VER=$(JDK_VER)" \
	 /D "JDK_DOTVER=$(JDK_DOTVER)" \
	 /D "HS_COMPANY=$(HS_COMPANY)" \
	 /D "HS_FILEDESC=$(HS_FILEDESC)" \
	 /D "HS_COPYRIGHT=$(HS_COPYRIGHT)" \
	 /D "HS_FNAME=$(HS_FNAME)" \
	 /D "HS_INTERNAL_NAME=$(HS_INTERNAL_NAME)" \
	 /D "HS_NAME=$(HS_NAME)"

# Need this to match the CPP_FLAGS settings
!if "$(MFC_DEBUG)" == "true"
RC_FLAGS = $(RC_FLAGS) /D "_DEBUG"
!endif

