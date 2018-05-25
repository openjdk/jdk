/*
 * Copyright (c) 1999, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM testbase nsk/stress/network/network002.
 * VM testbase keywords: [stress, slow, nonconcurrent, quick]
 * VM testbase readme:
 * DESCRIPTION
 *     This test transfers huge amount of data between 2 Java virtual machines
 *     using the TCP/IP protocol, and checks if those data are transfered correctly.
 *     Both client and server VMs run on the same local computer and attach TCP/IP
 *     sockets to the local host, or to the loopback domain "localhost" (having IP
 *     address 127.0.0.1).
 *     Information transfer is synchronized in this test. Client VM passes
 *     a large data parcel to server VM, and server reads that parcel and checks
 *     if it is same as expected (byte-to-byte equality). Then server passes
 *     (some other) parcel to client, and client reads and verifies those data.
 *     This ping-pong game is repeated 2000 times; and after that both VMs check
 *     if there are no extra bytes accudentally passed through their connection.
 *     Parcels lengths and contents are chosen randomly, and average parcel
 *     length is 125 bytes. So totally, each of the 2 VMs passes ~250Kb of data
 *     to its partner, and thus ~500Kb of data are transfered by this test.
 * COMMENTS
 *     HotSpot 1.3beta-H fails to start this test due to the hotspot bug:
 *         #4245704 (P1/S1) Fails to launch with: jre/bin/net.dll ...
 *     Test was fixed:
 *     added WAITTIME parameter defined timeout for TCP/IP sockets in minutes
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @build nsk.stress.network.network002
 * @run main/othervm PropertyResolvingWrapper
 *      nsk.stress.network.network002
 *      "${test.jdk}/bin/java ${test.vm.opts} ${test.java.opts}" 5
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
 * This test transfers huge amount of data between 2 Java virtual machines
 * using the TCP/IP protocol, and checks if those data are transfered correctly.
 * Both client and server VMs run on the same local computer and attach TCP/IP
 * sockets to the local host, or to the loopback domain ``<code>localhost</code>''
 * (having IP address <code>127.0.0.1</code>).
 * <p>
 * <p>Information transfer is synchronized in this test. Client VM passes
 * a large data parcel to server VM, and server reads that parcel and checks
 * if it is same as expected (byte-to-byte equality). Then server passes
 * (some other) parcel to client, and client reads and verifies those data.
 * This ping-pong game is repeated 2000 times; and after that both VMs check
 * if there are no extra bytes accudentally passed through their connection.
 * <p>
 * <p>Parcels lengths and contents are chosen randomly, and average parcel
 * length is 125 bytes. So totally, each of the 2 VMs passes ~250Kb of data
 * to its partner, and thus ~500Kb of data are transfered by this test.
 */
public class network002 {
    /**
     * Timeout for TCP/IP sockets (currently set to 1 min).
     */
    private static int SO_TIMEOUT;

    /**
     * Number of parcels to be sent/recieved.
     */
    private static final int DATA_PARCELS = 2000;

    /**
     * Maximal length of data parcel to be sent/recieved.
     */
    private static final int MAX_PARCEL = 250;

    /**
     * Either actually display optional reports or not.
     */
    static private final boolean DEBUG_MODE = false;

    //----------------------------------------------------------------//

    /**
     * Re-calls to the method <code>run(args[],out)</code> actually
     * performing the test. After <code>run(args[],out)</code> stops,
     * follow JDK-like convention for exit codes. I.e.: stop with
     * exit status 95 if the test has passed, or with status 97 if
     * the test has failed.
     *
     * @see #run(String[], PrintStream)
     */
    public static void main(String args[]) {
        int exitCode = run(args, System.out);
        System.exit(exitCode + 95);
        // JCK-like exit status.
    }

    /**
     * Incarnate new <code>network002</code> instance reporting to the given
     * <code>out</code> stream, and invoke the method <code>run(args)</code>
     * for that instance to perform the test.
     */
    public static int run(String args[], PrintStream out) {
        network002 test = new network002(out);
        int exitCode = test.run(args);
        return exitCode;
    }

