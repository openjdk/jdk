/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.tools.jstack;

import java.io.InputStream;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AttachNotSupportedException;
import sun.tools.attach.HotSpotVirtualMachine;
import jdk.internal.vm.agent.spi.ToolProvider;
import jdk.internal.vm.agent.spi.ToolProviderFinder;

/*
 * This class is the main class for the JStack utility. It parses its arguments
 * and decides if the command should be executed by the SA JStack tool or by
 * obtained the thread dump from a target process using the VM attach mechanism
 */
public class JStack {
    private static final String SA_JSTACK_TOOL_NAME = "jstack";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage(1); // no arguments
        }

        boolean useSA = false;
        boolean mixed = false;
        boolean locks = false;

        // Parse the options (arguments starting with "-" )
        int optionCount = 0;
        while (optionCount < args.length) {
            String arg = args[optionCount];
            if (!arg.startsWith("-")) {
                break;
            }
            if (arg.equals("-help") || arg.equals("-h")) {
                usage(0);
            }
            else if (arg.equals("-F")) {
                useSA = true;
            }
            else {
                if (arg.equals("-m")) {
                    mixed = true;
                } else {
                    if (arg.equals("-l")) {
                       locks = true;
                    } else {
                        usage(1);
                    }
                }
            }
            optionCount++;
        }

        // mixed stack implies SA tool
        if (mixed) {
            useSA = true;
        }

        // Next we check the parameter count. If there are two parameters
        // we assume core file and executable so we use SA.
        int paramCount = args.length - optionCount;
        if (paramCount == 0 || paramCount > 2) {
            usage(1);
        }
        if (paramCount == 2) {
            useSA = true;
        } else {
            // If we can't parse it as a pid then it must be debug server
            if (!args[optionCount].matches("[0-9]+")) {
                useSA = true;
            }
        }

        // now execute using the SA JStack tool or the built-in thread dumper
        if (useSA) {
            // parameters (<pid> or <exe> <core>
            String params[] = new String[paramCount];
            for (int i=optionCount; i<args.length; i++ ){
                params[i-optionCount] = args[i];
            }
            runJStackTool(mixed, locks, params);
        } else {
            // pass -l to thread dump operation to get extra lock info
            String pid = args[optionCount];
            String params[];
            if (locks) {
                params = new String[] { "-l" };
            } else {
                params = new String[0];
            }
            runThreadDump(pid, params);
        }
    }

    // SA JStack tool
    private static boolean isAgentToolPresent() {
        return ToolProviderFinder.find(SA_JSTACK_TOOL_NAME) != null;
    }

    private static void runJStackTool(boolean mixed, boolean locks, String args[]) throws Exception {
        ToolProvider tool = ToolProviderFinder.find(SA_JSTACK_TOOL_NAME);
        if (tool == null) {
            usage(1);            // SA not available
        }

        // JStack tool also takes -m and -l arguments
        if (mixed) {
            args = prepend("-m", args);
        }
        if (locks) {
            args = prepend("-l", args);
        }

        tool.run(args);
    }


    // Attach to pid and perform a thread dump
    private static void runThreadDump(String pid, String args[]) throws Exception {
        VirtualMachine vm = null;
        try {
            vm = VirtualMachine.attach(pid);
        } catch (Exception x) {
            String msg = x.getMessage();
            if (msg != null) {
                System.err.println(pid + ": " + msg);
            } else {
                x.printStackTrace();
            }
            if ((x instanceof AttachNotSupportedException) && isAgentToolPresent()) {
                System.err.println("The -F option can be used when the target " +
                    "process is not responding");
            }
            System.exit(1);
        }

        // Cast to HotSpotVirtualMachine as this is implementation specific
        // method.
        InputStream in = ((HotSpotVirtualMachine)vm).remoteDataDump((Object[])args);

        // read to EOF and just print output
        byte b[] = new byte[256];
        int n;
        do {
            n = in.read(b);
            if (n > 0) {
                String s = new String(b, 0, n, "UTF-8");
                System.out.print(s);
            }
        } while (n > 0);
        in.close();
        vm.detach();
    }

    // return a new string array with arg as the first element
    private static String[] prepend(String arg, String args[]) {
        String[] newargs = new String[args.length+1];
        newargs[0] = arg;
        System.arraycopy(args, 0, newargs, 1, args.length);
        return newargs;
    }

    // print usage message
    private static void usage(int exit) {
        System.err.println("Usage:");
        System.err.println("    jstack [-l] <pid>");
        System.err.println("        (to connect to running process)");

        if (isAgentToolPresent()) {
            System.err.println("    jstack -F [-m] [-l] <pid>");
            System.err.println("        (to connect to a hung process)");
            System.err.println("    jstack [-m] [-l] <executable> <core>");
            System.err.println("        (to connect to a core file)");
            System.err.println("    jstack [-m] [-l] [server_id@]<remote server IP or hostname>");
            System.err.println("        (to connect to a remote debug server)");
        }

        System.err.println("");
        System.err.println("Options:");

        if (isAgentToolPresent()) {
            System.err.println("    -F  to force a thread dump. Use when jstack <pid> does not respond" +
                " (process is hung)");
            System.err.println("    -m  to print both java and native frames (mixed mode)");
        }

        System.err.println("    -l  long listing. Prints additional information about locks");
        System.err.println("    -h or -help to print this help message");
        System.exit(exit);
    }
}
