#
# Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

-include $(SPEC)
include MakeBase.gmk
include JavaCompilation.gmk

default: all

JAVAC_JARS ?= "-Xbootclasspath/p:$(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javac.jar" \
		-jar $(LANGTOOLS_OUTPUTDIR)/dist/bootstrap/lib/javac.jar
DISABLE_JAXP_WARNINGS:=-Xlint:all,-deprecation,-unchecked,-rawtypes,-cast,-serial,-dep-ann,-static,-fallthrough

# The generate new bytecode uses the new compiler for to generate bytecode
# for the new jdk that is being built. The code compiled by this setup
# cannot necessarily be run with the boot jdk.
$(eval $(call SetupJavaCompiler,GENERATE_NEWBYTECODE_DEBUG,\
     JVM:=$(JAVA),\
     JAVAC:=$(JAVAC_JARS),\
     FLAGS:=-Xprefer:source -XDignore.symbol.file=true $(DISABLE_JAXP_WARNINGS) -g,\
     SERVER_DIR:=$(JAVAC_SERVERS),\
     SERVER_JVM:=$(SERVER_JAVA),\
     MODE:=$(JAVAC_USE_MODE),\
     USE_DEPS:=$(JAVAC_USE_DEPS)))

$(eval $(call SetupJavaCompilation,BUILD_JAXP,\
		SETUP:=GENERATE_NEWBYTECODE_DEBUG,\
		SRC:=$(JAXP_TOPDIR)/src/share/classes,\
		CLEAN:=.properties,\
		BIN:=$(JAXP_OUTPUTDIR)/classes,\
		SRCZIP:=$(JAXP_OUTPUTDIR)/dist/lib/src.zip))

$(eval $(call SetupArchive,ARCHIVE_JAXP,$(BUILD_JAXP),\
		SRCS:=$(JAXP_OUTPUTDIR)/classes,\
		SUFFIXES:=.class .properties,\
		JAR:=$(JAXP_OUTPUTDIR)/dist/lib/classes.jar))

all: $(JAXP_OUTPUTDIR)/dist/lib/classes.jar $(JAXP_OUTPUTDIR)/dist/lib/src.zip

.PHONY: default all 
