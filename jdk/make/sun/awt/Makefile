#
# Copyright 1995-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR = ../..
MODULE  = awt
PACKAGE = sun.awt
LIBRARY = awt
PRODUCT = sun

# Tell Defs.gmk that VIS is needed
VIS_NEEDED=true

# Use highest optimization level
OPTMIZATION_LEVEL = HIGHEST

include $(BUILDDIR)/common/Defs.gmk

OTHER_CFLAGS += -D__MEDIALIB_OLD_NAMES -D__USE_J2D_NAMES

#
# Files
#

#
# Include all Java source files in sun/awt and sun/java2d, except for:
# sun/awt/resources       handled by java/awt/Makefile
# sun/java2d/pisces       handled by sun/pisces/Makefile
#
AUTO_FILES_JAVA_DIRS = sun/awt sun/java2d com/sun/awt
AUTO_JAVA_PRUNE = resources pisces

ifeq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv WINDOWS

#
# Files
#
include FILES_c_windows.gmk
include FILES_export_windows.gmk

#
# Prune files that are currently unused on Windows (but still used on
# Solaris/Linux).
#
AUTO_JAVA_PRUNE += RemoteOffScreenImage.java

OTHER_CFLAGS += $(GX_OPTION) -DMLIB_NO_LIBSUNMATH -DUNICODE -D_UNICODE
OTHER_CXXFLAGS += $(GX_OPTION) -DUNICODE -D_UNICODE

ifeq ($(ARCH_DATA_MODEL), 64)
  OTHER_CFLAGS += -DMLIB_OS64BIT
endif

# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WINDOWS
endif # PLATFORM

ifneq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv SOLARIS/LINUX

#
# Files
#
include FILES_c_unix.gmk
include FILES_export_unix.gmk
#
# Prune the sun/awt/X11 subdirectory, which is handled by sun/xawt/Makefile.
# Also prune files that are currently unused on Solaris/Linux (but still
# used on Windows).
#
AUTO_JAVA_PRUNE += X11 AWTCharset.java

ifeq  ($(PLATFORM), solaris)
FILES_c = $(FILES_2D_c)
FILES_c += awt_LoadLibrary.c
OTHER_LDLIBS = $(JVMLIB) $(LIBM) -ldl
ifeq ($(CC_VER), 5.8)
  ifndef REMOVE_ALL_WORKAROUNDS
    ifeq ($(ARCH_FAMILY), i586)
      # If on Solaris 11 (or dlfcn.h has unknown_control_flow pragmas in it)
      #   we trigger a SS11 bug that causes the compiler to crash (bug 6343678)
      #   Here we check to see if we need this workaround and change the opt 
      #   settings for this one file.
      USE_WORKAROUND:=$(shell $(GREP) unknown_control_flow /usr/include/dlfcn.h)
      ifneq ($(USE_WORKAROUND),)
	ifeq ($(FASTDEBUG), true)
          CFLAGS_DBG/awt_Mlib.o = -xO0 \
	    $(warning  "WARNING: Using workaround for SS11 bug 6343678, on $@")
	endif
        # Compiler bug 6343678 need to prevent a SEGV in the compiler
        CFLAGS_OPT/awt_Mlib.o = -g \
	  $(warning  "WARNING: Using workaround for SS11 bug 6343678, on $@")
      endif
    endif
  endif
endif
endif

ifeq  ($(PLATFORM), linux)
FILES_c = $(FILES_2D_c)
FILES_c += awt_LoadLibrary.c
OTHER_CFLAGS += -DMLIB_NO_LIBSUNMATH
OTHER_LDLIBS = $(JVMLIB) $(LIBM) -ldl
endif

FILES_c += initIDs.c

# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ SOLARIS/LINUX
endif # PLATFORM 

# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv SOLARIS-SPARC
# solaris-sparc and solaris-sparcv9 both build 'vis'
ifeq ("$(PLATFORM)-$(ARCH_FAMILY)", "solaris-sparc")
  FILES_c += $(FILES_2D_vis)
  ASFLAGS += -P
  FILES_s += mlib_v_ImageCopy_blk.s
  INLINE_VIS = $(PLATFORM_SRC)/native/sun/awt/medialib/vis_$(ARCH_DATA_MODEL).il
  CFLAGS_sparcv9 = -DMLIB_OS64BIT
  
  CFLAGS += $(CFLAGS_$(ARCH)) -DMLIB_ADD_SUFF $(INLINE_VIS) \
	    -I$(SHARE_SRC)/native/sun/awt/medialib \
	    -I$(PLATFORM_SRC)/native/sun/awt/medialib \
	    -I$(PLATFORM_SRC)/native/sun/java2d/loops

