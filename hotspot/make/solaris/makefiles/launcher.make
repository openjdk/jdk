#
# Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
#

# Rules to build gamma launcher, used by vm.make

# gamma[_g]: launcher
LAUNCHER   = gamma
LAUNCHER_G = $(LAUNCHER)$(G_SUFFIX)

LAUNCHERDIR   = $(GAMMADIR)/src/os/$(Platform_os_family)/launcher
LAUNCHERFLAGS = $(ARCHFLAG) \
                -I$(LAUNCHERDIR) -I$(GAMMADIR)/src/share/vm/prims \
                -DFULL_VERSION=\"$(HOTSPOT_RELEASE_VERSION)\" \
                -DARCH=\"$(LIBARCH)\" \
                -DGAMMA \
                -DLAUNCHER_TYPE=\"gamma\" \
                -DLINK_INTO_$(LINK_INTO)

ifeq ($(LINK_INTO),AOUT)
  LAUNCHER.o                 = launcher.o $(JVM_OBJ_FILES)
  LAUNCHER_MAPFILE           = mapfile_reorder
  LFLAGS_LAUNCHER$(LDNOMAP) += $(MAPFLAG:FILENAME=$(LAUNCHER_MAPFILE))
  LIBS_LAUNCHER             += $(LIBS)
else
  LAUNCHER.o                 = launcher.o
  LFLAGS_LAUNCHER           += -L `pwd`
  LIBS_LAUNCHER             += -l$(JVM) $(LIBS)
endif

LINK_LAUNCHER = $(LINK.CC)

LINK_LAUNCHER/PRE_HOOK  = $(LINK_LIB.CC/PRE_HOOK)
LINK_LAUNCHER/POST_HOOK = $(LINK_LIB.CC/POST_HOOK)

ifeq ("${Platform_compiler}", "sparcWorks")
# Enable the following LAUNCHERFLAGS addition if you need to compare the
# built ELF objects.
#
# The -g option makes static data global and the "-W0,-noglobal"
# option tells the compiler to not globalize static data using a unique
# globalization prefix. Instead force the use of a static globalization
# prefix based on the source filepath so the objects from two identical
# compilations are the same.
#
# Note: The blog says to use "-W0,-xglobalstatic", but that doesn't
#       seem to work. I got "-W0,-noglobal" from Kelly and that works.
#LAUNCHERFLAGS += -W0,-noglobal
endif # Platform_compiler == sparcWorks

launcher.o: launcher.c $(LAUNCHERDIR)/java.c $(LAUNCHERDIR)/java_md.c
	$(CC) -g -c -o $@ launcher.c $(LAUNCHERFLAGS) $(CPPFLAGS) ${TARGET_DEFINES}

launcher.c:
	@echo Generating $@
	$(QUIETLY) { \
	echo '#define debug launcher_debug'; \
	echo '#include "java.c"'; \
	echo '#include "java_md.c"'; \
	} > $@

$(LAUNCHER): $(LAUNCHER.o) $(LIBJVM) $(LAUNCHER_MAPFILE)
ifeq ($(filter -sbfast -xsbfast, $(CFLAGS_BROWSE)),)
	@echo Linking launcher...
	$(QUIETLY) $(LINK_LAUNCHER/PRE_HOOK)
	$(QUIETLY) \
	$(LINK_LAUNCHER) $(LFLAGS_LAUNCHER) -o $@ $(LAUNCHER.o) $(LIBS_LAUNCHER)
	$(QUIETLY) $(LINK_LAUNCHER/POST_HOOK)
	[ -f $(LAUNCHER_G) ] || ln -s $@ $(LAUNCHER_G)
endif # filter -sbfast -xsbfast

