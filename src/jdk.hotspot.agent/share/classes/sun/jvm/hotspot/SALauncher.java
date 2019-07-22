/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot;

import java.util.ArrayList;
import java.util.Arrays;

import sun.jvm.hotspot.tools.JStack;
import sun.jvm.hotspot.tools.JMap;
import sun.jvm.hotspot.tools.JInfo;
import sun.jvm.hotspot.tools.JSnap;

public class SALauncher {

    private static boolean launcherHelp() {
        System.out.println("    clhsdb       \tcommand line debugger");
        System.out.println("    hsdb         \tui debugger");
        System.out.println("    debugd --help\tto get more information");
        System.out.println("    jstack --help\tto get more information");
        System.out.println("    jmap   --help\tto get more information");
        System.out.println("    jinfo  --help\tto get more information");
        System.out.println("    jsnap  --help\tto get more information");
        return false;
    }

    private static boolean commonHelp(String mode) {
        return commonHelp(mode, false);
    }

    private static boolean commonHelpWithConnect(String mode) {
        return commonHelp(mode, true);
    }

    private static boolean commonHelp(String mode, boolean canConnectToRemote) {
        // --pid <pid>
        // --exe <exe>
        // --core <core>
        // --connect [<id>@]<host>
        System.out.println("    --pid <pid>             To attach to and operate on the given live process.");
        System.out.println("    --core <corefile>       To operate on the given core file.");
        System.out.println("    --exe <executable for corefile>");
        if (canConnectToRemote) {
            System.out.println("    --connect [<id>@]<host> To connect to a remote debug server (debugd).");
        }
        System.out.println();
        System.out.println("    The --core and --exe options must be set together to give the core");
        System.out.println("    file, and associated executable, to operate on. They can use");
        System.out.println("    absolute or relative paths.");
        System.out.println("    The --pid option can be set to operate on a live process.");
        if (canConnectToRemote) {
            System.out.println("    The --connect option can be set to connect to a debug server (debugd).");
            System.out.println("    --core, --pid, and --connect are mutually exclusive.");
        } else {
            System.out.println("    --core and --pid are mutually exclusive.");
        }
        System.out.println();
        System.out.println("    Examples: jhsdb " + mode + " --pid 1234");
        System.out.println("          or  jhsdb " + mode + " --core ./core.1234 --exe ./myexe");
        if (canConnectToRemote) {
            System.out.println("          or  jhsdb " + mode + " --connect debugserver");
            System.out.println("          or  jhsdb " + mode + " --connect id@debugserver");
        }
        return false;
    }

    private static boolean debugdHelp() {
        // [options] <pid> [server-id]
        // [options] <executable> <core> [server-id]
        System.out.println("    --serverid <id>         A unique identifier for this debug server.");
        return commonHelp("debugd");
    }

    private static boolean jinfoHelp() {
        // --flags -> -flags
        // --sysprops -> -sysprops
        System.out.println("    --flags                 To print VM flags.");
        System.out.println("    --sysprops              To print Java System properties.");
        System.out.println("    <no option>             To print both of the above.");
        return commonHelpWithConnect("jinfo");
    }

    private static boolean jmapHelp() {
        // --heap -> -heap
        // --binaryheap -> -heap:format=b
        // --histo -> -histo
        // --clstats -> -clstats
        // --finalizerinfo -> -finalizerinfo

        System.out.println("    <no option>             To print same info as Solaris pmap.");
        System.out.println("    --heap                  To print java heap summary.");
        System.out.println("    --binaryheap            To dump java heap in hprof binary format.");
        System.out.println("    --dumpfile <name>       The name of the dump file.");
        System.out.println("    --histo                 To print histogram of java object heap.");
        System.out.println("    --clstats               To print class loader statistics.");
        System.out.println("    --finalizerinfo         To print information on objects awaiting finalization.");
        return commonHelpWithConnect("jmap");
    }

    private static boolean jstackHelp() {
        // --locks -> -l
        // --mixed -> -m
        System.out.println("    --locks                 To print java.util.concurrent locks.");
        System.out.println("    --mixed                 To print both Java and native frames (mixed mode).");
        return commonHelpWithConnect("jstack");
    }

    private static boolean jsnapHelp() {
        System.out.println("    --all                   To print all performance counters.");
        return commonHelpWithConnect("jsnap");
    }

    private static boolean toolHelp(String toolName) {
        if (toolName.equals("jstack")) {
            return jstackHelp();
        }
        if (toolName.equals("jinfo")) {
            return jinfoHelp();
        }
        if (toolName.equals("jmap")) {
            return jmapHelp();
        }
        if (toolName.equals("jsnap")) {
            return jsnapHelp();
        }
        if (toolName.equals("debugd")) {
            return debugdHelp();
        }
        if (toolName.equals("hsdb")) {
            return commonHelp("hsdb");
        }
        if (toolName.equals("clhsdb")) {
            return commonHelp("clhsdb");
        }
        return launcherHelp();
    }

    private static final String NO_REMOTE = null;

