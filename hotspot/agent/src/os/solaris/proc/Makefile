#
# Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

# Targets are:
#   sparc:    Build the 32 bit sparc version in ./sparc
#   sparcv9:  Build the 64 bit sparcv9 version in ./sparcv9
#   i386:     Build the 32 bit i386 version in ./i386

.PHONY: sparc sparcv9 i386 amd64

ARCH_ORIG = $(shell uname -p)

C++    := CC
RM     := /usr/bin/rm
MKDIRS := /usr/bin/mkdir -p

CLASSES_DIR = ../../../../build/classes
SAPROC_INCLUDES=-I${JAVA_HOME}/include -I${JAVA_HOME}/include/solaris
SADIS=../../../share/native/sadis.c

ifeq "$(ARCH_ORIG)" "i386"
 ALL_TARGET = i386 $(filter amd64,$(shell isalist))
else
 ALL_TARGET = sparc sparcv9
endif

CFLAGS/i386   =
CFLAGS/amd64  = -xarch=amd64
CFLAGS/sparc  = -xarch=v8
CFLAGS/sparv9 = -xarch=v9

all:: $(ALL_TARGET)

javahomecheck::
	@if [ "x$(JAVA_HOME)" = "x" ] ; then \
	  echo You must set the environment variable JAVA_HOME before executing this Makefile ; \
	  exit 1 ; \
        fi

i386 amd64 sparc sparcv9:: javahomecheck
	$(MKDIRS) $@
	@$(JAVA_HOME)/bin/javah -classpath $(CLASSES_DIR) -d $@ -jni sun.jvm.hotspot.asm.Disassembler sun.jvm.hotspot.debugger.proc.ProcDebuggerLocal
	CC $(CFLAGS/$@) -c -g -Kpic ${SAPROC_INCLUDES} -I$@ saproc.cpp -o $@/saproc.o
	cc $(CFLAGS/$@) -c -g -Kpic ${SAPROC_INCLUDES} -I$@ $(SADIS) -o $@/sadis.o
	CC $(CFLAGS/$@) -g -G -Kpic $@/saproc.o $@/sadis.o -M mapfile -o $@/libsaproc.so -ldemangle
	CC $(CFLAGS/$@) -o $@/libsaproc_audit.so -G -Kpic -z defs saproc_audit.cpp -lmapmalloc -ldl -lc 

clean::
	$(RM) -rf sparc sparcv9 i386 amd64
