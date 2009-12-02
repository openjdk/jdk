/*
 * Copyright 2004-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 4997445
 * @summary Test that with server=y, when VM runs to System.exit() no error happens
 *
 * @build VMConnection RunToExit Exit0
 * @run main/othervm RunToExit
 */
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.BufferedInputStream;
import java.net.ServerSocket;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.AttachingConnector;
import java.util.Map;
import java.util.List;
import java.util.Iterator;

public class RunToExit {

    /* Increment this when ERROR: seen */
    static int error_seen = 0;
    static volatile boolean ready = false;
    /*
     * Helper class to direct process output to a StringBuffer
     */
    static class IOHandler implements Runnable {
        private String              name;
        private BufferedInputStream in;
        private StringBuffer        buffer;

        IOHandler(String name, InputStream in) {
            this.name = name;
            this.in = new BufferedInputStream(in);
            this.buffer = new StringBuffer();
        }

        static void handle(String name, InputStream in) {
            IOHandler handler = new IOHandler(name, in);
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
                    buffer.append(new String(b, 0, n));
                }
            } catch (IOException ioe) { }

            String str = buffer.toString();
            if ( str.contains("ERROR:") ) {
                error_seen++;
            }
            System.out.println(name + ": " + str);
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
                     + File.separator + "bin" + File.separator;
        String arch = System.getProperty("os.arch");
        String osname = System.getProperty("os.name");
        if (osname.equals("SunOS") && arch.equals("sparcv9")) {
            exe += "sparcv9/java";
        } else if (osname.equals("SunOS") && arch.equals("amd64")) {
            exe += "amd64/java";
        } else {
            exe += "java";
        }
        String cmd = exe + " " + VMConnection.getDebuggeeVMOptions() +
            " -agentlib:jdwp=transport=dt_socket" +
            ",server=y" + ",suspend=y" + ",address=" + address +
            " " + class_name;

        System.out.println("Starting: " + cmd);

        Process p = Runtime.getRuntime().exec(cmd);

        IOHandler.handle("Input Stream", p.getInputStream());
        IOHandler.handle("Error Stream", p.getErrorStream());

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

        int exitCode = process.waitFor();

        // if the server debuggee ran cleanly, we assume we were clean
        if (exitCode == 0 && error_seen == 0) {
            System.out.println("Test passed - server debuggee cleanly terminated");
        } else {
            throw new RuntimeException("Test failed - server debuggee generated an error when it terminated");
        }
    }
}
