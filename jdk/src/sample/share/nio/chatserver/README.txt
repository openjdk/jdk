A Simple Chat Server Example

INTRODUCTION
============
This directory contains a very simple chat server, the server takes input from a
socket ("user") and sends it to all other connected sockets ("users") along with
the provided name the user was asked for when first connecting.

The server was written to demonstrate the asynchronous I/O API in JDK 7. 
The sample assumes the reader has some familiarity with the subject matter.

SETUP
=====

The server must be built with version 7 (or later) of the JDK.
The server is built with:

    % mkdir build
    % javac -source 7 -target 7 -d build *.java

EXECUTION
=========

    % java -classpath build ChatServer [-port <port number>]

    Usage:  ChatServer [options]
        options:
            -port port      port number
                default: 5000

CLIENT EXECUTION
================

No client binary is included in the sample.
Connections can be made using for example the telnet command or any program
that supports a raw TCP connection to a port.

SOURCE CODE OVERVIEW
====================
ChatServer is the main class, it handles the startup and handles incoming
connections on the listening sockets. It keeps a list of connected client
and provides methods for sending a message to them.

Client represents a connected user, it provides methods for reading/writing
from/to the underlying socket. It also contains a buffer of input read from
the user.

DataReader provides the interface of the two states a user can
be in. Waiting for a name (and not receiving any messages while doing so, implemented
by NameReader) and waiting for messages from the user (implemented by MessageReader).

ClientReader contains the "main loop" for a connected client. 

NameReader is the initial state for a new client, it sends the user a string and
waits for a response before changing the state to MessageReader.

MessageReader is the main state for a client, it checks for new messages to send to
other clients and reads messages from the client.

FINALLY
=======
This is a sample: it is not production quality and isn't optimized for performance.
