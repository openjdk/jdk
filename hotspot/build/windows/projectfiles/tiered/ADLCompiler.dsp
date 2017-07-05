# Microsoft Developer Studio Project File - Name="ADLCompiler" - Package Owner=<4>
# Microsoft Developer Studio Generated Build File, Format Version 6.00
# ** DO NOT EDIT **

# TARGTYPE "Win32 (x86) Console Application" 0x0103

CFG=ADLCompiler - Win32 Debug
!MESSAGE This is not a valid makefile. To build this project using NMAKE,
!MESSAGE use the Export Makefile command and run
!MESSAGE 
!MESSAGE NMAKE /f "ADLCompiler.mak".
!MESSAGE 
!MESSAGE You can specify a configuration when running NMAKE
!MESSAGE by defining the macro CFG on the command line. For example:
!MESSAGE 
!MESSAGE NMAKE /f "ADLCompiler.mak" CFG="ADLCompiler - Win32 Debug"
!MESSAGE 
!MESSAGE Possible choices for configuration are:
!MESSAGE 
!MESSAGE "ADLCompiler - Win32 Release" (based on "Win32 (x86) Console Application")
!MESSAGE "ADLCompiler - Win32 Debug" (based on "Win32 (x86) Console Application")
!MESSAGE 

# Begin Project
# PROP AllowPerConfigDependencies 0
# PROP Scc_ProjName ""
# PROP Scc_LocalPath ""
CPP=cl.exe
RSC=rc.exe

!IF  "$(CFG)" == "ADLCompiler - Win32 Release"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 0
# PROP BASE Output_Dir "Release"
# PROP BASE Intermediate_Dir "Release"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 0
# PROP Output_Dir ".\adlc\Release"
# PROP Intermediate_Dir ".\adlc\Release"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /W3 /GX /O2 /D "WIN32" /D "NDEBUG" /D "_CONSOLE" /D "_MBCS" /YX /FD /c
# ADD CPP /nologo /W3 /GX /O2 /I "." /I "$(HotSpotWorkSpace)\src\share\vm\opto" /I "$(HotSpotWorkSpace)\src\share\vm\prims" /I "$(HotSpotWorkSpace)\src\share\vm\lookup" /I "$(HotSpotWorkSpace)\src\share\vm\interpreter" /I "$(HotSpotWorkSpace)\src\share\vm\asm" /I "$(HotSpotWorkSpace)\src\share\vm\compiler" /I "$(HotSpotWorkSpace)\src\share\vm\utilities" /I "$(HotSpotWorkSpace)\src\share\vm\code" /I "$(HotSpotWorkSpace)\src\share\vm\oops" /I "$(HotSpotWorkSpace)\src\share\vm\runtime" /I "$(HotSpotWorkSpace)\src\share\vm\memory" /I "$(HotSpotWorkSpace)\src\share\vm\libadt" /I "$(HotSpotWorkSpace)\src\cpu\i486\vm" /I "$(HotSpotWorkSpace)\src\os\win32\vm" /D "WIN32" /D "NDEBUG" /D "_CONSOLE" /D "_MBCS" /FR /FD /c
# SUBTRACT CPP /YX /Yc /Yu
# ADD BASE RSC /l 0x409 /d "NDEBUG"
# ADD RSC /l 0x409 /d "NDEBUG"
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /nologo /o".\adlc\Release\adlc.bsc"
LINK32=link.exe
# ADD BASE LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib /nologo /subsystem:console /machine:I386
# ADD LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib /nologo /subsystem:console /machine:I386 /out:".\bin\adlc.exe"

!ELSEIF  "$(CFG)" == "ADLCompiler - Win32 Debug"

# PROP BASE Use_MFC 0
# PROP BASE Use_Debug_Libraries 1
# PROP BASE Output_Dir "Debug"
# PROP BASE Intermediate_Dir "Debug"
# PROP BASE Target_Dir ""
# PROP Use_MFC 0
# PROP Use_Debug_Libraries 1
# PROP Output_Dir ".\adlc\Debug"
# PROP Intermediate_Dir ".\adlc\Debug"
# PROP Ignore_Export_Lib 0
# PROP Target_Dir ""
# ADD BASE CPP /nologo /W3 /Gm /GX /Zi /Od /D "WIN32" /D "_CONSOLE" /D "_MBCS" /YX /FD /c
# ADD CPP /nologo /ML /W3 /WX /Gm /GX /Zi /Od /I "." /I "$(HotSpotWorkSpace)\src\share\vm\opto" /I "$(HotSpotWorkSpace)\src\share\vm\prims" /I "$(HotSpotWorkSpace)\src\share\vm\lookup" /I "$(HotSpotWorkSpace)\src\share\vm\interpreter" /I "$(HotSpotWorkSpace)\src\share\vm\asm" /I "$(HotSpotWorkSpace)\src\share\vm\compiler" /I "$(HotSpotWorkSpace)\src\share\vm\utilities" /I "$(HotSpotWorkSpace)\src\share\vm\code" /I "$(HotSpotWorkSpace)\src\share\vm\oops" /I "$(HotSpotWorkSpace)\src\share\vm\runtime" /I "$(HotSpotWorkSpace)\src\share\vm\memory" /I "$(HotSpotWorkSpace)\src\share\vm\libadt" /I "$(HotSpotWorkSpace)\src\cpu\i486\vm" /I "$(HotSpotWorkSpace)\src\os\win32\vm" /D "WIN32" /D "DEBUG" /D "_WINDOWS" /D "ASSERT" /Fr /FD /c
# ADD BASE RSC /l 0x409
# ADD RSC /l 0x409
BSC32=bscmake.exe
# ADD BASE BSC32 /nologo
# ADD BSC32 /o".\adlc\Debug\adlc_g.bsc"
# SUBTRACT BSC32 /nologo
LINK32=link.exe
# ADD BASE LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib odbccp32.lib /nologo /subsystem:console /debug /machine:I386 /pdbtype:sept
# ADD LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib advapi32.lib shell32.lib uuid.lib /nologo /subsystem:console /debug /machine:I386 /out:".\bin\adlc_g.exe"

!ENDIF 

# Begin Target

# Name "ADLCompiler - Win32 Release"
# Name "ADLCompiler - Win32 Debug"
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\adlparse.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\archDesc.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\arena.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\dfa.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\dict2.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\filebuff.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\forms.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\formsopt.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\formssel.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\main.cpp"
# SUBTRACT CPP /YX /Yc
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\opto\opcodes.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\output_c.cpp"
# End Source File
# Begin Source File

SOURCE="$(HotSpotWorkSpace)\src\share\vm\adlc\output_h.cpp"
# End Source File
# End Target
# End Project
