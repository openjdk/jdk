#
# Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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

# A list of object files built without the platform specific PIC flags, e.g.
# -fPIC on linux. Performance measurements show that by compiling GC related 
# code, we could significantly reduce the GC pause time on 32 bit Linux/Unix
# platforms. See 6454213 for more details.
include $(GAMMADIR)/make/scm.make

ifneq ($(OSNAME), windows)
  ifndef LP64
    PARTIAL_NONPIC=1
  endif
  PIC_ARCH = ppc arm
  ifneq ("$(filter $(PIC_ARCH),$(BUILDARCH))","")
    PARTIAL_NONPIC=0
  endif
  ifeq ($(PARTIAL_NONPIC),1)
    NONPIC_DIRS  = memory oops gc_implementation gc_interface 
    NONPIC_DIRS  := $(foreach dir,$(NONPIC_DIRS), $(GAMMADIR)/src/share/vm/$(dir))
    # Look for source files under NONPIC_DIRS
    NONPIC_FILES := $(foreach dir,$(NONPIC_DIRS),\
                      $(shell find $(dir) \( $(SCM_DIRS) \) -prune -o \
		      -name '*.cpp' -print))
    NONPIC_OBJ_FILES := $(notdir $(subst .cpp,.o,$(NONPIC_FILES)))
  endif
endif
