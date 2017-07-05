#
# Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

SERVER=SwDbgSrv.exe
SUBPROCESS=SwDbgSub.exe

SERVER_SOURCES =   \
  Buffer.cpp       \
  Dispatcher.cpp   \
  initWinsock.cpp  \
  IOBuf.cpp        \
  ioUtils.cpp      \
  isNT4.cpp        \
  nt4internals.cpp \
  procList.cpp     \
  Reaper.cpp       \
  SwDbgSrv.cpp     \
  serverLists.cpp  \
  toolHelp.cpp

SUBPROCESS_SOURCES = \
  SwDbgSub.cpp       \
  Buffer.cpp         \
  IOBuf.cpp          \
  isNT4.cpp          \
  libInfo.cpp        \
  Monitor.cpp        \
  nt4internals.cpp   \
  toolHelp.cpp

SERVER_OBJS     = $(SERVER_SOURCES:.cpp=.obj)
SUBPROCESS_OBJS = $(SUBPROCESS_SOURCES:.cpp=.obj)

CPP=cl.exe
LINK32=link.exe

# These do not need to be optimized (don't run a lot of code) and it
# will be useful to have the assertion checks in place

CFLAGS=/nologo /MD /W3 /Gm /GX /ZI /Od /D "WIN32" /D "_DEBUG" /D "_CONSOLE" /D "_MBCS" /YX /FD /GZ /c

LIBS=kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib          \
     ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib kernel32.lib user32.lib gdi32.lib     \
     winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib \
     odbccp32.lib ws2_32.lib  /nologo /subsystem:console /debug /machine:I386

default: $(SERVER) $(SUBPROCESS)

$(SERVER): $(SERVER_OBJS)
	$(LINK32) /out:$@ $(SERVER_OBJS) $(LIBS)

$(SUBPROCESS): $(SUBPROCESS_OBJS)
	$(LINK32) /out:$@ $(SUBPROCESS_OBJS) $(LIBS)

clean:
	rm -f *.obj *.idb *.pch *.pdb *.ncb *.opt *.plg *.exe *.ilk

.cpp.obj:
	@ $(CPP) $(CFLAGS) /o $@ $<
