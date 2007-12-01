README.txt


This Poller class demonstrates access to poll(2) functionality in Java.

Requires Solaris production (native threads) JDK 1.2 or later, currently
the C code compiles only on Solaris (SPARC and Intel).

Poller.java is the class, Poller.c is the supporting JNI code.

PollingServer.java is a sample application which uses the Poller class
to multiplex sockets.

SimpleServer.java is the functional equivalent that does not multiplex
but uses a single thread to handle each client connection.

Client.java is a sample application to drive against either server.

To build the Poller class and client/server demo :
 javac PollingServer.java Client.java
 javah Poller
 cc -G -o libpoller.so -I ${JAVA_HOME}/include -I ${JAVA_HOME}/include/solaris\
  Poller.c

You will need to set the environment variable LD_LIBRARY_PATH to search
the directory containing libpoller.so.

To use client/server, bump up your fd limit to handle the connections you
want (need root access to go beyond 1024).  For info on changing your file
descriptor limit, type "man limit".  If you are using Solaris 2.6
or later, a regression in loopback read() performance may hit you at low
numbers of connections, so run the client on another machine.

BASICs of Poller class usage :
 run "javadoc Poller" or see Poller.java for more details.

{
    Poller Mux = new Poller(65535); // allow it to contain 64K IO objects
    
    int fd1 = Mux.add(socket1, Poller.POLLIN);
    ...
    int fdN = Mux.add(socketN, Poller.POLLIN);

    int[] fds = new int[100];
    short[] revents = new revents[100];

    int numEvents = Mux.waitMultiple(100, fds, revents, timeout);

    for (int i = 0; i < numEvents; i++) {
       /*
        * Probably need more sophisticated mapping scheme than this!
	*/
        if (fds[i] == fd1) {
	    System.out.println("Got data on socket1");
	    socket1.getInputStream().read(byteArray);
	    // Do something based upon state of fd1 connection
	}
	...
    }
}

Poller class implementation notes :

  Currently all add(),remove(),isMember(), and waitMultiple() methods
are synchronized for each Poller object.  If one thread is blocked in
pObj.waitMultiple(), another thread calling pObj.add(fd) will block
until waitMultiple() returns.  There is no provided mechanism to
interrupt waitMultiple(), as one might expect a ServerSocket to be in
the list waited on (see PollingServer.java).

  One might also need to interrupt waitMultiple() to remove()
fds/sockets, in which case one could create a Pipe or loopback localhost
connection (at the level of PollingServer) and use a write() to that
connection to interrupt.  Or, better, one could queue up deletions
until the next return of waitMultiple().  Or one could implement an
interrupt mechanism in the JNI C code using a pipe(), and expose that
at the Java level.

  If frequent deletions/re-additions of socks/fds is to be done with
very large sets of monitored fds, the Solaris 7 kernel cache will
likely perform poorly without some tuning.  One could differentiate
between deleted (no longer cared for) fds/socks and those that are
merely being disabled while data is processed on their behalf.  In
that case, re-enabling a disabled fd/sock could put it in it's
original position in the poll array, thereby increasing the kernel
cache performance.  This would best be done in Poller.c.  Of course
this is not necessary for optimal /dev/poll performance.

  Caution...the next paragraph gets a little technical for the
benefit of those who already understand poll()ing fairly well.  Others
may choose to skip over it to read notes on the demo server.

  An optimal solution for frequent enabling/disabling of socks/fds
could involve a separately synchronized structure of "async"
operations.  Using a simple array (0..64k) containing the action
(ADD,ENABLE,DISABLE, NONE), the events, and the index into the poll
array, and having nativeWait() wake up in the poll() call periodically
to process these async operations, I was able to speed up performance
of the PollingServer by a factor of 2x at 8000 connections.  Of course
much of that gain was from the fact that I could (with the advent of
an asyncAdd() method) move the accept() loop into a separate thread
from the main poll() loop, and avoid the overhead of calling poll()
with up to 7999 fds just for an accept.  In implementing the async
Disable/Enable, a further large optimization was to auto-disable fds
with events available (before return from nativeWait()), so I could
just call asyncEnable(fd) after processing (read()ing) the available
data.  This removed the need for inefficient gang-scheduling the
attached PollingServer uses.  In order to separately synchronize the
async structure, yet still be able to operate on it from within
nativeWait(), synchronization had to be done at the C level here.  Due
to the new complexities this introduced, as well as the fact that it
was tuned specifically for Solaris 7 poll() improvements (not
/dev/poll), this extra logic was left out of this demo.


Client/Server Demo Notes :

  Do not run the sample client/server with high numbers of connections
unless you have a lot of free memory on your machine, as it can saturate
CPU and lock you out of CDE just by its very resource intensive nature
(much more so the SimpleServer than PollingServer).

  Different OS versions will behave very differently as far as poll()
performance (or /dev/poll existence) but, generally, real world applications
"hit the wall" much earlier when a separate thread is used to handle
each client connection.  Issues of thread synchronization and locking
granularity become performance killers.  There is some overhead associated
with multiplexing, such as keeping track of the state of each connection; as
the number of connections gets very large, however, this overhead is more
than made up for by the reduced synchronization overhead.

  As an example, running the servers on a Solaris 7 PC (Pentium II-350 x 
2 CPUS) with 1 GB RAM, and the client on an Ultra-2, I got the following
times (shorter is better) :

  1000 connections :

PollingServer took 11 seconds
SimpleServer took 12 seconds

  4000 connections :

PollingServer took 20 seconds
SimpleServer took 37 seconds

  8000 connections :

PollingServer took 39 seconds
SimpleServer took 1:48 seconds

  This demo is not, however, meant to be considered some form of proof
that multiplexing with the Poller class will gain you performance; this
code is actually very heavily biased towards the non-polling server as
very little synchronization is done, and most of the overhead is in the
kernel IO for both servers.  Use of multiplexing may be helpful in
many, but certainly not all, circumstances.

  Benchmarking a major Java server application which can run
in a single-thread-per-client mode or using the  new Poller class showed
Poller provided a 253% improvement in throughput at a moderate load, as
well as a 300% improvement in peak capacity.  It also yielded a 21%
smaller memory footprint at the lower load level.

  Finally, there is code in Poller.c to take advantage of /dev/poll
on OS versions that have that device; however, DEVPOLL must be defined
in compiling Poller.c (and it must be compiled on a machine with
/usr/include/sys/devpoll.h) to use it.  Code compiled with DEVPOLL
turned on will work on machines that don't have kernel support for
the device, as it will fall back to using poll() in those cases.
Currently /dev/poll does not correctly return an error if you attempt
to remove() an object that was never added, but this should be fixed
in an upcoming /dev/poll patch.  The binary as shipped is not built with
/dev/poll support as our build machine does not have devpoll.h.

