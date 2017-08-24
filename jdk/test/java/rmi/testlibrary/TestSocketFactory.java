/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.testng.Assert;
import org.testng.TestNG;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;


/**
 * A RMISocketFactory utility factory to log RMI stream contents and to
 * match and replace output stream contents to simulate failures.
 */
public class TestSocketFactory extends RMISocketFactory
        implements RMIClientSocketFactory, RMIServerSocketFactory, Serializable {

    private static final long serialVersionUID = 1L;

    private volatile transient byte[] matchBytes;

    private volatile transient byte[] replaceBytes;

    private transient final List<InterposeSocket> sockets = new ArrayList<>();

    private transient final List<InterposeServerSocket> serverSockets = new ArrayList<>();

    public static final boolean DEBUG = false;

    /**
     * Debugging output can be synchronized with logging of RMI actions.
     *
     * @param format a printf format
     * @param args   any args
     */
    private static void DEBUG(String format, Object... args) {
        if (DEBUG) {
            System.err.printf(format, args);
        }
    }

    /**
     * Create a socket factory that creates InputStreams that log
     * and OutputStreams that log .
     */
    public TestSocketFactory() {
        this.matchBytes = new byte[0];
        this.replaceBytes = this.matchBytes;
        System.out.printf("Creating TestSocketFactory()%n");
    }

    public void setMatchReplaceBytes(byte[] matchBytes, byte[] replaceBytes) {
        this.matchBytes = Objects.requireNonNull(matchBytes, "matchBytes");
        this.replaceBytes = Objects.requireNonNull(replaceBytes, "replaceBytes");
        sockets.forEach( s -> s.setMatchReplaceBytes(matchBytes, replaceBytes));
        serverSockets.forEach( s -> s.setMatchReplaceBytes(matchBytes, replaceBytes));

    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket socket = RMISocketFactory.getDefaultSocketFactory()
                .createSocket(host, port);
        InterposeSocket s = new InterposeSocket(socket, matchBytes, replaceBytes);
        sockets.add(s);
        return s;
    }

    /**
     * Return the current list of sockets.
     * @return Return a snapshot of the current list of sockets
     */
    public List<InterposeSocket> getSockets() {
        List<InterposeSocket> snap = new ArrayList<>(sockets);
        return snap;
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {

        ServerSocket serverSocket = RMISocketFactory.getDefaultSocketFactory()
                .createServerSocket(port);
        InterposeServerSocket ss = new InterposeServerSocket(serverSocket, matchBytes, replaceBytes);
        serverSockets.add(ss);
        return ss;
    }

    /**
     * Return the current list of server sockets.
     * @return Return a snapshot of the current list of server sockets
     */
    public List<InterposeServerSocket> getServerSockets() {
        List<InterposeServerSocket> snap = new ArrayList<>(serverSockets);
        return snap;
    }

    /**
     * An InterposeSocket wraps a socket that produces InputStreams
     * and OutputStreams that log the traffic.
     * The OutputStreams it produces match an array of bytes and replace them.
     * Useful for injecting protocol and content errors.
     */
    public static class InterposeSocket extends Socket {
        private final Socket socket;
        private InputStream in;
        private MatchReplaceOutputStream out;
        private volatile byte[] matchBytes;
        private volatile byte[] replaceBytes;
        private final ByteArrayOutputStream inLogStream;
        private final ByteArrayOutputStream outLogStream;
        private final String name;
        private static volatile int num = 0;    // index for created InterposeSockets

        public InterposeSocket(Socket socket, byte[] matchBytes, byte[] replaceBytes) {
            this.socket = socket;
            this.matchBytes = Objects.requireNonNull(matchBytes, "matchBytes");
            this.replaceBytes = Objects.requireNonNull(replaceBytes, "replaceBytes");
            this.inLogStream = new ByteArrayOutputStream();
            this.outLogStream = new ByteArrayOutputStream();
            this.name = "IS" + ++num + "::"
                    + Thread.currentThread().getName() + ": "
                    + socket.getLocalPort() + " <  " + socket.getPort();
        }

        public void setMatchReplaceBytes(byte[] matchBytes, byte[] replaceBytes) {
            this.matchBytes = matchBytes;
            this.replaceBytes = replaceBytes;
            out.setMatchReplaceBytes(matchBytes, replaceBytes);
        }

        @Override
        public void connect(SocketAddress endpoint) throws IOException {
            socket.connect(endpoint);
        }

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            socket.connect(endpoint, timeout);
        }

        @Override
        public void bind(SocketAddress bindpoint) throws IOException {
            socket.bind(bindpoint);
        }

        @Override
        public InetAddress getInetAddress() {
            return socket.getInetAddress();
        }

        @Override
        public InetAddress getLocalAddress() {
            return socket.getLocalAddress();
        }

        @Override
        public int getPort() {
            return socket.getPort();
        }

        @Override
        public int getLocalPort() {
            return socket.getLocalPort();
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return socket.getRemoteSocketAddress();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return socket.getLocalSocketAddress();
        }

        @Override
        public SocketChannel getChannel() {
            return socket.getChannel();
        }

        @Override
        public synchronized void close() throws IOException {
            socket.close();
        }

        @Override
        public String toString() {
            return "InterposeSocket " + name + ": " + socket.toString();
        }

        @Override
        public boolean isConnected() {
            return socket.isConnected();
        }

        @Override
        public boolean isBound() {
            return socket.isBound();
        }

        @Override
        public boolean isClosed() {
            return socket.isClosed();
        }

        @Override
        public <T> Socket setOption(SocketOption<T> name, T value) throws IOException {
            return socket.setOption(name, value);
        }

        @Override
        public <T> T getOption(SocketOption<T> name) throws IOException {
            return socket.getOption(name);
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return socket.supportedOptions();
        }

        @Override
        public synchronized InputStream getInputStream() throws IOException {
            if (in == null) {
                in = socket.getInputStream();
                String name = Thread.currentThread().getName() + ": "
                        + socket.getLocalPort() + " <  " + socket.getPort();
                in = new LoggingInputStream(in, name, inLogStream);
                DEBUG("Created new InterposeInputStream: %s%n", name);
            }
            return in;
        }

        @Override
        public synchronized OutputStream getOutputStream() throws IOException {
            if (out == null) {
                OutputStream o = socket.getOutputStream();
                String name = Thread.currentThread().getName() + ": "
                        + socket.getLocalPort() + "  > " + socket.getPort();
                out = new MatchReplaceOutputStream(o, name, outLogStream, matchBytes, replaceBytes);
                DEBUG("Created new MatchReplaceOutputStream: %s%n", name);
            }
            return out;
        }

        /**
         * Return the bytes logged from the input stream.
         * @return Return the bytes logged from the input stream.
         */
        public byte[] getInLogBytes() {
            return inLogStream.toByteArray();
        }

        /**
         * Return the bytes logged from the output stream.
         * @return Return the bytes logged from the output stream.
         */
        public byte[] getOutLogBytes() {
            return outLogStream.toByteArray();
        }

    }

    /**
     * InterposeServerSocket is a ServerSocket that wraps each Socket it accepts
     * with an InterposeSocket so that its input and output streams can be monitored.
     */
    public static class InterposeServerSocket extends ServerSocket {
        private final ServerSocket socket;
        private volatile byte[] matchBytes;
        private volatile byte[] replaceBytes;
        private final List<InterposeSocket> sockets = new ArrayList<>();

        public InterposeServerSocket(ServerSocket socket, byte[] matchBytes, byte[] replaceBytes) throws IOException {
            this.socket = socket;
            this.matchBytes = Objects.requireNonNull(matchBytes, "matchBytes");
            this.replaceBytes = Objects.requireNonNull(replaceBytes, "replaceBytes");
        }

        public void setMatchReplaceBytes(byte[] matchBytes, byte[] replaceBytes) {
            this.matchBytes = matchBytes;
            this.replaceBytes = replaceBytes;
            sockets.forEach(s -> s.setMatchReplaceBytes(matchBytes, replaceBytes));
        }
        /**
         * Return a snapshot of the current list of sockets created from this server socket.
         * @return Return a snapshot of the current list of sockets
         */
        public List<InterposeSocket> getSockets() {
            List<InterposeSocket> snap = new ArrayList<>(sockets);
            return snap;
        }

        @Override
        public void bind(SocketAddress endpoint) throws IOException {
            socket.bind(endpoint);
        }

        @Override
        public void bind(SocketAddress endpoint, int backlog) throws IOException {
            socket.bind(endpoint, backlog);
        }

        @Override
        public InetAddress getInetAddress() {
            return socket.getInetAddress();
        }

        @Override
        public int getLocalPort() {
            return socket.getLocalPort();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return socket.getLocalSocketAddress();
        }

        @Override
        public Socket accept() throws IOException {
            Socket s = socket.accept();
            InterposeSocket socket = new InterposeSocket(s, matchBytes, replaceBytes);
            sockets.add(socket);
            return socket;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        @Override
        public ServerSocketChannel getChannel() {
            return socket.getChannel();
        }

        @Override
        public boolean isClosed() {
            return socket.isClosed();
        }

        @Override
        public String toString() {
            return socket.toString();
        }

        @Override
        public <T> ServerSocket setOption(SocketOption<T> name, T value) throws IOException {
            return socket.setOption(name, value);
        }

        @Override
        public <T> T getOption(SocketOption<T> name) throws IOException {
            return socket.getOption(name);
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return socket.supportedOptions();
        }

        @Override
        public synchronized void setSoTimeout(int timeout) throws SocketException {
            socket.setSoTimeout(timeout);
        }

        @Override
        public synchronized int getSoTimeout() throws IOException {
            return socket.getSoTimeout();
        }
    }

    /**
     * LoggingInputStream is a stream and logs all bytes read to it.
     * For identification it is given a name.
     */
    public static class LoggingInputStream extends FilterInputStream {
        private int bytesIn = 0;
        private final String name;
        private final OutputStream log;

        public LoggingInputStream(InputStream in, String name, OutputStream log) {
            super(in);
            this.name = name;
            this.log = log;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                log.write(b);
                bytesIn++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytes = super.read(b, off, len);
            if (bytes > 0) {
                log.write(b, off, bytes);
                bytesIn += bytes;
            }
            return bytes;
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public void close() throws IOException {
            super.close();
        }

        @Override
        public String toString() {
            return String.format("%s: In: (%d)", name, bytesIn);
        }
    }

    /**
     * An OutputStream that replaces one string of bytes with another.
     * If any range matches, the match starts after the partial match.
     */
    static class MatchReplaceOutputStream extends OutputStream {
        private final OutputStream out;
        private final String name;
        private volatile byte[] matchBytes;
        private volatile byte[] replaceBytes;
        int matchIndex;
        private int bytesOut = 0;
        private final OutputStream log;

        MatchReplaceOutputStream(OutputStream out, String name, OutputStream log,
                                 byte[] matchBytes, byte[] replaceBytes) {
            this.out = out;
            this.name = name;
            this.matchBytes = Objects.requireNonNull(matchBytes, "matchBytes");
            this.replaceBytes = Objects.requireNonNull(replaceBytes, "replaceBytes");
            matchIndex = 0;
            this.log = log;
        }

        public void setMatchReplaceBytes(byte[] matchBytes, byte[] replaceBytes) {
            this.matchBytes = matchBytes;
            this.replaceBytes = replaceBytes;
            matchIndex = 0;
        }


        public void write(int b) throws IOException {
            b = b & 0xff;
            if (matchBytes.length == 0) {
                out.write(b);
                log.write(b);
                bytesOut++;
                return;
            }
            if (b == (matchBytes[matchIndex] & 0xff)) {
                if (++matchIndex >= matchBytes.length) {
                    matchIndex = 0;
                    DEBUG( "TestSocketFactory MatchReplace %s replaced %d bytes at offset: %d (x%04x)%n",
                            name, replaceBytes.length, bytesOut, bytesOut);
                    out.write(replaceBytes);
                    log.write(replaceBytes);
                    bytesOut += replaceBytes.length;
                }
            } else {
                if (matchIndex > 0) {
                    // mismatch, write out any that matched already
                    if (matchIndex > 0) // Only non-trivial matches
                        DEBUG( "Partial match %s matched %d bytes at offset: %d (0x%04x), expected: x%02x, actual: x%02x%n",
                                name, matchIndex, bytesOut, bytesOut,  matchBytes[matchIndex], b);
                    out.write(matchBytes, 0, matchIndex);
                    log.write(matchBytes, 0, matchIndex);
                    bytesOut += matchIndex;
                    matchIndex = 0;
                }
                if (b == (matchBytes[matchIndex] & 0xff)) {
                    matchIndex++;
                } else {
                    out.write(b);
                    log.write(b);
                    bytesOut++;
                }
            }
        }

        @Override
        public String toString() {
            return String.format("%s: Out: (%d)", name, bytesOut);
        }
    }

    private static byte[] orig = new byte[]{
            (byte) 0x80, 0x05,
            0x73, 0x72, 0x00, 0x12, // TC_OBJECT, TC_CLASSDESC, length = 18
            0x6A, 0x61, 0x76, 0x61, 0x2E, 0x72, 0x6D, 0x69, 0x2E, // "java.rmi."
            0x64, 0x67, 0x63, 0x2E, 0x4C, 0x65, 0x61, 0x73, 0x65  // "dgc.Lease"
    };
    private static byte[] repl = new byte[]{
            (byte) 0x80, 0x05,
            0x73, 0x72, 0x00, 0x12, // TC_OBJECT, TC_CLASSDESC, length = 18
            0x6A, 0x61, 0x76, 0x61, 0x2E, (byte) 'l', (byte) 'a', (byte) 'n', (byte) 'g',
            0x2E, (byte) 'R', (byte) 'u', (byte) 'n', (byte) 'n', (byte) 'a', (byte) 'b', (byte) 'l',
            (byte) 'e'
    };

    @DataProvider(name = "MatchReplaceData")
    static Object[][] matchReplaceData() {
        byte[] empty = new byte[0];
        byte[] byte1 = new byte[]{1, 2, 3, 4, 5, 6};
        byte[] bytes2 = new byte[]{1, 2, 4, 3, 5, 6};
        byte[] bytes3 = new byte[]{6, 5, 4, 3, 2, 1};
        byte[] bytes4 = new byte[]{1, 2, 0x10, 0x20, 0x30, 0x40, 5, 6};
        byte[] bytes4a = new byte[]{1, 2, 0x10, 0x20, 0x30, 0x40, 5, 7};  // mostly matches bytes4
        byte[] bytes5 = new byte[]{0x30, 0x40, 5, 6};
        byte[] bytes6 = new byte[]{1, 2, 0x10, 0x20, 0x30};

        return new Object[][]{
                {new byte[]{}, new byte[]{}, empty, empty},
                {new byte[]{}, new byte[]{}, byte1, byte1},
                {new byte[]{3, 4}, new byte[]{4, 3}, byte1, bytes2}, //swap bytes
                {new byte[]{3, 4}, new byte[]{0x10, 0x20, 0x30, 0x40}, byte1, bytes4}, // insert
                {new byte[]{1, 2, 0x10, 0x20}, new byte[]{}, bytes4, bytes5}, // delete head
                {new byte[]{0x40, 5, 6}, new byte[]{}, bytes4, bytes6},   // delete tail
                {new byte[]{0x40, 0x50}, new byte[]{0x60, 0x50}, bytes4, bytes4}, // partial match, replace nothing
                {bytes4a, bytes3, bytes4, bytes4}, // long partial match, not replaced
                {orig, repl, orig, repl},
        };
    }

    @Test(enabled = true, dataProvider = "MatchReplaceData")
    static void test3(byte[] match, byte[] replace,
                      byte[] input, byte[] expected) {
        System.out.printf("match: %s, replace: %s%n", Arrays.toString(match), Arrays.toString(replace));
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream log = new ByteArrayOutputStream();
             OutputStream out = new MatchReplaceOutputStream(output, "test3",
                     log, match, replace)) {
            out.write(input);
            byte[] actual = output.toByteArray();
            long index = Arrays.mismatch(actual, expected);

            if (index >= 0) {
                System.out.printf("array mismatch, offset: %d%n", index);
                System.out.printf("actual: %s%n", Arrays.toString(actual));
                System.out.printf("expected: %s%n", Arrays.toString(expected));
            }
            Assert.assertEquals(actual, expected, "match/replace fail");
        } catch (IOException ioe) {
            Assert.fail("unexpected exception", ioe);
        }
    }



}
