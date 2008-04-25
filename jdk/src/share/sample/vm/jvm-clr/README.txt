This sample provides C# "Hello World" program that is invoked
from Java application in the same process.

There is no way to invoke .NET methods from Java classes directly,
it is necessary to use native code level.
The sample contains C++ library that can invoke any .NET program by mscorlib library.
Using the JNI the Java application invokes the C# "Hello World".

The sample contains the following files:

Makefile     - make file
README.txt   - this readme
invoked.cs   - the invoked HelloWorld Java program
invoker.java - C# invoker application
invoker.cpp  - C++ wrapper
invokerExp.h - wrapper library exports
invoker.h    - javah generated file with the native method definition

After the success making the following files are produced:

invoked.exe   - the executable HelloWorld .NET program
invoker.class - the compiled Java class that invokes the .NET program
invoker.dll   - the wrapper library

The following environment needs to be set for the correct sample
build and execution:

INCLUDE must contain the paths to:
  1. MS Visual C++ standard include
  2. .NET SDK include
  3. Java includes
  Example: %MSDEV%/VC98/Include;%DOTNET%/Include;%JAVA_HOME%/include;%JAVA_HOME%/include/win32

LIB must contain the paths to:
  1. MS Visual C++ standard libraries
  2. .NET SDK libraries
  Example: %MSDEV%/VC98/Lib;%DOTNET%/Lib

PATH must contain the paths to:
  1. MS Visual C++ standard bin
  2. MS Dev common bin
  3. .NET SDK libraries
  4. Java bin
  Example: %MSDEV%/VC98/Bin;%MSDEV%/Common/MSDev98/Bin;%DOTNET%/Lib;%JAVA_HOME%/bin;%PATH%

To run the sample please do:

java invoker invoked.exe


--Dmitry Ryashchentsev
