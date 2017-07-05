/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
        System.out.println("    jstack --help\tto get more information");
        System.out.println("    jmap   --help\tto get more information");
        System.out.println("    jinfo  --help\tto get more information");
        System.out.println("    jsnap  --help\tto get more information");
        return false;
    }

    private static boolean commonHelp() {
        // --pid <pid>
        // --exe <exe>
        // --core <core>
        System.out.println("    --exe\texecutable image name");
        System.out.println("    --core\tpath to coredump");
        System.out.println("    --pid\tpid of process to attach");
        return false;
    }

    private static boolean jinfoHelp() {
        // --flags -> -flags
        // --sysprops -> -sysprops
        System.out.println("    --flags\tto print VM flags");
        System.out.println("    --sysprops\tto print Java System properties");
        System.out.println("    <no option>\tto print both of the above");
        return commonHelp();
    }

    private static boolean jmapHelp() {
        // --heap -> -heap
        // --binaryheap -> -heap:format=b
        // --histo -> -histo
        // --clstats -> -clstats
        // --finalizerinfo -> -finalizerinfo

        System.out.println("    <no option>\tto print same info as Solaris pmap");
        System.out.println("    --heap\tto print java heap summary");
        System.out.println("    --binaryheap\tto dump java heap in hprof binary format");
        System.out.println("    --histo\tto print histogram of java object heap");
        System.out.println("    --clstats\tto print class loader statistics");
        System.out.println("    --finalizerinfo\tto print information on objects awaiting finalization");
        return commonHelp();
    }

    private static boolean jstackHelp() {
        // --locks -> -l
        // --mixed -> -m
        System.out.println("    --locks\tto print java.util.concurrent locks");
        System.out.println("    --mixed\tto print both java and native frames (mixed mode)");
        return commonHelp();
    }

    private static boolean jsnapHelp() {
        System.out.println("    --all\tto print all performance counters");
        return commonHelp();
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
        if (toolName.equals("hsdb") || toolName.equals("clhsdb")) {
            return commonHelp();
        }
        return launcherHelp();
    }

    private static void buildAttachArgs(ArrayList<String> newArgs,
                                        String pid, String exe, String core) {
        if ((pid == null) && (exe == null)) {
            throw new IllegalArgumentException(
                                     "You have to set --pid or --exe.");
        }

        if (pid != null) { // Attach to live process
            if (exe != null) {
                throw new IllegalArgumentException(
                                             "Unnecessary argument: --exe");
            } else if (core != null) {
                throw new IllegalArgumentException(
                                             "Unnecessary argument: --core");
            } else if (!pid.matches("^\\d+$")) {
                throw new IllegalArgumentException("Invalid pid: " + pid);
            }

            newArgs.add(pid);
        } else {
            if (exe.length() == 0) {
                throw new IllegalArgumentException("You have to set --exe.");
            }

            newArgs.add(exe);

            if ((core == null) || (core.length() == 0)) {
                throw new IllegalArgumentException("You have to set --core.");
            }

            newArgs.add(core);
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

        buildAttachArgs(newArgs, pid, exe, core);
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

        buildAttachArgs(newArgs, pid, exe, core);
        HSDB.main(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runJSTACK(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid=",
                                 "mixed", "locks"};

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
            if (s.equals("mixed")) {
                newArgs.add("-m");
                continue;
            }
            if (s.equals("locks")) {
                newArgs.add("-l");
                continue;
            }
        }

        buildAttachArgs(newArgs, pid, exe, core);
        JStack.main(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runJMAP(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid=",
              "heap", "binaryheap", "histo", "clstats", "finalizerinfo"};

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
            if (s.equals("heap")) {
                newArgs.add("-heap");
                continue;
            }
            if (s.equals("binaryheap")) {
                newArgs.add("-heap:format=b");
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

        buildAttachArgs(newArgs, pid, exe, core);
        JMap.main(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runJINFO(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid=",
                                     "flags", "sysprops"};

        ArrayList<String> newArgs = new ArrayList();
        String exe = null;
        String pid = null;
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
            if (s.equals("flags")) {
                newArgs.add("-flags");
                continue;
            }
            if (s.equals("sysprops")) {
                newArgs.add("-sysprops");
                continue;
            }
        }

        buildAttachArgs(newArgs, pid, exe, core);
        JInfo.main(newArgs.toArray(new String[newArgs.size()]));
    }

    private static void runJSNAP(String[] oldArgs) {
        SAGetopt sg = new SAGetopt(oldArgs);
        String[] longOpts = {"exe=", "core=", "pid=", "all"};

        ArrayList<String> newArgs = new ArrayList();
        String exe = null;
        String pid = null;
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
            if (s.equals("all")) {
                newArgs.add("-a");
                continue;
            }
        }

        buildAttachArgs(newArgs, pid, exe, core);
        JSnap.main(newArgs.toArray(new String[newArgs.size()]));
    }

    public static void main(String[] args) {
        // Provide a help
        if (args.length == 0) {
            launcherHelp();
            return;
        }
        // No arguments imply help for jstack, jmap, jinfo but launch clhsdb and hsdb
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

            throw new IllegalArgumentException("Unknown tool: " + args[0]);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            toolHelp(args[0]);
        }
    }
}
