/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * A Launcher to launch a java process with its standard input, output,
 * and error streams connected to a socket.
 */
import java.net.*;
import java.nio.channels.*;
import java.io.IOException;

public class Launcher {

    static {
        System.loadLibrary("Launcher");
    }

    private static native void launch0(String cmdarray[], int fd) throws IOException;

    private static void launch(String className, String options[], String args[], int fd) throws IOException {
        String[] javacmd = Util.javaCommand();
        int options_len = (options == null) ? 0 : options.length;
        int args_len = (args == null) ? 0 : args.length;

        // java [-options] class [args...]
        int len = javacmd.length + options_len + 1 + args_len;

        String cmdarray[] = new String[len];
        int pos = 0;
        for (int i=0; i<javacmd.length; i++) {
            cmdarray[pos++] = javacmd[i];
        }
        for (int i=0; i<options_len; i++) {
            cmdarray[pos++] = options[i];
        }
        cmdarray[pos++] = className;
        for (int i=0; i<args_len; i++) {
            cmdarray[pos++] = args[i];
        }
        launch0(cmdarray, fd);
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

        InetSocketAddress isa = new InetSocketAddress(InetAddress.getLocalHost(), port);

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
