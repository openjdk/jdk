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

package sun.tools.jmap;

import java.lang.reflect.Method;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AttachNotSupportedException;
import sun.tools.attach.HotSpotVirtualMachine;

/*
 * This class is the main class for the JMap utility. It parses its arguments
 * and decides if the command should be satisfied using the VM attach mechanism
 * or an SA tool. At this time the only option that uses the VM attach mechanism
 * is the -dump option to get a heap dump of a running application. All other
 * options are mapped to SA tools.
 */
public class JMap {

    // Options handled by the attach mechanism
    private static String HISTO_OPTION = "-histo";
    private static String LIVE_HISTO_OPTION = "-histo:live";
    private static String DUMP_OPTION_PREFIX = "-dump:";

    // These options imply the use of a SA tool
    private static String SA_TOOL_OPTIONS =
      "-heap|-heap:format=b|-clstats|-finalizerinfo";

    // The -F (force) option is currently not passed through to SA
    private static String FORCE_SA_OPTION = "-F";

    // Default option (if nothing provided)
    private static String DEFAULT_OPTION = "-pmap";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage(1); // no arguments
        }

        // used to indicate if we should use SA
        boolean useSA = false;

        // the chosen option (-heap, -dump:*, ... )
        String option = null;

        // First iterate over the options (arguments starting with -).  There should be
        // one (but maybe two if -F is also used).
        int optionCount = 0;
        while (optionCount < args.length) {
            String arg = args[optionCount];
            if (!arg.startsWith("-")) {
                break;
            }
            if (arg.equals("-help") || arg.equals("-h")) {
                usage(0);
            } else if (arg.equals(FORCE_SA_OPTION)) {
                useSA = true;
            } else {
                if (option != null) {
                    usage(1);  // option already specified
                }
                option = arg;
            }
            optionCount++;
        }

        // if no option provided then use default.
        if (option == null) {
            option = DEFAULT_OPTION;
        }
        if (option.matches(SA_TOOL_OPTIONS)) {
            useSA = true;
        }

        // Next we check the parameter count. For the SA tools there are
        // one or two parameters. For the built-in -dump option there is
        // only one parameter (the process-id)
        int paramCount = args.length - optionCount;
        if (paramCount == 0 || paramCount > 2) {
            usage(1);
        }

        if (optionCount == 0 || paramCount != 1) {
            useSA = true;
        } else {
            // the parameter for the -dump option is a process-id.
            // If it doesn't parse to a number then it must be SA
            // debug server
            if (!args[optionCount].matches("[0-9]+")) {
                useSA = true;
            }
        }


        // at this point we know if we are executing an SA tool or a built-in
        // option.

        if (useSA) {
            // parameters (<pid> or <exe> <core>)
            String params[] = new String[paramCount];
            for (int i=optionCount; i<args.length; i++ ){
                params[i-optionCount] = args[i];
            }
            runTool(option, params);

        } else {
            String pid = args[1];
            // Here we handle the built-in options
            // As more options are added we should create an abstract tool class and
            // have a table to map the options
            if (option.equals(HISTO_OPTION)) {
                histo(pid, false);
            } else if (option.equals(LIVE_HISTO_OPTION)) {
                histo(pid, true);
            } else if (option.startsWith(DUMP_OPTION_PREFIX)) {
                dump(pid, option);
            } else {
                usage(1);
            }
        }
    }

    // Invoke SA tool  with the given arguments
    private static void runTool(String option, String args[]) throws Exception {
        String[][] tools = {
            { "-pmap",          "sun.jvm.hotspot.tools.PMap"             },
            { "-heap",          "sun.jvm.hotspot.tools.HeapSummary"      },
            { "-heap:format=b", "sun.jvm.hotspot.tools.HeapDumper"       },
            { "-histo",         "sun.jvm.hotspot.tools.ObjectHistogram"  },
            { "-clstats",       "sun.jvm.hotspot.tools.ClassLoaderStats" },
            { "-finalizerinfo", "sun.jvm.hotspot.tools.FinalizerInfo"    },
        };

        String tool = null;

        // -dump option needs to be handled in a special way
        if (option.startsWith(DUMP_OPTION_PREFIX)) {
            // first check that the option can be parsed
            String fn = parseDumpOptions(option);
            if (fn == null) {
                usage(1);
            }

            // tool for heap dumping
            tool = "sun.jvm.hotspot.tools.HeapDumper";

            // HeapDumper -f <file>
            args = prepend(fn, args);
            args = prepend("-f", args);
        } else {
            int i=0;
            while (i < tools.length) {
                if (option.equals(tools[i][0])) {
                    tool = tools[i][1];
                    break;
                }
                i++;
            }
        }
        if (tool == null) {
            usage(1);   // no mapping to tool
        }

        // Tool not available on this  platform.
        Class<?> c = loadClass(tool);
        if (c == null) {
            usage(1);
        }

        // invoke the main method with the arguments
        Class[] argTypes = { String[].class } ;
        Method m = c.getDeclaredMethod("main", argTypes);

        Object[] invokeArgs = { args };
        m.invoke(null, invokeArgs);
    }

    // loads the given class using the system class loader
    private static Class<?> loadClass(String name) {
        //
        // We specify the system clas loader so as to cater for development
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

    private static final String LIVE_OBJECTS_OPTION = "-live";
    private static final String ALL_OBJECTS_OPTION = "-all";
    private static void histo(String pid, boolean live) throws IOException {
        VirtualMachine vm = attach(pid);
        InputStream in = ((HotSpotVirtualMachine)vm).
            heapHisto(live ? LIVE_OBJECTS_OPTION : ALL_OBJECTS_OPTION);
        drain(vm, in);
    }

    private static void dump(String pid, String options) throws IOException {
        // parse the options to get the dump filename
        String filename = parseDumpOptions(options);
        if (filename == null) {
            usage(1);  // invalid options or no filename
        }

        // get the canonical path - important to avoid just passing
        // a "heap.bin" and having the dump created in the target VM
        // working directory rather than the directory where jmap
        // is executed.
        filename = new File(filename).getCanonicalPath();

        // dump live objects only or not
        boolean live = isDumpLiveObjects(options);

        VirtualMachine vm = attach(pid);
        System.out.println("Dumping heap to " + filename + " ...");
        InputStream in = ((HotSpotVirtualMachine)vm).
            dumpHeap((Object)filename,
                     (live ? LIVE_OBJECTS_OPTION : ALL_OBJECTS_OPTION));
        drain(vm, in);
    }

    // Parse the options to the -dump option. Valid options are format=b and
    // file=<file>. Returns <file> if provided. Returns null if <file> not
    // provided, or invalid option.
    private static String parseDumpOptions(String arg) {
        assert arg.startsWith(DUMP_OPTION_PREFIX);

        String filename = null;

        // options are separated by comma (,)
        String options[] = arg.substring(DUMP_OPTION_PREFIX.length()).split(",");

        for (int i=0; i<options.length; i++) {
            String option = options[i];

            if (option.equals("format=b")) {
                // ignore format (not needed at this time)
            } else if (option.equals("live")) {
                // a valid suboption
            } else {

                // file=<file> - check that <file> is specified
                if (option.startsWith("file=")) {
                    filename = option.substring(5);
                    if (filename.length() == 0) {
                        return null;
                    }
                } else {
                    return null;  // option not recognized
                }
            }
        }
        return filename;
    }

    private static boolean isDumpLiveObjects(String arg) {
        // options are separated by comma (,)
        String options[] = arg.substring(DUMP_OPTION_PREFIX.length()).split(",");
        for (String suboption : options) {
            if (suboption.equals("live")) {
                return true;
            }
        }
        return false;
    }

    // Attach to <pid>, existing if we fail to attach
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
            if ((x instanceof AttachNotSupportedException) && haveSA()) {
                System.err.println("The -F option can be used when the " +
                  "target process is not responding");
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

    // return a new string array with arg as the first element
    private static String[] prepend(String arg, String args[]) {
        String[] newargs = new String[args.length+1];
        newargs[0] = arg;
        System.arraycopy(args, 0, newargs, 1, args.length);
        return newargs;
    }

    // returns true if SA is available
    private static boolean haveSA() {
        Class<?> c = loadClass("sun.jvm.hotspot.tools.HeapSummary");
        return (c != null);
    }

    // print usage message
    private static void usage(int exit) {
        System.err.println("Usage:");
        if (haveSA()) {
            System.err.println("    jmap [option] <pid>");
            System.err.println("        (to connect to running process)");
            System.err.println("    jmap [option] <executable <core>");
            System.err.println("        (to connect to a core file)");
            System.err.println("    jmap [option] [server_id@]<remote server IP or hostname>");
            System.err.println("        (to connect to remote debug server)");
            System.err.println("");
            System.err.println("where <option> is one of:");
            System.err.println("    <none>               to print same info as Solaris pmap");
            System.err.println("    -heap                to print java heap summary");
            System.err.println("    -histo[:live]        to print histogram of java object heap; if the \"live\"");
            System.err.println("                         suboption is specified, only count live objects");
            System.err.println("    -clstats             to print class loader statistics");
            System.err.println("    -finalizerinfo       to print information on objects awaiting finalization");
            System.err.println("    -dump:<dump-options> to dump java heap in hprof binary format");
            System.err.println("                         dump-options:");
            System.err.println("                           live         dump only live objects; if not specified,");
            System.err.println("                                        all objects in the heap are dumped.");
            System.err.println("                           format=b     binary format");
            System.err.println("                           file=<file>  dump heap to <file>");
            System.err.println("                         Example: jmap -dump:live,format=b,file=heap.bin <pid>");
            System.err.println("    -F                   force. Use with -dump:<dump-options> <pid> or -histo");
            System.err.println("                         to force a heap dump or histogram when <pid> does not");
            System.err.println("                         respond. The \"live\" suboption is not supported");
            System.err.println("                         in this mode.");
            System.err.println("    -h | -help           to print this help message");
            System.err.println("    -J<flag>             to pass <flag> directly to the runtime system");
        } else {
            System.err.println("    jmap -histo <pid>");
            System.err.println("      (to connect to running process and print histogram of java object heap");
            System.err.println("    jmap -dump:<dump-options> <pid>");
            System.err.println("      (to connect to running process and dump java heap)");
            System.err.println("");
            System.err.println("    dump-options:");
            System.err.println("      format=b     binary default");
            System.err.println("      file=<file>  dump heap to <file>");
            System.err.println("");
            System.err.println("    Example:       jmap -dump:format=b,file=heap.bin <pid>");
        }

        System.exit(exit);
    }
}
