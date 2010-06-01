# Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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
# This Makefile is used to generate versions of the launcher program for
# use by the CustomLauncherTest.  The binaries are checked into SCCS
# so this Makefile is not used as part of test run.  It is provided to
# make rebuilding the launcher binaries a relatively painless activity.
#

#
# Edit the following to reflect the current build environment.
#
# Architecture being built (initially one of: solaris-sparc, solaris-i586,
# linux-i586.
#
PLATFORM=solaris
ARCH=sparc
OUTPUT_DIR =$(PLATFORM)-$(ARCH)

#
# Path to C compiler
#
# As of 6/1/06:
#	Solaris	CC=/java/devtools/$(ARCH)/SUNWspro/SS11/bin/cc
#	Linux	CC=/usr/bin/gcc
#
CC=/java/devtools/$(ARCH)/SUNWspro/SS11/bin/cc

#
# Include directory in JRE or JDK install
#
JINCLUDE=/java/re/jdk/1.6.0/latest/binaries/$(OUTPUT_DIR)/include

#
# PLATFORM dependent flags
#
# On pre-Solaris 10, -lthread is required.
#
ifeq ($(PLATFORM), solaris)
	PLATFORM_LIBS= -lthread
else
	PLATFORM_LIBS=
endif

#
# Targets
#
all: install

$(PLATFORM):
	mkdir $(OUTPUT_DIR) $(OUTPUT_DIR)/SCCS

install: $(PLATFORM)
	$(CC) -I$(JINCLUDE) -I$(JINCLUDE)/$(PLATFORM) \
		-o $(OUTPUT_DIR)/launcher launcher.c $(PLATFORM_LIBS) -ldl

clean:


clobber: clean
	rm $(OUTPUT_DIR)/launcher
