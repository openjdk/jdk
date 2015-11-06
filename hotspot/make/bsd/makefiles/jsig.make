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

# Rules to build signal interposition library, used by vm.make

# libjsig.so: signal interposition library
JSIG   = jsig

ifeq ($(OS_VENDOR), Darwin)
  LIBJSIG   = lib$(JSIG).$(LIBRARY_SUFFIX)

  LIBJSIG_DEBUGINFO   = lib$(JSIG).$(LIBRARY_SUFFIX).dSYM
  LIBJSIG_DIZ         = lib$(JSIG).diz
else
  LIBJSIG   = lib$(JSIG).so

  LIBJSIG_DEBUGINFO   = lib$(JSIG).debuginfo
  LIBJSIG_DIZ         = lib$(JSIG).diz
endif

JSIGSRCDIR = $(GAMMADIR)/src/os/$(Platform_os_family)/vm

DEST_JSIG           = $(JDK_LIBDIR)/$(LIBJSIG)
DEST_JSIG_DEBUGINFO = $(JDK_LIBDIR)/$(LIBJSIG_DEBUGINFO)
DEST_JSIG_DIZ       = $(JDK_LIBDIR)/$(LIBJSIG_DIZ)

LIBJSIG_MAPFILE = $(MAKEFILES_DIR)/mapfile-vers-jsig

# On Bsd we really dont want a mapfile, as this library is small
# and preloaded using LD_PRELOAD, making functions private will
# cause problems with interposing. See CR: 6466665
# LFLAGS_JSIG += $(MAPFLAG:FILENAME=$(LIBJSIG_MAPFILE))

LFLAGS_JSIG += -D_GNU_SOURCE -pthread $(LDFLAGS_HASH_STYLE)

# DEBUG_BINARIES overrides everything, use full -g debug information
ifeq ($(DEBUG_BINARIES), true)
  JSIG_DEBUG_CFLAGS = -g
endif

$(LIBJSIG): $(JSIGSRCDIR)/jsig.c $(LIBJSIG_MAPFILE)
	@echo $(LOG_INFO) Making signal interposition lib...
ifeq ($(STATIC_BUILD),true)
	$(QUIETLY) $(CC) -c $(SYMFLAG) $(EXTRA_CFLAGS) $(ARCHFLAG) $(PICFLAG) \
                          $(LFLAGS_JSIG) $(JSIG_DEBUG_CFLAGS) -o $(JSIG).o $<
	$(QUIETLY) $(AR) $(ARFLAGS) $@ $(JSIG).o
else
	$(QUIETLY) $(CC) $(SYMFLAG) $(ARCHFLAG) $(SHARED_FLAG) $(PICFLAG) \
                          $(LFLAGS_JSIG) $(JSIG_DEBUG_CFLAGS) $(EXTRA_CFLAGS) -o $@ $<
endif
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(OS_VENDOR), Darwin)
	$(DSYMUTIL) $@
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -r -y $(LIBJSIG_DIZ) $(LIBJSIG_DEBUGINFO)
	$(RM) -r $(LIBJSIG_DEBUGINFO)
    endif
  else
	$(QUIETLY) $(OBJCOPY) --only-keep-debug $@ $(LIBJSIG_DEBUGINFO)
	$(QUIETLY) $(OBJCOPY) --add-gnu-debuglink=$(LIBJSIG_DEBUGINFO) $@
    ifeq ($(STRIP_POLICY),all_strip)
	$(QUIETLY) $(STRIP) $@
    else
      ifeq ($(STRIP_POLICY),min_strip)
	$(QUIETLY) $(STRIP) -g $@
      # implied else here is no stripping at all
      endif
    endif
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -y $(LIBJSIG_DIZ) $(LIBJSIG_DEBUGINFO)
	$(RM) $(LIBJSIG_DEBUGINFO)
    endif
  endif
endif

install_jsig: $(LIBJSIG)
	@echo "Copying $(LIBJSIG) to $(DEST_JSIG)"
ifeq ($(OS_VENDOR), Darwin)
	$(QUIETLY) test ! -d $(LIBJSIG_DEBUGINFO) || \
	    $(CP) -f -r $(LIBJSIG_DEBUGINFO) $(DEST_JSIG_DEBUGINFO)
else
	$(QUIETLY) test ! -f $(LIBJSIG_DEBUGINFO) || \
	    $(CP) -f $(LIBJSIG_DEBUGINFO) $(DEST_JSIG_DEBUGINFO)
endif
	$(QUIETLY) test ! -f $(LIBJSIG_DIZ) || \
	    $(CP) -f $(LIBJSIG_DIZ) $(DEST_JSIG_DIZ)
	$(QUIETLY) $(CP) -f $(LIBJSIG) $(DEST_JSIG) && echo "Done"

.PHONY: install_jsig
