#
# Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

# This makefile creates a build tree and lights off a build.
# You can go back into the build tree and perform rebuilds or
# incremental builds as desired. Be sure to reestablish
# environment variable settings for LD_LIBRARY_PATH and JAVA_HOME.

# The make process now relies on java and javac. These can be
# specified either implicitly on the PATH, by setting the
# (JDK-inherited) ALT_BOOTDIR environment variable to full path to a
# JDK in which bin/java and bin/javac are present and working (e.g.,
# /usr/local/java/jdk1.3/solaris), or via the (JDK-inherited)
# default BOOTDIR path value. Note that one of ALT_BOOTDIR
# or BOOTDIR has to be set. We do *not* search javac, javah, rmic etc.
# from the PATH.
#
# One can set ALT_BOOTDIR or BOOTDIR to point to a jdk that runs on
# an architecture that differs from the target architecture, as long
# as the bootstrap jdk runs under the same flavor of OS as the target
# (i.e., if the target is linux, point to a jdk that runs on a linux
# box).  In order to use such a bootstrap jdk, set the make variable
# REMOTE to the desired remote command mechanism, e.g.,
#
#    make REMOTE="rsh -l me myotherlinuxbox"

# Along with VM, Serviceability Agent (SA) is built for SA/JDI binding.
# JDI binding on SA produces two binaries:
#  1. sa-jdi.jar       - This is built before building libjvm.so
#                        Please refer to ./makefiles/sa.make
#  2. libsa.so         - Native library for SA - This is built after
#                        libjsig.so (signal interposition library)
#                        Please refer to ./makefiles/vm.make
# If $(GAMMADIR)/agent dir is not present, SA components are not built.

ifeq ($(GAMMADIR),)
include ../../make/defs.make
else
include $(GAMMADIR)/make/defs.make
endif
include $(GAMMADIR)/make/$(OSNAME)/makefiles/rules.make

ifndef CC_INTERP
  ifndef FORCE_TIERED
    FORCE_TIERED=1
  endif
endif
# C1 is not ported on ppc64, so we cannot build a tiered VM:
# Notice: after 8046471 ARCH will be 'ppc' for top-level ppc64 builds but
# 'ppc64' for HotSpot-only ppc64 builds. Need to detect both variants here!
ifneq (,$(findstring $(ARCH), ppc ppc64))
  ifeq ($(ARCH_DATA_MODEL), 64)
    FORCE_TIERED=0
  endif
endif

ifdef LP64
  ifeq ("$(filter $(LP64_ARCH),$(BUILDARCH))","")
    _JUNK_ := $(shell echo >&2 \
       $(OSNAME) $(ARCH) "*** ERROR: this platform does not support 64-bit compilers!")
	@exit 1
  endif
endif

# we need to set up LP64 correctly to satisfy sanity checks in adlc
ifneq ("$(filter $(LP64_ARCH),$(BUILDARCH))","")
  MFLAGS += " LP64=1 "
endif

# pass USE_SUNCC further, through MFLAGS
ifdef USE_SUNCC
  MFLAGS += " USE_SUNCC=1 "
endif

# The following renders pathnames in generated Makefiles valid on
# machines other than the machine containing the build tree.
#
# For example, let's say my build tree lives on /files12 on
# exact.east.sun.com.  This logic will cause GAMMADIR to begin with
# /net/exact/files12/...
#
# We only do this on SunOS variants, for a couple of reasons:
#  * It is extremely rare that source trees exist on other systems
#  * It has been claimed that the Linux automounter is flakey, so
#    changing GAMMADIR in a way that exercises the automounter could
#    prove to be a source of unreliability in the build process.
# Obviously, this Makefile is only relevant on SunOS boxes to begin
# with, but the SunOS conditionalization will make it easier to
# combine Makefiles in the future (assuming we ever do that).

