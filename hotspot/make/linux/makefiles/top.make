#
# Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

# top.make is included in the Makefile in the build directories.
# It DOES NOT include the vm dependency info in order to be faster.
# Its main job is to implement the incremental form of make lists.
# It also:
#   -builds and runs adlc via adlc.make
#   -generates JVMTI source and docs via jvmti.make (JSR-163)
#   -generate sa-jdi.jar (JDI binding to core files)

# It assumes the following flags are set:
# CFLAGS Platform_file, Src_Dirs, SYSDEFS, AOUT, Obj_Files

# -- D. Ungar (5/97) from a file by Bill Bush

# Don't override the built-in $(MAKE).
# Instead, use "gmake" (or "gnumake") from the command line.  --Rose
#MAKE = gmake

TOPDIR      = $(shell echo `pwd`)
GENERATED   = $(TOPDIR)/../generated
VM          = $(GAMMADIR)/src/share/vm
Plat_File   = $(Platform_file)
CDG         = cd $(GENERATED); 

# Pick up MakeDeps' sources and definitions
include $(GAMMADIR)/make/$(Platform_os_family)/makefiles/makedeps.make
MakeDepsClass = MakeDeps.class

ifdef USE_PRECOMPILED_HEADER
PrecompiledOption = -DUSE_PRECOMPILED_HEADER
UpdatePCH         = $(MAKE) -f vm.make $(PRECOMPILED_HEADER) $(MFLAGS) 
else
UpdatePCH         = \# precompiled header is not used
PrecompiledOption = 
endif

MakeDeps    = $(RUN.JAVA) $(PrecompiledOption) -classpath $(GENERATED) MakeDeps

Include_DBs/GC          = $(VM)/includeDB_gc \
                          $(VM)/includeDB_gc_parallel \
                          $(VM)/gc_implementation/includeDB_gc_parallelScavenge \
                          $(VM)/gc_implementation/includeDB_gc_concurrentMarkSweep \
                          $(VM)/gc_implementation/includeDB_gc_parNew \
                          $(VM)/gc_implementation/includeDB_gc_g1     \
                          $(VM)/gc_implementation/includeDB_gc_serial \
                          $(VM)/gc_implementation/includeDB_gc_shared

Include_DBs/CORE        = $(VM)/includeDB_core   $(Include_DBs/GC) \
                          $(VM)/includeDB_jvmti \
                          $(VM)/includeDB_features
Include_DBs/COMPILER1   = $(Include_DBs/CORE) $(VM)/includeDB_compiler1
Include_DBs/COMPILER2   = $(Include_DBs/CORE) $(VM)/includeDB_compiler2
Include_DBs/TIERED      = $(Include_DBs/CORE) $(VM)/includeDB_compiler1 $(VM)/includeDB_compiler2
Include_DBs/ZERO        = $(Include_DBs/CORE) $(VM)/includeDB_zero
Include_DBs = $(Include_DBs/$(TYPE))

Cached_plat = $(GENERATED)/platform.current
Cached_db   = $(GENERATED)/includeDB.current

Incremental_Lists = $(Cached_db)
# list generation also creates $(GENERATED)/$(Cached_plat)


AD_Dir   = $(GENERATED)/adfiles
ADLC     = $(AD_Dir)/adlc
AD_Spec  = $(GAMMADIR)/src/cpu/$(Platform_arch)/vm/$(Platform_arch_model).ad
AD_Src   = $(GAMMADIR)/src/share/vm/adlc
AD_Names = ad_$(Platform_arch_model).hpp ad_$(Platform_arch_model).cpp
AD_Files = $(AD_Names:%=$(AD_Dir)/%)

# AD_Files_If_Required/COMPILER1 = ad_stuff
AD_Files_If_Required/COMPILER2 = ad_stuff
AD_Files_If_Required/TIERED = ad_stuff
AD_Files_If_Required = $(AD_Files_If_Required/$(TYPE))

# Wierd argument adjustment for "gnumake -j..."
adjust-mflags   = $(GENERATED)/adjust-mflags
MFLAGS-adjusted = -r `$(adjust-mflags) "$(MFLAGS)" "$(HOTSPOT_BUILD_JOBS)"`


