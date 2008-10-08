#
# Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

GENERATED = ..\generated

# tools.jar is needed by the JDI - SA binding
SA_CLASSPATH = $(BOOT_JAVA_HOME)\lib\tools.jar

SA_CLASSDIR = $(GENERATED)\saclasses

SA_BUILD_VERSION_PROP = sun.jvm.hotspot.runtime.VM.saBuildVersion=$(SA_BUILD_VERSION)

SA_PROPERTIES = $(SA_CLASSDIR)\sa.properties

default::  $(GENERATED)\sa-jdi.jar

$(GENERATED)\sa-jdi.jar: $(AGENT_ALLFILES:/=\) 
	@if not exist $(SA_CLASSDIR) mkdir $(SA_CLASSDIR)
	@echo ...Building sa-jdi.jar
	@echo ...$(COMPILE_JAVAC) -source 1.4 -classpath $(SA_CLASSPATH) -g -d $(SA_CLASSDIR) ....
	@$(COMPILE_JAVAC) -source 1.4 -classpath $(SA_CLASSPATH) -g -d $(SA_CLASSDIR) $(AGENT_ALLFILES:/=\)
	$(COMPILE_RMIC) -classpath $(SA_CLASSDIR) -d $(SA_CLASSDIR) sun.jvm.hotspot.debugger.remote.RemoteDebuggerServer
	$(QUIETLY) echo $(SA_BUILD_VERSION_PROP) > $(SA_PROPERTIES)
	$(RUN_JAR) cf $@ -C saclasses . 
	$(RUN_JAR) uf $@ -C $(AGENT_SRC_DIR:/=\) META-INF\services\com.sun.jdi.connect.Connector 
	$(RUN_JAVAH) -classpath $(SA_CLASSDIR) -jni sun.jvm.hotspot.debugger.windbg.WindbgDebuggerLocal
	$(RUN_JAVAH) -classpath $(SA_CLASSDIR) -jni sun.jvm.hotspot.debugger.x86.X86ThreadContext 
	$(RUN_JAVAH) -classpath $(SA_CLASSDIR) -jni sun.jvm.hotspot.debugger.ia64.IA64ThreadContext 
	$(RUN_JAVAH) -classpath $(SA_CLASSDIR) -jni sun.jvm.hotspot.debugger.amd64.AMD64ThreadContext 



# This second part is used to build sawindbg.dll
# We currently build it the same way for product, debug, and fastdebug.

SAWINDBG=sawindbg.dll

checkAndBuildSA:: $(SAWINDBG)

# These do not need to be optimized (don't run a lot of code) and it
# will be useful to have the assertion checks in place

!if "$(BUILDARCH)" == "ia64"
SA_CFLAGS = /nologo $(MS_RUNTIME_OPTION) /W3 $(GX_OPTION) /Od /D "WIN32" /D "WIN64" /D "_WINDOWS" /D "_DEBUG" /D "_CONSOLE" /D "_MBCS" /YX /FD /c
!elseif "$(BUILDARCH)" == "amd64"
SA_CFLAGS = /nologo $(MS_RUNTIME_OPTION) /W3 $(GX_OPTION) /Od /D "WIN32" /D "WIN64" /D "_WINDOWS" /D "_DEBUG" /D "_CONSOLE" /D "_MBCS" /YX /FD /c
# On amd64, VS2005 compiler requires bufferoverflowU.lib on the link command line, 
# otherwise we get missing __security_check_cookie externals at link time. 
SA_LINK_FLAGS = bufferoverflowU.lib
!else
SA_CFLAGS = /nologo $(MS_RUNTIME_OPTION) /W3 /Gm $(GX_OPTION) /ZI /Od /D "WIN32" /D "_WINDOWS" /D "_DEBUG" /D "_CONSOLE" /D "_MBCS" /YX /FD /GZ /c
!endif

SASRCFILE = $(AGENT_DIR)/src/os/win32/windbg/sawindbg.cpp
SA_LFLAGS = $(SA_LINK_FLAGS) /nologo /subsystem:console /map /debug /machine:$(MACHINE)

# Note that we do not keep sawindbj.obj around as it would then
# get included in the dumpbin command in build_vm_def.sh

$(SAWINDBG): $(SASRCFILE)
	set INCLUDE=$(SA_INCLUDE)$(INCLUDE)
	$(CPP) @<<
	  /I"$(BootStrapDir)/include" /I"$(BootStrapDir)/include/win32" 
	  /I"$(GENERATED)" $(SA_CFLAGS)
	  $(SASRCFILE)
	  /out:sawindbg.obj
<<
	set LIB=$(SA_LIB)$(LIB)
	$(LINK) /out:$@ /DLL sawindbg.obj dbgeng.lib $(SA_LFLAGS)
	-@rm -f sawindbg.obj

cleanall :
	rm -rf $(GENERATED:\=/)/saclasses
	rm -rf $(GENERATED:\=/)/sa-jdi.jar
!endif