    /**
     * Parse command-line parameters stored into <code>args[]</code> array,
     * then perform the test. I.e.: start the server thread at the same VM
     * this method runs, then start the other client VM, and verify data
     * transfer through TCP/IP connection between those different virtual
     * machines.
     * <p>
     * <p>There should be 1 or 2 command-line parameters:
     * <br>&nbsp;&nbsp;
     * <code>java network002 <i>java_command</i> <i>waittime</i>
     * [<i>IP-address</i> | <i>host_name</i> | localhost ]</code>
     * <br>where parameters are:
     * <br>&nbsp;&nbsp;
     * <code><i>java_command</i></code> - how to start java,
     * e.g.: ``<code>c:\jdk1.3\bin\java -classic</code>''
     * <br>&nbsp;&nbsp;
     * <code>waittime</code> - timeout for TCP/IP sockets in minutes
     * <br>&nbsp;&nbsp;
     * <code><i>IP-address</i></code> - local hots's address, or 127.0.0.1
     * <br>&nbsp;&nbsp;
     * <code><i>host_name</i></code> - local host's domain name, or the
     * keyword ``<code>localhost</code>''
     * <br>&nbsp;&nbsp;
     * <code>localhost</code> - placeholder for the IP-address 127.0.0.1
     * <p>
     * <p>Usually, <code><i>java_command</i></code> should point to the same
     * Java machine just executing this test. However, every compatible Java 2
     * implementation is appropriate.
     * <p>
     * <p>If optional parameter is ommited, the test invokes the method
     * <code>InetAddress.getLocalHost()</code> to get the domain name and
     * IP-address of the local computer.
     */
    private int run(String args[]) {
        //
        // Get the Internet address of the local machine.
        //
        InetAddress address = null;
        try {
            switch (args.length) {
                case 2:
                    address = InetAddress.getLocalHost();
                    break;
                case 3:
                    address = InetAddress.getByName(args[2]);
                    break;
                default:
                    complain("Illegal arguments number; execute:");
                    complain("    java network002 $JAVA_COMMAND " +
                            "[$IP_ADDRESS | $HOST_NAME | localhost]");
                    return 2; // FAILED
            }
        } catch (UnknownHostException exception) {
            complain(exception.toString());
            return 2; // FAILED
        }
        display("Host: " + address);

        //
        // Start the server thread on the same VM this method just runs.
        //
        Server server = null;
        try {
            server = new Server(address);
            server.start();
        } catch (Exception exception) {
            complain("Failed to start server: " + exception);
            return 2;
        }
        display("Server: " + server);

        //
        // Start the client process on different VM.
        //
        String IPAddress = server.getIPAddress(); // e.g.: 127.0.0.1
        int port = server.getPort();
        String command = args[0] + " " + network002.class.getName() + "$Client " + IPAddress + " " + port;
        try {
            SO_TIMEOUT = Integer.parseInt(args[1]) * 60 * 1000;
        } catch (NumberFormatException e) {
            complain("Wrong timeout argument: " + e);
            return 2;
        }

        Runtime runtime = Runtime.getRuntime();

        Process client = null;
        IORedirector redirectOut = null;
        IORedirector redirectErr = null;

        try {
            client = runtime.exec(command);

            InputStream clientOut = client.getInputStream();
            InputStream clientErr = client.getErrorStream();
            redirectOut = new IORedirector(clientOut, DEBUG_MODE ? out : null);
            redirectErr = new IORedirector(clientErr, out);
            redirectOut.start();
            redirectErr.start();

        } catch (Exception exception) {
            complain("Failed to start client: " + exception);
            return 2;
        }

        //
        // Wait until the server and client both stop.
        //
        try {
            client.waitFor();
            if (redirectOut.isAlive())
                redirectOut.join();
            if (redirectErr.isAlive())
                redirectErr.join();

            // If client has crashed, also terminate the server (to avoid hangup).
            int clientStatus = client.exitValue();
            if (clientStatus != 95) {
                complain("");
                complain("Client VM has crashed: exit status=" + clientStatus);
                if (server.isAlive())
                    complain("Server also should be terminated.");
                complain("Test failed.");
                return 2; // failure
            }

            // Client has finished OK; wait for the server.
            if (server.isAlive())
                server.join();

        } catch (Exception exception) {
            complain("Test interrupted: " + exception);
            complain("Test failed.");
            return 2; // FAILURE
        }

        //
        // Complain failure, if occured.
        //

        if (server.exception != null) {
            complain("Server exception: " + server.exception);
            complain("Test failed.");
            return 2; // failure
        }

        display("Test passed.");
        return 0; // Ok
    }

    //----------------------------------------------------------------//

    /**
     * The test should report to the given <code>out</code> stream.
     */
    private network002(PrintStream out) {
        this.out = out;
    }

    /**
     * Log stream for error messages and/or (optional) execution trace.
     */
    private PrintStream out;

    /**
     * Print error message.
     */
    private void complain(Object message) {
        out.println("# " + message);
        out.flush();
    }

    /**
     * Display optional report: comment ca va?
     */
    private void display(Object report) {
        if (DEBUG_MODE)
            out.println(report.toString());
        out.flush();
    }

    //----------------------------------------------------------------//

    /**
     * Server thread should reply to data parcels sent by Client VM.
     */
    private class Server extends Thread {
        /**
         * The socket to listen for a client.
         */
        private ServerSocket serverSocket;

        /**
         * Display the server socket.
         */
        public String toString() {
            return serverSocket.toString();
        }

        /**
         * Server's IP-address in the form ``<code><i>x.y.u.z</i></code>'',
         * or ``<code>127.0.0.1</code>'' for loopback connection.
         */
        public String getIPAddress() {
            return serverSocket.getInetAddress().getHostAddress();
        }

        /**
         * Which port is this socket listening?
         */
        int getPort() {
            return serverSocket.getLocalPort();
        }

