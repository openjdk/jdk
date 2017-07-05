#
# Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# Compiler-specific flags for sparcworks.

# tell make which C and C++ compilers to use
CC	= cc
CPP	= CC

# Note that this 'as' is an older version of the Sun Studio 'fbe', and will
#   use the older style options. The 'fbe' options will match 'cc' and 'CC'.
AS	= /usr/ccs/bin/as

NM	= /usr/ccs/bin/nm
NAWK    = /bin/nawk

REORDER_FLAG = -xF

# Check for the versions of C++ and C compilers ($CPP and $CC) used. 

# Get the last thing on the line that looks like x.x+ (x is a digit).
COMPILER_REV := \
$(shell $(CPP) -V 2>&1 | sed -n 's/^.*[ ,\t]C++[ ,\t]\([1-9]\.[0-9][0-9]*\).*/\1/p')
C_COMPILER_REV := \
$(shell $(CC) -V 2>&1 | sed -n 's/^.*[ ,\t]C[ ,\t]\([1-9]\.[0-9][0-9]*\).*/\1/p')

# Pick which compiler is validated
ifeq ($(JRE_RELEASE_VER),1.6.0)
  # Validated compiler for JDK6 is SS11 (5.8)
  VALIDATED_COMPILER_REV   := 5.8
  VALIDATED_C_COMPILER_REV := 5.8
else
  # Validated compiler for JDK7 is SS12 (5.9)
  VALIDATED_COMPILER_REV   := 5.9
  VALIDATED_C_COMPILER_REV := 5.9
endif

# Warning messages about not using the above validated version
ENFORCE_COMPILER_REV${ENFORCE_COMPILER_REV} := ${VALIDATED_COMPILER_REV}
ifneq (${COMPILER_REV},${ENFORCE_COMPILER_REV})
dummy_target_to_enforce_compiler_rev:=\
$(shell echo >&2 WARNING: You are using CC version ${COMPILER_REV} \
and should be using version ${ENFORCE_COMPILER_REV}. Set ENFORCE_COMPILER_REV=${COMPILER_REV} to avoid this warning.)
endif

ENFORCE_C_COMPILER_REV${ENFORCE_C_COMPILER_REV} := ${VALIDATED_C_COMPILER_REV}
ifneq (${C_COMPILER_REV},${ENFORCE_C_COMPILER_REV})
dummy_target_to_enforce_c_compiler_rev:=\
$(shell echo >&2 WARNING: You are using cc version ${C_COMPILER_REV} \
and should be using version ${ENFORCE_C_COMPILER_REV}. Set ENFORCE_C_COMPILER_REV=${C_COMPILER_REV} to avoid this warning.)
endif

COMPILER_REV_NUMERIC := $(shell echo $(COMPILER_REV) | awk -F. '{ print $$1 * 100 + $$2 }')

# Fail the build if __fabsf is used.  __fabsf exists only in Solaris 8 2/04
# and newer; objects with a dependency on this symbol will not run on older
# Solaris 8.
JVM_FAIL_IF_UNDEFINED = __fabsf

JVM_CHECK_SYMBOLS = $(NM) -u -p $(LIBJVM.o) | \
	$(NAWK) -v f="${JVM_FAIL_IF_UNDEFINED}" \
	     'BEGIN    { c=split(f,s); rc=0; } \
	      /:$$/     { file = $$1; } \
	      /[^:]$$/  { for(n=1;n<=c;++n) { \
			   if($$1==s[n]) { \
			     printf("JVM_CHECK_SYMBOLS: %s contains illegal symbol %s\n", \
				    file,$$1); \
			     rc=1; \
			   } \
		         } \
                       } \
	      END      { exit rc; }'

LINK_LIB.CC/PRE_HOOK += $(JVM_CHECK_SYMBOLS) || exit 1;

# Some interfaces (_lwp_create) changed with LP64 and Solaris 7
SOLARIS_7_OR_LATER := \
$(shell uname -r | awk -F. '{ if ($$2 >= 7) print "-DSOLARIS_7_OR_LATER"; }')
CFLAGS += ${SOLARIS_7_OR_LATER}

