/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


/*
 * @test
 * @key stress
 *
 * @summary converted from VM testbase nsk/stress/network/network003.
 * VM testbase keywords: [stress, slow, nonconcurrent, quick]
 * VM testbase readme:
 * DESCRIPTION
 *     This test transfers huge amount of data between one server and multiple
 *     clients communicating via TCP/IP sockets, and checks if those data are
 *     transfered correctly. All TCP/IP sockets are attached to local host
 *     (by its domain name), or to the ``localhost'' loopback (having the IP
 *     address 127.0.0.1).
 *     In this test, 128 client/server connections are established. Once a
 *     connection is established, client passes a large data parcel to server,
 *     and server reads that parcel and checks if it is same as expected
 *     (byte-to-byte equality is desired). Then server passes (some other) parcel
 *     to the client, and client reads and verifies those bytes. This ping-pong
 *     game is repeated 128 times; and after that each pair of sockets checks if
 *     there are no extra bytes accudentally passed through their connection.
 *     Parcels lengths and contents are chosen randomly, and average parcel
 *     length is 128 bytes. So totally, each pair of sockets passes ~16Kb of
 *     data to each other, and thus ~32Kb of data are transfered by each sockets
 *     pair. Totally, ~4Mb of data are transfered by all client/server pairs.
 * COMMENTS
 *     The production Solaris_JDK_1.3-b12 Server VM crashes under this test:
 *         #
 *         # HotSpot Virtual Machine Error, Unexpected Signal 10
 *         # Please report this error at
 *         # http://java.sun.com/cgi-bin/bugreport.cgi
 *         #
 *         # Error ID: 4F533F534F4C415249530E43505007D9 01
 *         #
 *         # Problematic Thread: prio=5 tid=0x214418 nid=0x103 runnable
 *         #
 *     (ErrorID == "os_solaris.cpp, 2009")
 *
 * @run main/othervm nsk.stress.network.network003
 */

package nsk.stress.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * This test transfers huge amount of data between one server and multiple
 * clients communicating via TCP/IP sockets, and checks if those data are
 * transfered correctly. All TCP/IP sockets are attached to local host
 * (by its domain name), or to the ``localhost'' loopback (having the IP
 * address 127.0.0.1).
 * <p>
 * <p>In this test, 128 client/server connections are established. Once a
 * connection is established, client passes a large data parcel to server,
 * and server reads that parcel and checks if it is same as expected
 * (byte-to-byte equality is desired). Then server passes (some other) parcel
 * to the client, and client reads and verifies those bytes. This ping-pong
 * game is repeated 128 times; and after that each pair of sockets checks if
 * there are no extra bytes accudentally passed through their connection.
 * <p>
 * <p>Parcels lengths and contents are chosen randomly, and average parcel
 * length is 128 bytes. So totally, each pair of sockets passes ~16Kb of
 * data to each other, and thus ~32Kb of data are transfered by each sockets
 * pair. Totally, ~4Mb of data are transfered by all client/server pairs.
 */
public class network003 {
    /**
     * Do actually display optional reports?
     */
    static private final boolean DEBUG_MODE = false;

    /**
     * Errors and optional reports log. Usually <code>System.out</code>.
     */
    static private PrintStream out = System.out;

    /**
     * Print error message: all clients and servers may print concurently.
     */
    static private synchronized void println(Object message) {
        out.println(message.toString());
    }

    /**
     * Display optional report: comment ca va.
     */
    static private void display(Object report) {
        if (DEBUG_MODE)
            println(report.toString());
    }

    /**
     * Maximal number of connections this test should open simultaneously.
     */
    private final static int MAX_CONNECTIONS = 128;

    /**
     * Check few more connections to make sure that MAX_CONNECTIONS are safe.
     */
    private final static int CONNECTIONS_RESERVE = 10;

    /**
     * Number of client/server connections to establish.
     */
    private static final int CONNECTIONS = detectOSLimitation();

    /**
     * Number of parcels to be sent/recieved.
     */
    private static final int DATA_PARCELS = 128;

    /**
     * Maximal length of data parcel to be sent/recieved
     * (it equals to 256 bytes now).
     */
    private static final int MAX_PARCEL = 1 << 8;

