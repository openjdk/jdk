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

# The cscope.out file is generated in the current directory.  The old cscope.out
# file is *not* removed because cscope is smart enough to only build what has
# changed.  cscope can be confused if files are renamed or removed, so it may be
# necessary to remove cscope.out (gmake cscope.clean) if a lot of reorganization
# has occurred.

include $(GAMMADIR)/make/scm.make

RM	= rm -f
HG	= hg
CS_TOP	= $(GAMMADIR)

CSDIRS	= $(CS_TOP)/src $(CS_TOP)/make
CSINCS	= $(CSDIRS:%=-I%)

CSCOPE		= cscope
CSCOPE_OUT	= cscope.out
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

# Ignore build products.
CS_PRUNE_GENERATED	= -o -name '${OSNAME}_*_core' -o \
			     -name '${OSNAME}_*_compiler?'

# O/S-specific files for all systems are included by default.  Set CS_OS to a
# space-separated list of identifiers to include only those systems.
ifdef	CS_OS
CS_PRUNE_OS	= $(patsubst %,-o -name '*%*',\
		    $(filter-out ${CS_OS},bsd linux macos solaris windows))
endif

# CPU-specific files for all processors are included by default.  Set CS_CPU 
# space-separated list identifiers to include only those CPUs.
ifdef	CS_CPU
CS_PRUNE_CPU	= $(patsubst %,-o -name '*%*',\
		    $(filter-out ${CS_CPU},arm ppc sparc x86 zero))
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

# Placeholder for user-defined excludes.
CS_PRUNE_EX	=

CS_PRUNE	= $(CS_PRUNE_STD) \
		  $(CS_PRUNE_OS) \
		  $(CS_PRUNE_CPU) \
		  $(CS_PRUNE_GENERATED) \
		  $(CS_PRUNE_EX) \
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

.PHONY:		cscope cscope.clean cscope.scratch TAGS.clean FORCE
.PRECIOUS:	cscope.out

cscope $(CSCOPE_OUT): cscope.files FORCE
	$(CSCOPE) -f $(CSCOPE_OUT) $(CSCOPE_FLAGS)

cscope.clean:
	$(QUIETLY) $(RM) $(CSCOPE_OUT) cscope.files

cscope.scratch:  cscope.clean cscope

# The raw list is reordered so cscope displays the most relevant files first.
cscope.files:
	$(QUIETLY)						\
	raw=cscope.$$$$;					\
	find $(CSDIRS) -type d \( $(CS_PRUNE) \) -prune -o	\
	    -type f \( $(CSFILENAMES) \) -print > $$raw;	\
	{							\
	echo "$(CSINCS)";					\
	egrep -v "\.java|/make/" $$raw;				\
	fgrep ".java" $$raw;					\
	fgrep "/make/" $$raw;					\
	} > $@;							\
	rm -f $$raw

TAGS:  cscope.files FORCE
	egrep -v '^-|^$$' $< | etags --members -

TAGS.clean:
	$(RM) TAGS