# New architecture options started in SS12 (5.9), we need both styles to build.
#   The older arch options for SS11 (5.8) or older and also for /usr/ccs/bin/as.
#   Note: default for 32bit sparc is now the same as v8plus, so the
#         settings below have changed all 32bit sparc builds to be v8plus.
ARCHFLAG_OLD/sparc   = -xarch=v8plus
ARCHFLAG_NEW/sparc   = -m32 -xarch=sparc
ARCHFLAG_OLD/sparcv9 = -xarch=v9
ARCHFLAG_NEW/sparcv9 = -m64 -xarch=sparc
ARCHFLAG_OLD/i486    =
ARCHFLAG_NEW/i486    = -m32
ARCHFLAG_OLD/amd64   = -xarch=amd64
ARCHFLAG_NEW/amd64   = -m64

# Select the ARCHFLAGs and other SS12 (5.9) options
ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 509), 1)
  ARCHFLAG/sparc   = $(ARCHFLAG_NEW/sparc)
  ARCHFLAG/sparcv9 = $(ARCHFLAG_NEW/sparcv9)
  ARCHFLAG/i486    = $(ARCHFLAG_NEW/i486)
  ARCHFLAG/amd64   = $(ARCHFLAG_NEW/amd64)
else
  ARCHFLAG/sparc   = $(ARCHFLAG_OLD/sparc)
  ARCHFLAG/sparcv9 = $(ARCHFLAG_OLD/sparcv9)
  ARCHFLAG/i486    = $(ARCHFLAG_OLD/i486)
  ARCHFLAG/amd64   = $(ARCHFLAG_OLD/amd64)
endif

# ARCHFLAGS for the current build arch
ARCHFLAG    = $(ARCHFLAG/$(BUILDARCH))
AS_ARCHFLAG = $(ARCHFLAG_OLD/$(BUILDARCH))

# Optional sub-directory in /usr/lib where BUILDARCH libraries are kept.
ISA_DIR=$(ISA_DIR/$(BUILDARCH))
ISA_DIR/sparcv9=/sparcv9
ISA_DIR/amd64=/amd64

# Use these to work around compiler bugs:
OPT_CFLAGS/SLOWER=-xO3
OPT_CFLAGS/O2=-xO2
OPT_CFLAGS/NOOPT=-xO1

#################################################
# Begin current (>=5.6) Forte compiler options #
#################################################

ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 506), 1)

ifeq ("${Platform_arch}", "sparc")

# We MUST allow data alignment of 4 for sparc (sparcv9 is ok at 8s)
ifndef LP64
CFLAGS += -xmemalign=4s
endif

endif

endif

#################################################
# Begin current (>=5.5) Forte compiler options #
#################################################

ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 505), 1)

CFLAGS     += $(ARCHFLAG)
AOUT_FLAGS += $(ARCHFLAG)
LIB_FLAGS  += $(ARCHFLAG)
LFLAGS     += $(ARCHFLAG)

ifeq ("${Platform_arch}", "sparc")

# Flags for Optimization

# [phh] Commented out pending verification that we do indeed want
#       to potentially bias against u1 and u3 targets.
#CFLAGS += -xchip=ultra2

OPT_CFLAGS=-xO4 $(EXTRA_OPT_CFLAGS)

endif # sparc

ifeq ("${Platform_arch_model}", "x86_32")

OPT_CFLAGS=-xtarget=pentium $(EXTRA_OPT_CFLAGS)

# UBE (CC 5.5) has bug 4923569 with -xO4
OPT_CFLAGS+=-xO3

endif # 32bit x86

ifeq ("${Platform_arch_model}", "x86_64")

ASFLAGS += $(AS_ARCHFLAG)
CFLAGS  += $(ARCHFLAG/amd64)
# this one seemed useless
LFLAGS_VM  += $(ARCHFLAG/amd64)
# this one worked
LFLAGS  += $(ARCHFLAG/amd64)
AOUT_FLAGS += $(ARCHFLAG/amd64)

# -xO3 is faster than -xO4 on specjbb with SS10 compiler
OPT_CFLAGS=-xO4 $(EXTRA_OPT_CFLAGS)

endif # 64bit x86

# Inline functions
CFLAGS += $(GAMMADIR)/src/os_cpu/solaris_${Platform_arch}/vm/solaris_${Platform_arch_model}.il

# no more exceptions
CFLAGS/NOEX=-features=no%except


# avoid compilation problems arising from fact that C++ compiler tries 
# to search for external template definition by just compiling additional
# source files in th same context
CFLAGS +=  -template=no%extdef

