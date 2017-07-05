#
# Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

##########################################################################################
#
# This Makefile, together with Tools.gmk, can be used to compile a set of
# gcc based cross compilation, portable, self contained packages, capable
# of building OpenJDK.
#
# In addition to the makefiles, access to Oracle Linux installation
# media is required. This has been tested against Oracle Enterprise Linux
# 5.5. Set variables RPM_DIR_x86_64 and RPM_DIR_i686 respectively to point
# to directory containing the RPMs.
#
# By default this Makefile will build crosstools for:
# * i686-unknown-linux-gnu
# * x86_64-unknown-linux-gnu
# The x86_64 version of the compilers will work in multi arch mode and will
# be able to compile 32bit binaries with the -m32 flag. This makes the
# explicit cross compiler for i686 somewhat redundant and is a known issue.
#
# To build the full set of crosstools, use a command line looking like this:
#
# make tars RPM_DIR_x86_64=/tmp/oel64-x86_64/Packages/ RPM_DIR_i686=/tmp/oel64-i686/Packages/
#
# To create a x86_64 package without the redundant i686 cross compiler, do
# like this:
#
# make tars platforms=x86_64-unknown-linux-gnu RPM_DIR_x86_64=/tmp/oel64-x86_64/Packages/ RPM_DIR_i686=/tmp/oel64-i686/Packages/

#
# Main makefile which iterates over all host and target platforms.
#

os := $(shell uname -o)
cpu := x86_64
#$(shell uname -p)

#
# This wrapper script can handle exactly these platforms
#
platforms := $(foreach p,x86_64 i686,$(p)-unknown-linux-gnu)
#platforms := $(foreach p,x86_64,$(p)-unknown-linux-gnu)

# Figure out what platform this is building on.
me := $(cpu)-$(if $(findstring Linux,$(os)),unknown-linux-gnu)

$(info Building on platform $(me))

all compile : $(platforms)

ifeq (,$(SKIP_ME))
  $(foreach p,$(filter-out $(me),$(platforms)),$(eval $(p) : $$(me)))
endif

OUTPUT_ROOT = $(abspath ../../build/devkit)
RESULT = $(OUTPUT_ROOT)/result

submakevars = HOST=$@ BUILD=$(me) \
    RESULT=$(RESULT) PREFIX=$(RESULT)/$@ \
    OUTPUT_ROOT=$(OUTPUT_ROOT)
$(platforms) :
	@echo 'Building compilers for $@'
	@echo 'Targets: $(platforms)'
	for p in $@ $(filter-out $@,$(platforms)); do \
	  $(MAKE) -f Tools.gmk all $(submakevars) \
	      TARGET=$$p || exit 1 ; \
	done
	@echo 'Building ccache program for $@'
	$(MAKE) -f Tools.gmk ccache $(submakevars) TARGET=$@
	@echo 'All done"'

$(foreach a,i686 x86_64,$(eval $(a) : $(filter $(a)%,$(platforms))))

ia32 : i686
today := $(shell date +%Y%m%d)


define Mktar
  $(1)_tar = $$(RESULT)/sdk-$(1)-$$(today).tar.gz
  $$($(1)_tar) : PLATFORM = $(1)
  TARFILES += $$($(1)_tar)
  $$($(1)_tar) : $(1) $$(shell find $$(RESULT)/$(1))
endef

$(foreach p,$(platforms),$(eval $(call Mktar,$(p))))

tars : all $(TARFILES)
onlytars : $(TARFILES)
%.tar.gz :
	@echo 'Creating compiler package $@'
	cd $(RESULT)/$(PLATFORM) && tar -czf $@ *
	touch $@

clean :
	rm -rf build result

FORCE :
.PHONY : $(configs) $(platforms)
