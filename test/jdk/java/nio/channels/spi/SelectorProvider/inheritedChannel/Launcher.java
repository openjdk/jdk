/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * A Launcher to launch a java process with its standard input, output,
 * and error streams connected to a socket.
 */
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Launcher {

    static {
        System.loadLibrary("InheritedChannel");
    }

    private static native void launch0(String cmdarray[], int fd) throws IOException;

    private static void launch(String className, String options[], String args[], int fd) throws IOException {
        // java [-options] class [args...]
        int optsLen = (options == null) ? 0 : options.length;
        int argsLen = (args == null) ? 0 : args.length;
        int len = 1 + optsLen + 1 + argsLen;
        String cmdarray[] = new String[len];
        int pos = 0;
        cmdarray[pos++] = Util.javaCommand();
        if (options != null) {
            for (String opt: options) {
                cmdarray[pos++] = opt;
            }
        }
        cmdarray[pos++] = className;
        if (args != null) {
            for (String arg: args) {
                cmdarray[pos++] = arg;
            }
        }
        launch0(cmdarray, fd);
    }


    /**
     * Launch 'java' with specified class using a UnixDomainSocket pair linking calling
     * process to the child VM. UnixDomainSocket is a simplified interface to PF_UNIX sockets
     * which supports byte a time reads and writes.
     */
    public static UnixDomainSocket launchWithUnixDomainSocket(String className) throws IOException {
        UnixDomainSocket[] socks = UnixDomainSocket.socketpair();
        launch(className, null, null, socks[0].fd());
        socks[0].close();
        return socks[1];
    }

    /*
     * Launch 'java' with specified class with the specified arguments (may be null).
     * The launched process will inherit a connected TCP socket. The remote endpoint
     * will be the SocketChannel returned by this method.
     */
    public static SocketChannel launchWithSocketChannel(String className, String options[], String args[]) throws IOException {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(0));
        InetSocketAddress isa = new InetSocketAddress(InetAddress.getLocalHost(),
                                                      ssc.socket().getLocalPort());
        SocketChannel sc1 = SocketChannel.open(isa);
        SocketChannel sc2 = ssc.accept();
        launch(className, options, args, Util.getFD(sc2));
        sc2.close();
        ssc.close();
        return sc1;
    }

    public static SocketChannel launchWithSocketChannel(String className, String args[]) throws IOException {
        return launchWithSocketChannel(className, null, args);
    }

    public static SocketChannel launchWithSocketChannel(String className) throws IOException {
        return launchWithSocketChannel(className, null);
    }

    /*
     * Launch 'java' with specified class with the specified arguments (may be null).
     * The launched process will inherited a TCP listener socket.
     * Once launched this method tries to connect to service. If a connection
     * can be established a SocketChannel, connected to the service, is returned.
     */
    public static SocketChannel launchWithServerSocketChannel(String className, String options[], String args[])
        throws IOException
    {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(0));
        int port = ssc.socket().getLocalPort();
        launch(className, options, args, Util.getFD(ssc));
        ssc.close();
        InetSocketAddress isa = new InetSocketAddress(InetAddress.getLocalHost(), port);
        return SocketChannel.open(isa);
    }

    public static SocketChannel launchWithServerSocketChannel(String className, String args[]) throws IOException {
        return launchWithServerSocketChannel(className, null, args);
    }

    public static SocketChannel launchWithServerSocketChannel(String className) throws IOException {
        return launchWithServerSocketChannel(className, null);
    }

    /*
     * Launch 'java' with specified class with the specified arguments (may be null).
     * The launch process will inherited a bound UDP socket.
     * Once launched this method creates a DatagramChannel and "connects
     * it to the service. The created DatagramChannel is then returned.
     * As it is connected any packets sent from the socket will be
     * sent to the service.
     */
    public static DatagramChannel launchWithDatagramChannel(String className, String options[], String args[])
        throws IOException
    {
        DatagramChannel dc = DatagramChannel.open();
        dc.socket().bind(new InetSocketAddress(0));

        int port = dc.socket().getLocalPort();
        launch(className, options, args, Util.getFD(dc));
        dc.close();

        dc = DatagramChannel.open();
        InetAddress address = InetAddress.getLocalHost();
        if (address.isLoopbackAddress()) {
            address = InetAddress.getLoopbackAddress();
        }
        InetSocketAddress isa = new InetSocketAddress(address, port);

        dc.connect(isa);
        return dc;
    }

    public static DatagramChannel launchWithDatagramChannel(String className, String args[]) throws IOException {
        return launchWithDatagramChannel(className, null, args);
    }

    public static DatagramChannel launchWithDatagramChannel(String className) throws IOException {
        return launchWithDatagramChannel(className, null);
    }
}