vpath %.c  $(PLATFORM_SRC)/native/sun/java2d/loops
vpath %.c  $(SHARE_SRC)/native/sun/awt/medialib
vpath %.c  $(PLATFORM_SRC)/native/sun/awt/medialib
vpath %.s  $(PLATFORM_SRC)/native/sun/awt/medialib

else
  FILES_c += MapAccelFunc.c
endif
# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ SOLARIS-SPARC

#
# Resources
#
LOCALE_SET_DEFINITION = jre
ifeq ($(PLATFORM), windows)
  RESOURCE_BUNDLES_COMPILED_PROPERTIES += \
	sun/awt/windows/awtLocalization.properties
endif

#
# Rules
#
ifeq ($(PLATFORM), linux)
  FILES_m = mapfile-vers-linux
  # libawt.so on Linux is statically linked with Motif and contains all the
  # Xm symbols. Mapfile is disabled so the symbols will remain public.
  LDNOMAP=true
endif

include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

build: fontconfigs


ifeq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv WINDOWS
#
# Additional dependencies.
#
# If make.depend is not working correctly, then it can be omitted
# if you always build from a clean workspace. 

include make.depend

# required additional rules:

# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WINDOWS
endif # PLATFORM

ifneq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv SOLARIS/LINUX

ifndef OPENJDK
build: dgalibs
endif
# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ SOLARIS/LINUX
endif # PLATFORM

clobber clean:: java2d.clean fontconfigs.clean

ifndef OPENJDK
clobber clean:: dgalib.clean 
endif

java2d.clean:
	$(RM) -r $(CLASSBINDIR)/sun/java2d .classes.*

#
# Add to the ambient vpath to pick up files in subdirectories
#
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/image
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/image/gif
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/image/cvutils
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/shell
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/medialib
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/debug
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/utility
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/../java2d
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/../java2d/loops
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/../java2d/pipe
vpath %.cpp $(SHARE_SRC)/native/$(PKGDIR)/image
ifeq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv WINDOWS
vpath %.cpp   $(PLATFORM_SRC)/native/sun/windows
vpath %.cpp   $(PLATFORM_SRC)/native/$(PKGDIR)
vpath %.cpp   $(PLATFORM_SRC)/native/$(PKGDIR)/../java2d/windows
vpath %.cpp   $(PLATFORM_SRC)/native/$(PKGDIR)/../java2d/d3d
vpath %.c     $(PLATFORM_SRC)/native/$(PKGDIR)/../java2d/opengl
vpath %.c     $(SHARE_SRC)/native/$(PKGDIR)/../java2d/opengl
vpath %.c     $(SHARE_SRC)/native/$(PKGDIR)/../font
# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WINDOWS
endif # PLATFORM

ifeq ($(PLATFORM), linux)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv LINUX
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/../java2d/opengl
vpath %.c   $(PLATFORM_SRC)/native/$(PKGDIR)/../java2d/opengl
vpath %.c   $(PLATFORM_SRC)/native/$(PKGDIR)/../java2d/x11
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/../font
# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ LINUX
endif # PLATFORM

ifeq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv WINDOWS
OTHER_LDLIBS = kernel32.lib user32.lib gdi32.lib winspool.lib \
               imm32.lib ole32.lib uuid.lib shell32.lib \
               comdlg32.lib winmm.lib comctl32.lib delayimp.lib \
               $(JVMLIB) \
               /DELAYLOAD:user32.dll /DELAYLOAD:gdi32.dll \
               /DELAYLOAD:shell32.dll /DELAYLOAD:winmm.dll \
               /DELAYLOAD:winspool.drv /DELAYLOAD:imm32.dll \
               /DELAYLOAD:ole32.dll /DELAYLOAD:comdlg32.dll \
               /DELAYLOAD:comctl32.dll

clean:: awt.clean

awt.clean: 
	$(RM) $(TEMPDIR)/awt.ico 
	$(RM) $(TEMPDIR)/awt.res 
	$(RM) $(TEMPDIR)/hand.cur 
	$(RM) $(TEMPDIR)/check.bmp
# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WINDOWS
endif # PLATFORM

ifneq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv SOLARIS/LINUX

LIBXTST = -lXtst

# Use -lXmu for EditRes support
LIBXMU_DBG	= -lXmu
LIBXMU_OPT	=
LIBXMU	        = $(LIBXMU_$(VARIANT))

#
# Extra C flags.
#


ifndef OPENJDK

#
# Solaris X11 Direct Graphics Access library
#

_DGALIBS_sparc = \
	libxinerama.so \
	libjdgaSUNWcg6.so \
	libjdgaSUNWffb.so \
	libjdgaSUNWm64.so \
	libjdgaSUNWafb.so

