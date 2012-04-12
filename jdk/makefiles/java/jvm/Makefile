#
# Copyright (c) 1995, 2011, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

BUILDDIR = ../..

include $(BUILDDIR)/common/Defs.gmk

JVMCFG = $(JVMCFG_DIR)/jvm.cfg

#
# How to install jvm.cfg.
#
ifeq ($(ZERO_BUILD), true)
JVMCFG_ARCH = zero
else
JVMCFG_ARCH = $(ARCH)
endif

ifeq ($(PLATFORM),macosx)
  JVMCFG_SRC=$(PLATFORM_SRC_MACOS)/bin/$(JVMCFG_ARCH)/jvm.cfg
  JVMCFG_DIR = $(LIBDIR)
else
  JVMCFG_SRC=$(PLATFORM_SRC)/bin/$(JVMCFG_ARCH)/jvm.cfg
  JVMCFG_DIR = $(LIBDIR)/$(LIBARCH)
endif

ifeq ($(ARCH_DATA_MODEL),32)
    # On 32 bit machines, we can have client and/or server libjvms installed.
    # Since the currently committed jvm.cfg expects clientANDserver, we need 
    # to patch the jvm.cfg when we have built only a client or only a server.
    # This should also support -kernel, -zero and -zeroshark.
    ifeq ($(JVM_VARIANTS),$(COMMA)client$(COMMA))
        # Create a patched jvm.cfg to use -client by default and alias -server to -client.
        $(JVMCFG)::
		$(MKDIR) -p $(JVMCFG_DIR)
		$(RM) -f $(JVMCFG)
		$(PRINTF) "-client KNOWN\n">$(JVMCFG)
		$(PRINTF) "-server IGNORE\n">>$(JVMCFG)
		$(PRINTF) "-hotspot ALIASED_TO -client\n">>$(JVMCFG)
		$(PRINTF) "-classic WARN\n">>$(JVMCFG)
		$(PRINTF) "-native ERROR\n">>$(JVMCFG)
		$(PRINTF) "-green ERROR\n">>$(JVMCFG)
    else
        ifeq ($(JVM_VARIANTS),$(COMMA)server$(COMMA))
            # Create a patched jvm.cfg to use -server by default and alias -client to -server.
            $(JVMCFG)::
		$(MKDIR) -p $(JVMCFG_DIR)
		$(RM) -f $(JVMCFG)
		$(PRINTF) "-server KNOWN\n">$(JVMCFG)
		$(PRINTF) "-client IGNORE\n">>$(JVMCFG)
		$(PRINTF) "-hotspot IGNORE\n">>$(JVMCFG)
		$(PRINTF) "-classic WARN\n">>$(JVMCFG)
		$(PRINTF) "-native ERROR\n">>$(JVMCFG)
		$(PRINTF) "-green ERROR\n">>$(JVMCFG)
        else
            # Use the default jvm.cfg for this 32 bit setup. 
            $(JVMCFG): $(JVMCFG_SRC)
		$(install-file)
        endif
    endif
else
    # Use the default jvm.cfg for this 64 bit setup.
    $(JVMCFG): $(JVMCFG_SRC)
	$(install-file)
endif

all: build

build: $(JVMCFG)

clean clobber:: 
	$(RM) $(JVMCFG)

