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
 * @summary converted from VM testbase nsk/stress/network/network004.
 * VM testbase keywords: [stress, slow, nonconcurrent, quick]
 * VM testbase readme:
 * DESCRIPTION
 *     This test transfers huge amount of data between 2 Java virtual machines
 *     using the TCP/IP protocol, and checks if those data are transfered correctly.
 *     Both client and server VMs run on the same local computer and attach TCP/IP
 *     sockets to the local host, or to the loopback domain ``localhost''
 *     (having IP address 127.0.0.1).
 *     In this test, 128 client/server connections are established. Once a
 *     connection is established, client passes a large data parcel to server,
 *     and server reads that parcel and checks if it is same as expected
 *     (byte-to-byte equality is desired). Then server passes (some other) parcel
 *     to the client, and client reads and verifies those bytes. This ping-pong
 *     game is repeated 128 times; and after that each pair of sockets checks if
 *     there are no extra bytes accudentally passed through their connection.
 *     Parcels lengths and contents are chosen randomly, and average
 *     parcel length is 128 bytes. So totally, each pair of sockets passes ~16Kb of
 *     data to each other, and thus ~32Kb of data are transfered by each sockets
 *     pair. Totally, ~4Mb of data are transfered by all client/server pairs.
 * COMMENTS
 *     The production Solaris_JDK_1.3-b12 Server VM intermittently crashes under
 *     this test, even when client part of the test is executed with Client HS:
 *         >>>> java -server network004 java
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
 *     If the client part of the test is executed with Server HS, the
 *     production Solaris_JDK_1.3-b12 Server VM intermittently fails
 *     this test due to timeout:
 *         >>>> time java -server network004 'java -server -showversion'
 *         java version "1.3"
 *         Java(TM) 2 Runtime Environment, Standard Edition (build Solaris_JDK_1.3-b12)
 *         Java HotSpot(TM) Server VM (build 1.3-b12, mixed mode)
 *         # Client #96: java.io.InterruptedIOException: Read timed out
 *         # Client VM has crashed: exit status=97
 *         # Test failed.
 *         156.0u 117.0s 7:06 63% 0+0k 0+0io 0pf+0w
 *     Test was fixed:
 *     added WAITTIME parameter defined timeout for TCP/IP sockets in minutes
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @build nsk.stress.network.network004
 * @run main/othervm PropertyResolvingWrapper
 *      nsk.stress.network.network004
 *      "${test.jdk}/bin/java ${test.vm.opts} ${test.java.opts}" 5
 */

package nsk.stress.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.StringTokenizer;

/**
 * This test transfers huge amount of data between 2 Java virtual machines
 * using the TCP/IP protocol, and checks if those data are transfered correctly.
 * Both client and server VMs run on the same local computer and attach TCP/IP
 * sockets to the local host, or to the loopback domain ``<code>localhost</code>''
 * (having IP address <code>127.0.0.1</code>).
 * <p>
 * <p>In this test, 128 client/server connections are established. Once a
 * connection is established, client passes a large data parcel to server,
 * and server reads that parcel and checks if it is same as expected
 * (byte-to-byte equality is desired). Then server passes (some other) parcel
 * to the client, and client reads and verifies those bytes. This ping-pong
 * game is repeated 128 times; and after that each pair of sockets checks if
 * there are no extra bytes accudentally passed through their connection.
 * <p>
 * <p>Parcels lengths and contents are chosen randomly, and average
 * parcel length is 128 bytes. So totally, each pair of sockets passes ~16Kb of
 * data to each other, and thus ~32Kb of data are transfered by each sockets
 * pair. Totally, ~4Mb of data are transfered by all client/server pairs.
 */
public class network004 {
    /**
     * Timeout for TCP/IP sockets (currently set to 1 min).
     */
    private static int SO_TIMEOUT;// = 2*60*1000;

    /**
     * Maximal number of connections this test should open simultaneously.
     */
    private final static int MAX_CONNECTIONS = 128;

    /**
     * Check few more connections to make sure that MAX_CONNECTIONS are safe.
     */
    private final static int CONNECTIONS_RESERVE = 10;

    /**
     * Number of parcels to be sent/recieved.
     */
    private final static int DATA_PARCELS = 128;

    /**
     * Maximal length of data parcel to be sent/recieved
     * (it equals to 256 bytes now).
     */
    private final static int MAX_PARCEL = 1 << 8;