        /**
         * Find some free port at the given <code>address</code>
         * and attach new server to hear that port.
         */
        public Server(InetAddress address) throws IOException {
            int someFreePort = 0;
            int backlog = 50; // default for new ServerSocket(port)
            serverSocket = new ServerSocket(someFreePort, backlog, address);
        }

        /**
         * Exception just arisen while the server was working,
         * or <code>null</code> if it was OK with the server.
         */
        Exception exception = null;

        /**
         * Accept connection, then reply to client's parcels.
         */
        public void run() {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(SO_TIMEOUT);

                InputStream istream = socket.getInputStream();
                OutputStream ostream = socket.getOutputStream();

                Random random = new Random(0);

                for (int i = 0; i < DATA_PARCELS; i++) {
                    display("Server: i=" + i);
                    Parcel etalon = new Parcel(random);

                    Parcel sample = new Parcel(istream); // read
                    if (!sample.equals(etalon)) {
                        complain("Server got unexpected parcel:\n"
                                + "sample=" + sample + "\n"
                                + "etalon=" + etalon);
                        throw new TestFailure(
                                "the parcel just read seems wrong for server");
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

    //----------------------------------------------------------------//

    /**
     * Client VM should send data parcels to Server VM and
     * recieve and verify the server's replies.
     */
    private static class Client {
        /**
         * Print error message.
         */
        private static void complain(Object message) {
            System.err.println("# " + message);
            System.err.flush();
        }

        /**
         * Display execution trace.
         */
        private static void display(Object message) {
            System.out.println(message.toString());
            System.out.flush();
        }

        /**
         * Exit with JCK-like status.
         */
        private static void exit(int exitCode) {
            System.exit(exitCode + 95);
        }

        /**
         * Atack server with huge data parcels, and check if it replies correctly.
         * The command-line parameters prescribe the server's IP-address and port:
         * <br>&nbsp;&nbsp;
         * <code>java network002$Client <i>IP-address</i> <i>port</i></code>
         * <br>where:
         * <br>&nbsp;&nbsp;
         * <code><i>IP-address</i></code> - local host's address,
         * or <code>127.0.0.1</code>
         * <br>&nbsp;&nbsp;
         * <code><i>port</i></code> - some port assigned by server
         */
        public static void main(String args[]) {
            if (args.length != 2) {
                complain("Illegal number of client paramenets, try:");
                complain("    java network002$Client IP-address port");
                exit(2); // FAILED
            }

            try {
                InetAddress address = InetAddress.getByName(args[0]);
                int port = Integer.parseInt(args[1]);

                Socket socket = new Socket(address, port);
                socket.setSoTimeout(SO_TIMEOUT);
                display("Client: " + socket);

                InputStream istream = socket.getInputStream();
                OutputStream ostream = socket.getOutputStream();

                Random random = new Random(0);

                for (int i = 0; i < DATA_PARCELS; i++) {
                    display("Client: i=" + i);
                    Parcel etalon = new Parcel(random);
                    etalon.send(ostream);
                    ostream.flush();

                    Parcel sample = new Parcel(istream); // read
                    if (!sample.equals(etalon)) {
                        complain("Client got unexpected parcel:\n"
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

            } catch (Exception exception) {
                complain("Client exception: " + exception);
                exit(2); // FAILED
            }
            exit(0); // PASSED, at least at the client side.
        }

    }

    /**
     * Two of such threads should redirect <code>out</code> and <code>err</code>
     * streams of client VM.
     */
    private static class IORedirector extends Thread {
        /**
         * Source stream.
         */
        InputStream in;
        /**
         * Destination stream.
         */
        OutputStream out;

        /**
         * Redirect <code>in</code> to <code>out</code>.
         */
        public IORedirector(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        /**
         * Read input stream until the EOF, and write everithing to output stream.
         * If output stream is assigned to <code>null</code>, do not print anything,
         * but read the input stream anywhere.
         */
        public void run() {
            try {
                for (; ; ) {
                    int symbol = in.read();
                    if (symbol < 0)
                        break; // EOF
                    if (out != null)
                        out.write(symbol);
                }

                if (out != null)
                    out.flush();

            } catch (Exception exception) {
                throw new TestFailure("IORedirector exception: " + exception);
            }
        }
    }

    //----------------------------------------------------------------//

    /**
     * A data parcel to be sent/recieved between Client VM and Server thread.
     * When data parcel is sent, first 4 bytes are transfered which encode the
     * <code>int</code> number equal to size of the parcel minus 1. I.e.: if
     * number of data bytes in the parcel's contents is <code>N</code>, then
     * the first 4 bytes encode the number <code>N-1</code>. After that, the
     * parcel's contents bytes are transered.
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
     * Server or Client may throw this exception to report the test failure.
     */
    static class TestFailure extends RuntimeException {
        /**
         * Report particular <code>purpose</code> of the test failure.
         */
        public TestFailure(String purpose) {
            super(purpose);
        }

    }

}
