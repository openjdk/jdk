#
# Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
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

# Single gnu makefile for solaris, linux and windows (windows requires cygwin and mingw)

# Default arch; it is changed below as needed.
ARCH		= i386
OS		= $(shell uname)
AR		= ar

## OS = SunOS ##
ifeq		($(OS),SunOS)
CPU             = $(shell uname -p)
ARCH1=$(CPU:i586=i386)
ARCH=$(ARCH1:i686=i386)
OS		= solaris
CC 		= cc
CFLAGS		+= -KPIC
ifdef LP64
ifeq ($(ARCH),sparc)
ARCH            = sparcv9
endif
ifeq ($(ARCH),i386)
ARCH            = amd64
endif
endif
CFLAGS/sparcv9	+= -xarch=v9
CFLAGS/amd64	+= -m64
CFLAGS		+= $(CFLAGS/$(ARCH))
DLDFLAGS	+= -G
LDFLAGS         += -ldl
OUTFLAGS	+= -o $@
LIB_EXT		= .so
else
## OS = Linux ##
ifeq		($(OS),Linux)
ifneq           ($(MINGW),)
LIB_EXT		= .dll
CPPFLAGS += -I$(TARGET_DIR)/include
LDFLAGS += -L$(TARGET_DIR)/lib
OS=windows
ifneq           ($(findstring x86_64-,$(MINGW)),)
ARCH=amd64
else
ARCH=i386
endif
CC 		= $(MINGW)-gcc
CONFIGURE_ARGS= --host=$(MINGW) --target=$(MINGW)
else   #linux
CPU             = $(shell uname -m)
ARCH1=$(CPU:x86_64=amd64)
ARCH2=$(ARCH1:i686=i386)
ARCH=$(ARCH2:sparc64=sparcv9)
ifdef LP64
CFLAGS/sparcv9	+= -m64
CFLAGS/amd64	+= -m64
CFLAGS/ppc64	+= -m64
CFLAGS/ppc64le  += -m64 -DABI_ELFv2
else
ARCH=$(ARCH2:amd64=i386)
ifneq ($(findstring arm,$(ARCH)),)
ARCH=arm
endif
CFLAGS/i386	+= -m32
CFLAGS/sparc	+= -m32
endif
CFLAGS		+= $(CFLAGS/$(ARCH))
CFLAGS		+= -fPIC
OS		= linux
LIB_EXT		= .so
CC 		= gcc
endif
CFLAGS		+= -O
DLDFLAGS	+= -shared
LDFLAGS         += -ldl
OUTFLAGS	+= -o $@
else
## OS = AIX ##
ifeq		($(OS),AIX)
OS              = aix
ARCH            = ppc64
CC              = xlc_r
CFLAGS          += -DAIX -g -qpic=large -q64
CFLAGS/ppc64    += -q64
AR              = ar -X64
DLDFLAGS        += -qmkshrobj -lz
OUTFLAGS        += -o $@
LIB_EXT		= .so
else
## OS = Darwin ##
ifeq ($(OS),Darwin)
CPU             = $(shell uname -m)
ARCH1=$(CPU:x86_64=amd64)
ARCH=$(ARCH1:i686=i386)
ifdef LP64
CFLAGS/sparcv9  += -m64
CFLAGS/amd64    += -m64
else
ARCH=$(ARCH1:amd64=i386)
CFLAGS/i386     += -m32
CFLAGS/sparc    += -m32
endif # LP64
CFLAGS          += $(CFLAGS/$(ARCH))
CFLAGS          += -fPIC
OS              = macosx
LIB_EXT         = .dylib
CC              = gcc
CFLAGS          += -O
# CFLAGS        += -DZ_PREFIX
DLDFLAGS        += -shared
DLDFLAGS        += -lz
LDFLAGS         += -ldl
OUTFLAGS        += -o $@
else
## OS = Windows ##
OS		= windows
CC		= gcc
CFLAGS		+=  /nologo /MD /W3 /WX /O2 /Fo$(@:.dll=.obj) /Gi-
CFLAGS		+= LIBARCH=\"$(LIBARCH)\"
DLDFLAGS	+= /dll /subsystem:windows /incremental:no \
			/export:decode_instruction
OUTFLAGS	+= /link /out:$@
LIB_EXT		= .dll
endif   # Darwin
endif   # AIX
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

JDKARCH=$(LIBARCH:i386=i586)

ifeq            ($(BINUTILS),)
# Pop all the way out of the workspace to look for binutils.
# ...You probably want to override this setting.
BINUTILSDIR	= $(shell cd build/binutils;pwd)
else
BINUTILSDIR	= $(shell cd $(BINUTILS);pwd)
endif

CPPFLAGS	+= -I$(BINUTILSDIR)/include -I$(BINUTILSDIR)/bfd -I$(TARGET_DIR)/bfd
CPPFLAGS	+= -DLIBARCH_$(LIBARCH) -DLIBARCH=\"$(LIBARCH)\" -DLIB_EXT=\"$(LIB_EXT)\"

TARGET_DIR	= build/$(OS)-$(JDKARCH)
TARGET		= $(TARGET_DIR)/hsdis-$(LIBARCH)$(LIB_EXT)

SOURCE		= hsdis.c

LIBRARIES =	$(TARGET_DIR)/bfd/libbfd.a \
		$(TARGET_DIR)/opcodes/libopcodes.a \
		$(TARGET_DIR)/libiberty/libiberty.a

DEMO_TARGET	= $(TARGET_DIR)/hsdis-demo
DEMO_SOURCE	= hsdis-demo.c

.PHONY:  all clean demo both

all:  $(TARGET)

both: all all64

%64:
	$(MAKE) LP64=1 ${@:%64=%}

demo: $(TARGET) $(DEMO_TARGET)

$(LIBRARIES): $(TARGET_DIR) $(TARGET_DIR)/Makefile
	if [ ! -f $@ ]; then cd $(TARGET_DIR); make all-opcodes; fi

$(TARGET_DIR)/Makefile:
	(cd $(TARGET_DIR); CC=$(CC) CFLAGS="$(CFLAGS)" AR="$(AR)" $(BINUTILSDIR)/configure --disable-nls $(CONFIGURE_ARGS))

$(TARGET): $(SOURCE) $(LIBS) $(LIBRARIES) $(TARGET_DIR)
	$(CC) $(OUTFLAGS) $(CPPFLAGS) $(CFLAGS) $(SOURCE) $(DLDFLAGS) $(LIBRARIES)

$(DEMO_TARGET): $(DEMO_SOURCE) $(TARGET) $(TARGET_DIR)
	$(CC) $(OUTFLAGS) -DTARGET_DIR=\"$(TARGET_DIR)\" $(CPPFLAGS) -g $(CFLAGS/$(ARCH)) $(DEMO_SOURCE) $(LDFLAGS)

$(TARGET_DIR):
	[ -d $@ ] || mkdir -p $@

clean:
	rm -rf $(TARGET_DIR)
