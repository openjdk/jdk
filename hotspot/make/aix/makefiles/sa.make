#
# Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
# Copyright 2012, 2013 SAP AG. All rights reserved.
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

# This makefile (sa.make) is included from the sa.make in the
# build directories.

# This makefile is used to build Serviceability Agent java code
# and generate JNI header file for native methods.

include $(GAMMADIR)/make/aix/makefiles/rules.make

include $(GAMMADIR)/make/defs.make

AGENT_DIR = $(GAMMADIR)/agent

include $(GAMMADIR)/make/sa.files

TOPDIR    = $(shell echo `pwd`)
GENERATED = $(TOPDIR)/../generated

# tools.jar is needed by the JDI - SA binding
SA_CLASSPATH = $(BOOT_JAVA_HOME)/lib/tools.jar

# TODO: if it's a modules image, check if SA module is installed.
MODULELIB_PATH= $(BOOT_JAVA_HOME)/lib/modules

AGENT_FILES_LIST := $(GENERATED)/agent.classes.list

SA_CLASSDIR = $(GENERATED)/saclasses

SA_BUILD_VERSION_PROP = "sun.jvm.hotspot.runtime.VM.saBuildVersion=$(SA_BUILD_VERSION)"

SA_PROPERTIES = $(SA_CLASSDIR)/sa.properties

# if $(AGENT_DIR) does not exist, we don't build SA
# also, we don't build SA on Itanium, PowerPC, ARM or zero.

all:
	if [ -d $(AGENT_DIR) -a "$(SRCARCH)" != "ia64" \
             -a "$(SRCARCH)" != "arm" \
             -a "$(SRCARCH)" != "ppc" \
             -a "$(SRCARCH)" != "zero" ] ; then \
	   $(MAKE) -f sa.make $(GENERATED)/sa-jdi.jar; \
	fi

$(GENERATED)/sa-jdi.jar: $(AGENT_FILES)
	$(QUIETLY) echo "Making $@"
	$(QUIETLY) if [ "$(BOOT_JAVA_HOME)" = "" ]; then \
	  echo "ALT_BOOTDIR, BOOTDIR or JAVA_HOME needs to be defined to build SA"; \
	  exit 1; \
	fi
	$(QUIETLY) if [ ! -f $(SA_CLASSPATH) -a ! -d $(MODULELIB_PATH) ] ; then \
	  echo "Missing $(SA_CLASSPATH) file. Use 1.6.0 or later version of JDK";\
	  echo ""; \
	  exit 1; \
	fi
	$(QUIETLY) if [ ! -d $(SA_CLASSDIR) ] ; then \
	  mkdir -p $(SA_CLASSDIR);        \
	fi
# Note: When indented, make tries to execute the '$(shell' comment.
# In some environments, cmd processors have limited line length.
# To prevent the javac invocation in the next block from using
# a very long cmd line, we use javac's @file-list option. We
# generate the file lists using make's built-in 'foreach' control
# flow which also avoids cmd processor line length issues. Since
# the 'foreach' is done as part of make's macro expansion phase,
# the initialization of the lists is also done in the same phase
# using '$(shell rm ...' instead of using the more traditional
# 'rm ...' rule.
	$(shell rm -rf $(AGENT_FILES_LIST))
# gnumake 3.78.1 does not accept the *'s that
# are in AGENT_FILES, so use the shell to expand them.
# Be extra carefull to not produce too long command lines in the shell!
	$(foreach file,$(AGENT_FILES),$(shell ls -1 $(file) >> $(AGENT_FILES_LIST)))
	$(QUIETLY) $(REMOTE) $(COMPILE.JAVAC) -classpath $(SA_CLASSPATH) -sourcepath $(AGENT_SRC_DIR) -d $(SA_CLASSDIR) @$(AGENT_FILES_LIST)
	$(QUIETLY) $(REMOTE) $(COMPILE.RMIC)  -classpath $(SA_CLASSDIR) -d $(SA_CLASSDIR) sun.jvm.hotspot.debugger.remote.RemoteDebuggerServer
	$(QUIETLY) echo "$(SA_BUILD_VERSION_PROP)" > $(SA_PROPERTIES)
	$(QUIETLY) rm -f $(SA_CLASSDIR)/sun/jvm/hotspot/utilities/soql/sa.js
	$(QUIETLY) cp $(AGENT_SRC_DIR)/sun/jvm/hotspot/utilities/soql/sa.js $(SA_CLASSDIR)/sun/jvm/hotspot/utilities/soql
	$(QUIETLY) mkdir -p $(SA_CLASSDIR)/sun/jvm/hotspot/ui/resources
	$(QUIETLY) rm -f $(SA_CLASSDIR)/sun/jvm/hotspot/ui/resources/*
	$(QUIETLY) cp $(AGENT_SRC_DIR)/sun/jvm/hotspot/ui/resources/*.png $(SA_CLASSDIR)/sun/jvm/hotspot/ui/resources/
	$(QUIETLY) cp -r $(AGENT_SRC_DIR)/images/* $(SA_CLASSDIR)/
	$(QUIETLY) $(REMOTE) $(RUN.JAR) cf $@ -C $(SA_CLASSDIR)/ .
	$(QUIETLY) $(REMOTE) $(RUN.JAR) uf $@ -C $(AGENT_SRC_DIR) META-INF/services/com.sun.jdi.connect.Connector
	$(QUIETLY) $(REMOTE) $(RUN.JAVAH) -classpath $(SA_CLASSDIR) -d $(GENERATED) -jni sun.jvm.hotspot.debugger.x86.X86ThreadContext
	$(QUIETLY) $(REMOTE) $(RUN.JAVAH) -classpath $(SA_CLASSDIR) -d $(GENERATED) -jni sun.jvm.hotspot.debugger.ia64.IA64ThreadContext
	$(QUIETLY) $(REMOTE) $(RUN.JAVAH) -classpath $(SA_CLASSDIR) -d $(GENERATED) -jni sun.jvm.hotspot.debugger.amd64.AMD64ThreadContext
	$(QUIETLY) $(REMOTE) $(RUN.JAVAH) -classpath $(SA_CLASSDIR) -d $(GENERATED) -jni sun.jvm.hotspot.debugger.sparc.SPARCThreadContext

clean:
	rm -rf $(SA_CLASSDIR)
	rm -rf $(GENERATED)/sa-jdi.jar
	rm -rf $(AGENT_FILES_LIST)
