#
# Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

DEFAULTACTIONS=clean post_update create

default:: $(SUBDIRS)

!ifndef DIR
DIR=.
!endif

!ifndef CPP
CPP=cl.exe
!endif


!ifdef SUBDIRS
$(SUBDIRS): FORCE
	@if not exist $@ mkdir $@
	@if not exist $@\local.make echo # Empty > $@\local.make
	@echo nmake $(ACTION) in $(DIR)\$@
	cd $@ && $(MAKE) /NOLOGO /f $(WorkSpace)\make\windows\makefiles\$@.make $(ACTION) DIR=$(DIR)\$@ BUILD_FLAVOR=$(BUILD_FLAVOR)
!endif

# Creates the needed directory
create::
!if "$(DIR)" != "."
	@echo mkdir $(DIR)
!endif

# Epilog to update for generating derived files
post_update::

# Removes scrap files
clean:: FORCE
	-@rm -f *.OLD *.publish

# Remove all scrap files and all generated files
pure:: clean
	-@rm -f *.OLD *.publish

$(DEFAULTACTIONS) $(ACTIONS)::
!ifdef SUBDIRS
	@$(MAKE) -nologo ACTION=$@ DIR=$(DIR)
!endif

FORCE:


