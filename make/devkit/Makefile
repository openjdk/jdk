#
# Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

################################################################################
#
# This Makefile, together with Tools.gmk, can be used to compile a set of
# gcc based cross compilation, portable, self contained packages, capable
# of building OpenJDK.
#
# By default this Makefile will build a native toolchain for the current
# platform if called with something like this:
#
# make tars BASE_OS=OL
#
# To build the full set of crosstools for additional platforms, use a command
# line looking like this:
#
# make TARGETS="aarch64-linux-gnu" BASE_OS=Fedora
# or
# make TARGETS="arm-linux-gnueabihf ppc64le-linux-gnu" BASE_OS=Fedora BASE_OS_VERSION=17
#
# to build several devkits for a specific OS version at once.
# You can find the final results under ../../build/devkit/result/<host>-to-<target>
#
# You may want the native toolchain to be used when compiling the cross
# compilation toolchains. To achieve this, first build the native toolchain,
# then add the bin directory from this build to the path when invoking this
# makefile again for cross compilation. Ex:
#
# PATH=$PWD/../../build/devkit/result/x86_64-linux-gnu-to-x86_64-linux-gnu/bin:$PATH \
#    make TARGETS="arm-linux-gnueabihf ppc64le-linux-gnu" BASE_OS=Fedora
#
# This is the makefile which iterates over all host and target platforms.
#

COMMA := ,

OS := $(shell uname -o)
CPU := $(shell uname -m)

# Figure out what platform this is building on.
ME := $(CPU)-$(if $(findstring Linux,$(OS)),linux-gnu)

$(info Building on platform $(ME))

#
# By default just build for the current platform, which is assumed to be Linux
#
ifeq ($(TARGETS), )
  PLATFORMS := $(ME)
  HOST_PLATFORMS := $(PLATFORMS)
else
  PLATFORMS := $(subst $(COMMA), , $(TARGETS))
  HOST_PLATFORMS := $(ME)
endif
TARGET_PLATFORMS := $(PLATFORMS)
$(info HOST_PLATFORMS $(HOST_PLATFORMS))
$(info TARGET_PLATFORMS $(TARGET_PLATFORMS))

all compile : $(PLATFORMS)

ifeq ($(SKIP_ME), )
  $(foreach p,$(filter-out $(ME),$(PLATFORMS)),$(eval $(p) : $$(ME)))
endif

OUTPUT_ROOT = $(abspath ../../build/devkit)
RESULT = $(OUTPUT_ROOT)/result

SUBMAKEVARS = HOST=$@ BUILD=$(ME) RESULT=$(RESULT) OUTPUT_ROOT=$(OUTPUT_ROOT)

$(HOST_PLATFORMS) :
	@echo 'Building compilers for $@'
	@echo 'Targets: $(TARGET_PLATFORMS)'
	for p in $(filter $@, $(TARGET_PLATFORMS)) $(filter-out $@, $(TARGET_PLATFORMS)); do \
	  $(MAKE) -f Tools.gmk download-rpms $(SUBMAKEVARS) \
              TARGET=$$p PREFIX=$(RESULT)/$@-to-$$p && \
	  $(MAKE) -f Tools.gmk all $(SUBMAKEVARS) \
              TARGET=$$p PREFIX=$(RESULT)/$@-to-$$p && \
	  $(MAKE) -f Tools.gmk ccache $(SUBMAKEVARS) \
              TARGET=$@ PREFIX=$(RESULT)/$@-to-$$p || exit 1 ; \
	done
	@echo 'All done"'

TODAY := $(shell date +%Y%m%d)

define Mktar
  $(1)-to-$(2)_tar = $$(RESULT)/sdk-$(1)-to-$(2)-$$(TODAY).tar.gz
  $$($(1)-to-$(2)_tar) : PLATFORM = $(1)-to-$(2)
  TARFILES += $$($(1)-to-$(2)_tar)
endef

$(foreach p,$(HOST_PLATFORMS),$(foreach t,$(TARGET_PLATFORMS),$(eval $(call Mktar,$(p),$(t)))))

tars : all $(TARFILES)
onlytars : $(TARFILES)
%.tar.gz :
	$(MAKE) -r -f Tars.gmk SRC_DIR=$(RESULT)/$(PLATFORM) TAR_FILE=$@

clean :
	rm -rf $(addprefix ../../build/devkit/, result $(HOST_PLATFORMS))
dist-clean: clean
	rm -rf $(addprefix ../../build/devkit/, src download)

FORCE :
.PHONY : all compile tars $(HOST_PLATFORMS) clean dist-clean