ifeq ($(OSNAME),solaris)

  #   prepend current directory to relative pathnames.
  NEW_GAMMADIR :=                                    \
    $(shell echo $(GAMMADIR) |                       \
      sed -e "s=^\([^/].*\)=$(shell pwd)/\1="        \
     )
  unexport NEW_GAMMADIR

  # If NEW_GAMMADIR doesn't already start with "/net/":
  ifeq ($(strip $(filter /net/%,$(NEW_GAMMADIR))),)
    #   prepend /net/$(HOST)
    #   remove /net/$(HOST) if name already began with /home/
    #   remove /net/$(HOST) if name already began with /java/
    #   remove /net/$(HOST) if name already began with /lab/
    NEW_GAMMADIR :=                                     \
         $(shell echo $(NEW_GAMMADIR) |                 \
                 sed -e "s=^\(.*\)=/net/$(HOST)\1="     \
                     -e "s=^/net/$(HOST)/home/=/home/=" \
                     -e "s=^/net/$(HOST)/java/=/java/=" \
                     -e "s=^/net/$(HOST)/lab/=/lab/="   \
          )
    # Don't use the new value for GAMMADIR unless a file with the new
    # name actually exists.
    ifneq ($(wildcard $(NEW_GAMMADIR)),)
      GAMMADIR := $(NEW_GAMMADIR)
    endif
  endif

endif

# BUILDARCH is set to "zero" for Zero builds.  VARIANTARCH
# is used to give the build directories meaningful names.
VARIANTARCH = $(subst i386,i486,$(ZERO_LIBARCH))

# There is a (semi-) regular correspondence between make targets and actions:
#
#       Target          Tree Type       Build Dir
#
#       debug           compiler2       <os>_<arch>_compiler2/debug
#       fastdebug       compiler2       <os>_<arch>_compiler2/fastdebug
#       optimized       compiler2       <os>_<arch>_compiler2/optimized
#       product         compiler2       <os>_<arch>_compiler2/product
#
#       debug1          compiler1       <os>_<arch>_compiler1/debug
#       fastdebug1      compiler1       <os>_<arch>_compiler1/fastdebug
#       optimized1      compiler1       <os>_<arch>_compiler1/optimized
#       product1        compiler1       <os>_<arch>_compiler1/product
#
#       debugcore       core            <os>_<arch>_core/debug
#       fastdebugcore   core            <os>_<arch>_core/fastdebug
#       optimizedcore   core            <os>_<arch>_core/optimized
#       productcore     core            <os>_<arch>_core/product
#
#       debugzero       zero            <os>_<arch>_zero/debug
#       fastdebugzero   zero            <os>_<arch>_zero/fastdebug
#       optimizedzero   zero            <os>_<arch>_zero/optimized
#       productzero     zero            <os>_<arch>_zero/product
#
#       debugshark      shark           <os>_<arch>_shark/debug
#       fastdebugshark  shark           <os>_<arch>_shark/fastdebug
#       optimizedshark  shark           <os>_<arch>_shark/optimized
#       productshark    shark           <os>_<arch>_shark/product
#
#       fastdebugminimal1 minimal1      <os>_<arch>_minimal1/fastdebug
#       productminimal1   minimal1      <os>_<arch>_minimal1/product
#
# What you get with each target:
#
# debug*     - debug compile with asserts enabled
# fastdebug* - optimized compile, but with asserts enabled
# optimized* - optimized compile, no asserts
# product*   - the shippable thing:  optimized compile, no asserts, -DPRODUCT

# This target list needs to be coordinated with the usage message
# in the build.sh script:
TARGETS           = debug fastdebug optimized product

ifeq ($(findstring true, $(JVM_VARIANT_ZERO) $(JVM_VARIANT_ZEROSHARK)), true)
  SUBDIR_DOCS     = $(OSNAME)_$(VARIANTARCH)_docs
else
  SUBDIR_DOCS     = $(OSNAME)_$(BUILDARCH)_docs
