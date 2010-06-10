#
# Copyright (c) 2000, 2008, Oracle and/or its affiliates. All rights reserved.
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

#
# The cscope.out file is made in the current directory and spans the entire
# source tree.
#
# Things to note:
#	1. We use relative names for cscope.
#	2. We *don't* remove the old cscope.out file, because cscope is smart
#	   enough to only build what has changed.  It can be confused, however,
#	   if files are renamed or removed, so it may be necessary to manually
#	   remove cscope.out if a lot of reorganization has occurred.
#

include $(GAMMADIR)/make/scm.make

NAWK	= /usr/xpg4/bin/awk
RM	= rm -f
HG	= hg
CS_TOP	= ../..

CSDIRS	= $(CS_TOP)/src $(CS_TOP)/make
CSINCS	= $(CSDIRS:%=-I%)

CSCOPE		= cscope
CSCOPE_FLAGS	= -b

# Allow .java files to be added from the environment (CSCLASSES=yes).
ifdef	CSCLASSES
ADDCLASSES=	-o -name '*.java'
endif

# Adding CClassHeaders also pushes the file count of a full workspace up about
# 200 files (these files also don't exist in a new workspace, and thus will
# cause the recreation of the database as they get created, which might seem
# a little confusing).  Thus allow these files to be added from the environment
# (CSHEADERS=yes).
ifndef	CSHEADERS
RMCCHEADERS=	-o -name CClassHeaders
endif

# Use CS_GENERATED=x to include auto-generated files in the make directories.
ifdef	CS_GENERATED
CS_ADD_GENERATED	= -o -name '*.incl'
else
CS_PRUNE_GENERATED	= -o -name '${OS}_*_core' -o -name '${OS}_*_compiler?'
endif

# OS-specific files for other systems are excluded by default.  Use CS_OS=yes
# to include platform-specific files for other platforms.
ifndef	CS_OS
CS_OS		= linux macos solaris win32
CS_PRUNE_OS	= $(patsubst %,-o -name '*%*',$(filter-out ${OS},${CS_OS}))
endif

# Processor-specific files for other processors are excluded by default.  Use
# CS_CPU=x to include platform-specific files for other platforms.
ifndef	CS_CPU
CS_CPU		= i486 sparc amd64 ia64
CS_PRUNE_CPU	= $(patsubst %,-o -name '*%*',$(filter-out ${SRCARCH},${CS_CPU}))
endif

# What files should we include?  A simple rule might be just those files under
# SCCS control, however this would miss files we create like the opcodes and
# CClassHeaders.  The following attempts to find everything that is *useful*.
# (.del files are created by sccsrm, demo directories contain many .java files
# that probably aren't useful for development, and the pkgarchive may contain
# duplicates of files within the source hierarchy).

# Directories to exclude.
CS_PRUNE_STD	= $(SCM_DIRS) \
		  -o -name '.del-*' \
		  -o -name '*demo' \
		  -o -name pkgarchive

CS_PRUNE	= $(CS_PRUNE_STD) \
		  $(CS_PRUNE_OS) \
		  $(CS_PRUNE_CPU) \
		  $(CS_PRUNE_GENERATED) \
		  $(RMCCHEADERS)

# File names to include.
CSFILENAMES	= -name '*.[ch]pp' \
		  -o -name '*.[Ccshlxy]' \
		  $(CS_ADD_GENERATED) \
		  -o -name '*.d' \
		  -o -name '*.il' \
		  -o -name '*.cc' \
		  -o -name '*[Mm]akefile*' \
		  -o -name '*.gmk' \
		  -o -name '*.make' \
		  -o -name '*.ad' \
		  $(ADDCLASSES)

.PRECIOUS:	cscope.out

cscope cscope.out: cscope.files FORCE
	$(CSCOPE) $(CSCOPE_FLAGS)

# The .raw file is reordered here in an attempt to make cscope display the most
# relevant files first.
cscope.files: .cscope.files.raw
	echo "$(CSINCS)" > $@
	-egrep -v "\.java|\/make\/"	$< >> $@
	-fgrep ".java"			$< >> $@
	-fgrep "/make/"		$< >> $@

.cscope.files.raw:  .nametable.files
	-find $(CSDIRS) -type d \( $(CS_PRUNE) \) -prune -o \
	    -type f \( $(CSFILENAMES) \) -print > $@

cscope.clean:  nametable.clean
	-$(RM) cscope.out cscope.files .cscope.files.raw

TAGS:  cscope.files FORCE
	egrep -v '^-|^$$' $< | etags --members -

TAGS.clean:  nametable.clean
	-$(RM) TAGS

# .nametable.files and .nametable.files.tmp are used to determine if any files
# were added to/deleted from/renamed in the workspace.  If not, then there's
# normally no need to rebuild the cscope database. To force a rebuild of
# the cscope database: gmake nametable.clean.
.nametable.files:  .nametable.files.tmp
	( cmp -s $@ $< ) || ( cp $< $@ )
	-$(RM) $<

# `hg status' is slightly faster than `hg fstatus'. Both are
# quite a bit slower on an NFS mounted file system, so this is
# really geared towards repos on local file systems.
.nametable.files.tmp:
	-$(HG) fstatus -acmn > $@

nametable.clean:
	-$(RM) .nametable.files .nametable.files.tmp

FORCE:

.PHONY:		cscope cscope.clean TAGS.clean nametable.clean FORCE
