/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6354345
 * @summary Check that a double agent request fails
 *
 * @build VMConnection DoubleAgentTest Exit0
 * @run main DoubleAgentTest
 *
 */
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;

public class DoubleAgentTest {

    static Object locker = new Object();
    static String outputText = "";

    /*
     * Helper class to redirect process output/error
     */
    static class IOHandler implements Runnable {
        InputStream in;

        IOHandler(InputStream in) {
            this.in = in;
        }

        static Thread handle(InputStream in) {
            IOHandler handler = new IOHandler(in);
            Thread thr = new Thread(handler);
            thr.setDaemon(true);
            thr.start();
            return thr;
        }

        public void run() {
            try {
                byte b[] = new byte[100];
                for (;;) {
                    int n = in.read(b, 0, 100);
                    // The first thing that will get read is
                    //    Listening for transport dt_socket at address: xxxxx
                    // which shows the debuggee is ready to accept connections.
                    synchronized(locker) {
                        locker.notify();
                    }
                    if (n < 0) {
                        break;
                    }
                    String s = new String(b, 0, n, "UTF-8");
                    System.out.print(s);
                    synchronized(outputText) {
                        outputText += s;
                    }
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

    }

    /*
     * Launch a server debuggee with the given address
     */
    private static Process launch(String address, String class_name) throws IOException {
        String exe =   System.getProperty("java.home")
                     + File.separator + "bin" + File.separator;
        String arch = System.getProperty("os.arch");
        if (arch.equals("sparcv9")) {
            exe += "sparcv9/java";
        } else if (arch.equals("amd64")) {
            exe += "amd64/java";
        } else {
            exe += "java";
        }
        String jdwpOption = "-agentlib:jdwp=transport=dt_socket"
                         + ",server=y" + ",suspend=y" + ",address=" + address;
        String cmd = exe + " " + VMConnection.getDebuggeeVMOptions()
                         + " " + jdwpOption
                         + " " + jdwpOption
                         + " " + class_name;

        System.out.println("Starting: " + cmd);

        Process p = Runtime.getRuntime().exec(cmd);

        return p;
    }

    /*
     * - pick a TCP port
     * - Launch a server debuggee that should fail
     * - verify we saw error
     */
    public static void main(String args[]) throws Exception {
        // find a free port
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        ss.close();

        String address = String.valueOf(port);

        // launch the server debuggee
        Process process = launch(address, "Exit0");
        Thread t1 = IOHandler.handle(process.getInputStream());
        Thread t2 = IOHandler.handle(process.getErrorStream());

        // wait for the debugge to be ready
        synchronized(locker) {
            locker.wait();
        }

        int exitCode = process.waitFor();
        try {
            t1.join();
            t2.join();
        } catch ( InterruptedException e ) {
            e.printStackTrace();
            throw new Exception("Debuggee failed InterruptedException");
        }

        if ( outputText.contains("capabilities") ) {
            throw new Exception(
                "Debuggee failed with ERROR about capabilities: " + outputText);
        }

        if ( !outputText.contains("ERROR") ) {
            throw new Exception(
                "Debuggee does not have ERROR in the output: " + outputText);
        }

        if ( exitCode == 0 ) {
            throw new Exception(
                "Debuggee should have failed with an non-zero exit code");
        }

    }

}