endif
SUBDIRS_C1        = $(addprefix $(OSNAME)_$(BUILDARCH)_compiler1/,$(TARGETS))
SUBDIRS_C2        = $(addprefix $(OSNAME)_$(BUILDARCH)_compiler2/,$(TARGETS))
SUBDIRS_TIERED    = $(addprefix $(OSNAME)_$(BUILDARCH)_tiered/,$(TARGETS))
SUBDIRS_CORE      = $(addprefix $(OSNAME)_$(BUILDARCH)_core/,$(TARGETS))
SUBDIRS_ZERO      = $(addprefix $(OSNAME)_$(VARIANTARCH)_zero/,$(TARGETS))
SUBDIRS_SHARK     = $(addprefix $(OSNAME)_$(VARIANTARCH)_shark/,$(TARGETS))
SUBDIRS_MINIMAL1  = $(addprefix $(OSNAME)_$(BUILDARCH)_minimal1/,$(TARGETS))

TARGETS_C2        = $(TARGETS)
TARGETS_C1        = $(addsuffix 1,$(TARGETS))
TARGETS_TIERED    = $(addsuffix tiered,$(TARGETS))
TARGETS_CORE      = $(addsuffix core,$(TARGETS))
TARGETS_ZERO      = $(addsuffix zero,$(TARGETS))
TARGETS_SHARK     = $(addsuffix shark,$(TARGETS))
TARGETS_MINIMAL1 =  $(addsuffix minimal1,$(TARGETS))

BUILDTREE_MAKE    = $(GAMMADIR)/make/$(OSNAME)/makefiles/buildtree.make
BUILDTREE_VARS    = GAMMADIR=$(GAMMADIR) OS_FAMILY=$(OSNAME) SRCARCH=$(SRCARCH) BUILDARCH=$(BUILDARCH) LIBARCH=$(LIBARCH)
BUILDTREE_VARS   += HOTSPOT_VERSION_STRING=$(HOTSPOT_VERSION_STRING) VERSION_STRING=$(VERSION_STRING)
BUILDTREE_VARS   += ENABLE_FULL_DEBUG_SYMBOLS=$(ENABLE_FULL_DEBUG_SYMBOLS) OBJCOPY=$(OBJCOPY) STRIP_POLICY=$(STRIP_POLICY) ZIP_DEBUGINFO_FILES=$(ZIP_DEBUGINFO_FILES) ZIPEXE=$(ZIPEXE)

BUILDTREE         = $(MAKE) -f $(BUILDTREE_MAKE) $(BUILDTREE_VARS)

#-------------------------------------------------------------------------------

# Could make everything by default, but that would take a while.
all:
	@echo "Try '$(MAKE) <target> ...'  where <target> is one or more of"
	@echo "  $(TARGETS_C2)"
	@echo "  $(TARGETS_C1)"
	@echo "  $(TARGETS_CORE)"
	@echo "  $(TARGETS_ZERO)"
	@echo "  $(TARGETS_SHARK)"
	@echo "  $(TARGETS_MINIMAL1)"

checks: check_os_version check_j2se_version

# We do not want people accidentally building on old systems (e.g. Linux 2.2.x,
# Solaris 2.5.1, 2.6).
# Disable this check by setting DISABLE_HOTSPOT_OS_VERSION_CHECK=ok.

SUPPORTED_OS_VERSION = 2.4% 2.5% 2.6% 3% 4%
OS_VERSION := $(shell uname -r)
EMPTY_IF_NOT_SUPPORTED = $(filter $(SUPPORTED_OS_VERSION),$(OS_VERSION))

check_os_version:
ifeq ($(DISABLE_HOTSPOT_OS_VERSION_CHECK)$(EMPTY_IF_NOT_SUPPORTED),)
	$(QUIETLY) >&2 echo "*** This OS is not supported:" `uname -a`; exit 1;
endif

# jvmti.make requires XSLT (J2SE 1.4.x or newer):
XSLT_CHECK	= $(REMOTE) $(RUN.JAVAP) javax.xml.transform.TransformerFactory
# If not found then fail fast.
check_j2se_version:
	$(QUIETLY) if ! $(XSLT_CHECK) > /dev/null 2>&1; then \
	  $(REMOTE) $(RUN.JAVA) -version; \
	  echo "*** An XSLT processor (J2SE 1.4.x or newer) is required" \
	  "to bootstrap this build" 1>&2; \
	  exit 1; \
	fi

