#
# Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

# Single gnu makefile for solaris, linux and windows (windows requires mks or
# cygwin).

ifeq            ($(BINUTILS),)
# Pop all the way out of the workspace to look for binutils.
# ...You probably want to override this setting.
BINUTILS	= $(shell cd ../../../../..;pwd)/binutils-2.17-$(LIBARCH)
endif

# Default arch; it is changed below as needed.
ARCH		= i386
OS		= $(shell uname)

CPPFLAGS	+= -I$(BINUTILS)/include -I$(BINUTILS)/bfd
CPPFLAGS	+= -DHOTSPOT_LIB_ARCH=\"$(LIBARCH)\" -DLIBARCH_$(LIBARCH)
CPPFLAGS	+= -DHOTSPOT_OS=\"$(OS)\" -DOS_$(OS)

## OS = SunOS ##
ifeq		($(OS),SunOS)
ARCH    	= $(shell uname -p)
OS		= solaris
CC 		= cc
CCFLAGS		+= -Kpic -g
CCFLAGS/amd64   += -xarch=amd64
CCFLAGS/sparcv9 += -xarch=v9
CCFLAGS		+= $(CCFLAGS/$(LIBARCH))
DLDFLAGS	+= -G
OUTFLAGS	+= -o $@
LIB_EXT		= .so
else
## OS = Linux ##
ifeq		($(OS),Linux)
CPU             = $(shell uname -m)
ifeq		($(CPU),ia64)
ARCH    	= ia64
else
ifeq		($(CPU),x86_64)
CCFLAGS		+= -fPIC
endif   # x86_64
endif   # ia64
OS		= linux
CC 		= gcc
CCFLAGS		+= -O
DLDFLAGS	+= -shared
OUTFLAGS	+= -o $@
LIB_EXT		= .so
CPPFLAGS	+= -Iinclude -Iinclude/$(OS)_$(ARCH)/
## OS = Windows ##
else   # !SunOS, !Linux => Windows
OS		= win
CC		= cl
#CPPFLAGS	+= /D"WIN32" /D"_WINDOWS" /D"DEBUG" /D"NDEBUG"
CCFLAGS		+=  /nologo /MD /W3 /WX /O2 /Fo$(@:.dll=.obj) /Gi-
CCFLAGS		+= -Iinclude -Iinclude/gnu -Iinclude/$(OS)_$(ARCH)
CCFLAGS		+= /D"HOTSPOT_LIB_ARCH=\"$(LIBARCH)\""
DLDFLAGS	+= /dll /subsystem:windows /incremental:no \
			/export:decode_instruction
OUTFLAGS	+= /link /out:$@
LIB_EXT		= .dll
endif	# Linux
endif	# SunOS

LIBARCH		= $(ARCH)
ifdef		LP64
LIBARCH64/sparc	= sparcv9
LIBARCH64/i386	= amd64
LIBARCH64	= $(LIBARCH64/$(ARCH))
ifneq		($(LIBARCH64),)
LIBARCH		= $(LIBARCH64)
endif   # LIBARCH64/$(ARCH)
endif   # LP64

TARGET_DIR	= bin/$(OS)
TARGET		= $(TARGET_DIR)/hsdis-$(LIBARCH)$(LIB_EXT)

SOURCE		= hsdis.c

LIBRARIES =	$(BINUTILS)/bfd/libbfd.a \
		$(BINUTILS)/opcodes/libopcodes.a \
		$(BINUTILS)/libiberty/libiberty.a

DEMO_TARGET	= $(TARGET_DIR)/hsdis-demo-$(LIBARCH)
DEMO_SOURCE	= hsdis-demo.c

.PHONY:  all clean demo both

all:  $(TARGET) demo

both: all all64

%64:
	$(MAKE) LP64=1 ${@:%64=%}

demo: $(TARGET) $(DEMO_TARGET)

$(LIBRARIES):
	@echo "*** Please build binutils first; see ./README: ***"
	@sed < ./README '1,/__________/d' | head -20
	@echo "..."; exit 1

$(TARGET): $(SOURCE) $(LIBS) $(LIBRARIES) $(TARGET_DIR)
	$(CC) $(OUTFLAGS) $(CPPFLAGS) $(CCFLAGS) $(SOURCE) $(DLDFLAGS) $(LIBRARIES)

$(DEMO_TARGET): $(DEMO_SOURCE) $(TARGET) $(TARGET_DIR)
	$(CC) $(OUTFLAGS) $(CPPFLAGS) $(CCFLAGS) $(DEMO_SOURCE) $(LDFLAGS)

$(TARGET_DIR):
	[ -d $@ ] || mkdir -p $@

clean:
	rm -rf $(TARGET_DIR)