    /**
     * Either actually display optional reports or not.
     */
    static private final boolean DEBUG_MODE = false;

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
            complain("------------------------- CAUTION: -------------------");
            complain("While checking the OS limitations, the test found that");
            complain("only " + i + " TCP/IP socket connections could be safely open");
            complain("simultaneously. However, possibility to open at least");
            complain("" + MAX_CONNECTIONS + "+" + CONNECTIONS_RESERVE
                    + " connections were expected.");
            complain("");
            complain("So, the test will check only " + safeConnections + " connection"
                    + (safeConnections == 1 ? "" : "s") + " which seem");
            complain("safe to be open simultaneously.");
            complain("------------------------------------------------------");
        }
        return safeConnections;
    }

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
     * Parse command-line parameters stored into <code>args[]</code> array,
     * then perform the test. I.e.: start the server thread at the same VM
     * this method runs, then start the other client VM, and verify data
     * transfer through TCP/IP connection between those different virtual
     * machines.
     * <p>
     * <p>There should be 1 or 2 command-line parameters:
     * <br>&nbsp;&nbsp;
     * <code>java network004 <i>java_command</i>
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
    public static int run(String args[], PrintStream out) {
        network004.out = out;

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
                    complain("    java network004 $JAVA_COMMAND " +
                            "[$IP_ADDRESS | $HOST_NAME | localhost]");
                    return 2; // FAILED
            }
        } catch (UnknownHostException exception) {
            complain(exception.toString());
            return 2; // FAILED
        }
        display("Host: " + address);

        //
        // Detect if it is safe to open MAX_CONNETIONS simultaneously:
        //
        final int CONNECTIONS = detectOSLimitation();

        //
        // Start the server thread on the same VM (which executes this method).
        //
        Server server[] = new Server[CONNECTIONS];
        for (int i = 0; i < CONNECTIONS; i++) {
            try {
                server[i] = new Server(address);
            } catch (Exception exception) {
                complain("Server #" + i + ": " + exception);
                return 2;
            }
            display("Server #" + i + ": " + server[i]);
            server[i].start();
        }

        //
        // Start the client process on different VM.
        //
        String command = args[0] + " " + network004.class.getName() + "$Client";
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
            // Start clients on different JVM:
            client = runtime.exec(command);

            // Provide clients with access to stderr and stdout:
            InputStream clientOut = client.getInputStream();
            InputStream clientErr = client.getErrorStream();
            redirectOut = new IORedirector(clientOut, DEBUG_MODE ? out : null);
            redirectErr = new IORedirector(clientErr, out);
            redirectOut.start();
            redirectErr.start();

            // Pass parameters to clients (number of connections, and IP adresses and ports):
            PrintStream clientIn = new PrintStream(client.getOutputStream());
            clientIn.println(CONNECTIONS);
            for (int i = 0; i < CONNECTIONS; i++)
                clientIn.println(server[i].getIPAddress() + " " + server[i].getPort());
            clientIn.flush();
            clientIn.close();

        } catch (Exception exception) {
            complain("Failed to start client: " + exception);
            return 2;
        }

        //
        // Wait until the server and client both stop.
        //
        boolean testFailed = false;
        try {
            client.waitFor();
            // Let I/O redirectors to flush:
            if (redirectOut.isAlive())
                redirectOut.join();
            if (redirectErr.isAlive())
                redirectErr.join();

            // If client has crashed, also terminate the server (to avoid hangup).
            int clientStatus = client.exitValue();
            if (clientStatus != 95) {
                complain("Client VM has failed: exit status=" + clientStatus);
                testFailed = true;
            }

            // Client has finished OK; wait for the server.
            for (int i = 0; i < CONNECTIONS; i++) {
                display("Server: waiting for #" + i);
                while (server[i].isAlive())
                    server[i].join();
                if (server[i].exception != null) {
                    complain("Server thread #" + i + ": " + server[i].exception);
                    testFailed = true;
                }
            }

        } catch (Exception exception) {
            complain("Test interrupted: " + exception);
            testFailed = true;
        }

        if (testFailed)
            complain("Test failed.");
        else
            display("Test passed.");
        return testFailed ? 2 : 0;
    }

    //----------------------------------------------------------------//

    /**
     * Log stream for error messages and/or (optional) execution trace.
     */
    private static PrintStream out;

    /**
     * Print error message.
     */
    private static synchronized void complain(Object message) {
        out.println("# " + message);
        out.flush();
    }

    /**
     * Display optional report: comment ca va?
     */
    private static synchronized void display(Object report) {
        if (DEBUG_MODE)
            out.println(report.toString());
        out.flush();
    }

    //----------------------------------------------------------------//

    /**
     * Server thread should reply to data parcels sent by Client VM.
     */
    private static class Server extends Thread {
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
//              display("Server: " + socket);

                InputStream istream = socket.getInputStream();
                OutputStream ostream = socket.getOutputStream();

                Random random = new Random(getPort());

                for (int i = 0; i < DATA_PARCELS; i++) {
                    Parcel etalon = new Parcel(random);

                    Parcel sample = new Parcel(istream); // read
                    if (!sample.equals(etalon)) {
                        complain("Server thread for port #"
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

    //----------------------------------------------------------------//

    /**
     * Client VM should send data parcels to Server VM and
     * recieve and verify the server's replies.
     */
    private static class Client extends Thread {
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
            socket.setSoTimeout(SO_TIMEOUT);
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
                        complain("Client thread for port #"
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

        /**
         * Establish connections to lots of server sockets, atack servers with
         * huge data parcels, and check if it replies correctly. The addresses
         * and port numbers for server sockets are passed through <code>stdin</code>.
         * The input stream must consist of the stipulated number (up to 128+1) of
         * lines containing the pair of symbolic server domain name and the port number,
         * like:
         * <br>&nbsp;&nbsp; actual_number_of_sockets
         * <br>&nbsp;&nbsp; address_1 port_1
         * <br>&nbsp;&nbsp; address_2 port_2
         * <br>&nbsp;&nbsp; . . .
         * <br>&nbsp;&nbsp; address_N port_N
         * <br>where N must equal to the actual_number_of_sockets.
         */
        public static void main(String args[]) {
            // ---- Parse stdin for the list of server sockets: ---- //
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            final int CONNECTIONS;
            try {
                String line = in.readLine();
                if (line == null) {
                    complain("Client expects paramenets passed through stdin:");
                    complain("    actual_number_of_sockets");
                    complain("    IP-address_1 port_1");
                    complain("    IP-address_2 port_2");
                    complain("    .   .   .");
                    complain("    IP-address_N port_N");
                    exit(2); // FAILED
                }
                CONNECTIONS = Integer.parseInt(line);
            } catch (IOException ioe) {
                complain("Client failed to read the actual number of CONNECTIONS");
                throw new RuntimeException(ioe.toString());
            }

            Client client[] = new Client[CONNECTIONS];
            for (int i = 0; i < CONNECTIONS; i++)
                try {
                    String line = in.readLine();
                    if (line == null) {
                        complain("Client: failed to read address/port for client #" + i);
                        exit(3);
                    }

                    StringTokenizer tokenz = new StringTokenizer(line);
                    if (tokenz.countTokens() != 2) {
                        complain("Client: illegal input string: " + line);
                        exit(3);
                    }
                    String serverName = (String) tokenz.nextElement();
                    InetAddress address = InetAddress.getByName(serverName);
                    int port = Integer.parseInt((String) tokenz.nextElement());

                    client[i] = new Client(address, port);

                    display("Client #" + i + ": " + client[i]);

                } catch (IOException ioe) {
                    complain("Client #" + i + ": " + ioe);
                    exit(3);
                }

            // ---- Start testing: ---- //

            for (int i = 0; i < CONNECTIONS; i++)
                client[i].start();

            int status = 0;
            for (int i = 0; i < CONNECTIONS; i++) {
                display("Client: waiting for #" + i);
                while (client[i].isAlive())
                    yield();
                if (client[i].exception != null) {
                    complain("Client #" + i + ": " + client[i].exception);
                    status = 2;
                }
            }

            exit(status);
        }

        /**
         * Print error message.
         */
        private static synchronized void complain(Object message) {
            System.err.println("# " + message);
            System.err.flush();
        }

        /**
         * Display execution trace.
         */
        private static synchronized void display(Object message) {
            if (!DEBUG_MODE)
                return;
            System.out.println(message.toString());
            System.out.flush();
        }

        /**
         * Exit with JCK-like status.
         */
        private static void exit(int exitCode) {
            System.exit(exitCode + 95);
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
