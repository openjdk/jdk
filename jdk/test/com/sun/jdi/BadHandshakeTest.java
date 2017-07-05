/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 6306165 6432567
 * @summary Check that a bad handshake doesn't cause a debuggee to abort
 *
 * @build VMConnection BadHandshakeTest Exit0
 * @run main BadHandshakeTest
 *
 */
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.BufferedInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.AttachingConnector;
import java.util.Map;
import java.util.List;
import java.util.Iterator;

public class BadHandshakeTest {

    static volatile boolean ready = false;

    /*
     * Helper class to redirect process output/error
     */
    static class IOHandler implements Runnable {
        InputStream in;

        IOHandler(InputStream in) {
            this.in = in;
        }

        static void handle(InputStream in) {
            IOHandler handler = new IOHandler(in);
            Thread thr = new Thread(handler);
            thr.setDaemon(true);
            thr.start();
        }

        public void run() {
            try {
                byte b[] = new byte[100];
                for (;;) {
                    int n = in.read(b, 0, 100);
                    // The first thing that will get read is
                    //    Listening for transport dt_socket at address: xxxxx
                    // which shows the debuggee is ready to accept connections.
                    ready = true;
                    if (n < 0) {
                        break;
                    }
                    String s = new String(b, 0, n, "UTF-8");
                    System.out.print(s);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

    }

    /*
     * Find a connector by name
     */
    private static Connector findConnector(String name) {
        List connectors = Bootstrap.virtualMachineManager().allConnectors();
        Iterator iter = connectors.iterator();
        while (iter.hasNext()) {
            Connector connector = (Connector)iter.next();
            if (connector.name().equals(name)) {
                return connector;
            }
        }
        return null;
    }

    /*
     * Launch a server debuggee with the given address
     */
    private static Process launch(String address, String class_name) throws IOException {
        String exe =   System.getProperty("java.home")
                     + File.separator + "bin" + File.separator + "java";
        String cmd = exe + " " + VMConnection.getDebuggeeVMOptions() +
            " -agentlib:jdwp=transport=dt_socket" +
            ",server=y" + ",suspend=y" + ",address=" + address +
            " " + class_name;

        System.out.println("Starting: " + cmd);

        Process p = Runtime.getRuntime().exec(cmd);

        IOHandler.handle(p.getInputStream());
        IOHandler.handle(p.getErrorStream());

        return p;
    }

    /*
     * - pick a TCP port
     * - Launch a server debuggee: server=y,suspend=y,address=${port}
     * - run it to VM death
     * - verify we saw no error
     */
    public static void main(String args[]) throws Exception {
        // find a free port
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        ss.close();

        String address = String.valueOf(port);

        // launch the server debuggee
        Process process = launch(address, "Exit0");

        // wait for the debugge to be ready
        while (!ready) {
            try {
                Thread.sleep(1000);
            } catch(Exception ee) {
                throw ee;
            }
        }

        // Connect to the debuggee and handshake with garbage
        Socket s = new Socket(InetAddress.getLocalHost(), port);
        s.getOutputStream().write("Here's a poke in the eye".getBytes("UTF-8"));
        s.close();

        // Re-connect and to a partial handshake - don't disconnect
        s = new Socket(InetAddress.getLocalHost(), port);
        s.getOutputStream().write("JDWP-".getBytes("UTF-8"));


        // attach to server debuggee and resume it so it can exit
        AttachingConnector conn = (AttachingConnector)findConnector("com.sun.jdi.SocketAttach");
        Map conn_args = conn.defaultArguments();
        Connector.IntegerArgument port_arg =
            (Connector.IntegerArgument)conn_args.get("port");
        port_arg.setValue(port);
        VirtualMachine vm = conn.attach(conn_args);

        // The first event is always a VMStartEvent, and it is always in
        // an EventSet by itself.  Wait for it.
        EventSet evtSet = vm.eventQueue().remove();
        for (Event event: evtSet) {
            if (event instanceof VMStartEvent) {
                break;
            }
            throw new RuntimeException("Test failed - debuggee did not start properly");
        }

        vm.eventRequestManager().deleteAllBreakpoints();
        vm.resume();

        process.waitFor();
    }

}
