/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4531526
 * @summary Test that more than one debuggee cannot bind to same port
 *          at the same time.
 *
 * @build VMConnection ExclusiveBind HelloWorld
 * @run main ExclusiveBind
 */
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.net.ServerSocket;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.AttachingConnector;
import java.util.Map;
import java.util.List;
import java.util.Iterator;

public class ExclusiveBind {

    /*
     * Helper class to direct process output to the parent
     * System.out
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
                    int n = in.read(b);
                    if (n < 0) return;
                    for (int i=0; i<n; i++) {
                        System.out.print((char)b[i]);
                    }
                }
            } catch (IOException ioe) { }
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
     * Launch (in server mode) a debuggee with the given address and
     * suspend mode.
     */
    private static Process launch(String address, boolean suspend, String class_name) throws IOException {
        String exe = System.getProperty("java.home") + File.separator + "bin" +
            File.separator;
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
            " -agentlib:jdwp=transport=dt_socket,server=y,suspend=";
        if (suspend) {
            cmd += "y";
        } else {
            cmd += "n";
        }
        cmd += ",address=" + address + " " + class_name;

        System.out.println("Starting: " + cmd);

        Process p = Runtime.getRuntime().exec(cmd);
        IOHandler.handle(p.getInputStream());
        IOHandler.handle(p.getErrorStream());

        return p;
    }

    /*
     * - pick a TCP port
     * - Launch a debuggee in server=y,suspend=y,address=${port}
     * - Launch a second debuggee in server=y,suspend=n with the same port
     * - Second debuggee should fail with an error (address already in use)
     * - For clean-up we attach to the first debuggee and resume it.
     */
    public static void main(String args[]) throws Exception {
        // find a free port
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        ss.close();

        String address = String.valueOf(port);

        // launch the first debuggee
        Process process1 = launch(address, true, "HelloWorld");

        // give first debuggee time to suspend
        Thread.currentThread().sleep(5000);

        // launch a second debuggee with the same address
        Process process2 = launch(address, false, "HelloWorld");

        // get exit status from second debuggee
        int exitCode = process2.waitFor();

        // clean-up - attach to first debuggee and resume it
        AttachingConnector conn = (AttachingConnector)findConnector("com.sun.jdi.SocketAttach");
        Map conn_args = conn.defaultArguments();
        Connector.IntegerArgument port_arg =
            (Connector.IntegerArgument)conn_args.get("port");
        port_arg.setValue(port);
        VirtualMachine vm = conn.attach(conn_args);
        vm.resume();

        // if the second debuggee ran to completion then we've got a problem
        if (exitCode == 0) {
            throw new RuntimeException("Test failed - second debuggee didn't fail to bind");
        } else {
            System.out.println("Test passed - second debuggee correctly failed to bind");
        }
    }
}