    /**
     * How many IP sockets can we open simultaneously?
     * Check if <code>MAX_CONNECTIONS</code> connections
     * can be open simultaneously.
     */
    private static int detectOSLimitation() {
        final int CONNECTIONS_TO_TRY = MAX_CONNECTIONS + CONNECTIONS_RESERVE;
        ServerSocket ssoc[] = new ServerSocket[CONNECTIONS_TO_TRY];
        display("--- Trying to open " + CONNECTIONS_TO_TRY + " connections:");
        int i;
        for (i = 0; i < CONNECTIONS_TO_TRY; i++)
            try {
                ssoc[i] = new ServerSocket(0);
                display("--- Open: ssoc[" + i + "] = " + ssoc[i]);
            } catch (IOException ioe) {
                display("--- OOPS! -- failed to open connection #" + i);
                break;
            }
        display("--- Could open " +
                (i < CONNECTIONS_TO_TRY ? "only " : "") + i + " connections.");
        display("--- Closing them:");
        for (int j = 0; j < i; j++)
            try {
                ssoc[j].close();
            } catch (IOException ioe) {
                throw new Error("FATAL error while loading the test: " + ioe);
            }
        display("--- OK.");
        int safeConnections = i - CONNECTIONS_RESERVE;
        if (safeConnections < 1)
            safeConnections = 1;
        if (safeConnections < MAX_CONNECTIONS) {
            println("# ------------------------- CAUTION: -------------------");
            println("# While checking the OS limitations, the test found that");
            println("# only " + i + " TCP/IP socket connections could be safely open");
            println("# simultaneously. However, possibility to open at least");
            println("# " + MAX_CONNECTIONS + "+" + CONNECTIONS_RESERVE
                    + " connections were expected.");
            println("# ");
            println("# So, the test will check only " + safeConnections + " connection"
                    + (safeConnections == 1 ? "" : "s") + " which seem");
            println("# safe to be open simultaneously.");
            println("# ------------------------------------------------------");
        }
        return safeConnections;
    }

    /**
     * Server thread intended to reply to data parcels sent by Client thread.
     */
    static private class Server extends Thread {
        /**
         * This server thread listens the single socket.
         */
        private ServerSocket serverSocket;

        /**
         * Address and port of this server socket.
         */
        public String toString() {
            return serverSocket.toString();
        }

        /**
         * Did the thread failed? If yes, what is the failure's reason.
         */
        Exception exception = null;

        /**
         * What is the port number this socket is listening for?
         */
        int getPort() {
            return serverSocket.getLocalPort();
        }

        /**
         * Find some free port at the given <code>address</code>
         * and attach new server to hear that port.
         */
        Server(InetAddress address) throws IOException {
            int someFreePort = 0;
            int backlog = 50; // default for new ServerSocket(port)
            serverSocket = new ServerSocket(someFreePort, backlog, address);
        }

        /**
         * Accept connection, then read/respond <code>DATA_PARCELS</code> parcels
         * of random data. Set initial seed for pseudo-random numbers generator
         * to the value of the local port number.
         *
         * @see #DATA_PARCELS
         * @see #getPort()
         */
        public void run() {
            try {
                Socket socket = serverSocket.accept();
                display("Server socket: " + socket);

                InputStream istream = socket.getInputStream();
                OutputStream ostream = socket.getOutputStream();

                Random random = new Random(getPort());

                for (int i = 0; i < DATA_PARCELS; i++) {
                    Parcel etalon = new Parcel(random);

                    Parcel sample = new Parcel(istream); // read
                    if (!sample.equals(etalon)) {
                        println("Server thread for port #"
                                + getPort() + " got unexpected parcel:\n"
                                + "sample=" + sample + "\n"
                                + "etalon=" + etalon);
                        throw new TestFailure(
                                "server has read unexpected parcel");
                    }

                    etalon.send(ostream);
                    ostream.flush();
                }

                int datum = istream.read(); // wait for client close()
                if (datum >= 0)
                    throw new TestFailure(
                            "server has read ambigous byte: " + datum);

                ostream.close(); // implies: socket.close();

            } catch (Exception oops) {
                exception = oops;
            }
        }

    }

    /**
     * Client thread intended to send data parcels to Server thread and
     * to recieve the server's replies.
     */
    static private class Client extends Thread {
        /**
         * This thread uses the single client socket.
         */
        private Socket socket;

        /**
         * Address and port of this socket.
         */
        public String toString() {
            return socket.toString();
        }

        /**
         * Did the thread failed? If yes, what is the failure's reason.
         */
        Exception exception = null;

        /**
         * Connect client socket on the given <code>address</code>
         * and <code>port</code>.
         */
        Client(InetAddress address, int port) throws IOException {
            socket = new Socket(address, port);
        }

        /**
         * What is the port number this socket is listening for?
         */
        int getPort() {
            return socket.getPort();
        }


