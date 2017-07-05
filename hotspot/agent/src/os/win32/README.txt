This is a "Simple Windows Debug Server" written for the purpose of
enabling the Serviceability Agent on Win32. It has backends both for
Windows NT 4.0 (using internal Windows APIs for a few routines) as
well as for 95/98/ME/2000 via the Tool Help APIs.

The reason this debug server is necessary is that the Win32 debug APIs
by design tear down the target process when the debugger exits (see
knowledge base article Q164205 on msdn.microsoft.com). On Solaris, one
can attach to and detach from a process with no effect; this is key to
allowing dbx and gcore to work.

The Simple Windows Debug Server effectively implements attach/detach
functionality for arbitrary debug clients. This allows the SA to
attach non-destructively to a process, and will enable gcore for Win32
to be written shortly. While the debugger (the "client" in all of the
source code) is attached, the target process is suspended. (Note that
the debug server could be extended to support resumption of the target
process and transmission of debug events over to the debugger, but
this has been left for the future.)

The makefile (type "nmake") builds two executables: SwDbgSrv.exe,
which is the server process, and SwDbgSub.exe, which is forked by the
server and should not be directly invoked by the user.

The intent is that these two executables can be installed into
C:\WINNT\SYSTEM32 and SwDbgSrv installed to run as a service (on NT),
for example using ServiceInstaller (http://www.kcmultimedia.com/smaster/). 
However, SwDbgSrv can also be run from the command line. It generates
no text output unless the source code is changed to enable debugging
printouts. As long as any processes which have been attached to by the
SA are alive, the SwDbgSrv and any forked SwDbgSub processes must be
left running. Terminating them will cause termination of the target
processes.

The debug server opens port 27000 and accepts incoming connections
from localhost only. The security model assumes that if one can run a
process on the given machine then one basically has access to most or
all of the machine's facilities; this seems to be in line with the
standard Windows security model. The protocol used is text-based, so
one can debug the debug server using telnet. See README-commands.txt
for documentation on the supported commands.

Testing indicates that the performance impact of attaching to a
process (and therefore permanently attaching a debugger) is minimal.
Some serious performance problems had been seen which ultimately
appeared to be a lack of physical memory on the machine running the
system.

Bugs:

This debug server is fundamentally incompatible with the Visual C++
debugger. Once the debug server is used to attach to a process, the
Visual C++ IDE will not be able to attach to the same process (even if
the debug server is "detached" from that process). Note that this
system is designed to work with the same primitives that C and C++
debuggers use (like "symbol lookup" and "read from process memory")
and exposes these primitives to Java, so in the long term we could
solve this problem by implementing platform-specific debug symbol
parsing and a platform-independent C++ debugger in Java.

Note:

The files IOBuf.cpp and IOBuf.hpp are also used in 
building src/os/solaris/agent.
