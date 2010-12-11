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

LAUNCHER_SCRIPT = hotspot
LAUNCHER   = gamma

LAUNCHERDIR   = $(GAMMADIR)/src/os/posix/launcher
LAUNCHERDIR_SHARE := $(GAMMADIR)/src/share/tools/launcher
LAUNCHERFLAGS = $(ARCHFLAG) \
                -I$(LAUNCHERDIR) -I$(GAMMADIR)/src/share/vm/prims \
                -I$(LAUNCHERDIR_SHARE) \
                -DFULL_VERSION=\"$(HOTSPOT_RELEASE_VERSION)\" \
                -DJDK_MAJOR_VERSION=\"$(JDK_MAJOR_VERSION)\" \
                -DJDK_MINOR_VERSION=\"$(JDK_MINOR_VERSION)\" \
                -DARCH=\"$(LIBARCH)\" \
                -DGAMMA \
                -DLAUNCHER_TYPE=\"gamma\" \
                -DLINK_INTO_$(LINK_INTO) \
                $(TARGET_DEFINES)

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

LAUNCHER_OUT = launcher

SUFFIXES += .d

SOURCES := $(shell find $(LAUNCHERDIR) -name "*.c")
SOURCES_SHARE := $(shell find $(LAUNCHERDIR_SHARE) -name "*.c")

OBJS := $(patsubst $(LAUNCHERDIR)/%.c,$(LAUNCHER_OUT)/%.o,$(SOURCES)) $(patsubst $(LAUNCHERDIR_SHARE)/%.c,$(LAUNCHER_OUT)/%.o,$(SOURCES_SHARE))

DEPFILES := $(patsubst %.o,%.d,$(OBJS))
-include $(DEPFILES)

$(LAUNCHER_OUT)/%.o: $(LAUNCHERDIR_SHARE)/%.c
	$(QUIETLY) [ -d $(LAUNCHER_OUT) ] || { mkdir -p $(LAUNCHER_OUT); }
	$(QUIETLY) $(CC) -g -o $@ -c $< -MMD $(LAUNCHERFLAGS) $(CPPFLAGS)

$(LAUNCHER_OUT)/%.o: $(LAUNCHERDIR)/%.c
	$(QUIETLY) [ -d $(LAUNCHER_OUT) ] || { mkdir -p $(LAUNCHER_OUT); }
	$(QUIETLY) $(CC) -g -o $@ -c $< -MMD $(LAUNCHERFLAGS) $(CPPFLAGS)

$(LAUNCHER): $(OBJS) $(LIBJVM) $(LAUNCHER_MAPFILE)
ifeq ($(filter -sbfast -xsbfast, $(CFLAGS_BROWSE)),)
	$(QUIETLY) echo Linking launcher...
	$(QUIETLY) $(LINK_LAUNCHER/PRE_HOOK)
	$(QUIETLY) $(LINK_LAUNCHER) $(LFLAGS_LAUNCHER) -o $@ $(OBJS) $(LIBS_LAUNCHER)
	$(QUIETLY) $(LINK_LAUNCHER/POST_HOOK)
endif # filter -sbfast -xsbfast

$(LAUNCHER): $(LAUNCHER_SCRIPT)

$(LAUNCHER_SCRIPT): $(LAUNCHERDIR)/launcher.script
	$(QUIETLY) sed -e 's/@@LIBARCH@@/$(LIBARCH)/g' $< > $@
	$(QUIETLY) chmod +x $@