        /**
         * Establish connection, then read/respond <code>DATA_PARCELS</code> parcels
         * of random data. Set initial seed for pseudo-random numbers generator
         * to the value of the local port number.
         *
         * @see #DATA_PARCELS
         * @see #getPort()
         */
        public void run() {
            try {
                InputStream istream = socket.getInputStream();
                OutputStream ostream = socket.getOutputStream();

                Random random = new Random(getPort());

                for (int i = 0; i < DATA_PARCELS; i++) {
                    Parcel etalon = new Parcel(random);
                    etalon.send(ostream);
                    ostream.flush();

                    Parcel sample = new Parcel(istream); // read
                    if (!sample.equals(etalon)) {
                        println("Client thread for port #"
                                + getPort() + " got unexpected parcel:\n"
                                + "sample=" + sample + "\n"
                                + "etalon=" + etalon);
                        throw new TestFailure(
                                "parcel context is unexpected to client");
                    }
                }

                if (istream.available() > 0) {
                    int datum = istream.read();
                    throw new TestFailure(
                            "client has read ambigous byte: " + datum);
                }
                ostream.close(); // implies: socket.close()

            } catch (Exception oops) {
                exception = oops;
            }
        }

    }

    /**
     * A data parcel to sent/recieved between Client and Server threads.
     * When data parcel is sent, first 4 bytes transfered encode the size
     * of the parcel (i.e.: number of data bytes in the parcel's contents).
     * Then the parcel's contents bytes are transered.
     */
    static class Parcel {
        private byte[] parcel;

        /**
         * Display all bytes as integer values from 0 to 255;
         * or return ``<tt>null</tt>'' if this Parcel is not
         * yet initialized.
         */
        public String toString() {
            if (parcel == null)
                return "null";
            String s = "{";
            for (int i = 0; i < parcel.length; i++)
                s += (i > 0 ? ", " : "") + ((int) parcel[i] & 0xFF);
            return s + "}";
        }

        /**
         * Generate new <code>parcel[]</code> array using the given
         * <code>random</code> numbers generator. Client and Server
         * threads should use identical <code>random</code> generators,
         * so that those threads could generate equal data parcels and
         * check the parcel just transfered.
         */
        public Parcel(Random random) {
            int size = random.nextInt(MAX_PARCEL) + 1;
            parcel = new byte[size];
            for (int i = 0; i < size; i++)
                parcel[i] = (byte) random.nextInt(256);
        }

        /**
         * Read exactly <code>size</code> bytes from the <code>istream</code>
         * if possible, or throw <code>TestFailure</code> if unexpected end of
         * <code>istream</code> occurs.
         */
        private static byte[] readBytes(int size, InputStream istream)
                throws IOException {

            byte data[] = new byte[size];
            for (int i = 0; i < size; i++) {
                int datum = istream.read();
                if (datum < 0)
                    throw new TestFailure(
                            "unexpected EOF: have read: " + i + " bytes of " + size);
                data[i] = (byte) datum;
            }
            return data;
        }

        /**
         * Read 4 bytes from <code>istream</code> and threat them to encode
         * size of data parcel following these 4 bytes.
         */
        private static int getSize(InputStream istream) throws IOException {
            byte data[] = readBytes(4, istream);
            int data0 = (int) data[0] & 0xFF;
            int data1 = (int) data[1] & 0xFF;
            int data2 = (int) data[2] & 0xFF;
            int data3 = (int) data[3] & 0xFF;
            int sizeWord = data0 + (data1 << 8) + (data2 << 16) + (data3 << 24);
            int size = sizeWord + 1;
            if (size <= 0)
                throw new TestFailure("illegal size: " + size);
            return size;
        }

        /**
         * Send 4 bytes encoding actual size of the parcel just to be transfered.
         */
        private static void putSize(OutputStream ostream, int size)
                throws IOException {

            if (size <= 0)
                throw new TestFailure("illegal size: " + size);

            int sizeWord = size - 1;
            byte data[] = new byte[4];
            data[0] = (byte) sizeWord;
            data[1] = (byte) (sizeWord >> 8);
            data[2] = (byte) (sizeWord >> 16);
            data[3] = (byte) (sizeWord >> 24);
            ostream.write(data);
        }

        /**
         * Recieve data parcel.
         */
        public Parcel(InputStream istream) throws IOException {
            int size = getSize(istream);
            parcel = readBytes(size, istream);
        }

        /**
         * Send <code>this</code> data parcel.
         */
        public void send(OutputStream ostream) throws IOException {
            int size = parcel.length;
            putSize(ostream, size);
            ostream.write(parcel);
        }

        /**
         * Check byte-to-byte equality between <code>this</code> and the
         * <code>other</code> parcels.
         */
        public boolean equals(Parcel other) {
            if (this.parcel.length != other.parcel.length)
                return false;
            int size = parcel.length;
            for (int i = 0; i < size; i++)
                if (this.parcel[i] != other.parcel[i])
                    return false;
            return true;
        }

    }