# default target: make makeDeps, update lists, make vm
# done in stages to force sequential order with parallel make
#

default: vm_build_preliminaries the_vm
	@echo All done.

# This is an explicit dependency for the sake of parallel makes.
vm_build_preliminaries:  checks $(Incremental_Lists) $(AD_Files_If_Required) jvmti_stuff sa_stuff
	@# We need a null action here, so implicit rules don't get consulted.

# make makeDeps: (and zap the cached db files to force a nonincremental run)

$(GENERATED)/$(MakeDepsClass): $(MakeDepsSources)
	@$(REMOTE) $(COMPILE.JAVAC) -classpath $(GAMMADIR)/src/share/tools/MakeDeps -d $(GENERATED) $(MakeDepsSources)
	@echo Removing $(Incremental_Lists) to force regeneration.
	@rm -f $(Incremental_Lists)
	@$(CDG) echo >$(Cached_plat)

# make incremental_lists, if cached files out of date, run makeDeps

$(Incremental_Lists): $(Include_DBs) $(Plat_File) $(GENERATED)/$(MakeDepsClass)
	$(CDG) cat $(Include_DBs) > $(GENERATED)/includeDB
	$(CDG) if [ ! -r incls ] ; then \
	mkdir incls ; \
	fi
	$(CDG) (echo $(CDG) echo $(MakeDeps) diffs UnixPlatform $(Cached_plat) $(Cached_db) $(Plat_File) $(GENERATED)/includeDB $(MakeDepsOptions)) > makeDeps.sh
	$(CDG) $(REMOTE) sh $(GENERATED)/makeDeps.sh
	$(CDG) cp includeDB    $(Cached_db)
	$(CDG) cp $(Plat_File) $(Cached_plat)

# symbolic target for command lines
lists: $(Incremental_Lists)
	@: lists are now up to date

# make AD files as necessary
ad_stuff: $(Incremental_Lists) $(adjust-mflags)
	@$(MAKE) -f adlc.make $(MFLAGS-adjusted)

# generate JVMTI files from the spec
jvmti_stuff: $(Incremental_Lists) $(adjust-mflags)
	@$(MAKE) -f jvmti.make $(MFLAGS-adjusted)

# generate SA jar files and native header
sa_stuff:
	@$(MAKE) -f sa.make $(MFLAGS-adjusted)

# and the VM: must use other makefile with dependencies included

# We have to go to great lengths to get control over the -jN argument
# to the recursive invocation of vm.make.  The problem is that gnumake
# resets -jN to -j1 for recursive runs.  (How helpful.)
# Note that the user must specify the desired parallelism level via a
# command-line or environment variable name HOTSPOT_BUILD_JOBS.
$(adjust-mflags): $(GAMMADIR)/make/$(Platform_os_family)/makefiles/adjust-mflags.sh
	@+rm -f $@ $@+
	@+cat $< > $@+
	@+chmod +x $@+
	@+mv $@+ $@

the_vm: vm_build_preliminaries $(adjust-mflags)
	@$(UpdatePCH)
	@$(MAKE) -f vm.make $(MFLAGS-adjusted)

install: the_vm
	@$(MAKE) -f vm.make install

# next rules support "make foo.[oi]"

%.o %.i %.s:
	$(UpdatePCH) 
	$(MAKE) -f vm.make $(MFLAGS) $@
	#$(MAKE) -f vm.make $@

# this should force everything to be rebuilt
clean: 
	rm -f $(GENERATED)/*.class
	$(MAKE) $(MFLAGS) $(GENERATED)/$(MakeDepsClass)
	$(MAKE) -f vm.make $(MFLAGS) clean

# just in case it doesn't, this should do it
realclean:
	$(MAKE) -f vm.make $(MFLAGS) clean
	rm -fr $(GENERATED)

.PHONY: default vm_build_preliminaries
.PHONY: lists ad_stuff jvmti_stuff sa_stuff the_vm clean realclean
.PHONY: checks check_os_version install