# Reduce code bloat by reverting back to 5.0 behavior for static initializers
CFLAGS += -features=no%split_init

# Use -D_Crun_inline_placement so we don't get references to 
#    __1c2n6FIpv_0_ or   void*operator new(unsigned,void*)
#  This avoids the hard requirement of the newer Solaris C++ runtime patches.
#  NOTE: This is an undocumented feature of the SS10 compiler. See 6306698.
CFLAGS += -D_Crun_inline_placement

# PIC is safer for SPARC, and is considerably slower
# a file foo.o which wants to compile -pic can set "PICFLAG/foo.o = -PIC"
PICFLAG         = -KPIC
PICFLAG/DEFAULT = $(PICFLAG)
# [RGV] Need to figure which files to remove to get link to work
#PICFLAG/BETTER  = -pic
PICFLAG/BETTER  = $(PICFLAG/DEFAULT)
PICFLAG/BYFILE  = $(PICFLAG/$@)$(PICFLAG/DEFAULT$(PICFLAG/$@))

# Use $(MAPFLAG:FILENAME=real_file_name) to specify a map file.
MAPFLAG = -M FILENAME

# Use $(SONAMEFLAG:SONAME=soname) to specify the intrinsic name of a shared obj
SONAMEFLAG = -h SONAME

# Build shared library
SHARED_FLAG = -G

# We don't need libCstd.so and librwtools7.so, only libCrun.so
CFLAGS += -library=%none
LFLAGS += -library=%none

LFLAGS += -mt

endif	# COMPILER_REV_NUMERIC >= 505

######################################
# End 5.5 Forte compiler options     #
######################################

######################################
# Begin 5.2 Forte compiler options   #
######################################

ifeq ($(COMPILER_REV_NUMERIC), 502)

CFLAGS     += $(ARCHFLAG)
AOUT_FLAGS += $(ARCHFLAG)
LIB_FLAGS  += $(ARCHFLAG)
LFLAGS     += $(ARCHFLAG)

ifeq ("${Platform_arch}", "sparc")

# Flags for Optimization

# [phh] Commented out pending verification that we do indeed want
#       to potentially bias against u1 and u3 targets.
#CFLAGS += -xchip=ultra2

ifdef LP64
# SC5.0 tools on v9 are flakey at -xO4
# [phh] Is this still true for 6.1?
OPT_CFLAGS=-xO3 $(EXTRA_OPT_CFLAGS)
else
OPT_CFLAGS=-xO4 $(EXTRA_OPT_CFLAGS)
endif

endif # sparc

ifeq ("${Platform_arch_model}", "x86_32")

OPT_CFLAGS=-xtarget=pentium $(EXTRA_OPT_CFLAGS)

# SC5.0 tools on x86 are flakey at -xO4
# [phh] Is this still true for 6.1?
OPT_CFLAGS+=-xO3

endif # 32bit x86

# no more exceptions
CFLAGS/NOEX=-noex

# Inline functions
CFLAGS += $(GAMMADIR)/src/os_cpu/solaris_${Platform_arch}/vm/solaris_${Platform_arch_model}.il

# Reduce code bloat by reverting back to 5.0 behavior for static initializers
CFLAGS += -Qoption ccfe -one_static_init

# PIC is safer for SPARC, and is considerably slower
# a file foo.o which wants to compile -pic can set "PICFLAG/foo.o = -PIC"
PICFLAG         = -KPIC
PICFLAG/DEFAULT = $(PICFLAG)
# [RGV] Need to figure which files to remove to get link to work
#PICFLAG/BETTER  = -pic
PICFLAG/BETTER  = $(PICFLAG/DEFAULT)
PICFLAG/BYFILE  = $(PICFLAG/$@)$(PICFLAG/DEFAULT$(PICFLAG/$@))

# Use $(MAPFLAG:FILENAME=real_file_name) to specify a map file.
MAPFLAG = -M FILENAME

# Use $(SONAMEFLAG:SONAME=soname) to specify the intrinsic name of a shared obj
SONAMEFLAG = -h SONAME

# Build shared library
SHARED_FLAG = -G

# Would be better if these weren't needed, since we link with CC, but
# at present removing them causes run-time errors
LFLAGS += -library=Crun
LIBS   += -library=Crun -lCrun

