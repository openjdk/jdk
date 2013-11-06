#
# Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
# but not for CORE configuration.

ifneq ("${TYPE}", "CORE")

ifeq ($(OS_VENDOR), Darwin)
# we build dtrace for macosx using USDT2 probes

DtraceOutDir = $(GENERATED)/dtracefiles

# Bsd does not build libjvm_db, does not compile on macosx
# disabled in build: rule in vm.make
JVM_DB = libjvm_db
LIBJVM_DB = libjvm_db.dylib

LIBJVM_DB_DEBUGINFO   = libjvm_db.dylib.dSYM
LIBJVM_DB_DIZ         = libjvm_db.diz

JVM_DTRACE = jvm_dtrace
LIBJVM_DTRACE = libjvm_dtrace.dylib

LIBJVM_DTRACE_DEBUGINFO   = libjvm_dtrace.dylib.dSYM
LIBJVM_DTRACE_DIZ         = libjvm_dtrace.diz

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
DTRACE_OPTS = -D_LP64
endif

# making libjvm_db

# Use mapfile with libjvm_db.so
LIBJVM_DB_MAPFILE = # no mapfile for usdt2 # $(MAKEFILES_DIR)/mapfile-vers-jvm_db
#LFLAGS_JVM_DB += $(MAPFLAG:FILENAME=$(LIBJVM_DB_MAPFILE))

# Use mapfile with libjvm_dtrace.so
LIBJVM_DTRACE_MAPFILE = # no mapfile for usdt2 # $(MAKEFILES_DIR)/mapfile-vers-jvm_dtrace
#LFLAGS_JVM_DTRACE += $(MAPFLAG:FILENAME=$(LIBJVM_DTRACE_MAPFILE))

LFLAGS_JVM_DB += $(PICFLAG) # -D_REENTRANT
LFLAGS_JVM_DTRACE += $(PICFLAG) # -D_REENTRANT

ISA = $(subst i386,i486,$(BUILDARCH))

# Making 64/libjvm_db.so: 64-bit version of libjvm_db.so which handles 32-bit libjvm.so
ifneq ("${ISA}","${BUILDARCH}")

XLIBJVM_DIR = 64
XLIBJVM_DB = $(XLIBJVM_DIR)/$(LIBJVM_DB)
XLIBJVM_DTRACE = $(XLIBJVM_DIR)/$(LIBJVM_DTRACE)
XARCH = $(subst sparcv9,v9,$(shell echo $(ISA)))

XLIBJVM_DB_DEBUGINFO       = $(XLIBJVM_DIR)/$(LIBJVM_DB_DEBUGINFO)
XLIBJVM_DB_DIZ             = $(XLIBJVM_DIR)/$(LIBJVM_DB_DIZ)
XLIBJVM_DTRACE_DEBUGINFO   = $(XLIBJVM_DIR)/$(LIBJVM_DTRACE_DEBUGINFO)
XLIBJVM_DTRACE_DIZ         = $(XLIBJVM_DIR)/$(LIBJVM_DTRACE_DIZ)

$(XLIBJVM_DB): $(DTRACE_SRCDIR)/$(JVM_DB).c $(JVMOFFS).h $(LIBJVM_DB_MAPFILE)
	@echo Making $@
	$(QUIETLY) mkdir -p $(XLIBJVM_DIR) ; \
	$(CC) $(SYMFLAG) -xarch=$(XARCH) -D$(TYPE) -I. -I$(GENERATED) \
		$(SHARED_FLAG) $(LFLAGS_JVM_DB) -o $@ $(DTRACE_SRCDIR)/$(JVM_DB).c #-lc
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(OS_VENDOR), Darwin)
	$(DSYMUTIL) $@
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
        # Do this part in the $(XLIBJVM_DIR) subdir so $(XLIBJVM_DIR)
        # is not in the archived name:
	( cd $(XLIBJVM_DIR) && $(ZIPEXE) -q -r -y $(LIBJVM_DB_DIZ) $(LIBJVM_DB_DEBUGINFO) )
	$(RM) -r $(XLIBJVM_DB_DEBUGINFO)
    endif
  else
	$(QUIETLY) $(OBJCOPY) --only-keep-debug $@ $(XLIBJVM_DB_DEBUGINFO)
        # Do this part in the $(XLIBJVM_DIR) subdir so $(XLIBJVM_DIR)
        # is not in the link name:
        $(QUIETLY) ( cd $(XLIBJVM_DIR) && $(OBJCOPY) --add-gnu-debuglink=$(LIBJVM_DB_DEBUGINFO) $(LIBJVM_DB) )
    ifeq ($(STRIP_POLICY),all_strip)
	$(QUIETLY) $(STRIP) $@
    else
      ifeq ($(STRIP_POLICY),min_strip)
	$(QUIETLY) $(STRIP) -x $@
      # implied else here is no stripping at all
      endif
    endif
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
        # Do this part in the $(XLIBJVM_DIR) subdir so $(XLIBJVM_DIR)
        # is not in the archived name:
	( cd $(XLIBJVM_DIR) && $(ZIPEXE) -q -y $(LIBJVM_DB_DIZ) $(LIBJVM_DB_DEBUGINFO) )
	$(RM) $(XLIBJVM_DB_DEBUGINFO)
    endif
  endif
endif

$(XLIBJVM_DTRACE): $(DTRACE_SRCDIR)/$(JVM_DTRACE).c $(DTRACE_SRCDIR)/$(JVM_DTRACE).h $(LIBJVM_DTRACE_MAPFILE)
	@echo Making $@
	$(QUIETLY) mkdir -p $(XLIBJVM_DIR) ; \
	$(CC) $(SYMFLAG) -xarch=$(XARCH) -D$(TYPE) -I. \
		$(SHARED_FLAG) $(LFLAGS_JVM_DTRACE) -o $@ $(DTRACE_SRCDIR)/$(JVM_DTRACE).c #-lc -lthread -ldoor
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(OS_VENDOR), Darwin)
	$(DSYMUTIL) $@
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
        # Do this part in the $(XLIBJVM_DIR) subdir so $(XLIBJVM_DIR)
        # is not in the archived name:
	( cd $(XLIBJVM_DIR) && $(ZIPEXE) -q -r -y $(LIBJVM_DTRACE_DIZ) $(LIBJVM_DTRACE_DEBUGINFO) )
	$(RM) -r $(XLIBJVM_DTRACE_DEBUGINFO)
    endif
  else
	$(QUIETLY) $(OBJCOPY) --only-keep-debug $@ $(XLIBJVM_DTRACE_DEBUGINFO)
        # Do this part in the $(XLIBJVM_DIR) subdir so $(XLIBJVM_DIR)
        # is not in the link name:
	( cd $(XLIBJVM_DIR) && $(OBJCOPY) --add-gnu-debuglink=$(LIBJVM_DTRACE_DEBUGINFO) $(LIBJVM_DTRACE) )
    ifeq ($(STRIP_POLICY),all_strip)
	$(QUIETLY) $(STRIP) $@
    else
      ifeq ($(STRIP_POLICY),min_strip)
	$(QUIETLY) $(STRIP) -x $@
      # implied else here is no stripping at all
      endif
    endif
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
        # Do this part in the $(XLIBJVM_DIR) subdir so $(XLIBJVM_DIR)
        # is not in the archived name:
	( cd $(XLIBJVM_DIR) && $(ZIPEXE) -q -y $(LIBJVM_DTRACE_DIZ) $(LIBJVM_DTRACE_DEBUGINFO) )
	$(RM) $(XLIBJVM_DTRACE_DEBUGINFO)
    endif
  endif
endif

endif # ifneq ("${ISA}","${BUILDARCH}")

LFLAGS_GENOFFS += -L.

lib$(GENOFFS).dylib: $(DTRACE_SRCDIR)/$(GENOFFS).cpp $(DTRACE_SRCDIR)/$(GENOFFS).h \
                  $(LIBJVM.o)
	$(QUIETLY) $(CXX) $(CXXFLAGS) $(GENOFFS_CFLAGS) $(SHARED_FLAG) $(PICFLAG) \
		 $(LFLAGS_GENOFFS) -o $@ $(DTRACE_SRCDIR)/$(GENOFFS).cpp -ljvm

$(GENOFFS): $(DTRACE_SRCDIR)/$(GENOFFS)Main.c lib$(GENOFFS).dylib
	$(QUIETLY) $(LINK.CXX) -o $@ $(DTRACE_SRCDIR)/$(GENOFFS)Main.c \
		./lib$(GENOFFS).dylib

# $@.tmp is created first to avoid an empty $(JVMOFFS).h if an error occurs.
$(JVMOFFS).h: $(GENOFFS)
	$(QUIETLY) DYLD_LIBRARY_PATH=.:$(DYLD_LIBRARY_PATH) ./$(GENOFFS) -header > $@.tmp; touch $@; \
	if [ `diff $@.tmp $@ > /dev/null 2>&1; echo $$?` -ne 0 ] ; \
	then rm -f $@; mv $@.tmp $@; \
	else rm -f $@.tmp; \
	fi

$(JVMOFFS)Index.h: $(GENOFFS)
	$(QUIETLY) DYLD_LIBRARY_PATH=.:$(DYLD_LIBRARY_PATH) ./$(GENOFFS) -index > $@.tmp; touch $@; \
	if [ `diff $@.tmp $@ > /dev/null 2>&1; echo $$?` -ne 0 ] ; \
	then rm -f $@; mv $@.tmp $@; \
	else rm -f $@.tmp; \
	fi

$(JVMOFFS).cpp: $(GENOFFS) $(JVMOFFS).h $(JVMOFFS)Index.h
	$(QUIETLY) DYLD_LIBRARY_PATH=.:$(DYLD_LIBRARY_PATH) ./$(GENOFFS) -table > $@.tmp; touch $@; \
	if [ `diff $@.tmp $@ > /dev/null 2>&1; echo $$?` -ne 0 ] ; \
	then rm -f $@; mv $@.tmp $@; \
	else rm -f $@.tmp; \
	fi

$(JVMOFFS.o): $(JVMOFFS).h $(JVMOFFS).cpp 
	$(QUIETLY) $(CXX) -c -I. -o $@ $(ARCHFLAG) -D$(TYPE) $(JVMOFFS).cpp

$(LIBJVM_DB): $(DTRACE_SRCDIR)/$(JVM_DB).c $(JVMOFFS.o) $(XLIBJVM_DB) $(LIBJVM_DB_MAPFILE)
	@echo Making $@
	$(QUIETLY) $(CC) $(SYMFLAG) $(ARCHFLAG) -D$(TYPE) -I. -I$(GENERATED) \
		$(SHARED_FLAG) $(LFLAGS_JVM_DB) -o $@ $(DTRACE_SRCDIR)/$(JVM_DB).c -Wall # -lc
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(OS_VENDOR), Darwin)
	$(DSYMUTIL) $@
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -r -y $(LIBJVM_DB_DIZ) $(LIBJVM_DB_DEBUGINFO)
	$(RM) -r $(LIBJVM_DB_DEBUGINFO)
    endif
  else
	$(QUIETLY) $(OBJCOPY) --only-keep-debug $@ $(LIBJVM_DB_DEBUGINFO)
	$(QUIETLY) $(OBJCOPY) --add-gnu-debuglink=$(LIBJVM_DB_DEBUGINFO) $@
    ifeq ($(STRIP_POLICY),all_strip)
	$(QUIETLY) $(STRIP) $@
    else
      ifeq ($(STRIP_POLICY),min_strip)
	$(QUIETLY) $(STRIP) -x $@
      # implied else here is no stripping at all
      endif
    endif
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -y $(LIBJVM_DB_DIZ) $(LIBJVM_DB_DEBUGINFO)
	$(RM) $(LIBJVM_DB_DEBUGINFO)
    endif
  endif
endif

$(LIBJVM_DTRACE): $(DTRACE_SRCDIR)/$(JVM_DTRACE).c $(XLIBJVM_DTRACE) $(DTRACE_SRCDIR)/$(JVM_DTRACE).h $(LIBJVM_DTRACE_MAPFILE)
	@echo Making $@
	$(QUIETLY) $(CC) $(SYMFLAG) $(ARCHFLAG) -D$(TYPE) -I.  \
		$(SHARED_FLAG) $(LFLAGS_JVM_DTRACE) -o $@ $(DTRACE_SRCDIR)/$(JVM_DTRACE).c #-lc -lthread -ldoor
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(OS_VENDOR), Darwin)
	$(DSYMUTIL) $@
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -r -y $(LIBJVM_DTRACE_DIZ) $(LIBJVM_DTRACE_DEBUGINFO) 
	$(RM) -r $(LIBJVM_DTRACE_DEBUGINFO)
    endif
  else
	$(QUIETLY) $(OBJCOPY) --only-keep-debug $@ $(LIBJVM_DTRACE_DEBUGINFO)
	$(QUIETLY) $(OBJCOPY) --add-gnu-debuglink=$(LIBJVM_DTRACE_DEBUGINFO) $@
    ifeq ($(STRIP_POLICY),all_strip)
	$(QUIETLY) $(STRIP) $@
    else
      ifeq ($(STRIP_POLICY),min_strip)
	$(QUIETLY) $(STRIP) -x $@
      # implied else here is no stripping at all
      endif
    endif
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -y $(LIBJVM_DTRACE_DIZ) $(LIBJVM_DTRACE_DEBUGINFO) 
	$(RM) $(LIBJVM_DTRACE_DEBUGINFO)
    endif
  endif
endif

#$(DTRACE).d: $(DTRACE_SRCDIR)/hotspot.d $(DTRACE_SRCDIR)/hotspot_jni.d \
#             $(DTRACE_SRCDIR)/hs_private.d $(DTRACE_SRCDIR)/jhelper.d
#	$(QUIETLY) cat $^ > $@

$(DtraceOutDir):
	mkdir $(DtraceOutDir)

$(DtraceOutDir)/hotspot.h: $(DTRACE_SRCDIR)/hotspot.d | $(DtraceOutDir)
	$(QUIETLY) $(DTRACE_PROG) $(DTRACE_OPTS) -C -I. -h -o $@ -s $(DTRACE_SRCDIR)/hotspot.d

$(DtraceOutDir)/hotspot_jni.h: $(DTRACE_SRCDIR)/hotspot_jni.d | $(DtraceOutDir)
	$(QUIETLY) $(DTRACE_PROG) $(DTRACE_OPTS) -C -I. -h -o $@ -s $(DTRACE_SRCDIR)/hotspot_jni.d

$(DtraceOutDir)/hs_private.h: $(DTRACE_SRCDIR)/hs_private.d | $(DtraceOutDir)
	$(QUIETLY) $(DTRACE_PROG) $(DTRACE_OPTS) -C -I. -h -o $@ -s $(DTRACE_SRCDIR)/hs_private.d

$(DtraceOutDir)/jhelper.h: $(DTRACE_SRCDIR)/jhelper.d $(JVMOFFS).o | $(DtraceOutDir)
	$(QUIETLY) $(DTRACE_PROG) $(DTRACE_OPTS) -C -I. -h -o $@ -s $(DTRACE_SRCDIR)/jhelper.d

# jhelper currently disabled
dtrace_gen_headers: $(DtraceOutDir)/hotspot.h $(DtraceOutDir)/hotspot_jni.h $(DtraceOutDir)/hs_private.h 

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
#$(DTRACE.o): $(DTRACE).d $(JVMOFFS).h $(JVMOFFS)Index.h $(DTraced_Files)
#	@echo Compiling $(DTRACE).d

#	$(QUIETLY) $(DTRACE_PROG) $(DTRACE_OPTS) -C -I. -G -xlazyload -o $@ -s $(DTRACE).d \
#     $(DTraced_Files) ||\
#  STATUS=$$?;\
#	if [ x"$$STATUS" = x"1" -a \
#       x`uname -r` = x"5.10" -a \
#       x`uname -p` = x"sparc" ]; then\
#    echo "*****************************************************************";\
#    echo "* If you are building server compiler, and the error message is ";\
#    echo "* \"incorrect ELF machine type...\", you have run into solaris bug ";\
#    echo "* 6213962, \"dtrace -G doesn't work on sparcv8+ object files\".";\
#    echo "* Either patch/upgrade your system (>= S10u1_15), or set the ";\
#    echo "* environment variable HOTSPOT_DISABLE_DTRACE_PROBES to disable ";\
#    echo "* dtrace probes for this build.";\
#    echo "*****************************************************************";\
#  fi;\
#  exit $$STATUS
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
#	$(QUIETLY) if [ -f lib$(GENOFFS).so ]; then touch lib$(GENOFFS).so; fi
#	$(QUIETLY) if [ -f $(GENOFFS) ]; then touch $(GENOFFS); fi
#	$(QUIETLY) if [ -f $(JVMOFFS.o) ]; then touch $(JVMOFFS.o); fi

.PHONY: dtraceCheck

#SYSTEM_DTRACE_H = /usr/include/dtrace.h
SYSTEM_DTRACE_PROG = /usr/sbin/dtrace
#PATCH_DTRACE_PROG = /opt/SUNWdtrd/sbin/dtrace
systemDtraceFound := $(wildcard ${SYSTEM_DTRACE_PROG})
#patchDtraceFound := $(wildcard ${PATCH_DTRACE_PROG})
#systemDtraceHdrFound := $(wildcard $(SYSTEM_DTRACE_H))

#ifneq ("$(systemDtraceHdrFound)", "") 
#CFLAGS += -DHAVE_DTRACE_H
#endif

#ifneq ("$(patchDtraceFound)", "")
#DTRACE_PROG=$(PATCH_DTRACE_PROG)
#DTRACE_INCL=-I/opt/SUNWdtrd/include
#else
ifneq ("$(systemDtraceFound)", "")
DTRACE_PROG=$(SYSTEM_DTRACE_PROG)
else

endif # ifneq ("$(systemDtraceFound)", "")
#endif # ifneq ("$(patchDtraceFound)", "")

ifneq ("${DTRACE_PROG}", "")
ifeq ("${HOTSPOT_DISABLE_DTRACE_PROBES}", "")

DTRACE_OBJS = $(DTRACE.o) #$(JVMOFFS.o)
CFLAGS += -DDTRACE_ENABLED #$(DTRACE_INCL)
#clangCFLAGS += -DDTRACE_ENABLED -fno-optimize-sibling-calls
#MAPFILE_DTRACE_OPT = $(MAPFILE_DTRACE)


dtraceCheck:

dtrace_stuff: dtrace_gen_headers
	$(QUIETLY) echo "dtrace headers generated"


else # manually disabled

dtraceCheck:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled via environment variable"

dtrace_stuff:

endif # ifeq ("${HOTSPOT_DISABLE_DTRACE_PROBES}", "")

else # No dtrace program found

dtraceCheck:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled: not supported by system"

dtrace_stuff:

endif # ifneq ("${dtraceFound}", "")

endif # ifeq ($(OS_VENDOR), Darwin)


else # CORE build

dtraceCheck:
	$(QUIETLY) echo "**NOTICE** Dtrace support disabled for CORE builds"

endif # ifneq ("${TYPE}", "CORE")
