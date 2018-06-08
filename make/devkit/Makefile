#
# Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
# In addition to the makefiles, access to Oracle Linux installation
# media is required. This has been tested against Oracle Enterprise Linux
# 6.4.
#
# By default this Makefile will build a native toolchain for the current
# platform if called with something like this:
#
# make tars
#
# To build the full set of crosstools for additional platforms, use a command
# line looking like this:
#
# make cross_compile_target="aarch64-linux-gnu" BASE_OS=Fedora27
# or
# make cross_compile_target="arm-linux-gnueabihf" BASE_OS=Fedora27
#
# This is the makefile which iterates over all host and target platforms.
#

os := $(shell uname -o)
cpu := $(shell uname -p)

# Figure out what platform this is building on.
me := $(cpu)-$(if $(findstring Linux,$(os)),unknown-linux-gnu)

$(info Building on platform $(me))

#
# By default just build for the current platform, which is assumed to be Linux
#
ifeq ($(cross_compile_target), )
  platforms := $(me)
  host_platforms := $(platforms)
else
  platforms := $(cross_compile_target)
  host_platforms := $(me)
endif
target_platforms := $(platforms)
$(info host_platforms $(host_platforms))
$(info target_platforms $(target_platforms))

all compile : $(platforms)

ifeq (,$(SKIP_ME))
  $(foreach p,$(filter-out $(me),$(platforms)),$(eval $(p) : $$(me)))
endif

OUTPUT_ROOT = $(abspath ../../build/devkit)
RESULT = $(OUTPUT_ROOT)/result

submakevars = HOST=$@ BUILD=$(me) \
    RESULT=$(RESULT) PREFIX=$(RESULT)/$@ \
    OUTPUT_ROOT=$(OUTPUT_ROOT)
$(host_platforms) :
	@echo 'Building compilers for $@'
	@echo 'Targets: $(target_platforms)'
	for p in $(filter $@, $(target_platforms)) $(filter-out $@, $(target_platforms)); do \
	  $(MAKE) -f Tools.gmk download-rpms $(submakevars) TARGET=$$p && \
	  $(MAKE) -f Tools.gmk all $(submakevars) \
	      TARGET=$$p || exit 1 ; \
	done
	@echo 'Building ccache program for $@'
	$(MAKE) -f Tools.gmk ccache $(submakevars) TARGET=$@
	@echo 'All done"'

today := $(shell date +%Y%m%d)

define Mktar
  $(1)_tar = $$(RESULT)/sdk-$(1)-$$(today).tar.gz
  $$($(1)_tar) : PLATFORM = $(1)
  TARFILES += $$($(1)_tar)
  $$($(1)_tar) : $(1) $$(shell find $$(RESULT)/$(1))
endef

$(foreach p,$(host_platforms),$(eval $(call Mktar,$(p))))

tars : all $(TARFILES)
onlytars : $(TARFILES)
%.tar.gz :
	@echo 'Creating compiler package $@'
	cd $(RESULT)/$(PLATFORM) && tar -czf $@ *
	touch $@

clean :
	rm -rf $(addprefix ../../build/devkit/, result $(host_platforms))
dist-clean: clean
	rm -rf $(addprefix ../../build/devkit/, src download)

FORCE :
.PHONY : all compile tars $(configs) $(host_platforms) clean dist-clean
