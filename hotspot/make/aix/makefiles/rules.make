#
# Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

# Common rules/macros for the vm, adlc.

# Tell make that .cpp is important
.SUFFIXES: .cpp $(SUFFIXES)

DEMANGLER       = c++filt
DEMANGLE        = $(DEMANGLER) < $@ > .$@ && $(MV) -f .$@ $@

# $(CC) is the c compiler (cc/gcc), $(CXX) is the c++ compiler (CC/g++).
CC_COMPILE       = $(CC) $(CXXFLAGS) $(CFLAGS)
CXX_COMPILE      = $(CXX) $(CXXFLAGS) $(CFLAGS)

AS.S            = $(AS) $(ASFLAGS)

COMPILE.CC       = $(CC_COMPILE) -c
GENASM.CC        = $(CC_COMPILE) -S
LINK.CC          = $(CC) $(LFLAGS) $(AOUT_FLAGS) $(PROF_AOUT_FLAGS)
LINK_LIB.CC      = $(CC) $(LFLAGS) $(SHARED_FLAG)
PREPROCESS.CC    = $(CC_COMPILE) -E

COMPILE.CXX      = $(CXX_COMPILE) -c
GENASM.CXX       = $(CXX_COMPILE) -S
LINK.CXX         = $(CXX) $(LFLAGS) $(AOUT_FLAGS) $(PROF_AOUT_FLAGS)
LINK_NOPROF.CXX  = $(CXX) $(LFLAGS) $(AOUT_FLAGS)
LINK_LIB.CXX     = $(CXX) $(LFLAGS) $(SHARED_FLAG)
PREPROCESS.CXX   = $(CXX_COMPILE) -E

# cross compiling the jvm with c2 requires host compilers to build
# adlc tool

HOST.CXX_COMPILE      = $(HOSTCXX) $(CXXFLAGS) $(CFLAGS)
HOST.COMPILE.CXX      = $(HOST.CXX_COMPILE) -c
HOST.LINK_NOPROF.CXX  = $(HOSTCXX) $(LFLAGS) $(AOUT_FLAGS)


# Effect of REMOVE_TARGET is to delete out-of-date files during "gnumake -k".
REMOVE_TARGET   = rm -f $@

# Note use of ALT_BOOTDIR to explicitly specify location of java and
# javac; this is the same environment variable used in the J2SE build
# process for overriding the default spec, which is BOOTDIR.
# Note also that we fall back to using JAVA_HOME if neither of these is
# specified.

ifdef ALT_BOOTDIR

RUN.JAVA  = $(ALT_BOOTDIR)/bin/java
RUN.JAVAP = $(ALT_BOOTDIR)/bin/javap
RUN.JAVAH = $(ALT_BOOTDIR)/bin/javah
RUN.JAR   = $(ALT_BOOTDIR)/bin/jar
COMPILE.JAVAC = $(ALT_BOOTDIR)/bin/javac
COMPILE.RMIC = $(ALT_BOOTDIR)/bin/rmic
BOOT_JAVA_HOME = $(ALT_BOOTDIR)

else

ifdef BOOTDIR

RUN.JAVA  = $(BOOTDIR)/bin/java
RUN.JAVAP = $(BOOTDIR)/bin/javap
RUN.JAVAH = $(BOOTDIR)/bin/javah
RUN.JAR   = $(BOOTDIR)/bin/jar
COMPILE.JAVAC = $(BOOTDIR)/bin/javac
COMPILE.RMIC  = $(BOOTDIR)/bin/rmic
BOOT_JAVA_HOME = $(BOOTDIR)

else

ifdef JAVA_HOME

RUN.JAVA  = $(JAVA_HOME)/bin/java
RUN.JAVAP = $(JAVA_HOME)/bin/javap
RUN.JAVAH = $(JAVA_HOME)/bin/javah
RUN.JAR   = $(JAVA_HOME)/bin/jar
COMPILE.JAVAC = $(JAVA_HOME)/bin/javac
COMPILE.RMIC  = $(JAVA_HOME)/bin/rmic
BOOT_JAVA_HOME = $(JAVA_HOME)

else

# take from the PATH, if ALT_BOOTDIR, BOOTDIR and JAVA_HOME are not defined

RUN.JAVA  = java
RUN.JAVAP = javap
RUN.JAVAH = javah
RUN.JAR   = jar
COMPILE.JAVAC = javac
COMPILE.RMIC  = rmic

endif
endif
endif

COMPILE.JAVAC += $(BOOTSTRAP_JAVAC_FLAGS)

SUM = /usr/bin/sum

# 'gmake MAKE_VERBOSE=y' gives all the gory details.
QUIETLY$(MAKE_VERBOSE)  = @
RUN.JAR$(MAKE_VERBOSE) += >/dev/null

# Settings for javac
JAVAC_FLAGS = -g -encoding ascii

# Prefer BOOT_JDK_SOURCETARGET if it's set (typically by the top build system)
# Fall back to the values here if it's not set (hotspot only builds)
ifeq ($(BOOT_JDK_SOURCETARGET),)
BOOTSTRAP_SOURCETARGET := -source 8 -target 8
else
BOOTSTRAP_SOURCETARGET := $(BOOT_JDK_SOURCETARGET)
endif

BOOTSTRAP_JAVAC_FLAGS = $(JAVAC_FLAGS) $(BOOTSTRAP_SOURCETARGET)

# With parallel makes, print a message at the end of compilation.
ifeq    ($(findstring j,$(MFLAGS)),j)
COMPILE_DONE    = && { echo Done with $<; }
endif

# Include $(NONPIC_OBJ_FILES) definition
ifndef LP64
include $(GAMMADIR)/make/pic.make
endif

include $(GAMMADIR)/make/altsrc.make

# The non-PIC object files are only generated for 32 bit platforms.
ifdef LP64
%.o: %.cpp
	@echo $(LOG_INFO) Compiling $<
	$(QUIETLY) $(REMOVE_TARGET)
	$(QUIETLY) $(COMPILE.CXX) $(DEPFLAGS) -o $@ $< $(COMPILE_DONE)
else
%.o: %.cpp
	@echo $(LOG_INFO) Compiling $<
	$(QUIETLY) $(REMOVE_TARGET)
	$(QUIETLY) $(if $(findstring $@, $(NONPIC_OBJ_FILES)), \
	   $(subst $(VM_PICFLAG), ,$(COMPILE.CXX)) $(DEPFLAGS) -o $@ $< $(COMPILE_DONE), \
	   $(COMPILE.CXX) $(DEPFLAGS) -o $@ $< $(COMPILE_DONE))
endif

%.o: %.s
	@echo $(LOG_INFO) Assembling $<
	$(QUIETLY) $(REMOVE_TARGET)
	$(QUIETLY) $(AS.S) $(DEPFLAGS) -o $@ $< $(COMPILE_DONE)

%.s: %.cpp
	@echo $(LOG_INFO) Generating assembly for $<
	$(QUIETLY) $(GENASM.CXX) -o $@ $<
	$(QUIETLY) $(DEMANGLE) $(COMPILE_DONE)

# Intermediate files (for debugging macros)
%.i: %.cpp
	@echo $(LOG_INFO) Preprocessing $< to $@
	$(QUIETLY) $(PREPROCESS.CXX) $< > $@ $(COMPILE_DONE)

#  Override gnumake built-in rules which do sccs get operations badly.
#  (They put the checked out code in the current directory, not in the
#  directory of the original file.)  Since this is a symptom of a teamware
#  failure, and since not all problems can be detected by gnumake due
#  to incomplete dependency checking... just complain and stop.
%:: s.%
	@echo "========================================================="
	@echo File $@
	@echo is out of date with respect to its SCCS file.
	@echo This file may be from an unresolved Teamware conflict.
	@echo This is also a symptom of a Teamware bringover/putback failure
	@echo in which SCCS files are updated but not checked out.
	@echo Check for other out of date files in your workspace.
	@echo "========================================================="
	@exit 666

%:: SCCS/s.%
	@echo "========================================================="
	@echo File $@
	@echo is out of date with respect to its SCCS file.
	@echo This file may be from an unresolved Teamware conflict.
	@echo This is also a symptom of a Teamware bringover/putback failure
	@echo in which SCCS files are updated but not checked out.
	@echo Check for other out of date files in your workspace.
	@echo "========================================================="
	@exit 666

.PHONY: default
