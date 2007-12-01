This sample provides Java "Hello World" program that is invoked
from C# application in the same process.

The problem of direct call of the JVM API from CLR applications
by PInvoke interface is the JVM API functions do not have static
adresses, they need to be got by JNI_CreateJavaVM() call.
The sample contains C++ libraty that wraps JVM API calls by the
static functions that are called from the C# application by
PInvoke interface.

The sample contains the following files:

Makefile      - make file
README.txt    - this readme
invoked.java  - the invoked HelloWorld Java program
invoker.cs    - C# invoker application
jinvoker.cpp  - C++ wrapper
jinvokerExp.h - wrapper library exports

After the success making the following files are produced:

invoked.class - the compiled HelloWorld class
invoker.exe   - the executable .NET program that invokes Java
jinvoker.dll  - the wrapper library

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
  3. jvm.dll
  Example: %MSDEV%/VC98/Lib;%DOTNET%/Lib;%JAVA_HOME%/jre/bin/client

PATH must contain the paths to:
  1. MS Visual C++ standard bin
  2. MS Dev common bin
  3. .NET SDK libraries
  4. Java bin
  5. jvm.dll
  Example: %MSDEV%/VC98/Bin;%MSDEV%/Common/MSDev98/Bin;%DOTNET%/Lib;%JAVA_HOME%/bin;%JAVA_HOME%/jre/bin/client;%PATH%

To run the sample please do:

  invoker.exe invoked


--Dmitry Ryashchentsev