    private static void buildAttachArgs(ArrayList<String> newArgs, String pid,
                                  String exe, String core, String remote, boolean allowEmpty) {
        if (!allowEmpty && (pid == null) && (exe == null) && (remote == NO_REMOTE)) {
            throw new SAGetoptException("You have to set --pid or --exe or --connect.");
        }

        if (pid != null) { // Attach to live process
            if (exe != null) {
                throw new SAGetoptException("Unnecessary argument: --exe");
            } else if (core != null) {
                throw new SAGetoptException("Unnecessary argument: --core");
            } else if (remote != NO_REMOTE) {
                throw new SAGetoptException("Unnecessary argument: --connect");
            } else if (!pid.matches("^\\d+$")) {
                throw new SAGetoptException("Invalid pid: " + pid);
            }

            newArgs.add(pid);
        } else if (exe != null) {
            if (remote != NO_REMOTE) {
                throw new SAGetoptException("Unnecessary argument: --connect");
            } else if (exe.length() == 0) {
                throw new SAGetoptException("You have to set --exe.");
            }

            newArgs.add(exe);

            if ((core == null) || (core.length() == 0)) {
                throw new SAGetoptException("You have to set --core.");
            }

            newArgs.add(core);
        } else if (remote != NO_REMOTE) {
            newArgs.add(remote);
        }
    }

    private static void runCLHSDB(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid="};

        ArrayList<String> newArgs = new ArrayList();
        String pid = null;
        String exe = null;
        String core = null;
        String s = null;

        while((s = sg.next(null, longOpts)) != null) {
            if (s.equals("exe")) {
                exe = sg.getOptarg();
                continue;
            }
            if (s.equals("core")) {
                core = sg.getOptarg();
                continue;
            }
            if (s.equals("pid")) {
                pid = sg.getOptarg();
                continue;
            }
        }

        buildAttachArgs(newArgs, pid, exe, core, NO_REMOTE, true);
        CLHSDB.main(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runHSDB(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid="};

        ArrayList<String> newArgs = new ArrayList();
        String pid = null;
        String exe = null;
        String core = null;
        String s = null;

        while((s = sg.next(null, longOpts)) != null) {
            if (s.equals("exe")) {
                exe = sg.getOptarg();
                continue;
            }
            if (s.equals("core")) {
                core = sg.getOptarg();
                continue;
            }
            if (s.equals("pid")) {
                pid = sg.getOptarg();
                continue;
            }
        }

        buildAttachArgs(newArgs, pid, exe, core, NO_REMOTE, true);
        HSDB.main(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runJSTACK(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid=", "connect=",
                                 "mixed", "locks"};

        ArrayList<String> newArgs = new ArrayList();
        String pid = null;
        String exe = null;
        String core = null;
        String remote = NO_REMOTE;
        String s = null;

        while((s = sg.next(null, longOpts)) != null) {
            if (s.equals("exe")) {
                exe = sg.getOptarg();
                continue;
            }
            if (s.equals("core")) {
                core = sg.getOptarg();
                continue;
            }
            if (s.equals("pid")) {
                pid = sg.getOptarg();
                continue;
            }
            if (s.equals("connect")) {
                remote = sg.getOptarg();
                continue;
            }
            if (s.equals("mixed")) {
                newArgs.add("-m");
                continue;
            }
            if (s.equals("locks")) {
                newArgs.add("-l");
                continue;
            }
        }

        buildAttachArgs(newArgs, pid, exe, core, remote, false);
        JStack jstack = new JStack(false, false);
        jstack.runWithArgs(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runJMAP(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid=", "connect=",
              "heap", "binaryheap", "dumpfile=", "histo", "clstats", "finalizerinfo"};

        ArrayList<String> newArgs = new ArrayList();
        String pid = null;
        String exe = null;
        String core = null;
        String remote = NO_REMOTE;
        String s = null;
        String dumpfile = null;
        boolean requestHeapdump = false;

        while((s = sg.next(null, longOpts)) != null) {
            if (s.equals("exe")) {
                exe = sg.getOptarg();
                continue;
            }
            if (s.equals("core")) {
                core = sg.getOptarg();
                continue;
            }
            if (s.equals("pid")) {
                pid = sg.getOptarg();
                continue;
            }
            if (s.equals("connect")) {
                remote = sg.getOptarg();
                continue;
            }
            if (s.equals("heap")) {
                newArgs.add("-heap");
                continue;
            }
            if (s.equals("binaryheap")) {
                requestHeapdump = true;
                continue;
            }
            if (s.equals("dumpfile")) {
                dumpfile = sg.getOptarg();
                continue;
            }
            if (s.equals("histo")) {
                newArgs.add("-histo");
                continue;
            }
            if (s.equals("clstats")) {
                newArgs.add("-clstats");
                continue;
            }
            if (s.equals("finalizerinfo")) {
                newArgs.add("-finalizerinfo");
                continue;
            }
        }

        if (!requestHeapdump && (dumpfile != null)) {
            throw new IllegalArgumentException("Unexpected argument dumpfile");
        }
        if (requestHeapdump) {
            if (dumpfile == null) {
                newArgs.add("-heap:format=b");
            } else {
                newArgs.add("-heap:format=b,file=" + dumpfile);
            }
        }

        buildAttachArgs(newArgs, pid, exe, core, remote, false);
        JMap.main(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runJINFO(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid=", "connect=",
                                     "flags", "sysprops"};

        ArrayList<String> newArgs = new ArrayList();
        String exe = null;
        String pid = null;
        String core = null;
        String remote = NO_REMOTE;
        String s = null;

        while((s = sg.next(null, longOpts)) != null) {
            if (s.equals("exe")) {
                exe = sg.getOptarg();
                continue;
            }
            if (s.equals("core")) {
                core = sg.getOptarg();
                continue;
            }
            if (s.equals("pid")) {
                pid = sg.getOptarg();
                continue;
            }
            if (s.equals("connect")) {
                remote = sg.getOptarg();
                continue;
            }
            if (s.equals("flags")) {
                newArgs.add("-flags");
                continue;
            }
            if (s.equals("sysprops")) {
                newArgs.add("-sysprops");
                continue;
            }
        }

        buildAttachArgs(newArgs, pid, exe, core, remote, false);
        JInfo.main(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runJSNAP(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid=", "connect=", "all"};

        ArrayList<String> newArgs = new ArrayList();
        String exe = null;
        String pid = null;
        String core = null;
        String remote = NO_REMOTE;
        String s = null;

        while((s = sg.next(null, longOpts)) != null) {
            if (s.equals("exe")) {
                exe = sg.getOptarg();
                continue;
            }
            if (s.equals("core")) {
                core = sg.getOptarg();
                continue;
            }
            if (s.equals("pid")) {
                pid = sg.getOptarg();
                continue;
            }
            if (s.equals("connect")) {
                remote = sg.getOptarg();
                continue;
            }
            if (s.equals("all")) {
                newArgs.add("-a");
                continue;
            }
        }

        buildAttachArgs(newArgs, pid, exe, core, remote, false);
        JSnap.main(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runDEBUGD(String[] oldArgs) {
        // By default SA agent classes prefer Windows process debugger
        // to windbg debugger. SA expects special properties to be set
        // to choose other debuggers. We will set those here before
        // attaching to SA agent.
        System.setProperty("sun.jvm.hotspot.debugger.useWindbgDebugger", "true");

        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid=", "serverid="};

        ArrayList<String> newArgs = new ArrayList<>();
        String exe = null;
        String pid = null;
        String core = null;
        String s = null;
        String serverid = null;

        while((s = sg.next(null, longOpts)) != null) {
          if (s.equals("exe")) {
              exe = sg.getOptarg();
              continue;
          }
          if (s.equals("core")) {
              core = sg.getOptarg();
              continue;
          }
          if (s.equals("pid")) {
              pid = sg.getOptarg();
              continue;
          }
          if (s.equals("serverid")) {
              serverid = sg.getOptarg();
              continue;
          }
        }

        buildAttachArgs(newArgs, pid, exe, core, NO_REMOTE, false);
        if (serverid != null) {
            newArgs.add(serverid);
        }

        // delegate to the actual SA debug server.
        sun.jvm.hotspot.DebugServer.main(newArgs.toArray(new String[newArgs.size()]));
    }

    public static void main(String[] args) {
        // Provide a help
        if (args.length == 0) {
            launcherHelp();
            return;
        }
        // No arguments imply help for debugd, jstack, jmap, jinfo but launch clhsdb and hsdb
        if (args.length == 1 && !args[0].equals("clhsdb") && !args[0].equals("hsdb")) {
            toolHelp(args[0]);
            return;
        }

        for (String arg : args) {
            if (arg.equals("-h") || arg.equals("-help") || arg.equals("--help")) {
                toolHelp(args[0]);
                return;
            }
        }

        String[] oldArgs = Arrays.copyOfRange(args, 1, args.length);

        try {
            // Run SA interactive mode
            if (args[0].equals("clhsdb")) {
                runCLHSDB(oldArgs);
                return;
            }

            if (args[0].equals("hsdb")) {
                runHSDB(oldArgs);
                return;
            }

            // Run SA tmtools mode
            if (args[0].equals("jstack")) {
                runJSTACK(oldArgs);
                return;
            }

            if (args[0].equals("jmap")) {
                runJMAP(oldArgs);
                return;
            }

            if (args[0].equals("jinfo")) {
                runJINFO(oldArgs);
                return;
            }

            if (args[0].equals("jsnap")) {
                runJSNAP(oldArgs);
                return;
            }

            if (args[0].equals("debugd")) {
                runDEBUGD(oldArgs);
                return;
            }

            throw new SAGetoptException("Unknown tool: " + args[0]);
        } catch (SAGetoptException e) {
            System.err.println(e.getMessage());
            toolHelp(args[0]);
        }
    }
}
