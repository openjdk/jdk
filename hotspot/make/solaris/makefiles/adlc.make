#
# Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

# This makefile (adlc.make) is included from the adlc.make in the
# build directories.
# It knows how to compile, link, and run the adlc.

include $(GAMMADIR)/make/$(Platform_os_family)/makefiles/rules.make

# #########################################################################

# OUTDIR must be the same as AD_Dir = $(GENERATED)/adfiles in top.make:
GENERATED = ../generated
OUTDIR  = $(GENERATED)/adfiles

ARCH = $(Platform_arch)
OS = $(Platform_os_family)

SOURCE.AD = $(OUTDIR)/$(OS)_$(Platform_arch_model).ad 

SOURCES.AD = $(GAMMADIR)/src/cpu/$(ARCH)/vm/$(Platform_arch_model).ad \
	     $(GAMMADIR)/src/os_cpu/$(OS)_$(ARCH)/vm/$(OS)_$(Platform_arch_model).ad 

EXEC	= $(OUTDIR)/adlc

# set VPATH so make knows where to look for source files
Src_Dirs_V += $(GAMMADIR)/src/share/vm/adlc
VPATH += $(Src_Dirs_V:%=%:)

# set INCLUDES for C preprocessor
Src_Dirs_I += $(GAMMADIR)/src/share/vm/adlc $(GENERATED)
INCLUDES += $(Src_Dirs_I:%=-I%)

# set flags for adlc compilation
CPPFLAGS = $(SYSDEFS) $(INCLUDES)

# Force assertions on.
CPPFLAGS += -DASSERT

ifndef USE_GCC
  # We need libCstd.so for adlc 
  CFLAGS += -library=Cstd -g
  LFLAGS += -library=Cstd -g
endif

# CFLAGS_WARN holds compiler options to suppress/enable warnings.
# Compiler warnings are treated as errors
ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 509), 1)
  CFLAGS_WARN = +w -errwarn
endif
CFLAGS += $(CFLAGS_WARN)

ifeq ("${Platform_compiler}", "sparcWorks")
# Enable the following CFLAGS addition if you need to compare the
# built ELF objects.
#
# The -g option makes static data global and the "-Qoption ccfe
# -xglobalstatic" option tells the compiler to not globalize static
# data using a unique globalization prefix. Instead force the use
# of a static globalization prefix based on the source filepath so
# the objects from two identical compilations are the same.
#CFLAGS += -Qoption ccfe -xglobalstatic
endif # Platform_compiler == sparcWorks

OBJECTNAMES = \
	adlparse.o \
	archDesc.o \
	arena.o \
	dfa.o \
	dict2.o \
	filebuff.o \
	forms.o \
	formsopt.o \
	formssel.o \
	main.o \
	adlc-opcodes.o \
	output_c.o \
	output_h.o \

OBJECTS = $(OBJECTNAMES:%=$(OUTDIR)/%)

GENERATEDNAMES = \
        ad_$(Platform_arch_model).cpp \
        ad_$(Platform_arch_model).hpp \
        ad_$(Platform_arch_model)_clone.cpp \
        ad_$(Platform_arch_model)_expand.cpp \
        ad_$(Platform_arch_model)_format.cpp \
        ad_$(Platform_arch_model)_gen.cpp \
        ad_$(Platform_arch_model)_misc.cpp \
        ad_$(Platform_arch_model)_peephole.cpp \
        ad_$(Platform_arch_model)_pipeline.cpp \
        adGlobals_$(Platform_arch_model).hpp \
        dfa_$(Platform_arch_model).cpp \

GENERATEDFILES = $(GENERATEDNAMES:%=$(OUTDIR)/%)

# #########################################################################

all: $(EXEC)

$(EXEC) : $(OBJECTS)
	@echo Making adlc
	$(QUIETLY) $(LINK_NOPROF.CC) -o $(EXEC) $(OBJECTS)

# Random dependencies:
$(OBJECTS): opcodes.hpp classes.hpp adlc.hpp adlcVMDeps.hpp adlparse.hpp archDesc.hpp arena.hpp dict2.hpp filebuff.hpp forms.hpp formsopt.hpp formssel.hpp

# The source files refer to ostream.h, which sparcworks calls iostream.h
$(OBJECTS): ostream.h

ostream.h :
	@echo >$@ '#include <iostream.h>'

dump:
	: OUTDIR=$(OUTDIR)
	: OBJECTS=$(OBJECTS)
	: products = $(GENERATEDFILES)

all: $(GENERATEDFILES)

$(GENERATEDFILES): refresh_adfiles