endif	# COMPILER_REV_NUMERIC == 502

##################################
# End 5.2 Forte compiler options #
##################################

##################################
# Begin old 5.1 compiler options #
##################################
ifeq ($(COMPILER_REV_NUMERIC), 501)

_JUNK_ := $(shell echo >&2 \
       "*** ERROR: sparkWorks.make incomplete for 5.1 compiler")
	@exit 1
endif
##################################
# End old 5.1 compiler options   #
##################################

##################################
# Begin old 5.0 compiler options #
##################################

ifeq	(${COMPILER_REV_NUMERIC}, 500)

# Had to hoist this higher apparently because of other changes. Must
# come before -xarch specification.
#  NOTE: native says optimize for the machine doing the compile, bad news.
CFLAGS += -xtarget=native

CFLAGS     += $(ARCHFLAG)
AOUT_FLAGS += $(ARCHFLAG)
LIB_FLAGS  += $(ARCHFLAG)
LFLAGS     += $(ARCHFLAG)

CFLAGS += -library=iostream
LFLAGS += -library=iostream  -library=Crun
LIBS += -library=iostream -library=Crun -lCrun

# Flags for Optimization
ifdef LP64
# SC5.0 tools on v9 are flakey at -xO4
OPT_CFLAGS=-xO3 $(EXTRA_OPT_CFLAGS)
else
OPT_CFLAGS=-xO4 $(EXTRA_OPT_CFLAGS)
endif

ifeq ("${Platform_arch}", "sparc")

CFLAGS += $(GAMMADIR)/src/os_cpu/solaris_sparc/vm/atomic_solaris_sparc.il

endif # sparc

ifeq ("${Platform_arch_model}", "x86_32")
OPT_CFLAGS=-xtarget=pentium $(EXTRA_OPT_CFLAGS)
ifeq ("${COMPILER_REV_NUMERIC}", "500")
# SC5.0 tools on x86 are flakey at -xO4
OPT_CFLAGS+=-xO3
else
OPT_CFLAGS+=-xO4
endif

CFLAGS += $(GAMMADIR)/src/os_cpu/solaris_x86/vm/solaris_x86_32.il

endif  # 32bit x86

# The following options run into misaligned ldd problem (raj)
#OPT_CFLAGS = -fast -O4 $(ARCHFLAG/sparc) -xchip=ultra

# no more exceptions
CFLAGS/NOEX=-noex

# PIC is safer for SPARC, and is considerably slower
# a file foo.o which wants to compile -pic can set "PICFLAG/foo.o = -PIC"
PICFLAG         = -PIC
PICFLAG/DEFAULT = $(PICFLAG)
# [RGV] Need to figure which files to remove to get link to work
#PICFLAG/BETTER  = -pic
PICFLAG/BETTER  = $(PICFLAG/DEFAULT)
PICFLAG/BYFILE  = $(PICFLAG/$@)$(PICFLAG/DEFAULT$(PICFLAG/$@))

endif	# COMPILER_REV_NUMERIC = 500

################################
# End old 5.0 compiler options #
################################

ifeq ("${COMPILER_REV_NUMERIC}", "402")
# 4.2 COMPILERS SHOULD NO LONGER BE USED
_JUNK_ := $(shell echo >&2 \
       "*** ERROR: SC4.2 compilers are not supported by this code base!")
	@exit 1
endif

# do not include shared lib path in a.outs
AOUT_FLAGS += -norunpath
LFLAGS_VM = -norunpath -z noversion

# need position-indep-code for shared libraries
# (ild appears to get errors on PIC code, so we'll try non-PIC for debug)
ifeq ($(PICFLAGS),DEFAULT)
VM_PICFLAG/LIBJVM  = $(PICFLAG/DEFAULT)
else
VM_PICFLAG/LIBJVM  = $(PICFLAG/BYFILE)
endif
VM_PICFLAG/AOUT    =

VM_PICFLAG = $(VM_PICFLAG/$(LINK_INTO))
CFLAGS += $(VM_PICFLAG)

# less dynamic linking (no PLTs, please)
#LIB_FLAGS += $(LINK_MODE)
# %%%%% despite -znodefs, -Bsymbolic gets link errors -- Rose

LINK_MODE = $(LINK_MODE/$(VERSION))
LINK_MODE/debug     =
LINK_MODE/optimized = -Bsymbolic -znodefs

