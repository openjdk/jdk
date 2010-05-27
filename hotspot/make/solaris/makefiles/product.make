#
# Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

# Sets make macros for making optimized version of Gamma VM
# (This is the "product", not the "release" version.)

# Compiler specific OPT_CFLAGS are passed in from gcc.make, sparcWorks.make
OPT_CFLAGS/DEFAULT= $(OPT_CFLAGS)
OPT_CFLAGS/BYFILE = $(OPT_CFLAGS/$@)$(OPT_CFLAGS/DEFAULT$(OPT_CFLAGS/$@))

# Workaround for a bug in dtrace.  If ciEnv::post_compiled_method_load_event()
# is inlined, the resulting dtrace object file needs a reference to this
# function, whose symbol name is too long for dtrace.  So disable inlining
# for this method for now. (fix this when dtrace bug 6258412 is fixed)
ifndef USE_GCC
OPT_CFLAGS/ciEnv.o = $(OPT_CFLAGS) -xinline=no%__1cFciEnvbFpost_compiled_method_load_event6MpnHnmethod__v_
endif

# (OPT_CFLAGS/SLOWER is also available, to alter compilation of buggy files)
ifeq ("${Platform_compiler}", "sparcWorks")

ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 509), 1)
# dtrace cannot handle tail call optimization (6672627, 6693876)
OPT_CFLAGS/jni.o = $(OPT_CFLAGS/DEFAULT) $(OPT_CCFLAGS/NO_TAIL_CALL_OPT)
endif # COMPILER_NUMERIC_REV >= 509

# Workaround SS11 bug 6345274 (all platforms) (Fixed in SS11 patch and SS12)
ifeq ($(COMPILER_REV_NUMERIC),508)
OPT_CFLAGS/ciTypeFlow.o = $(OPT_CFLAGS/O2)
endif # COMPILER_REV_NUMERIC == 508

endif # Platform_compiler == sparcWorks

# If you set HOTSPARC_GENERIC=yes, you disable all OPT_CFLAGS settings
CFLAGS$(HOTSPARC_GENERIC) += $(OPT_CFLAGS/BYFILE)
# Set the environment variable HOTSPARC_GENERIC to "true"
# to inhibit the effect of the previous line on CFLAGS.

# Linker mapfiles
# NOTE: inclusion of nonproduct mapfile not necessary; read it for details
ifdef USE_GCC
MAPFILE = $(GAMMADIR)/make/solaris/makefiles/mapfile-vers
else
MAPFILE = $(GAMMADIR)/make/solaris/makefiles/mapfile-vers \
          $(GAMMADIR)/make/solaris/makefiles/mapfile-vers-nonproduct

# This mapfile is only needed when compiling with dtrace support, 
# and mustn't be otherwise.
MAPFILE_DTRACE = $(GAMMADIR)/make/solaris/makefiles/mapfile-vers-$(TYPE)

REORDERFILE = $(GAMMADIR)/make/solaris/makefiles/reorder_$(TYPE)_$(BUILDARCH)
endif

# Don't strip in VM build; JDK build will strip libraries later
# LINK_LIB.CC/POST_HOOK += $(STRIP_LIB.CC/POST_HOOK)

G_SUFFIX =
SYSDEFS += -DPRODUCT
SYSDEFS += $(REORDER_FLAG)
VERSION = optimized
