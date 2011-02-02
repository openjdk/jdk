#
# Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

# Support for setting HS_CLOSED_PATH, required GAMMADIR and SRCARCH

CLOSED_DIR_EXISTS := $(shell                                \
  if [ -d $(GAMMADIR)/src/closed ] ; then                   \
    echo true;                                              \
  else                                                      \
    echo false;                                             \
  fi)

CLOSED_SRCARCH_DIR_EXISTS := $(shell                        \
  if [ -d $(GAMMADIR)/src/closed/cpu/$(SRCARCH)/vm ] ; then \
    echo true;                                              \
  else                                                      \
    echo false;                                             \
  fi)

ifeq ($(CLOSED_SRCARCH_DIR_EXISTS), true)
  HS_CLOSED_PATH=closed/
endif

# Support for setting HS_JNI_ARCH_SRC, requires HS_SRC_DIR and HS_ARCH

CLOSED_HS_ARCH_DIR_EXISTS := $(shell                        \
  if [ -d $(HS_SRC_DIR)/closed/cpu/$(HS_ARCH)/vm ] ; then   \
    echo true;                                              \
  else                                                      \
    echo false;                                             \
  fi)

ifeq ($(CLOSED_HS_ARCH_DIR_EXISTS), true)
  HS_JNI_ARCH_SRC=$(HS_SRC_DIR)/closed/cpu/$(HS_ARCH)/vm/jni_$(HS_ARCH).h
else
  HS_JNI_ARCH_SRC=$(HS_SRC_DIR)/cpu/$(HS_ARCH)/vm/jni_$(HS_ARCH).h
endif

