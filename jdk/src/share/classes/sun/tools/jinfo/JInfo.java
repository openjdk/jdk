/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.jinfo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.io.IOException;
import java.io.InputStream;

import com.sun.tools.attach.VirtualMachine;

import sun.tools.attach.HotSpotVirtualMachine;

/*
 * This class is the main class for the JInfo utility. It parses its arguments
 * and decides if the command should be satisfied using the VM attach mechanism
 * or an SA tool.
 */
public class JInfo {

    @SuppressWarnings("fallthrough")
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage(1); // no arguments
        }

        // First determine if we should launch SA or not
        boolean useSA = false;
        if (args[0].equals("-F")) {
            // delete the -F
            args = Arrays.copyOfRange(args, 1, args.length);
            useSA = true;
        } else if (args[0].equals("-flags")
            || args[0].equals("-sysprops"))
        {
            if (args.length == 2) {
                if (!args[1].matches("[0-9]+")) {
                    // If args[1] doesn't parse to a number then
                    // it must be the SA debug server
                    // (otherwise it is the pid)
                    useSA = true;
                }
            }
            if (args.length == 3) {
                // arguments include an executable and a core file
                useSA = true;
            }
        } else if (!args[0].startsWith("-")) {
            if (args.length == 2) {
                // the only arguments are an executable and a core file
                useSA = true;
            }
        } else if (args[0].equals("-h")
                || args[0].equals("-help")) {
            usage(0);
        }

        if (useSA) {
            // SA only supports -flags or -sysprops
            if (args[0].startsWith("-")) {
                if (!(args[0].equals("-flags") || args[0].equals("-sysprops"))) {
                    usage(1);
                }
            }

            // invoke SA which does it's own argument parsing
            runTool(args);

        } else {
            // Now we can parse arguments for the non-SA case
            String pid = null;

            switch(args[0]) {
            case "-flag":
                if (args.length != 3) {
                    usage(1);
                }
                String option = args[1];
                pid = args[2];
                flag(pid, option);
                break;
            case "-flags":
                if (args.length != 2) {
                    usage(1);
                }
                pid = args[1];
                flags(pid);
                break;
            case "-sysprops":
                if (args.length != 2) {
                    usage(1);
                }
                pid = args[1];
                sysprops(pid);
                break;
            case "-help":
            case "-h":
                usage(0);
                // Fall through
            default:
               if (args.length == 1) {
                   // no flags specified, we do -sysprops and -flags
                   pid = args[0];
                   sysprops(pid);
                   System.out.println();
                   flags(pid);
               } else {
                   usage(1);
               }
            }
        }
    }

    // Invoke SA tool with the given arguments
    private static void runTool(String args[]) throws Exception {
        String tool = "sun.jvm.hotspot.tools.JInfo";
        // Tool not available on this platform.
        Class<?> c = loadClass(tool);
        if (c == null) {
            usage(1);
        }

        // invoke the main method with the arguments
        Class<?>[] argTypes = { String[].class } ;
        Method m = c.getDeclaredMethod("main", argTypes);

        Object[] invokeArgs = { args };
        m.invoke(null, invokeArgs);
    }

    // loads the given class using the system class loader
    private static Class<?> loadClass(String name) {
        //
        // We specify the system class loader so as to cater for development
        // environments where this class is on the boot class path but sa-jdi.jar
        // is on the system class path. Once the JDK is deployed then both
        // tools.jar and sa-jdi.jar are on the system class path.
        //
        try {
            return Class.forName(name, true,
                                 ClassLoader.getSystemClassLoader());
        } catch (Exception x)  { }
        return null;
    }

    private static void flag(String pid, String option) throws IOException {
        HotSpotVirtualMachine vm = (HotSpotVirtualMachine) attach(pid);
        String flag;
        InputStream in;
        int index = option.indexOf('=');
        if (index != -1) {
            flag = option.substring(0, index);
            String value = option.substring(index + 1);
            in = vm.setFlag(flag, value);
        } else {
            char c = option.charAt(0);
            switch (c) {
                case '+':
                    flag = option.substring(1);
                    in = vm.setFlag(flag, "1");
                    break;
                case '-':
                    flag = option.substring(1);
                    in = vm.setFlag(flag, "0");
                    break;
                default:
                    flag = option;
                    in = vm.printFlag(flag);
                    break;
            }
        }

        drain(vm, in);
    }

    private static void flags(String pid) throws IOException {
        HotSpotVirtualMachine vm = (HotSpotVirtualMachine) attach(pid);
        InputStream in = vm.executeJCmd("VM.flags");
        System.out.println("VM Flags:");
        drain(vm, in);
    }

    private static void sysprops(String pid) throws IOException {
        HotSpotVirtualMachine vm = (HotSpotVirtualMachine) attach(pid);
        InputStream in = vm.executeJCmd("VM.system_properties");
        System.out.println("Java System Properties:");
        drain(vm, in);
    }

    // Attach to <pid>, exiting if we fail to attach
    private static VirtualMachine attach(String pid) {
        try {
            return VirtualMachine.attach(pid);
        } catch (Exception x) {
            String msg = x.getMessage();
            if (msg != null) {
                System.err.println(pid + ": " + msg);
            } else {
                x.printStackTrace();
            }
            System.exit(1);
            return null; // keep compiler happy
        }
    }

    // Read the stream from the target VM until EOF, then detach
    private static void drain(VirtualMachine vm, InputStream in) throws IOException {
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


    // print usage message
    private static void usage(int exit) {

        Class<?> c = loadClass("sun.jvm.hotspot.tools.JInfo");
        boolean usageSA = (c != null);

        System.err.println("Usage:");
        if (usageSA) {
            System.err.println("    jinfo [option] <pid>");
            System.err.println("        (to connect to a running process)");
            System.err.println("    jinfo -F [option] <pid>");
            System.err.println("        (to connect to a hung process)");
            System.err.println("    jinfo [option] <executable> <core>");
            System.err.println("        (to connect to a core file)");
            System.err.println("    jinfo [option] [server_id@]<remote server IP or hostname>");
            System.err.println("        (to connect to remote debug server)");
            System.err.println("");
            System.err.println("where <option> is one of:");
            System.err.println("  for running processes:");
            System.err.println("    -flag <name>         to print the value of the named VM flag");
            System.err.println("    -flag [+|-]<name>    to enable or disable the named VM flag");
            System.err.println("    -flag <name>=<value> to set the named VM flag to the given value");
            System.err.println("  for running or hung processes and core files:");
            System.err.println("    -flags               to print VM flags");
            System.err.println("    -sysprops            to print Java system properties");
            System.err.println("    <no option>          to print both VM flags and system properties");
            System.err.println("    -h | -help           to print this help message");
        } else {
            System.err.println("    jinfo <option> <pid>");
            System.err.println("       (to connect to a running process)");
            System.err.println("");
            System.err.println("where <option> is one of:");
            System.err.println("    -flag <name>         to print the value of the named VM flag");
            System.err.println("    -flag [+|-]<name>    to enable or disable the named VM flag");
            System.err.println("    -flag <name>=<value> to set the named VM flag to the given value");
            System.err.println("    -flags               to print VM flags");
            System.err.println("    -sysprops            to print Java system properties");
            System.err.println("    <no option>          to print both VM flags and system properties");
            System.err.println("    -h | -help           to print this help message");
        }

        System.exit(exit);
    }
}