# Have thread local errnos
ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 505), 1)
CFLAGS += -mt
else
CFLAGS += -D_REENTRANT
endif

ifdef CC_INTERP
# C++ Interpreter
CFLAGS += -DCC_INTERP
endif

# Flags for Debugging
DEBUG_CFLAGS = -g
FASTDEBUG_CFLAGS = -g0
# The -g0 setting allows the C++ frontend to inline, which is a big win.

# Special global options for SS12
ifeq ($(COMPILER_REV_NUMERIC),509)
  # There appears to be multiple issues with the new Dwarf2 debug format, so
  #   we tell the compiler to use the older 'stabs' debug format all the time.
  #   Note that this needs to be used in optimized compiles too to be 100%.
  #   This is a workaround for SS12 (5.9) bug 6694600
  CFLAGS += -xdebugformat=stabs
endif

# Enable the following CFLAGS additions if you need to compare the
# built ELF objects.
#
# The -g option makes static data global and the "-Qoption ccfe
# -xglobalstatic" option tells the compiler to not globalize static
# data using a unique globalization prefix. Instead force the use of
# a static globalization prefix based on the source filepath so the
# objects from two identical compilations are the same.
#DEBUG_CFLAGS += -Qoption ccfe -xglobalstatic
#FASTDEBUG_CFLAGS += -Qoption ccfe -xglobalstatic

ifeq	(${COMPILER_REV_NUMERIC}, 502)
COMPILER_DATE := $(shell $(CPP) -V 2>&1 | sed -n '/^.*[ ]C++[ ]\([1-9]\.[0-9][0-9]*\)/p' | awk '{ print $$NF; }')
ifeq	(${COMPILER_DATE}, 2001/01/31)
# disable -g0 in fastdebug since SC6.1 dated 2001/01/31 seems to be buggy
# use an innocuous value because it will get -g if it's empty
FASTDEBUG_CFLAGS = -c
endif
endif

# Uncomment or 'gmake CFLAGS_BROWSE=-sbfast' to get source browser information.
# CFLAGS_BROWSE	= -sbfast
CFLAGS		+= $(CFLAGS_BROWSE)

# ILD is gone as of SS11 (5.8), not supportted in SS10 (5.7)
ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \< 507), 1)
  # use ild when debugging (but when optimizing we want reproducible results)
  ILDFLAG = $(ILDFLAG/$(VERSION))
  ILDFLAG/debug     = -xildon
  ILDFLAG/optimized =
  AOUT_FLAGS += $(ILDFLAG)
endif

# Where to put the *.o files (a.out, or shared library)?
LINK_INTO = $(LINK_INTO/$(VERSION))
LINK_INTO/debug = LIBJVM
LINK_INTO/optimized = LIBJVM

# We link the debug version into the a.out because:
#  1. ild works on a.out but not shared libraries, and using ild
#     can cut rebuild times by 25% for small changes. (ILD is gone in SS11)
#  2. dbx cannot gracefully set breakpoints in shared libraries
#

# apply this setting to link into the shared library even in the debug version:
ifdef LP64
LINK_INTO = LIBJVM
else
#LINK_INTO = LIBJVM
endif

MCS	= /usr/ccs/bin/mcs
STRIP	= /usr/ccs/bin/strip

# Solaris platforms collect lots of redundant file-ident lines,
# to the point of wasting a significant percentage of file space.
# (The text is stored in ELF .comment sections, contributed by
# all "#pragma ident" directives in header and source files.)
# This command "compresses" the .comment sections simply by
# removing repeated lines.  The data can be extracted from
# binaries in the field by using "mcs -p libjvm.so" or the older
# command "what libjvm.so".
LINK_LIB.CC/POST_HOOK += $(MCS) -c $@ || exit 1;
# (The exit 1 is necessary to cause a build failure if the command fails and
# multiple commands are strung together, and the final semicolon is necessary
# since the hook must terminate itself as a valid command.)

# Also, strip debug and line number information (worth about 1.7Mb).
STRIP_LIB.CC/POST_HOOK = $(STRIP) -x $@ || exit 1;
# STRIP_LIB.CC/POST_HOOK is incorporated into LINK_LIB.CC/POST_HOOK
# in certain configurations, such as product.make.  Other configurations,
# such as debug.make, do not include the strip operation.