# Get a unique temporary directory name, so multiple makes can run in parallel.
# Note that product files are updated via "mv", which is atomic.
TEMPDIR := $(OUTDIR)/mktmp$(shell echo $$$$)

# Debuggable by default
CFLAGS += -g

# Pass -D flags into ADLC.
ADLCFLAGS += $(SYSDEFS)

# Note "+="; it is a hook so flags.make can add more flags, like -g or -DFOO.
ADLCFLAGS += -q -T

# Normally, debugging is done directly on the ad_<arch>*.cpp files.
# But -g will put #line directives in those files pointing back to <arch>.ad.
ADLCFLAGS += -g

ifdef LP64
ADLCFLAGS += -D_LP64
else
ADLCFLAGS += -U_LP64
endif

#
# adlc_updater is a simple sh script, under sccs control. It is
# used to selectively update generated adlc files. This should
# provide a nice compilation speed improvement.
#
ADLC_UPDATER_DIRECTORY = $(GAMMADIR)/make/$(OS)
ADLC_UPDATER = adlc_updater
$(ADLC_UPDATER): $(ADLC_UPDATER_DIRECTORY)/$(ADLC_UPDATER)
	$(QUIETLY) cp $< $@; chmod +x $@

# This action refreshes all generated adlc files simultaneously.
# The way it works is this:
# 1) create a scratch directory to work in.
# 2) if the current working directory does not have $(ADLC_UPDATER), copy it.
# 3) run the compiled adlc executable. This will create new adlc files in the scratch directory.
# 4) call $(ADLC_UPDATER) on each generated adlc file. It will selectively update changed or missing files.
# 5) If we actually updated any files, echo a notice.
#
refresh_adfiles: $(EXEC) $(SOURCE.AD) $(ADLC_UPDATER)
	@rm -rf $(TEMPDIR); mkdir $(TEMPDIR)
	$(QUIETLY) $(EXEC) $(ADLCFLAGS) $(SOURCE.AD) \
 -c$(TEMPDIR)/ad_$(Platform_arch_model).cpp -h$(TEMPDIR)/ad_$(Platform_arch_model).hpp -a$(TEMPDIR)/dfa_$(Platform_arch_model).cpp -v$(TEMPDIR)/adGlobals_$(Platform_arch_model).hpp \
	    || { rm -rf $(TEMPDIR); exit 1; }
	$(QUIETLY) ./$(ADLC_UPDATER) ad_$(Platform_arch_model).cpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) ad_$(Platform_arch_model).hpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) ad_$(Platform_arch_model)_clone.cpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) ad_$(Platform_arch_model)_expand.cpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) ad_$(Platform_arch_model)_format.cpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) ad_$(Platform_arch_model)_gen.cpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) ad_$(Platform_arch_model)_misc.cpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) ad_$(Platform_arch_model)_peephole.cpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) ad_$(Platform_arch_model)_pipeline.cpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) adGlobals_$(Platform_arch_model).hpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) ./$(ADLC_UPDATER) dfa_$(Platform_arch_model).cpp $(TEMPDIR) $(OUTDIR)
	$(QUIETLY) [ -f $(TEMPDIR)/made-change ] \
		|| echo "Rescanned $(SOURCE.AD) but encountered no changes."
	$(QUIETLY) rm -rf $(TEMPDIR)


# #########################################################################

$(SOURCE.AD): $(SOURCES.AD)
	$(QUIETLY) $(PROCESS_AD_FILES) $(SOURCES.AD) > $(SOURCE.AD)

#PROCESS_AD_FILES = cat
# Pass through #line directives, in case user enables -g option above:
PROCESS_AD_FILES = awk '{ \
    if (CUR_FN != FILENAME) { CUR_FN=FILENAME; NR_BASE=NR-1; need_lineno=1 } \
    if (need_lineno && $$0 !~ /\/\//) \
      { print "\n\n\#line " (NR-NR_BASE) " \"" FILENAME "\""; need_lineno=0 }; \
    print }'

$(OUTDIR)/%.o: %.cpp
	@echo Compiling $<
	$(QUIETLY) $(REMOVE_TARGET)
	$(QUIETLY) $(COMPILE.CC) -o $@ $< $(COMPILE_DONE)

# Some object files are given a prefix, to disambiguate
# them from objects of the same name built for the VM.
$(OUTDIR)/adlc-%.o: %.cpp
	@echo Compiling $<
	$(QUIETLY) $(REMOVE_TARGET)
	$(QUIETLY) $(COMPILE.CC) -o $@ $< $(COMPILE_DONE)

# #########################################################################

clean	:
	rm $(OBJECTS)

cleanall :
	rm $(OBJECTS) $(EXEC)

# #########################################################################

.PHONY: all dump refresh_adfiles clean cleanall
