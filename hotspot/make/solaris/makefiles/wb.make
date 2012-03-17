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

# Rules to build whitebox testing library, used by vm.make

WB = wb

WBSRCDIR = $(GAMMADIR)/src/share/tools/whitebox

WB_JAR = $(GENERATED)/$(WB).jar

WB_JAVA_SRCS = $(shell find $(WBSRCDIR) -name '*.java')
WB_JAVA_CLASSDIR = $(GENERATED)/wb/classes

WB_JAVA_CLASSES  = $(patsubst $(WBSRCDIR)/%,$(WB_JAVA_CLASSDIR)/%, \
	$(patsubst %.java,%.class,$(WB_JAVA_SRCS)))

$(WB_JAVA_CLASSDIR)/%.class: $(WBSRCDIR)/%.java $(WB_JAVA_CLASSDIR)
	$(REMOTE) $(COMPILE.JAVAC) -nowarn -d $(WB_JAVA_CLASSDIR) $<

$(WB_JAR): $(WB_JAVA_CLASSES)
	$(QUIETLY) $(REMOTE) $(RUN.JAR) cf $@ -C $(WB_JAVA_CLASSDIR)/ .

$(WB_JAVA_CLASSDIR):
	$(QUIETLY) mkdir -p $@

