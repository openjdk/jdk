#
# Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#  
#

# Targets are:
#   sparc:    Build the 32 bit sparc version in ./sparc
#   sparcv9:  Build the 64 bit sparcv9 version in ./sparcv9
#   i386:     Build the 32 bit i386 version in ./i386

.PHONY: sparc sparcv9 i386

ARCH_ORIG = $(shell uname -p)

C++    := CC
RM     := /usr/bin/rm
MKDIRS := /usr/bin/mkdir -p

CLASSES_DIR = ../../../../build/classes

ifeq "$(ARCH_ORIG)" "i386"
 ALL_TARGET = i386 $(filter amd64,$(shell isalist))
else
 ALL_TARGET = sparc sparcv9
endif

all:: $(ALL_TARGET)

javahomecheck::
	@if [ "x$(JAVA_HOME)" = "x" ] ; then \
	  echo You must set the environment variable JAVA_HOME before executing this Makefile ; \
	  exit 1 ; \
        fi

i386:: javahomecheck
	$(MKDIRS) $@
	@javah -classpath $(CLASSES_DIR) -jni sun.jvm.hotspot.debugger.proc.ProcDebuggerLocal
	CC -G -KPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/solaris saproc.cpp \
           -M mapfile -o $@/libsaproc.so -ldemangle
	CC -o $@/libsaproc_audit.so -G -Kpic -z defs saproc_audit.cpp -lmapmalloc -ldl -lc 

amd64:: javahomecheck
	$(MKDIRS) $@
	@javah -classpath $(CLASSES_DIR) -jni sun.jvm.hotspot.debugger.proc.ProcDebuggerLocal
	CC -G -KPIC -xarch=amd64 -I${JAVA_HOME}/include -I${JAVA_HOME}/include/solaris saproc.cpp \
           -M mapfile -o $@/libsaproc.so -ldemangle
	CC -xarch=amd64 -o $@/libsaproc_audit.so -G -Kpic -z defs saproc_audit.cpp -lmapmalloc -ldl -lc 

sparc:: javahomecheck
	$(MKDIRS) $@
	@javah -classpath $(CLASSES_DIR) -jni sun.jvm.hotspot.debugger.proc.ProcDebuggerLocal
	CC -G -KPIC -xarch=v8  -I${JAVA_HOME}/include -I${JAVA_HOME}/include/solaris saproc.cpp \
           -M mapfile -o $@/libsaproc.so -ldemangle
	CC -xarch=v8 -o $@/libsaproc_audit.so -G -Kpic -z defs saproc_audit.cpp -lmapmalloc -ldl -lc 

sparcv9:: javahomecheck
	$(MKDIRS) $@
	@javah -classpath $(CLASSES_DIR) -jni sun.jvm.hotspot.debugger.proc.ProcDebuggerLocal
	CC -G -KPIC -xarch=v9 -I${JAVA_HOME}/include -I${JAVA_HOME}/include/solaris saproc.cpp \
           -M mapfile -o $@/libsaproc.so -ldemangle
	CC -xarch=v9 -o $@/libsaproc_audit.so -G -Kpic -z defs saproc_audit.cpp -lmapmalloc -ldl -lc 

clean::
	$(RM) -rf sun_jvm_hotspot_debugger_proc_ProcDebuggerLocal.h
	$(RM) -rf sparc sparcv9 i386
