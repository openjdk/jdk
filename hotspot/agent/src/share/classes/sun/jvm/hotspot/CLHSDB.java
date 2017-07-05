/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.*;
import sun.jvm.hotspot.debugger.*;

import java.io.*;
import java.util.*;

public class CLHSDB {
    public static void main(String[] args) {
        new CLHSDB(args).run();
    }

    private void run() {
        // At this point, if pidText != null we are supposed to attach to it.
        // Else, if execPath != null, it is the path of a jdk/bin/java
        // and coreFilename is the pathname of a core file we are
        // supposed to attach to.

        agent = new HotSpotAgent();

        Runtime.getRuntime().addShutdownHook(new java.lang.Thread() {
                public void run() {
                    detachDebugger();
                }
            });

        if (pidText != null) {
            attachDebugger(pidText);
        } else if (execPath != null) {
            attachDebugger(execPath, coreFilename);
        }


        CommandProcessor.DebuggerInterface di = new CommandProcessor.DebuggerInterface() {
                public HotSpotAgent getAgent() {
                    return agent;
                }
                public boolean isAttached() {
                    return attached;
                }
                public void attach(String pid) {
                    attachDebugger(pid);
                }
                public void attach(String java, String core) {
                    attachDebugger(java, core);
                }
                public void detach() {
                    detachDebugger();
                }
                public void reattach() {
                    if (attached) {
                        detachDebugger();
                    }
                    if (pidText != null) {
                        attach(pidText);
                    } else {
                        attach(execPath, coreFilename);
                    }
                }
            };


        BufferedReader in =
            new BufferedReader(new InputStreamReader(System.in));
        CommandProcessor cp = new CommandProcessor(di, in, System.out, System.err);
        cp.run(true);

    }

    //--------------------------------------------------------------------------------
    // Internals only below this point
    //
    private HotSpotAgent agent;
    private boolean      attached;
    // These had to be made data members because they are referenced in inner classes.
    private String pidText;
    private int pid;
    private String execPath;
    private String coreFilename;

    private void doUsage() {
        System.out.println("Usage:  java CLHSDB [[pid] | [path-to-java-executable [path-to-corefile]] | help ]");
        System.out.println("           pid:                     attach to the process whose id is 'pid'");
        System.out.println("           path-to-java-executable: Debug a core file produced by this program");
        System.out.println("           path-to-corefile:        Debug this corefile.  The default is 'core'");
        System.out.println("        If no arguments are specified, you can select what to do from the GUI.\n");
        HotSpotAgent.showUsage();
    }

    private CLHSDB(String[] args) {
        switch (args.length) {
        case (0):
            break;

        case (1):
            if (args[0].equals("help") || args[0].equals("-help")) {
                doUsage();
                System.exit(0);
            }
            // If all numbers, it is a PID to attach to
            // Else, it is a pathname to a .../bin/java for a core file.
            try {
                int unused = Integer.parseInt(args[0]);
                // If we get here, we have a PID and not a core file name
                pidText = args[0];
            } catch (NumberFormatException e) {
                execPath = args[0];
                coreFilename = "core";
            }
            break;

        case (2):
            execPath = args[0];
            coreFilename = args[1];
            break;

        default:
            System.out.println("HSDB Error: Too many options specified");
            doUsage();
            System.exit(1);
        }
    }

    /** NOTE we are in a different thread here than either the main
        thread or the Swing/AWT event handler thread, so we must be very
        careful when creating or removing widgets */
    private void attachDebugger(String pidText) {
        try {
            this.pidText = pidText;
            pid = Integer.parseInt(pidText);
        }
        catch (NumberFormatException e) {
            System.err.print("Unable to parse process ID \"" + pidText + "\".\nPlease enter a number.");
        }

        try {
            System.err.println("Attaching to process " + pid + ", please wait...");

            // FIXME: display exec'd debugger's output messages during this
            // lengthy call
            agent.attach(pid);
            attached = true;
        }
        catch (DebuggerException e) {
            final String errMsg = formatMessage(e.getMessage(), 80);
            System.err.println("Unable to connect to process ID " + pid + ":\n\n" + errMsg);
            agent.detach();
            return;
        }
    }

    /** NOTE we are in a different thread here than either the main
        thread or the Swing/AWT event handler thread, so we must be very
        careful when creating or removing widgets */
    private void attachDebugger(final String executablePath, final String corePath) {
        // Try to open this core file
        try {
            System.err.println("Opening core file, please wait...");

            // FIXME: display exec'd debugger's output messages during this
            // lengthy call
            agent.attach(executablePath, corePath);
            attached = true;
        }
        catch (DebuggerException e) {
            final String errMsg = formatMessage(e.getMessage(), 80);
            System.err.println("Unable to open core file\n" + corePath + ":\n\n" + errMsg);
            agent.detach();
            return;
        }
    }

    /** NOTE we are in a different thread here than either the main
        thread or the Swing/AWT event handler thread, so we must be very
        careful when creating or removing widgets */
    private void connect(final String remoteMachineName) {
        // Try to open this core file
        try {
            System.err.println("Connecting to debug server, please wait...");
            agent.attach(remoteMachineName);
            attached = true;
        }
        catch (DebuggerException e) {
            final String errMsg = formatMessage(e.getMessage(), 80);
            System.err.println("Unable to connect to machine \"" + remoteMachineName + "\":\n\n" + errMsg);
            agent.detach();
            return;
        }
    }

    private void detachDebugger() {
        if (!attached) {
            return;
        }
        agent.detach();
        attached = false;
    }

    private void detach() {
        detachDebugger();
    }

    /** Punctuates the given string with \n's where necessary to not
        exceed the given number of characters per line. Strips
        extraneous whitespace. */
    private String formatMessage(String message, int charsPerLine) {
        StringBuffer buf = new StringBuffer(message.length());
        StringTokenizer tokenizer = new StringTokenizer(message);
        int curLineLength = 0;
        while (tokenizer.hasMoreTokens()) {
            String tok = tokenizer.nextToken();
            if (curLineLength + tok.length() > charsPerLine) {
                buf.append('\n');
                curLineLength = 0;
            } else {
                if (curLineLength != 0) {
                    buf.append(' ');
                    ++curLineLength;
                }
            }
            buf.append(tok);
            curLineLength += tok.length();
        }
        return buf.toString();
    }
}
