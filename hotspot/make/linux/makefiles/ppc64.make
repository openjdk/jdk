#
# Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
# Copyright 2012, 2013 SAP AG. All rights reserved.
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

# make c code know it is on a 64 bit platform.
CFLAGS += -D_LP64=1

ifeq ($(origin OPENJDK_TARGET_CPU_ENDIAN),undefined)
  # This can happen during hotspot standalone build. Set endianness from
  # uname. We assume build and target machines are the same.
  OPENJDK_TARGET_CPU_ENDIAN:=$(if $(filter ppc64le,$(shell uname -m)),little,big)
endif

ifeq ($(filter $(OPENJDK_TARGET_CPU_ENDIAN),big little),)
  $(error OPENJDK_TARGET_CPU_ENDIAN value should be 'big' or 'little')
endif

ifeq ($(OPENJDK_TARGET_CPU_ENDIAN),big)
  # fixes `relocation truncated to fit' error for gcc 4.1.
  CFLAGS += -mminimal-toc

  # finds use ppc64 instructions, but schedule for power5
  CFLAGS += -mcpu=powerpc64 -mtune=power5 -minsert-sched-nops=regroup_exact -mno-multiple -mno-string
else
  # Little endian machine uses ELFv2 ABI.
  CFLAGS += -DVM_LITTLE_ENDIAN -DABI_ELFv2

  # Use Power8, this is the first CPU to support PPC64 LE with ELFv2 ABI.
  CFLAGS += -mcpu=power7 -mtune=power8 -minsert-sched-nops=regroup_exact -mno-multiple -mno-string
endif
