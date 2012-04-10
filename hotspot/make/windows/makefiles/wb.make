#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

# This makefile is used to build the whitebox testing lib
# and compile the tests which use it

!include $(WorkSpace)/make/windows/makefiles/rules.make

WBSRCDIR = $(WorkSpace)/src/share/tools/whitebox

# turn GENERATED into a windows path to get sane dependencies
WB_CLASSES=$(GENERATED:/=\)\wb\classes
WB_JAR=$(GENERATED:/=\)\wb.jar

# call recursive make to do wildcard expansion
.SUFFIXES : .java .class
wb_java_srcs: $(WorkSpace)\src\share\tools\whitebox\sun\hotspot\*.java $(WB_CLASSES)
	$(MAKE) -f $(WorkSpace)\make\windows\makefiles\$(BUILD_FLAVOR).make $(**:.java=.class)


{$(WorkSpace)\src\share\tools\whitebox\sun\hotspot}.java.class::
	$(COMPILE_JAVAC) -d $(WB_CLASSES) $<

$(WB_JAR): wb_java_srcs
	$(RUN_JAR) cf $@ -C $(WB_CLASSES) .

# turn $@ to a unix path because mkdir in PATH is cygwin/mks mkdir
$(WB_CLASSES):
	mkdir -p $(@:\=/)

# main target to build wb
wb: $(WB_JAR)

