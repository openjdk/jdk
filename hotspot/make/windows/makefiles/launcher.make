#
# Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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


LAUNCHER_FLAGS=$(CXX_FLAGS) $(ARCHFLAG) \
	/D FULL_VERSION=\"$(HOTSPOT_RELEASE_VERSION)\" \
	/D JDK_MAJOR_VERSION=\"$(JDK_MAJOR_VERSION)\" \
	/D JDK_MINOR_VERSION=\"$(JDK_MINOR_VERSION)\" \
	/D GAMMA \
	/D LAUNCHER_TYPE=\"gamma\" \
	/D _CRT_SECURE_NO_WARNINGS \
	/D _CRT_SECURE_NO_DEPRECATE \
	/D LINK_INTO_LIBJVM \
	/I $(WorkSpace)\src\os\windows\launcher \
	/I $(WorkSpace)\src\share\tools\launcher \
	/I $(WorkSpace)\src\share\vm\prims \
	/I $(WorkSpace)\src\share\vm \
	/I $(WorkSpace)\src\cpu\$(Platform_arch)\vm \
	/I $(WorkSpace)\src\os\windows\vm

LD_FLAGS=/manifest $(HS_INTERNAL_NAME).lib kernel32.lib user32.lib /nologo /machine:$(MACHINE) /map /debug /subsystem:console 

!if "$(COMPILER_NAME)" == "VS2005"
# This VS2005 compiler has /GS as a default and requires bufferoverflowU.lib
#    on the link command line, otherwise we get missing __security_check_cookie
#    externals at link time. Even with /GS-, you need bufferoverflowU.lib.
BUFFEROVERFLOWLIB = bufferoverflowU.lib
LD_FLAGS = $(LD_FLAGS) $(BUFFEROVERFLOWLIB)
!endif

!if "$(COMPILER_NAME)" == "VS2010" && "$(BUILDARCH)" == "i486"
LD_FLAGS = /SAFESEH $(LD_FLAGS)
!endif

LAUNCHERDIR = $(WorkSpace)/src/os/windows/launcher
LAUNCHERDIR_SHARE = $(WorkSpace)/src/share/tools/launcher

OUTDIR = launcher

{$(LAUNCHERDIR)}.c{$(OUTDIR)}.obj:
	-mkdir $(OUTDIR) 2>NUL >NUL
        $(CXX) $(LAUNCHER_FLAGS) /c /Fo$@ $<

{$(LAUNCHERDIR_SHARE)}.c{$(OUTDIR)}.obj:
	-mkdir $(OUTDIR) 2>NUL >NUL
        $(CXX) $(LAUNCHER_FLAGS) /c /Fo$@ $<

$(OUTDIR)\*.obj: $(LAUNCHERDIR)\*.c $(LAUNCHERDIR)\*.h $(LAUNCHERDIR_SHARE)\*.c $(LAUNCHERDIR_SHARE)\*.h

launcher: $(OUTDIR)\java.obj $(OUTDIR)\java_md.obj $(OUTDIR)\jli_util.obj
	echo $(JAVA_HOME) > jdkpath.txt  
	$(LD) $(LD_FLAGS) /out:hotspot.exe $**