    /**
     * Server or Client thread may throw this exception to report the test
     * failure.
     */
    static class TestFailure extends RuntimeException {
        /**
         * Report particular <code>purpose</code> of the test failure.
         */
        public TestFailure(String purpose) {
            super(purpose);
        }

    }

    /**
     * Attach client and server sockets to the local host, and check if
     * huge amount of data could be correctly transfered between these
     * sockets.
     * <p>
     * <p>Command-line parameters provided with <code>args[]</code> may
     * prompt the local host IP address or domain name. Execute:
     * <br>&nbsp;&nbsp;
     * <code>java network003 [<i>IP-address</i> | <i>host_name</i> |
     * localhost ]</code>
     * <br>where parameters are:
     * <br>&nbsp;&nbsp;
     * <code><i>IP-address</i></code> - local hots's address, or 127.0.0.1
     * <br>&nbsp;&nbsp;
     * <code><i>host_name</i></code> - local host's domain name, or the
     * keyword ``<code>localhost</code>''
     * <br>&nbsp;&nbsp;
     * <code>localhost</code> - placeholder for the IP-address 127.0.0.1
     * <br>By default, the test uses the Internet address available via
     * the method <code>InetAddress.getLocalHost()</code>
     */
    public static int run(String args[], PrintStream out) {
        network003.out = out;

        //
        // Get IP address of the local machine.
        //

        InetAddress address = null;
        try {
            switch (args.length) {
                case 0:
                    address = InetAddress.getLocalHost();
                    break;
                case 1:
                    String hostName = args[0];
                    address = InetAddress.getByName(args[0]);
                    break;
                default:
                    println("Use:");
                    println("    java network003");
                    println("or:");
                    println("    java network003 ${IP_ADDRESS}");
                    println("or:");
                    println("    java network003 ${HOST_NAME}");
                    println("or:");
                    println("    java network003 localhost");
                    return 2; // FAILED
            }
        } catch (UnknownHostException exception) {
            println(exception);
            return 2; // FAILED
        }
        display("Host: " + address);

        //
        // Incarnate the server & the client sockets.
        //

        Server server[] = new Server[CONNECTIONS];
        Client client[] = new Client[CONNECTIONS];

        for (int i = 0; i < CONNECTIONS; i++) {
            try {
                server[i] = new Server(address);
            } catch (IOException io) {
                println("Failed to create server #" + i + ": " + io);
                return 2;
            }
            display("Server #" + i + ": " + server[i]);
        }

        for (int i = 0; i < CONNECTIONS; i++) {
            int port = server[i].getPort();
            try {
                client[i] = new Client(address, port);
            } catch (IOException io) {
                out.println("Failed to create client #" + i + ": " + io);
                return 2;
            }
            display("Client socket #" + i + ": " + client[i]);
        }

        //
        // Execute the server and client threads.
        //

        Exception exception = null;
        try {
            for (int i = 0; i < CONNECTIONS; i++)
                server[i].start();
            for (int i = 0; i < CONNECTIONS; i++)
                client[i].start();
            boolean someIsAlive = true;
            while (someIsAlive) {
                boolean aliveFound = false;
                boolean someBroken = false;
                for (int i = 0; i < CONNECTIONS; i++)
                    if (client[i].isAlive() || server[i].isAlive()) {
                        if ((client[i].exception != null) ||
                                (server[i].exception != null))
                            someBroken = true;
                        aliveFound = true;
                        Thread.yield();
                    }
                someIsAlive = aliveFound;
                if (someBroken)
                    break;
            }
        } catch (TestFailure failure) {
            exception = failure;
        }

        // Failure diagnostics, if needed.

        Exception problem[] = new Exception[2 * CONNECTIONS + 1];
        problem[0] = exception;
        for (int i = 0; i < CONNECTIONS; i++) {
            problem[2 * i + 1] = server[i].exception;
            problem[2 * i + 2] = client[i].exception;
        }

        int exitCode = 0;

        for (int i = 0; i < 2 * CONNECTIONS + 1; i++)
            if (problem[i] != null) {
                out.println("#### OOPS ! ####");
                problem[i].printStackTrace(out);
                exitCode = 2;
            }

        if (exitCode != 0) {
            out.println("#### OOPS ! ####");
            out.println("# Test failed.");
            return 2; // FAILED
        }
        display("Test passed.");
        return 0; // PASSED
    }

    /**
     * Re-calls to the method <code>run(args[],out)</code> actually
     * performing the test; and stop with exit code 95 if the test
     * has passed, or with code 97 if the test has failed.
     * (This is JCK-like exit codes convention.)
     *
     * @see #run(String[], PrintStream)
     */
    public static void main(String args[]) {
        int exitCode = run(args, System.out);
        System.exit(exitCode + 95);
        // JCK-like exit code.
    }

}
