/*
 * Copyright (c) 1996, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.rmi.transport.tcp;

import java.io.*;
import java.util.*;
import java.rmi.server.LogStream;

import sun.rmi.runtime.Log;

/**
 * ConnectionMultiplexer manages the transparent multiplexing of
 * multiple virtual connections from one endpoint to another through
 * one given real connection to that endpoint.  The input and output
 * streams for the the underlying real connection must be supplied.
 * A callback object is also supplied to be informed of new virtual
 * connections opened by the remote endpoint.  After creation, the
 * run() method must be called in a thread created for demultiplexing
 * the connections.  The openConnection() method is called to
 * initiate a virtual connection from this endpoint.
 *
 * @author Peter Jones
 */
final class ConnectionMultiplexer {

    /** "multiplex" log level */
    static int logLevel = LogStream.parseLevel(getLogLevel());

    private static String getLogLevel() {
        return java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("sun.rmi.transport.tcp.multiplex.logLevel"));
    }

    /* multiplex system log */
    static final Log multiplexLog =
        Log.getLog("sun.rmi.transport.tcp.multiplex",
                   "multiplex", ConnectionMultiplexer.logLevel);

    /** multiplexing protocol operation codes */
    private final static int OPEN     = 0xE1;
    private final static int CLOSE    = 0xE2;
    private final static int CLOSEACK = 0xE3;
    private final static int REQUEST  = 0xE4;
    private final static int TRANSMIT = 0xE5;

    /** object to notify for new connections from remote endpoint */
    private TCPChannel channel;

    /** input stream for underlying single connection */
    private InputStream in;

    /** output stream for underlying single connection */
    private OutputStream out;

    /** true if underlying connection originated from this endpoint
        (used for generating unique connection IDs) */
    private boolean orig;

    /** layered stream for reading formatted data from underlying connection */
    private DataInputStream dataIn;

    /** layered stream for writing formatted data to underlying connection */
    private DataOutputStream dataOut;

    /** table holding currently open connection IDs and related info */
    private Hashtable connectionTable = new Hashtable(7);

    /** number of currently open connections */
    private int numConnections = 0;

    /** maximum allowed open connections */
    private final static int maxConnections = 256;

    /** ID of last connection opened */
    private int lastID = 0x1001;

    /** true if this mechanism is still alive */
    private boolean alive = true;

    /**
     * Create a new ConnectionMultiplexer using the given underlying
     * input/output stream pair.  The run method must be called
     * (possibly on a new thread) to handle the demultiplexing.
     * @param channel object to notify when new connection is received
     * @param in input stream of underlying connection
     * @param out output stream of underlying connection
     * @param orig true if this endpoint intiated the underlying
     *        connection (needs to be set differently at both ends)
     */
    public ConnectionMultiplexer(
        TCPChannel    channel,
        InputStream   in,
        OutputStream  out,
        boolean       orig)
    {
        this.channel = channel;
        this.in      = in;
        this.out     = out;
        this.orig    = orig;

        dataIn = new DataInputStream(in);
        dataOut = new DataOutputStream(out);
    }

    /**
     * Process multiplexing protocol received from underlying connection.
     */
    public void run() throws IOException
    {
        try {
            int op, id, length;
            Integer idObj;
            MultiplexConnectionInfo info;

            while (true) {

                // read next op code from remote endpoint
                op = dataIn.readUnsignedByte();
                switch (op) {

                // remote endpoint initiating new connection
                case OPEN:
                    id = dataIn.readUnsignedShort();

                    if (multiplexLog.isLoggable(Log.VERBOSE)) {
                        multiplexLog.log(Log.VERBOSE, "operation  OPEN " + id);
                    }

                    idObj = new Integer(id);
                    info =
                        (MultiplexConnectionInfo) connectionTable.get(idObj);
                    if (info != null)
                        throw new IOException(
                            "OPEN: Connection ID already exists");
                    info = new MultiplexConnectionInfo(id);
                    info.in = new MultiplexInputStream(this, info, 2048);
                    info.out = new MultiplexOutputStream(this, info, 2048);
                    synchronized (connectionTable) {
                        connectionTable.put(idObj, info);
                        ++ numConnections;
                    }
                    sun.rmi.transport.Connection conn;
                    conn = new TCPConnection(channel, info.in, info.out);
                    channel.acceptMultiplexConnection(conn);
                    break;

                // remote endpoint closing connection
                case CLOSE:
                    id = dataIn.readUnsignedShort();

                    if (multiplexLog.isLoggable(Log.VERBOSE)) {
                        multiplexLog.log(Log.VERBOSE, "operation  CLOSE " + id);
                    }

                    idObj = new Integer(id);
                    info =
                        (MultiplexConnectionInfo) connectionTable.get(idObj);
                    if (info == null)
                        throw new IOException(
                            "CLOSE: Invalid connection ID");
                    info.in.disconnect();
                    info.out.disconnect();
                    if (!info.closed)
                        sendCloseAck(info);
                    synchronized (connectionTable) {
                        connectionTable.remove(idObj);
                        -- numConnections;
                    }
                    break;

                // remote endpoint acknowledging close of connection
                case CLOSEACK:
                    id = dataIn.readUnsignedShort();

                    if (multiplexLog.isLoggable(Log.VERBOSE)) {
                        multiplexLog.log(Log.VERBOSE,
                            "operation  CLOSEACK " + id);
                    }

                    idObj = new Integer(id);
                    info =
                        (MultiplexConnectionInfo) connectionTable.get(idObj);
                    if (info == null)
                        throw new IOException(
                            "CLOSEACK: Invalid connection ID");
                    if (!info.closed)
                        throw new IOException(
                            "CLOSEACK: Connection not closed");
                    info.in.disconnect();
                    info.out.disconnect();
                    synchronized (connectionTable) {
                        connectionTable.remove(idObj);
                        -- numConnections;
                    }
                    break;

                // remote endpoint declaring additional bytes receivable
                case REQUEST:
                    id = dataIn.readUnsignedShort();
                    idObj = new Integer(id);
                    info =
                        (MultiplexConnectionInfo) connectionTable.get(idObj);
                    if (info == null)
                        throw new IOException(
                            "REQUEST: Invalid connection ID");
                    length = dataIn.readInt();

                    if (multiplexLog.isLoggable(Log.VERBOSE)) {
                        multiplexLog.log(Log.VERBOSE,
                            "operation  REQUEST " + id + ": " + length);
                    }

                    info.out.request(length);
                    break;

                // remote endpoint transmitting data packet
                case TRANSMIT:
                    id = dataIn.readUnsignedShort();
                    idObj = new Integer(id);
                    info =
                        (MultiplexConnectionInfo) connectionTable.get(idObj);
                    if (info == null)
                        throw new IOException("SEND: Invalid connection ID");
                    length = dataIn.readInt();

                    if (multiplexLog.isLoggable(Log.VERBOSE)) {
                        multiplexLog.log(Log.VERBOSE,
                            "operation  TRANSMIT " + id + ": " + length);
                    }

                    info.in.receive(length, dataIn);
                    break;

                default:
                    throw new IOException("Invalid operation: " +
                                          Integer.toHexString(op));
                }
            }
        } finally {
            shutDown();
        }
    }

    /**
     * Initiate a new multiplexed connection through the underlying
     * connection.
     */
    public synchronized TCPConnection openConnection() throws IOException
    {
        // generate ID that should not be already used
        // If all possible 32768 IDs are used,
        // this method will block searching for a new ID forever.
        int id;
        Integer idObj;
        do {
            lastID = (++ lastID) & 0x7FFF;
            id = lastID;

            // The orig flag (copied to the high bit of the ID) is used
            // to have two distinct ranges to choose IDs from for the
            // two endpoints.
            if (orig)
                id |= 0x8000;
            idObj = new Integer(id);
        } while (connectionTable.get(idObj) != null);

        // create multiplexing streams and bookkeeping information
        MultiplexConnectionInfo info = new MultiplexConnectionInfo(id);
        info.in = new MultiplexInputStream(this, info, 2048);
        info.out = new MultiplexOutputStream(this, info, 2048);

        // add to connection table if multiplexer has not died
        synchronized (connectionTable) {
            if (!alive)
                throw new IOException("Multiplexer connection dead");
            if (numConnections >= maxConnections)
                throw new IOException("Cannot exceed " + maxConnections +
                    " simultaneous multiplexed connections");
            connectionTable.put(idObj, info);
            ++ numConnections;
        }

        // inform remote endpoint of new connection
        synchronized (dataOut) {
            try {
                dataOut.writeByte(OPEN);
                dataOut.writeShort(id);
                dataOut.flush();
            } catch (IOException e) {
                multiplexLog.log(Log.BRIEF, "exception: ", e);

                shutDown();
                throw e;
            }
        }

        return new TCPConnection(channel, info.in, info.out);
    }

    /**
     * Shut down all connections and clean up.
     */
    public void shutDown()
    {
        // inform all associated streams
        synchronized (connectionTable) {
            // return if multiplexer already officially dead
            if (!alive)
                return;
            alive = false;

            Enumeration enum_ = connectionTable.elements();
            while (enum_.hasMoreElements()) {
                MultiplexConnectionInfo info =
                    (MultiplexConnectionInfo) enum_.nextElement();
                info.in.disconnect();
                info.out.disconnect();
            }
            connectionTable.clear();
            numConnections = 0;
        }

        // close underlying connection, if possible (and not already done)
        try {
            in.close();
        } catch (IOException e) {
        }
        try {
            out.close();
        } catch (IOException e) {
        }
    }

    /**
     * Send request for more data on connection to remote endpoint.
     * @param info connection information structure
     * @param len number of more bytes that can be received
     */
    void sendRequest(MultiplexConnectionInfo info, int len) throws IOException
    {
        synchronized (dataOut) {
            if (alive && !info.closed)
                try {
                    dataOut.writeByte(REQUEST);
                    dataOut.writeShort(info.id);
                    dataOut.writeInt(len);
                    dataOut.flush();
                } catch (IOException e) {
                    multiplexLog.log(Log.BRIEF, "exception: ", e);

                    shutDown();
                    throw e;
                }
        }
    }

    /**
     * Send packet of requested data on connection to remote endpoint.
     * @param info connection information structure
     * @param buf array containg bytes to send
     * @param off offset of first array index of packet
     * @param len number of bytes in packet to send
     */
    void sendTransmit(MultiplexConnectionInfo info,
                      byte buf[], int off, int len) throws IOException
    {
        synchronized (dataOut) {
            if (alive && !info.closed)
                try {
                    dataOut.writeByte(TRANSMIT);
                    dataOut.writeShort(info.id);
                    dataOut.writeInt(len);
                    dataOut.write(buf, off, len);
                    dataOut.flush();
                } catch (IOException e) {
                    multiplexLog.log(Log.BRIEF, "exception: ", e);

                    shutDown();
                    throw e;
                }
        }
    }

    /**
     * Inform remote endpoint that connection has been closed.
     * @param info connection information structure
     */
    void sendClose(MultiplexConnectionInfo info) throws IOException
    {
        info.out.disconnect();
        synchronized (dataOut) {
            if (alive && !info.closed)
                try {
                    dataOut.writeByte(CLOSE);
                    dataOut.writeShort(info.id);
                    dataOut.flush();
                    info.closed = true;
                } catch (IOException e) {
                    multiplexLog.log(Log.BRIEF, "exception: ", e);

                    shutDown();
                    throw e;
                }
        }
    }

    /**
     * Acknowledge remote endpoint's closing of connection.
     * @param info connection information structure
     */
    void sendCloseAck(MultiplexConnectionInfo info) throws IOException
    {
        synchronized (dataOut) {
            if (alive && !info.closed)
                try {
                    dataOut.writeByte(CLOSEACK);
                    dataOut.writeShort(info.id);
                    dataOut.flush();
                    info.closed = true;
                } catch (IOException e) {
                    multiplexLog.log(Log.BRIEF, "exception: ", e);

                    shutDown();
                    throw e;
                }
        }
    }

    /**
     * Shut down connection upon finalization.
     */
    protected void finalize() throws Throwable
    {
        super.finalize();
        shutDown();
    }
}
