#
# Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

# Sets make macros for making profiled version of Gamma VM
# (It is also optimized.)

CFLAGS += -pg

# On x86 Solaris 2.6, 7, and 8 if LD_LIBRARY_PATH has /usr/lib in it then
# adlc linked with -pg puts out empty header files. To avoid linking adlc
# with -pg the profile flag is split out separately and used in rules.make

PROF_AOUT_FLAGS += -pg

SYSDEFS += $(REORDER_FLAG)

# To do a profiled build of the product, such as for generating the
# reordering file, set PROFILE_PRODUCT.  Otherwise the reordering file will
# contain references to functions which are not defined in the PRODUCT build.

ifdef PROFILE_PRODUCT
  SYSDEFS += -DPRODUCT
endif

LDNOMAP = true
