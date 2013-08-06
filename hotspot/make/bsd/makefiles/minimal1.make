#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

TYPE=MINIMAL1

# Force all variables to false, overriding any other
# setting that may have occurred in the makefiles. These
# can still be overridden by passing the variable as an
# argument to 'make'
INCLUDE_JVMTI := false
INCLUDE_FPROF := false
INCLUDE_VM_STRUCTS := false
INCLUDE_JNI_CHECK := false
INCLUDE_SERVICES := false
INCLUDE_MANAGEMENT := false
INCLUDE_ALL_GCS := false
INCLUDE_NMT := false
INCLUDE_TRACE := false
INCLUDE_CDS := false

CXXFLAGS += -DMINIMAL_JVM -DCOMPILER1 -DVMTYPE=\"Minimal\"
CFLAGS += -DMINIMAL_JVM -DCOMPILER1 -DVMTYPE=\"Minimal\"

Src_Dirs/MINIMAL1 = $(CORE_PATHS) $(COMPILER1_PATHS)

Src_Files_EXCLUDE/MINIMAL1 += $(COMPILER2_SPECIFIC_FILES) $(ZERO_SPECIFIC_FILES) $(SHARK_SPECIFIC_FILES) ciTypeFlow.cpp

-include $(HS_ALT_MAKE)/$(OSNAME)/makefiles/minimal1.make

.PHONY: $(HS_ALT_MAKE)/$(OSNAME)/makefiles/minimal1.make
