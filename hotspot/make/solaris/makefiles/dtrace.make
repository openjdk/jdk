#
# Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

# Rules to build jvm_db/dtrace, used by vm.make

# We build libjvm_dtrace/libjvm_db/dtrace for COMPILER1 and COMPILER2
# but not for CORE or KERNEL configurations.

ifneq ("${TYPE}", "CORE")
ifneq ("${TYPE}", "KERNEL")

ifdef USE_GCC

dtraceCheck:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled for gcc builds"

else

JVM_DB = libjvm_db
LIBJVM_DB = libjvm_db.so
LIBJVM_DB_G = libjvm$(G_SUFFIX)_db.so

JVM_DTRACE = jvm_dtrace
LIBJVM_DTRACE = libjvm_dtrace.so
LIBJVM_DTRACE_G = libjvm$(G_SUFFIX)_dtrace.so

JVMOFFS = JvmOffsets
JVMOFFS.o = $(JVMOFFS).o
GENOFFS = generate$(JVMOFFS)

DTRACE_SRCDIR = $(GAMMADIR)/src/os/$(Platform_os_family)/dtrace
DTRACE = dtrace
DTRACE.o = $(DTRACE).o

# to remove '-g' option which causes link problems
# also '-z nodefs' is used as workaround
GENOFFS_CFLAGS = $(shell echo $(CFLAGS) | sed -e 's/ -g / /g' -e 's/ -g0 / /g';)

ifdef LP64
DTRACE_OPTS = -64 -D_LP64
endif

# making libjvm_db

# Use mapfile with libjvm_db.so
LIBJVM_DB_MAPFILE = $(MAKEFILES_DIR)/mapfile-vers-jvm_db
LFLAGS_JVM_DB += $(MAPFLAG:FILENAME=$(LIBJVM_DB_MAPFILE))

# Use mapfile with libjvm_dtrace.so
LIBJVM_DTRACE_MAPFILE = $(MAKEFILES_DIR)/mapfile-vers-jvm_dtrace
LFLAGS_JVM_DTRACE += $(MAPFLAG:FILENAME=$(LIBJVM_DTRACE_MAPFILE))

ifdef USE_GCC
LFLAGS_JVM_DB += -D_REENTRANT $(PICFLAG)
LFLAGS_JVM_DTRACE += -D_REENTRANT $(PICFLAG)
else
LFLAGS_JVM_DB += -mt $(PICFLAG) -xnolib
LFLAGS_JVM_DTRACE += -mt $(PICFLAG) -xnolib -ldl
endif

ISA = $(subst i386,i486,$(shell isainfo -n))

# Making 64/libjvm_db.so: 64-bit version of libjvm_db.so which handles 32-bit libjvm.so
ifneq ("${ISA}","${BUILDARCH}")

XLIBJVM_DB = 64/$(LIBJVM_DB)
XLIBJVM_DB_G = 64/$(LIBJVM_DB_G)
XLIBJVM_DTRACE = 64/$(LIBJVM_DTRACE)
XLIBJVM_DTRACE_G = 64/$(LIBJVM_DTRACE_G)

$(XLIBJVM_DB): $(DTRACE_SRCDIR)/$(JVM_DB).c $(JVMOFFS).h $(LIBJVM_DB_MAPFILE)
	@echo Making $@
	$(QUIETLY) mkdir -p 64/ ; \
	$(CC) $(SYMFLAG) $(ARCHFLAG/$(ISA)) -D$(TYPE) -I. -I$(GENERATED) \
		$(SHARED_FLAG) $(LFLAGS_JVM_DB) -o $@ $(DTRACE_SRCDIR)/$(JVM_DB).c -lc
	[ -f $(XLIBJVM_DB_G) ] || { ln -s $(LIBJVM_DB) $(XLIBJVM_DB_G); }

$(XLIBJVM_DTRACE): $(DTRACE_SRCDIR)/$(JVM_DTRACE).c $(DTRACE_SRCDIR)/$(JVM_DTRACE).h $(LIBJVM_DTRACE_MAPFILE)
	@echo Making $@
	$(QUIETLY) mkdir -p 64/ ; \
	$(CC) $(SYMFLAG) $(ARCHFLAG/$(ISA)) -D$(TYPE) -I. \
		$(SHARED_FLAG) $(LFLAGS_JVM_DTRACE) -o $@ $(DTRACE_SRCDIR)/$(JVM_DTRACE).c -lc -lthread -ldoor
	[ -f $(XLIBJVM_DTRACE_G) ] || { ln -s $(LIBJVM_DTRACE) $(XLIBJVM_DTRACE_G); }

endif # ifneq ("${ISA}","${BUILDARCH}")

ifdef USE_GCC
LFLAGS_GENOFFS += -D_REENTRANT
else
LFLAGS_GENOFFS += -mt -xnolib -norunpath
endif

lib$(GENOFFS).so: $(DTRACE_SRCDIR)/$(GENOFFS).cpp $(DTRACE_SRCDIR)/$(GENOFFS).h \
                  $(LIBJVM.o)
	$(QUIETLY) $(CCC) $(CPPFLAGS) $(GENOFFS_CFLAGS) $(SHARED_FLAG) $(PICFLAG) \
		 $(LFLAGS_GENOFFS) -o $@ $(DTRACE_SRCDIR)/$(GENOFFS).cpp -lc

$(GENOFFS): $(DTRACE_SRCDIR)/$(GENOFFS)Main.c lib$(GENOFFS).so
	$(QUIETLY) $(LINK.CC) -z nodefs -o $@ $(DTRACE_SRCDIR)/$(GENOFFS)Main.c \
		./lib$(GENOFFS).so

CONDITIONALLY_UPDATE_JVMOFFS_TARGET = \
	cmp -s $@ $@.tmp; \
	case $$? in \
	0) rm -f $@.tmp;; \
	*) rm -f $@ && mv $@.tmp $@ && echo Updated $@;; \
	esac

# $@.tmp is created first to avoid an empty $(JVMOFFS).h if an error occurs.
$(JVMOFFS).h: $(GENOFFS)
	$(QUIETLY) LD_LIBRARY_PATH=. ./$(GENOFFS) -header > $@.tmp
	$(QUIETLY) $(CONDITIONALLY_UPDATE_JVMOFFS_TARGET)

$(JVMOFFS)Index.h: $(GENOFFS)
	$(QUIETLY) LD_LIBRARY_PATH=. ./$(GENOFFS) -index > $@.tmp
	$(QUIETLY)  $(CONDITIONALLY_UPDATE_JVMOFFS_TARGET)

$(JVMOFFS).cpp: $(GENOFFS) $(JVMOFFS).h $(JVMOFFS)Index.h
	$(QUIETLY) LD_LIBRARY_PATH=. ./$(GENOFFS) -table > $@.tmp
	$(QUIETLY) $(CONDITIONALLY_UPDATE_JVMOFFS_TARGET)

$(JVMOFFS.o): $(JVMOFFS).h $(JVMOFFS).cpp 
	$(QUIETLY) $(CCC) -c -I. -o $@ $(ARCHFLAG) -D$(TYPE) $(JVMOFFS).cpp

$(LIBJVM_DB): $(DTRACE_SRCDIR)/$(JVM_DB).c $(JVMOFFS.o) $(XLIBJVM_DB) $(LIBJVM_DB_MAPFILE)
	@echo Making $@
	$(QUIETLY) $(CC) $(SYMFLAG) $(ARCHFLAG) -D$(TYPE) -I. -I$(GENERATED) \
		$(SHARED_FLAG) $(LFLAGS_JVM_DB) -o $@ $(DTRACE_SRCDIR)/$(JVM_DB).c -lc
	[ -f $(LIBJVM_DB_G) ] || { ln -s $@ $(LIBJVM_DB_G); }

$(LIBJVM_DTRACE): $(DTRACE_SRCDIR)/$(JVM_DTRACE).c $(XLIBJVM_DTRACE) $(DTRACE_SRCDIR)/$(JVM_DTRACE).h $(LIBJVM_DTRACE_MAPFILE)
	@echo Making $@
	$(QUIETLY) $(CC) $(SYMFLAG) $(ARCHFLAG) -D$(TYPE) -I.  \
		$(SHARED_FLAG) $(LFLAGS_JVM_DTRACE) -o $@ $(DTRACE_SRCDIR)/$(JVM_DTRACE).c -lc -lthread -ldoor
	[ -f $(LIBJVM_DTRACE_G) ] || { ln -s $@ $(LIBJVM_DTRACE_G); }

$(DTRACE).d: $(DTRACE_SRCDIR)/hotspot.d $(DTRACE_SRCDIR)/hotspot_jni.d \
             $(DTRACE_SRCDIR)/hs_private.d $(DTRACE_SRCDIR)/jhelper.d
	$(QUIETLY) cat $^ > $@

DTraced_Files = ciEnv.o \
                classLoadingService.o \
                compileBroker.o \
                hashtable.o \
                instanceKlass.o \
                java.o \
                jni.o \
                jvm.o \
                memoryManager.o \
                nmethod.o \
                objectMonitor.o \
                runtimeService.o \
                sharedRuntime.o \
                synchronizer.o \
                thread.o \
                unsafe.o \
                vmThread.o \
                vmCMSOperations.o \
                vmPSOperations.o \
                vmGCOperations.o \

# Dtrace is available, so we build $(DTRACE.o)  
$(DTRACE.o): $(DTRACE).d $(JVMOFFS).h $(JVMOFFS)Index.h $(DTraced_Files)
	@echo Compiling $(DTRACE).d

	$(QUIETLY) $(DTRACE_PROG) $(DTRACE_OPTS) -C -I. -G -xlazyload -o $@ -s $(DTRACE).d \
     $(DTraced_Files) ||\
  STATUS=$$?;\
	if [ x"$$STATUS" = x"1" -a \
       x`uname -r` = x"5.10" -a \
       x`uname -p` = x"sparc" ]; then\
    echo "*****************************************************************";\
    echo "* If you are building server compiler, and the error message is ";\
    echo "* \"incorrect ELF machine type...\", you have run into solaris bug ";\
    echo "* 6213962, \"dtrace -G doesn't work on sparcv8+ object files\".";\
    echo "* Either patch/upgrade your system (>= S10u1_15), or set the ";\
    echo "* environment variable HOTSPOT_DISABLE_DTRACE_PROBES to disable ";\
    echo "* dtrace probes for this build.";\
    echo "*****************************************************************";\
  fi;\
  exit $$STATUS
  # Since some DTraced_Files are in LIBJVM.o and they are touched by this
  # command, and libgenerateJvmOffsets.so depends on LIBJVM.o, 'make' will
  # think it needs to rebuild libgenerateJvmOffsets.so and thus JvmOffsets*
  # files, but it doesn't, so we touch the necessary files to prevent later
  # recompilation. Note: we only touch the necessary files if they already
  # exist in order to close a race where an empty file can be created
  # before the real build rule is executed.
  # But, we can't touch the *.h files:  This rule depends
  # on them, and that would cause an infinite cycle of rebuilding.
  # Neither the *.h or *.ccp files need to be touched, since they have
  # rules which do not update them when the generator file has not
  # changed their contents.
	$(QUIETLY) if [ -f lib$(GENOFFS).so ]; then touch lib$(GENOFFS).so; fi
	$(QUIETLY) if [ -f $(GENOFFS) ]; then touch $(GENOFFS); fi
	$(QUIETLY) if [ -f $(JVMOFFS.o) ]; then touch $(JVMOFFS.o); fi

.PHONY: dtraceCheck

SYSTEM_DTRACE_H = /usr/include/dtrace.h
SYSTEM_DTRACE_PROG = /usr/sbin/dtrace
PATCH_DTRACE_PROG = /opt/SUNWdtrd/sbin/dtrace
systemDtraceFound := $(wildcard ${SYSTEM_DTRACE_PROG})
patchDtraceFound := $(wildcard ${PATCH_DTRACE_PROG})
systemDtraceHdrFound := $(wildcard $(SYSTEM_DTRACE_H))

ifneq ("$(systemDtraceHdrFound)", "") 
CFLAGS += -DHAVE_DTRACE_H
endif

ifneq ("$(patchDtraceFound)", "")
DTRACE_PROG=$(PATCH_DTRACE_PROG)
DTRACE_INCL=-I/opt/SUNWdtrd/include
else
ifneq ("$(systemDtraceFound)", "")
DTRACE_PROG=$(SYSTEM_DTRACE_PROG)
else

endif # ifneq ("$(systemDtraceFound)", "")
endif # ifneq ("$(patchDtraceFound)", "")

ifneq ("${DTRACE_PROG}", "")
ifeq ("${HOTSPOT_DISABLE_DTRACE_PROBES}", "")

DTRACE_OBJS = $(DTRACE.o) $(JVMOFFS.o)
CFLAGS += $(DTRACE_INCL) -DDTRACE_ENABLED
MAPFILE_DTRACE_OPT = $(MAPFILE_DTRACE)

dtraceCheck:

else # manually disabled

dtraceCheck:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled via environment variable"

endif # ifeq ("${HOTSPOT_DISABLE_DTRACE_PROBES}", "")

else # No dtrace program found

dtraceCheck:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled: not supported by system"

endif # ifneq ("${dtraceFound}", "")

endif # ifdef USE_GCC

else # KERNEL build

dtraceCheck:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled for KERNEL builds"

endif # ifneq ("${TYPE}", "KERNEL")

else # CORE build

dtraceCheck:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled for CORE builds"

endif # ifneq ("${TYPE}", "CORE")
