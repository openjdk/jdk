#
# Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

include $(GAMMADIR)/make/solaris/makefiles/rules.make
AGENT_DIR = $(GAMMADIR)/agent
include $(GAMMADIR)/make/sa.files
GENERATED = ../generated

# tools.jar is needed by the JDI - SA binding
SA_CLASSPATH = $(BOOT_JAVA_HOME)/lib/tools.jar

# TODO: if it's a modules image, check if SA module is installed.
MODULELIB_PATH= $(BOOT_JAVA_HOME)/lib/modules

# gnumake 3.78.1 does not accept the *s that
# are in AGENT_FILES1 and AGENT_FILES2, so use the shell to expand them
AGENT_FILES1 := $(shell /usr/bin/test -d $(AGENT_DIR) && /bin/ls $(AGENT_FILES1))
AGENT_FILES2 := $(shell /usr/bin/test -d $(AGENT_DIR) && /bin/ls $(AGENT_FILES2))

AGENT_FILES1_LIST := $(GENERATED)/agent1.classes.list
AGENT_FILES2_LIST := $(GENERATED)/agent2.classes.list

SA_CLASSDIR = $(GENERATED)/saclasses

SA_BUILD_VERSION_PROP = "sun.jvm.hotspot.runtime.VM.saBuildVersion=$(SA_BUILD_VERSION)"

SA_PROPERTIES = $(SA_CLASSDIR)/sa.properties

# if $(AGENT_DIR) does not exist, we don't build SA.
all: 
	$(QUIETLY) if [ -d $(AGENT_DIR) ] ; then \
	   $(MAKE) -f sa.make $(GENERATED)/sa-jdi.jar; \
	fi

$(GENERATED)/sa-jdi.jar: $(AGENT_FILES1) $(AGENT_FILES2) agent_files_preclean
	$(QUIETLY) echo "Making $@";
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
	
	$(foreach file,$(AGENT_FILES1),$(shell echo $(file) >> $(AGENT_FILES1_LIST)))
	$(foreach file,$(AGENT_FILES2),$(shell echo $(file) >> $(AGENT_FILES2_LIST)))
	
	$(QUIETLY) $(COMPILE.JAVAC) -source 1.4 -target 1.4 -classpath $(SA_CLASSPATH) -sourcepath $(AGENT_SRC_DIR) -d $(SA_CLASSDIR) @$(AGENT_FILES1_LIST)
	$(QUIETLY) $(COMPILE.JAVAC) -source 1.4 -target 1.4 -classpath $(SA_CLASSPATH) -sourcepath $(AGENT_SRC_DIR) -d $(SA_CLASSDIR) @$(AGENT_FILES2_LIST)
	
	$(QUIETLY) $(COMPILE.RMIC)  -classpath $(SA_CLASSDIR) -d $(SA_CLASSDIR) sun.jvm.hotspot.debugger.remote.RemoteDebuggerServer
	$(QUIETLY) echo "$(SA_BUILD_VERSION_PROP)" > $(SA_PROPERTIES)
	$(QUIETLY) rm -f $(SA_CLASSDIR)/sun/jvm/hotspot/utilities/soql/sa.js
	$(QUIETLY) cp $(AGENT_SRC_DIR)/sun/jvm/hotspot/utilities/soql/sa.js $(SA_CLASSDIR)/sun/jvm/hotspot/utilities/soql
	$(QUIETLY) mkdir -p $(SA_CLASSDIR)/sun/jvm/hotspot/ui/resources
	$(QUIETLY) rm -f $(SA_CLASSDIR)/sun/jvm/hotspot/ui/resources/*
	$(QUIETLY) cp $(AGENT_SRC_DIR)/sun/jvm/hotspot/ui/resources/*.png $(SA_CLASSDIR)/sun/jvm/hotspot/ui/resources/
	$(QUIETLY) cp -r $(AGENT_SRC_DIR)/images/* $(SA_CLASSDIR)/
	$(QUIETLY) $(RUN.JAR) cf $@ -C $(SA_CLASSDIR)/ .
	$(QUIETLY) $(RUN.JAR) uf $@ -C $(AGENT_SRC_DIR) META-INF/services/com.sun.jdi.connect.Connector
	$(QUIETLY) $(RUN.JAVAH) -classpath $(SA_CLASSDIR) -d $(GENERATED) -jni sun.jvm.hotspot.debugger.proc.ProcDebuggerLocal

agent_files_preclean:
	rm -rf $(AGENT_FILES1_LIST) $(AGENT_FILES2_LIST)

clean:
	rm -rf $(SA_CLASSDIR)
	rm -rf $(GENERATED)/sa-jdi.jar
	rm -rf $(AGENT_FILES1_LIST) $(AGENT_FILES2_LIST)
