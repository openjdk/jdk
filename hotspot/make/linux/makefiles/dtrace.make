#
# Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
# Copyright (c) 2012 Red Hat, Inc.
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

# Linux does not build jvm_db
LIBJVM_DB =

# Only OPENJDK builds test and support SDT probes currently.
ifndef OPENJDK
REASON = "This JDK does not support SDT probes"
else

# We need a recent GCC for the default
ifeq "$(shell expr \( $(CC_VER_MAJOR) \>= 4 \) \& \( $(CC_VER_MINOR) \>= 4 \) )" "0"
REASON = "gcc version is too old"
else

# But it does have a SystemTap dtrace compatible sys/sdt.h
ifneq ($(ALT_SDT_H),)
  SDT_H_FILE = $(ALT_SDT_H)
else
  SDT_H_FILE = /usr/include/sys/sdt.h
endif

DTRACE_ENABLED = $(shell test -f $(SDT_H_FILE) && echo $(SDT_H_FILE))
REASON = "$(SDT_H_FILE) not found"

endif # GCC version
endif # OPENJDK


DTRACE_COMMON_SRCDIR = $(GAMMADIR)/src/os/posix/dtrace
DTRACE_PROG = dtrace
DtraceOutDir = $(GENERATED)/dtracefiles

$(DtraceOutDir):
	mkdir $(DtraceOutDir)

$(DtraceOutDir)/hotspot.h: $(DTRACE_COMMON_SRCDIR)/hotspot.d | $(DtraceOutDir)
	$(QUIETLY) $(DTRACE_PROG) $(DTRACE_OPTS) -C -I. -h -o $@ -s $(DTRACE_COMMON_SRCDIR)/hotspot.d

$(DtraceOutDir)/hotspot_jni.h: $(DTRACE_COMMON_SRCDIR)/hotspot_jni.d | $(DtraceOutDir)
	$(QUIETLY) $(DTRACE_PROG) $(DTRACE_OPTS) -C -I. -h -o $@ -s $(DTRACE_COMMON_SRCDIR)/hotspot_jni.d

$(DtraceOutDir)/hs_private.h: $(DTRACE_COMMON_SRCDIR)/hs_private.d | $(DtraceOutDir)
	$(QUIETLY) $(DTRACE_PROG) $(DTRACE_OPTS) -C -I. -h -o $@ -s $(DTRACE_COMMON_SRCDIR)/hs_private.d

ifneq ($(DTRACE_ENABLED),)
CFLAGS += -DDTRACE_ENABLED
dtrace_gen_headers: $(DtraceOutDir)/hotspot.h $(DtraceOutDir)/hotspot_jni.h $(DtraceOutDir)/hs_private.h
else
dtrace_gen_headers:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled: $(REASON)"
endif

# Phony target used in vm.make build target to check whether enabled.
ifeq ($(DTRACE_ENABLED),)
dtraceCheck:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled: $(REASON)"
else
dtraceCheck:
endif

.PHONY: dtrace_gen_headers dtraceCheck

# It doesn't support HAVE_DTRACE_H though.

