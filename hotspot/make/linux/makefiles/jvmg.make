#
# Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

# Sets make macros for making debug version of VM

# Compiler specific DEBUG_CFLAGS are passed in from gcc.make, sparcWorks.make
DEBUG_CFLAGS/DEFAULT= $(DEBUG_CFLAGS)
DEBUG_CFLAGS/BYFILE = $(DEBUG_CFLAGS/$@)$(DEBUG_CFLAGS/DEFAULT$(DEBUG_CFLAGS/$@))
CFLAGS += $(DEBUG_CFLAGS/BYFILE)

# Set the environment variable HOTSPARC_GENERIC to "true"
# to inhibit the effect of the previous line on CFLAGS.

# Linker mapfile
MAPFILE = $(GAMMADIR)/make/linux/makefiles/mapfile-vers-debug

G_SUFFIX = _g
VERSION = debug
SYSDEFS += -DASSERT -DDEBUG
PICFLAGS = DEFAULT