$(SUBDIRS_TIERED): $(BUILDTREE_MAKE)
	$(QUIETLY) $(MAKE) -f $(GAMMADIR)/make/$(OSNAME)/Makefile checks
	+$(BUILDTREE) VARIANT=tiered

$(SUBDIRS_C2): $(BUILDTREE_MAKE)
ifeq ($(FORCE_TIERED),1)
	$(QUIETLY) $(MAKE) -f $(GAMMADIR)/make/$(OSNAME)/Makefile checks
	+$(BUILDTREE) VARIANT=tiered FORCE_TIERED=1
else
	$(QUIETLY) $(MAKE) -f $(GAMMADIR)/make/$(OSNAME)/Makefile checks
	+$(BUILDTREE) VARIANT=compiler2
endif

$(SUBDIRS_C1): $(BUILDTREE_MAKE)
	$(QUIETLY) $(MAKE) -f $(GAMMADIR)/make/$(OSNAME)/Makefile checks
	+$(BUILDTREE) VARIANT=compiler1

$(SUBDIRS_CORE): $(BUILDTREE_MAKE)
	$(QUIETLY) $(MAKE) -f $(GAMMADIR)/make/$(OSNAME)/Makefile checks
	+$(BUILDTREE) VARIANT=core

$(SUBDIRS_ZERO): $(BUILDTREE_MAKE) platform_zero
	$(QUIETLY) $(MAKE) -f $(GAMMADIR)/make/$(OSNAME)/Makefile checks
	+$(BUILDTREE) VARIANT=zero VARIANTARCH=$(VARIANTARCH)

$(SUBDIRS_SHARK): $(BUILDTREE_MAKE) platform_zero
	$(QUIETLY) $(MAKE) -f $(GAMMADIR)/make/$(OSNAME)/Makefile checks
	+$(BUILDTREE) VARIANT=shark VARIANTARCH=$(VARIANTARCH)

$(SUBDIRS_MINIMAL1): $(BUILDTREE_MAKE)
	$(QUIETLY) $(MAKE) -f $(GAMMADIR)/make/$(OSNAME)/Makefile checks
	+$(BUILDTREE) VARIANT=minimal1


platform_zero: $(GAMMADIR)/make/$(OSNAME)/platform_zero.in
	$(SED) 's/@ZERO_ARCHDEF@/$(ZERO_ARCHDEF)/g;s/@ZERO_LIBARCH@/$(ZERO_LIBARCH)/g;' < $< > $@

# Define INSTALL=y at command line to automatically copy JVM into JAVA_HOME

$(TARGETS_C2):  $(SUBDIRS_C2)
	cd $(OSNAME)_$(BUILDARCH)_compiler2/$@ && $(MAKE) $(MFLAGS)
ifdef INSTALL
	cd $(OSNAME)_$(BUILDARCH)_compiler2/$@ && $(MAKE) $(MFLAGS) install
endif

$(TARGETS_TIERED):  $(SUBDIRS_TIERED)
	cd $(OSNAME)_$(BUILDARCH)_tiered/$(patsubst %tiered,%,$@) && $(MAKE) $(MFLAGS)
ifdef INSTALL
	cd $(OSNAME)_$(BUILDARCH)_tiered/$(patsubst %tiered,%,$@) && $(MAKE) $(MFLAGS) install
endif

$(TARGETS_C1):  $(SUBDIRS_C1)
	cd $(OSNAME)_$(BUILDARCH)_compiler1/$(patsubst %1,%,$@) && $(MAKE) $(MFLAGS)
ifdef INSTALL
	cd $(OSNAME)_$(BUILDARCH)_compiler1/$(patsubst %1,%,$@) && $(MAKE) $(MFLAGS) install
endif

$(TARGETS_CORE):  $(SUBDIRS_CORE)
	cd $(OSNAME)_$(BUILDARCH)_core/$(patsubst %core,%,$@) && $(MAKE) $(MFLAGS)
ifdef INSTALL
	cd $(OSNAME)_$(BUILDARCH)_core/$(patsubst %core,%,$@) && $(MAKE) $(MFLAGS) install
endif

$(TARGETS_ZERO):  $(SUBDIRS_ZERO)
	cd $(OSNAME)_$(VARIANTARCH)_zero/$(patsubst %zero,%,$@) && $(MAKE) $(MFLAGS)
ifdef INSTALL
	cd $(OSNAME)_$(VARIANTARCH)_zero/$(patsubst %zero,%,$@) && $(MAKE) $(MFLAGS) install
endif

$(TARGETS_SHARK):  $(SUBDIRS_SHARK)
	cd $(OSNAME)_$(VARIANTARCH)_shark/$(patsubst %shark,%,$@) && $(MAKE) $(MFLAGS)
ifdef INSTALL
	cd $(OSNAME)_$(VARIANTARCH)_shark/$(patsubst %shark,%,$@) && $(MAKE) $(MFLAGS) install
endif

$(TARGETS_MINIMAL1):  $(SUBDIRS_MINIMAL1)
	cd $(OSNAME)_$(BUILDARCH)_minimal1/$(patsubst %minimal1,%,$@) && $(MAKE) $(MFLAGS)
ifdef INSTALL
	cd $(OSNAME)_$(BUILDARCH)_minimal1/$(patsubst %minimal1,%,$@) && $(MAKE) $(MFLAGS) install
endif

# Just build the tree, and nothing else:
tree:      $(SUBDIRS_C2)
tree1:     $(SUBDIRS_C1)
treecore:  $(SUBDIRS_CORE)
treezero:  $(SUBDIRS_ZERO)
treeshark: $(SUBDIRS_SHARK)
treeminimal1: $(SUBDIRS_MINIMAL1)

# Doc target.  This is the same for all build options.
#     Hence create a docs directory beside ...$(ARCH)_[...]
# We specify 'BUILD_FLAVOR=product' so that the proper
# ENABLE_FULL_DEBUG_SYMBOLS value is used.
docs: checks
	$(QUIETLY) mkdir -p $(SUBDIR_DOCS)
	$(MAKE) -f $(GAMMADIR)/make/$(OSNAME)/makefiles/jvmti.make $(MFLAGS) $(BUILDTREE_VARS) JvmtiOutDir=$(SUBDIR_DOCS) BUILD_FLAVOR=product jvmtidocs

# Synonyms for win32-like targets.
compiler2:  debug product

compiler1:  debug1 product1

core: debugcore productcore

zero: debugzero productzero

shark: debugshark productshark

warn_jvmg_deprecated:
	echo "Warning: The jvmg target has been replaced with debug"
	echo "Warning: Please update your usage"

jvmg: warn_jvmg_deprecated debug

jvmg1: warn_jvmg_deprecated debug1

jvmgcore: warn_jvmg_deprecated debugcore

jvmgzero: warn_jvmg_deprecated debugzero

jvmgshark: warn_jvmg_deprecated debugshark

clean_docs:
	rm -rf $(SUBDIR_DOCS)

clean_compiler1 clean_compiler2 clean_core clean_zero clean_shark clean_minimal1:
	rm -rf $(OSNAME)_$(BUILDARCH)_$(subst clean_,,$@)

clean:  clean_compiler2 clean_compiler1 clean_core clean_zero clean_shark clean_minimal1 clean_docs

include $(GAMMADIR)/make/cscope.make

#
# Include alternate Makefile if it exists.
#
-include $(HS_ALT_MAKE)/$(OSNAME)/Makefile.make

#-------------------------------------------------------------------------------

.PHONY: $(TARGETS_C2) $(TARGETS_C1) $(TARGETS_CORE) $(TARGETS_ZERO) $(TARGETS_SHARK) $(TARGETS_MINIMAL1)
.PHONY: tree tree1 treecore treezero treeshark
.PHONY: all compiler1 compiler2 core zero shark
.PHONY: clean clean_compiler1 clean_compiler2 clean_core clean_zero clean_shark docs clean_docs
.PHONY: checks check_os_version check_j2se_version
.PHONY: $(HS_ALT_MAKE)/$(OSNAME)/Makefile.make

.NOTPARALLEL:
