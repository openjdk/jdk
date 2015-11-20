#
# Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
LIBJVM_DB = libjvm_db.$(LIBRARY_SUFFIX)

LIBJVM_DB_DEBUGINFO   = libjvm_db.$(LIBRARY_SUFFIX).dSYM
LIBJVM_DB_DIZ         = libjvm_db.diz

JVM_DTRACE = jvm_dtrace
LIBJVM_DTRACE = libjvm_dtrace.$(LIBRARY_SUFFIX)

LIBJVM_DTRACE_DEBUGINFO   = libjvm_dtrace.$(LIBRARY_SUFFIX).dSYM
LIBJVM_DTRACE_DIZ         = libjvm_dtrace.diz

JVMOFFS = JvmOffsets
JVMOFFS.o = $(JVMOFFS).o
GENOFFS = generate$(JVMOFFS)

DTRACE_SRCDIR = $(GAMMADIR)/src/os/$(Platform_os_family)/dtrace
DTRACE_COMMON_SRCDIR = $(GAMMADIR)/src/os/posix/dtrace
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

# Use mapfile with libjvm_dtrace.so
LIBJVM_DTRACE_MAPFILE = # no mapfile for usdt2 # $(MAKEFILES_DIR)/mapfile-vers-jvm_dtrace

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
	@echo $(LOG_INFO) Making $@
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
	@echo $(LOG_INFO) Making $@
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

lib$(GENOFFS).$(LIBRARY_SUFFIX): $(DTRACE_SRCDIR)/$(GENOFFS).cpp $(DTRACE_SRCDIR)/$(GENOFFS).h \
                  $(LIBJVM.o)
	$(QUIETLY) $(CXX) $(CXXFLAGS) $(GENOFFS_CFLAGS) $(SHARED_FLAG) $(PICFLAG) \
		 $(LFLAGS_GENOFFS) -o $@ $(DTRACE_SRCDIR)/$(GENOFFS).cpp -ljvm

$(GENOFFS): $(DTRACE_SRCDIR)/$(GENOFFS)Main.c lib$(GENOFFS).$(LIBRARY_SUFFIX)
	$(QUIETLY) $(LINK.CXX) -o $@ $(DTRACE_SRCDIR)/$(GENOFFS)Main.c \
		./lib$(GENOFFS).$(LIBRARY_SUFFIX)

# $@.tmp is created first to avoid an empty $(JVMOFFS).h if an error occurs.
$(JVMOFFS).h: $(GENOFFS)
	$(QUIETLY) DYLD_LIBRARY_PATH=.:$(DYLD_LIBRARY_PATH) ./$(GENOFFS) -header > $@.tmp; touch $@; \
	if diff $@.tmp $@ > /dev/null 2>&1 ; \
	then rm -f $@.tmp; \
	else rm -f $@; mv $@.tmp $@; \
	fi

$(JVMOFFS)Index.h: $(GENOFFS)
	$(QUIETLY) DYLD_LIBRARY_PATH=.:$(DYLD_LIBRARY_PATH) ./$(GENOFFS) -index > $@.tmp; touch $@; \
	if diff $@.tmp $@ > /dev/null 2>&1 ; \
	then rm -f $@.tmp; \
	else rm -f $@; mv $@.tmp $@; \
	fi

$(JVMOFFS).cpp: $(GENOFFS) $(JVMOFFS).h $(JVMOFFS)Index.h
	$(QUIETLY) DYLD_LIBRARY_PATH=.:$(DYLD_LIBRARY_PATH) ./$(GENOFFS) -table > $@.tmp; touch $@; \
	if diff $@.tmp $@ > /dev/null 2>&1; \
	then rm -f $@.tmp; \
	else rm -f $@; mv $@.tmp $@; \
	fi

$(JVMOFFS.o): $(JVMOFFS).h $(JVMOFFS).cpp 
	$(QUIETLY) $(CXX) -c -I. -o $@ $(ARCHFLAG) -D$(TYPE) $(JVMOFFS).cpp

$(LIBJVM_DB): $(DTRACE_SRCDIR)/$(JVM_DB).c $(JVMOFFS.o) $(XLIBJVM_DB) $(LIBJVM_DB_MAPFILE)
	@echo $(LOG_INFO) Making $@
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
	@echo $(LOG_INFO) Making $@
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


$(DtraceOutDir):
	mkdir $(DtraceOutDir)

# When building using a devkit, dtrace cannot find the correct preprocessor so
# we run it explicitly before runing dtrace.
$(DtraceOutDir)/hotspot.h: $(DTRACE_COMMON_SRCDIR)/hotspot.d | $(DtraceOutDir)
	$(QUIETLY) $(CC) -E $(DTRACE_OPTS) -I. -x c $(DTRACE_COMMON_SRCDIR)/hotspot.d > $(DtraceOutDir)/hotspot.d
	$(QUIETLY) $(DTRACE_PROG) -h -o $@ -s $(DtraceOutDir)/hotspot.d

$(DtraceOutDir)/hotspot_jni.h: $(DTRACE_COMMON_SRCDIR)/hotspot_jni.d | $(DtraceOutDir)
	$(QUIETLY) $(CC) -E $(DTRACE_OPTS) -I. -x c $(DTRACE_COMMON_SRCDIR)/hotspot_jni.d > $(DtraceOutDir)/hotspot_jni.d
	$(QUIETLY) $(DTRACE_PROG) -h -o $@ -s $(DtraceOutDir)/hotspot_jni.d

$(DtraceOutDir)/hs_private.h: $(DTRACE_COMMON_SRCDIR)/hs_private.d | $(DtraceOutDir)
	$(QUIETLY) $(CC) -E $(DTRACE_OPTS) -I. -x c $(DTRACE_COMMON_SRCDIR)/hs_private.d > $(DtraceOutDir)/hs_private.d
	$(QUIETLY) $(DTRACE_PROG) -h -o $@ -s $(DtraceOutDir)/hs_private.d

dtrace_gen_headers: $(DtraceOutDir)/hotspot.h $(DtraceOutDir)/hotspot_jni.h $(DtraceOutDir)/hs_private.h 


.PHONY: dtraceCheck

SYSTEM_DTRACE_PROG = /usr/sbin/dtrace
systemDtraceFound := $(wildcard ${SYSTEM_DTRACE_PROG})

ifneq ("$(systemDtraceFound)", "")
DTRACE_PROG=$(SYSTEM_DTRACE_PROG)
else

endif

ifneq ("${DTRACE_PROG}", "")
ifeq ("${HOTSPOT_DISABLE_DTRACE_PROBES}", "")

DTRACE_OBJS = $(DTRACE.o) #$(JVMOFFS.o)
CFLAGS += -DDTRACE_ENABLED #$(DTRACE_INCL)


dtraceCheck:

dtrace_stuff: dtrace_gen_headers
	$(QUIETLY) echo $(LOG_INFO) "dtrace headers generated"


else # manually disabled

dtraceCheck:
	$(QUIETLY) echo $(LOG_INFO) "**NOTICE** Dtrace support disabled via environment variable"

dtrace_stuff:

endif # ifeq ("${HOTSPOT_DISABLE_DTRACE_PROBES}", "")

else # No dtrace program found

dtraceCheck:
	$(QUIETLY) echo $(LOG_INFO) "**NOTICE** Dtrace support disabled: not supported by system"

dtrace_stuff:

endif # ifneq ("${dtraceFound}", "")

endif # ifeq ($(OS_VENDOR), Darwin)


else # CORE build

dtraceCheck:
	$(QUIETLY) echo $(LOG_INFO) "**NOTICE** Dtrace support disabled for CORE builds"

endif # ifneq ("${TYPE}", "CORE")
