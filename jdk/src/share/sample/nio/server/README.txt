        A Simple NIO-based HTTP/HTTPS Server Example


INTRODUCTION
============
This directory contains a simple HTTP/HTTPS server.  HTTP/HTTPS are two
common network protocols that provide for data transfer, and are more
fully described in RFC 2616 and RFC 2818 (Available at
http://www.ietf.org ). HTTPS is essentially HTTP after the connection
has been secured with SSL/TLS.  TLS is the successor to SSL, and is
described in RFC 2246.

This server was written to demonstrate some of the functionality new to
the Java 2 platform.  The demo is not meant to be a full tutorial, and
assumes the reader has some familiarity with the subject matter.

In particular, it shows:

    New I/O (java.nio, java.nio.channels, java.util.regex, java.nio.charset)

        Introduced in version 1.4 of the platform, NIO was designed to
        overcome some of the scalability limitations found in the
        existing blocking java.net.* API's, and to address other
        concepts such as Regular Expression parsing and Character
        Sets.

        This server demonstrates:

            ByteBuffer
            Blocking and Non-Blocking I/O
            SocketChannel
            ServerSocketChannel
            Selector
            CharacterSet
            Pattern matching using Regular Expressions

    JSSE (javax.net.ssl)

	Introduced in version 1.4 of the platform, JSSE provides
	network security using SSL/TLS for java.net.Socket-based
	traffic.  In version 1.5, the SSLEngine API was introduced
	which separates the SSL/TLS functionality from the underlying
	I/O model.  By making this separation, applications can adapt
	I/O and compute strategies to best fit their circumstances.

        This server demonstrates:

            Using SSLEngine to create a HTTPS server
	    Creating simple key material for use with HTTPS

    Concurrency Library (java.util.concurrent)

        Introduced in version 1.5 of the platform, the concurrency
        library provides a mechanism which decouples task submission
        from the mechanics of how each task will be run.

        This server demonstrates:

            A ThreadPool with a fixed number of threads, which is
            based on the number of available processors.


SETUP
=====

The server must be built on version 1.5 (or later) of the platform.
Invoking the following should be sufficient:

    % mkdir build
    % javac -source 1.5 -target 1.5 -d build *.java

The following creates the document root:

    % mkdir root

All documents should be placed in this directory.

For HTTPS, the server authenticates itself to clients by using simple
Public Key Infrastructure (PKI) credentials in the form of
X509Certificates.  You must create the server's credentials before
attempting to run the server in "-secure" mode.  The server is
currently hardcoded to look for its credentials in a file called
"testkeys".

In this example, we'll create credentials for a fictional widget web
site owned by the ubiquitous "Xyzzy, Inc.".  When you run this in your
own environment, replace "widgets.xyzzy.com" with the hostname of your
server.

The easiest way to create the SSL/TLS credentials is to use the
java keytool, by doing the following:

        (<CR> represents your end-of-line key)

    % keytool -genkey -keyalg rsa -keystore testkeys -alias widgets
    Enter keystore password:  passphrase
    What is your first and last name?
    [Unknown]:  widgets.xyzzy.com<CR>
    What is the name of your organizational unit?
    [Unknown]:  Consumer Widgets Group<CR>
    What is the name of your organization?
    [Unknown]:  Xyzzy, Inc.<CR>
    What is the name of your City or Locality?
    [Unknown]:  Arcata<CR>
    What is the name of your State or Province?
    [Unknown]:  CA<CR>
    What is the two-letter country code for this unit?
    [Unknown]:  US<CR>
    Is CN=widgets.xyzzy.com, OU=Consumer Widgets Group, O="Xyzzy, Inc.",
    L=Arcata, ST=CA, C=US correct?
    [no]:  yes<CR>

    Enter key password for <mykey>
    (RETURN if same as keystore password):  <CR>

This directory also contain a very simple URL reader (URLDumper), which
connects to a specified URL and places all output into a specified file.


SERVER EXECUTION
================

    % java -classpath build Server N1

    Usage:  Server <type> [options]
        type:
                B1      Blocking/Single-threaded Server
                BN      Blocking/Multi-threaded Server
                BP      Blocking/Pooled-thread Server
                N1      Nonblocking/Single-threaded Server
                N2      Nonblocking/Dual-threaded Server

        options:
                -port port                port number
                    default:  8000
                -backlog backlog          backlog
                    default:  1024
                -secure                   encrypt with SSL/TLS
		    default is insecure

"http://" URLs should be used with insecure mode, and
"https://" for secure mode.

The "B*" servers use classic blocking I/O:  in other words, calls to
read()/write() will not return until the I/O operation has completed.  The
"N*" servers use non-blocking mode and Selectors to determine which
Channels are ready to perform I/O.

B1:	A single-threaded server which completely services each
	connection before moving to the next.

B2:	A multi-threaded server which creates a new thread for each
	connection.  This is not efficient for large numbers of
	connections.

BP:	A multi-threaded server which creates a pool of threads for use
	by the server.  The Thread pool decides how to schedule those
	threads.

N1:	A single-threaded server.  All accept() and read()/write()
	operations are performed by a single thread, but only after
	being selected for those operations by a Selector.

N2:	A dual-threaded server which performs accept()s in one thread, and
	services requests in a second.  Both threads use select().


CLIENT EXECUTION
================
You can test the server using any standard browser such as Internet
Explorer or Mozilla, but since the browser will not trust the
credentials you just created, you may need to accept the credentials
via the browser's pop-up dialog box.

Alternatively, to use the certificates using the simple included JSSE
client URLDumper, export the server certificate into a new truststore,
and then run the application using the new truststore.

    % keytool -export -keystore testkeys -alias widgets -file widgets.cer
    Enter keystore password:  passphrase<CR>
    Certificate stored in file <widgets.cer>

    % keytool -import -keystore trustCerts -alias widgetServer \
            -file widgets.cer
    Enter keystore password:  passphrase<CR>
    Owner: CN=widgets.xyzzy.com, OU=Consumer, O="xyzzy, inc.", L=Arcata,
    ST=CA, C=US
    Issuer: CN=widgets.xyzzy.com, OU=Consumer, O="xyzzy, inc.",
    L=Arcata, ST=CA, C=US
    Serial number: 4086cc7a
    Valid from: Wed Apr 21 12:33:14 PDT 2004 until: Tue Jul 20 12:33:14
    PDT 2004
    Certificate fingerprints:
        MD5:  39:71:42:CD:BF:0D:A9:8C:FB:8B:4A:CD:F8:6D:19:1F
        SHA1: 69:5D:38:E9:F4:6C:E5:A7:4C:EA:45:8E:FB:3E:F3:9A:84:01:6F:22
    Trust this certificate? [no]:  yes<CR>
    Certificate was added to keystore

    % java -classpath build -Djavax.net.ssl.trustStore=trustCerts \
        -Djavax.net.ssl.TrustStorePassword=passphrase \
        URLDumper https://widgets.xyzzy.com:8000/ outputFile

NOTE:  The server must be run with "-secure" in order to receive
"https://" URLs.

WARNING:  This is just a simple example for code exposition, you should
spend more time understanding PKI security concerns.


SOURCE CODE OVERVIEW
====================

The main class is Server, which handles program startup, and is
subclassed by the "B*" and "N*" server classes.

Following a successful accept(), the "B*" variants each create a
RequestServicer object to perform the actual request/reply operations.  The
primary differences between the different "B*" servers is how the
RequestServicer is actually run:

    B1:	RequestServicer.run() is directly called.
    BN:	A new thread is started, and the thread calls RequestServicer.run().
    BP:	A ThreadPool is created, and the pool framework is given Runnable
	tasks to complete.

In the "N*" variations, a Dispatcher object is created, which is
responsible for performing the select, and then issuing the
corresponding handler:

    N1:	A single thread is used for all accept()/read()/write() operations
    N2:	Similar to N1, but a separate thread is used for the accept()
	operations.

In all cases, once the connection has been accepted, a ChannelIO object
is created to handle all I/O.  In the insecure case, the corresponding
SocketChannel methods are directly called.  However in the secure case,
more manipulations are needed to first secure the channel, then
encrypt/decrypt the data, and finally properly send any shutdown
messages.  ChannelIOSecure extends ChannelIO, and provides the secure
variants of the corresponding ChannelIO calls.

RequestServicer and RequestHandler are the main drivers for the
blocking and non-blocking variants, respectively.  They are responsible
for:

    Performing any initial handshaking

    Reading the request data
        All data is stored in a local buffer in the ChannelIO
        structure.

    Parsing the request
        The request data is obtained from the ChannelIO object, and
        is processed by Request class, which represents the
        parsed URI address.

    Locating/preparing/sending the data or reporting error conditions.
        A Reply object is created which represents the entire object to send,
        including the HTTP/HTTPS headers.

    Shutdown/closing the channel.


CLOSING THOUGHTS
================
This example represents a simple server: it is not production quality.
It was primarily meant to demonstrate the new APIs in versions 1.4 and
1.5 of the platform.

This example could certainly be expanded to address other areas of
concern: for example, assigning multiple threads to handle the selected
Channels, or delegating SSLEngine tasks to multiple threads.  There are
so many ways to implement compute and I/O strategies, we encourage you
to experiment and find what works best for your situation.

To steal a phrase from many textbooks:

    "It is left as an exercise for the reader..."

