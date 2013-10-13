#
# Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

# This makefile is used to build Serviceability Agent code
# and generate JNI header file for native methods.

AGENT_DIR = $(WorkSpace)/agent
checkAndBuildSA::

!if "$(BUILD_WIN_SA)" != "1"
# Already warned about this in build.make
!else

# This first part is used to build sa-jdi.jar
!include $(WorkSpace)/make/windows/makefiles/rules.make
!include $(WorkSpace)/make/sa.files

GENERATED = ../generated

# tools.jar is needed by the JDI - SA binding
SA_CLASSPATH = $(BOOT_JAVA_HOME)/lib/tools.jar

SA_CLASSDIR = $(GENERATED)/saclasses

SA_BUILD_VERSION_PROP = sun.jvm.hotspot.runtime.VM.saBuildVersion=$(SA_BUILD_VERSION)

SA_PROPERTIES = $(SA_CLASSDIR)/sa.properties

default::  $(GENERATED)/sa-jdi.jar

# Remove the space between $(SA_BUILD_VERSION_PROP) and > below as it adds a white space
# at the end of SA version string and causes a version mismatch with the target VM version.

$(GENERATED)/sa-jdi.jar: $(AGENT_FILES)
	$(QUIETLY) mkdir -p $(SA_CLASSDIR)
	@echo ...Building sa-jdi.jar into $(SA_CLASSDIR)
	@echo ...$(COMPILE_JAVAC) -classpath $(SA_CLASSPATH) -d $(SA_CLASSDIR) ....
	@$(COMPILE_JAVAC) -classpath $(SA_CLASSPATH) -sourcepath $(AGENT_SRC_DIR) -d $(SA_CLASSDIR) $(AGENT_FILES)
	$(COMPILE_RMIC) -classpath $(SA_CLASSDIR) -d $(SA_CLASSDIR) sun.jvm.hotspot.debugger.remote.RemoteDebuggerServer
	$(QUIETLY) echo $(SA_BUILD_VERSION_PROP)> $(SA_PROPERTIES)
	$(QUIETLY) rm -f $(SA_CLASSDIR)/sun/jvm/hotspot/utilities/soql/sa.js
	$(QUIETLY) cp $(AGENT_SRC_DIR)/sun/jvm/hotspot/utilities/soql/sa.js $(SA_CLASSDIR)/sun/jvm/hotspot/utilities/soql
	$(QUIETLY) rm -rf $(SA_CLASSDIR)/sun/jvm/hotspot/ui/resources
	$(QUIETLY) mkdir $(SA_CLASSDIR)/sun/jvm/hotspot/ui/resources
	$(QUIETLY) cp $(AGENT_SRC_DIR)/sun/jvm/hotspot/ui/resources/*.png $(SA_CLASSDIR)/sun/jvm/hotspot/ui/resources
	$(QUIETLY) cp -r $(AGENT_SRC_DIR)/images/* $(SA_CLASSDIR)
	$(RUN_JAR) cf $@ -C $(SA_CLASSDIR) .
	$(RUN_JAR) uf $@ -C $(AGENT_SRC_DIR) META-INF/services/com.sun.jdi.connect.Connector
	$(RUN_JAVAH) -classpath $(SA_CLASSDIR) -jni sun.jvm.hotspot.debugger.windbg.WindbgDebuggerLocal
	$(RUN_JAVAH) -classpath $(SA_CLASSDIR) -jni sun.jvm.hotspot.debugger.x86.X86ThreadContext 
	$(RUN_JAVAH) -classpath $(SA_CLASSDIR) -jni sun.jvm.hotspot.debugger.amd64.AMD64ThreadContext 
	$(RUN_JAVAH) -classpath $(SA_CLASSDIR) -jni sun.jvm.hotspot.asm.Disassembler



# This second part is used to build sawindbg.dll
# We currently build it the same way for product, debug, and fastdebug.

SAWINDBG=sawindbg.dll

checkAndBuildSA:: $(SAWINDBG)

# These do not need to be optimized (don't run a lot of code) and it
# will be useful to have the assertion checks in place

!if "$(BUILDARCH)" == "ia64"
SA_CFLAGS = -nologo $(MS_RUNTIME_OPTION) -W3 $(GX_OPTION) -Od -D "WIN32" -D "WIN64" -D "_WINDOWS" -D "_DEBUG" -D "_CONSOLE" -D "_MBCS" -YX -FD -c
!elseif "$(BUILDARCH)" == "amd64"
SA_CFLAGS = -nologo $(MS_RUNTIME_OPTION) -W3 $(GX_OPTION) -Od -D "WIN32" -D "WIN64" -D "_WINDOWS" -D "_DEBUG" -D "_CONSOLE" -D "_MBCS" -YX -FD -c
!if "$(COMPILER_NAME)" == "VS2005"
# On amd64, VS2005 compiler requires bufferoverflowU.lib on the link command line, 
# otherwise we get missing __security_check_cookie externals at link time. 
SA_LD_FLAGS = bufferoverflowU.lib
!endif
!else
SA_CFLAGS = -nologo $(MS_RUNTIME_OPTION) -W3 -Gm $(GX_OPTION) -Od -D "WIN32" -D "_WINDOWS" -D "_DEBUG" -D "_CONSOLE" -D "_MBCS" -YX -FD -GZ -c
!if "$(ENABLE_FULL_DEBUG_SYMBOLS)" == "1"
SA_CFLAGS = $(SA_CFLAGS) -ZI
!endif
!endif
!if "$(MT)" != ""
SA_LD_FLAGS = -manifest $(SA_LD_FLAGS)
!endif
SASRCFILE = $(AGENT_DIR)/src/os/win32/windbg/sawindbg.cpp
SA_LFLAGS = $(SA_LD_FLAGS) -nologo -subsystem:console -machine:$(MACHINE)
!if "$(ENABLE_FULL_DEBUG_SYMBOLS)" == "1"
SA_LFLAGS = $(SA_LFLAGS) -map -debug
!endif
!if "$(BUILDARCH)" == "i486"
SA_LFLAGS = $(SAFESEH_FLAG) $(SA_LFLAGS)
!endif

# Note that we do not keep sawindbj.obj around as it would then
# get included in the dumpbin command in build_vm_def.sh

# In VS2005 or VS2008 the link command creates a .manifest file that we want
# to insert into the linked artifact so we do not need to track it separately.
# Use ";#2" for .dll and ";#1" for .exe in the MT command below:
$(SAWINDBG): $(SASRCFILE)
	set INCLUDE=$(SA_INCLUDE)$(INCLUDE)
	$(CXX) @<<
	  -I"$(BootStrapDir)/include" -I"$(BootStrapDir)/include/win32" 
	  -I"$(GENERATED)" $(SA_CFLAGS)
	  $(SASRCFILE)
	  -out:$*.obj
<<
	set LIB=$(SA_LIB)$(LIB)
	$(LD) -out:$@ -DLL $*.obj dbgeng.lib $(SA_LFLAGS)
!if "$(MT)" != ""
	$(MT) -manifest $(@F).manifest -outputresource:$(@F);#2
!endif
!if "$(ENABLE_FULL_DEBUG_SYMBOLS)" == "1"
!if "$(ZIP_DEBUGINFO_FILES)" == "1"
	$(ZIPEXE) -q $*.diz $*.map $*.pdb
	$(RM) $*.map $*.pdb
!endif
!endif
	-@rm -f $*.obj

cleanall :
	rm -rf $(GENERATED)/saclasses
	rm -rf $(GENERATED)/sa-jdi.jar
!endif