_DGALIBS_sparcv9 = \
	libxinerama.so \
	libjdgaSUNWcg6.so \
	libjdgaSUNWffb.so \
	libjdgaSUNWm64.so \
	libjdgaSUNWafb.so

_DGALIBS_i586 = 	# no i586 library yet

_DGALIBS_amd64 = 	# no amd64 library yet

DGALIBS = $(_DGALIBS_$(ARCH):%=$(LIBDIR)/$(LIBARCH)/%)

dgalibs: $(DGALIBS)

$(LIBDIR)/$(LIBARCH)/libxinerama.so: $(CLOSED_SRC)/solaris/lib/$(ARCH)/libxinerama.so
	$(install-file)
	$(call chmod-file, a+x)

$(LIBDIR)/$(LIBARCH)/libjdgaSUNW%.so: $(CLOSED_SRC)/solaris/lib/$(ARCH)/libjdgaSUNW%.so
	$(install-file)
	$(call chmod-file, a+x)

$(LIBDIR)/$(LIBARCH)/libjdgaSUNWafb.so: $(LIBDIR)/$(LIBARCH)/libjdgaSUNWffb.so
	$(prep-target)
	$(call install-sym-link, libjdgaSUNWffb.so)

clean:: dgalib.clean

dgalib.clean:
	$(RM) $(LIBDIR)/$(LIBARCH)/libjdgaSUNW* $(LIBDIR)/$(LIBARCH)/libxinerama.so
endif

# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ SOLARIS/LINUX
endif # PLATFORM

#
# Font configs
#

ifeq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv WINDOWS

FONTCONFIGS_SRC = $(PLATFORM_SRC)/classes/sun/awt/windows
_FONTCONFIGS = \
        fontconfig.properties

FONTCONFIGS_SRC_PREFIX =

# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WINDOWS
endif # PLATFORM

ifeq ($(PLATFORM), linux)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv LINUX
ifdef OPENJDK

FONTCONFIGS_SRC	= $(PLATFORM_SRC)/classes/sun/awt/fontconfigs
_FONTCONFIGS	= \
	fontconfig.properties				\
	fontconfig.SuSE.properties                      \
	fontconfig.Ubuntu.properties                    \
	fontconfig.Fedora.properties
else

FONTCONFIGS_SRC	= $(CLOSED_SRC)/solaris/classes/sun/awt/fontconfigs
_FONTCONFIGS	= \
	fontconfig.properties				\
	fontconfig.RedHat.properties			\
	fontconfig.RedHat.2.1.properties		\
	fontconfig.RedHat.3.properties		\
	fontconfig.RedHat.4.properties			\
	fontconfig.Sun.properties			\
	fontconfig.Turbo.properties			\
	fontconfig.SuSE.properties
endif

FONTCONFIGS_SRC_PREFIX = $(PLATFORM).

# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ LINUX
endif # PLATFORM

ifeq ($(PLATFORM), solaris)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv SOLARIS

FONTCONFIGS_SRC	= $(PLATFORM_SRC)/classes/sun/awt/fontconfigs
_FONTCONFIGS	= \
	fontconfig.properties	\
	fontconfig.5.9.properties	\
	fontconfig.5.8.properties

FONTCONFIGS_SRC_PREFIX = $(PLATFORM).

# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ SOLARIS
endif # PLATFORM

FONTCONFIGS     = $(_FONTCONFIGS:%=$(LIBDIR)/%.src)
BINARYFONTCONFIGS = $(_FONTCONFIGS:%.properties=$(LIBDIR)/%.bfc)

fontconfigs: $(FONTCONFIGS) $(BINARYFONTCONFIGS)

$(LIBDIR)/%.src: $(FONTCONFIGS_SRC)/$(FONTCONFIGS_SRC_PREFIX)%
	$(install-file)

COMPILEFONTCONFIG_JARFILE = $(BUILDTOOLJARDIR)/compilefontconfig.jar

$(LIBDIR)/%.bfc: $(FONTCONFIGS_SRC)/$(FONTCONFIGS_SRC_PREFIX)%.properties \
    $(COMPILEFONTCONFIG_JARFILE)
	$(prep-target)
	$(BOOT_JAVA_CMD) -jar $(COMPILEFONTCONFIG_JARFILE) $< $@
	$(install-module-file)
	$(call chmod-file, 444)
	@$(java-vm-cleanup)

fontconfigs.clean :
	$(RM) $(FONTCONFIGS)
	$(RM) $(BINARYFONTCONFIGS)

ifeq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv WINDOWS
OTHER_CFLAGS += $(GX_OPTION) -DUNICODE -D_UNICODE

ifeq ($(MILESTONE), internal)
  CPPFLAGS += -DINTERNAL_BUILD
endif

#
# Useful variables that name parts of the src tree.
#
WINAWT_native        = $(PLATFORM_SRC)/native/sun/windows
WINAWT_COMMON_native = $(PLATFORM_SRC)/native/sun/awt_common

# FIXME: awt shouldn't have to see the VM's private headers.
# FIXME: if you re-order this list, the build breaks, possibly because 
#        of filename collision. Such a collision, if present, is pure
#        evil. Need to investigate this.

OTHER_INCLUDES += -I$(CLASSHDRDIR)/../../java/jvm \
	-I$(OBJDIR) \
	-I$(SHARE_SRC)/native/common \
	-I$(WINAWT_native) \
	-I$(DXSDK_INCLUDE_PATH) \
	-I$(SHARE_SRC)/native/sun/awt/image/cvutils \
	-I$(SHARE_SRC)/native/sun/awt/image \
	-I$(SHARE_SRC)/native/sun/java2d/loops \
	-I$(SHARE_SRC)/native/sun/java2d \
	-I$(PLATFORM_SRC)/native/sun/java2d \
	-I$(SHARE_SRC)/native/sun/java2d/opengl \
	-I$(PLATFORM_SRC)/native/sun/java2d/d3d \
	-I$(PLATFORM_SRC)/native/sun/java2d/opengl \
	-I$(PLATFORM_SRC)/native/sun/java2d/windows \
	-I$(SHARE_SRC)/native/sun/font \
	-I$(SHARE_SRC)/native/sun/java2d/pipe \
	-I$(SHARE_SRC)/native/sun/dc/path \
	-I$(SHARE_SRC)/native/sun/dc/doe \
	-I$(SHARE_SRC)/native/sun/awt/debug \
	-I$(PLATFORM_SRC)/native/sun/awt
#	-I$(WINAWT_COMMON_native)

# this is only required for compiling )/native/sun/awt/medialib/*.c files
#
OTHER_INCLUDES += -I$(SHARE_SRC)/native/sun/awt/medialib

#
# .res file construction.
#

ifdef OPENJDK
    RC_FLAGS += -i "$(PLATFORM_SRC)/resource/icons"
else
    RC_FLAGS += -i "$(CLOSED_SRC)/windows/native/sun/windows"
endif
VERSIONINFO_RESOURCE = $(WINAWT_native)/awt.rc

# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WINDOWS
endif # PLATFORM

ifneq ($(PLATFORM), windows)
# vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv SOLARIS/LINUX

#
# Other extra flags needed for compiling.
#

ifeq ($(PLATFORM), linux)
CPPFLAGS += -I$(OPENWIN_HOME)/include \
            -I$(OPENWIN_HOME)/include/X11/extensions \
            -I$(PLATFORM_SRC)/native/$(PKGDIR)/font 
endif
CPPFLAGS += -I$(SHARE_SRC)/native/$(PKGDIR)/debug \
            -I$(SHARE_SRC)/native/$(PKGDIR)/../font \
            -I$(PLATFORM_SRC)/native/$(PKGDIR)/../font \
            -I$(SHARE_SRC)/native/$(PKGDIR)/image \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/image/cvutils \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/shell \
            -I$(SHARE_SRC)/native/$(PKGDIR)/medialib \
            -I$(PLATFORM_SRC)/native/$(PKGDIR)/medialib \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../java2d \
	    -I$(PLATFORM_SRC)/native/$(PKGDIR)/../java2d \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../java2d/loops \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../java2d/pipe \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../java2d/opengl \
	    -I$(PLATFORM_SRC)/native/$(PKGDIR)/../java2d/opengl \
	    -I$(PLATFORM_SRC)/native/$(PKGDIR)/../java2d/x11 \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../dc/doe \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../dc/path \
            -I$(PLATFORM_SRC)/native/$(PKGDIR)/../jdga \
            -I$(PLATFORM_SRC)/native/$(PKGDIR) \
	    $(EVENT_MODEL)

ifeq ($(PLATFORM), linux)
LDFLAGS += -L$(OPENWIN_LIB)
endif

LDFLAGS += -L$(LIBDIR)/$(LIBARCH)/$(TSOBJDIR) \
	   $(AWT_RUNPATH)

# ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ SOLARIS/LINUX
endif # PLATFORM

CLASSES.export += java.io.InputStream \
		  java.lang.Integer \
		  java.lang.ThreadGroup

.PHONY: dgalibs dgalib.clean fontconfigs fontconfigs.clean

