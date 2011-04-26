/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.example.debug.gui;

import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

import com.sun.jdi.*;
import com.sun.tools.example.debug.bdi.*;

public class GUI extends JPanel {

    private static final long serialVersionUID = 3292463234530679091L;
    private CommandTool cmdTool;
    private ApplicationTool appTool;
    //###HACK##
    //### There is currently dirty code in Environment that
    //### accesses this directly.
    //private SourceTool srcTool;
    public static SourceTool srcTool;

    private SourceTreeTool sourceTreeTool;
    private ClassTreeTool classTreeTool;
    private ThreadTreeTool threadTreeTool;
    private StackTraceTool stackTool;
    private MonitorTool monitorTool;

    public static final String progname = "javadt";
    public static final String version = "1.0Beta";  //### FIX ME.
    public static final String windowBanner = "Java(tm) platform Debug Tool";

    private Font fixedFont = new Font("monospaced", Font.PLAIN, 10);

    private GUI(Environment env) {
        setLayout(new BorderLayout());

        setBorder(new EmptyBorder(5, 5, 5, 5));

        add(new JDBToolBar(env), BorderLayout.NORTH);

        srcTool = new SourceTool(env);
        srcTool.setPreferredSize(new java.awt.Dimension(500, 300));
        srcTool.setTextFont(fixedFont);

        stackTool = new StackTraceTool(env);
        stackTool.setPreferredSize(new java.awt.Dimension(500, 100));

        monitorTool = new MonitorTool(env);
        monitorTool.setPreferredSize(new java.awt.Dimension(500, 50));

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, srcTool,
            new JSplitPane(JSplitPane.VERTICAL_SPLIT, stackTool, monitorTool));

        sourceTreeTool = new SourceTreeTool(env);
        sourceTreeTool.setPreferredSize(new java.awt.Dimension(200, 450));

        classTreeTool = new ClassTreeTool(env);
        classTreeTool.setPreferredSize(new java.awt.Dimension(200, 450));

        threadTreeTool = new ThreadTreeTool(env);
        threadTreeTool.setPreferredSize(new java.awt.Dimension(200, 450));

        JTabbedPane treePane = new JTabbedPane(SwingConstants.BOTTOM);
        treePane.addTab("Source", null, sourceTreeTool);
        treePane.addTab("Classes", null, classTreeTool);
        treePane.addTab("Threads", null, threadTreeTool);

        JSplitPane centerTop = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePane, right);

        cmdTool = new CommandTool(env);
        cmdTool.setPreferredSize(new java.awt.Dimension(700, 150));

        appTool = new ApplicationTool(env);
        appTool.setPreferredSize(new java.awt.Dimension(700, 200));

        JSplitPane centerBottom = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cmdTool, appTool);
        //        centerBottom.setPreferredSize(new java.awt.Dimension(700, 350));

        JSplitPane center = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerTop, centerBottom);

        add(center, BorderLayout.CENTER);


    }

    private static void usage() {
        String separator = File.pathSeparator;
        System.out.println("Usage: " + progname + " <options> <class> <arguments>");
        System.out.println();
        System.out.println("where options include:");
        System.out.println("    -help             print out this message and exit");
        System.out.println("    -sourcepath <directories separated by \"" +
                           separator + "\">");
        System.out.println("                      list directories in which to look for source files");
        System.out.println("    -remote <hostname>:<port-number>");
        System.out.println("                      host machine and port number of interpreter to attach to");
        System.out.println("    -dbgtrace [flags] print info for debugging " + progname);
        System.out.println();
        System.out.println("options forwarded to debuggee process:");
        System.out.println("    -v -verbose[:class|gc|jni]");
        System.out.println("                      turn on verbose mode");
        System.out.println("    -D<name>=<value>  set a system property");
        System.out.println("    -classpath <directories separated by \"" +
                           separator + "\">");
        System.out.println("                      list directories in which to look for classes");
        System.out.println("    -X<option>        non-standard debuggee VM option");
        System.out.println();
        System.out.println("<class> is the name of the class to begin debugging");
        System.out.println("<arguments> are the arguments passed to the main() method of <class>");
        System.out.println();
        System.out.println("For command help type 'help' at " + progname + " prompt");
    }

    public static void main(String argv[]) {
        String clsName = "";
        String progArgs = "";
        String javaArgs = "";
        final Environment env = new Environment();

        JPanel mainPanel = new GUI(env);

        ContextManager context = env.getContextManager();
        ExecutionManager runtime = env.getExecutionManager();

        for (int i = 0; i < argv.length; i++) {
            String token = argv[i];
            if (token.equals("-dbgtrace")) {
            if ((i == argv.length - 1) ||
                ! Character.isDigit(argv[i+1].charAt(0))) {
                runtime.setTraceMode(VirtualMachine.TRACE_ALL);
            } else {
                String flagStr = argv[++i];
                runtime.setTraceMode(Integer.decode(flagStr).intValue());
            }
        } else if (token.equals("-X")) {
                System.out.println(
                       "Use 'java -X' to see the available non-standard options");
                System.out.println();
                usage();
                System.exit(1);
            } else if (
                   // Standard VM options passed on
                   token.equals("-v") || token.startsWith("-v:") ||  // -v[:...]
                   token.startsWith("-verbose") ||                  // -verbose[:...]
                   token.startsWith("-D") ||
                   // NonStandard options passed on
                   token.startsWith("-X") ||
                   // Old-style options
                   // (These should remain in place as long as the standard VM accepts them)
                   token.equals("-noasyncgc") || token.equals("-prof") ||
                   token.equals("-verify") || token.equals("-noverify") ||
                   token.equals("-verifyremote") ||
                   token.equals("-verbosegc") ||
                   token.startsWith("-ms") || token.startsWith("-mx") ||
                   token.startsWith("-ss") || token.startsWith("-oss") ) {
                javaArgs += token + " ";
            } else if (token.equals("-sourcepath")) {
                if (i == (argv.length - 1)) {
                    System.out.println("No sourcepath specified.");
                    usage();
                    System.exit(1);
                }
                env.getSourceManager().setSourcePath(new SearchPath(argv[++i]));
            } else if (token.equals("-classpath")) {
                if (i == (argv.length - 1)) {
                    System.out.println("No classpath specified.");
                    usage();
                    System.exit(1);
                }
                env.getClassManager().setClassPath(new SearchPath(argv[++i]));
            } else if (token.equals("-remote")) {
                if (i == (argv.length - 1)) {
                    System.out.println("No remote specified.");
                    usage();
                    System.exit(1);
                }
                env.getContextManager().setRemotePort(argv[++i]);
            } else if (token.equals("-help")) {
                usage();
                System.exit(0);
            } else if (token.equals("-version")) {
                System.out.println(progname + " version " + version);
                System.exit(0);
            } else if (token.startsWith("-")) {
                System.out.println("invalid option: " + token);
                usage();
                System.exit(1);
            } else {
                // Everything from here is part of the command line
                clsName = token;
                for (i++; i < argv.length; i++) {
                    progArgs += argv[i] + " ";
                }
                break;
            }
        }

        context.setMainClassName(clsName);
        context.setProgramArguments(progArgs);
        context.setVmArguments(javaArgs);

        // Force Cross Platform L&F
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            // If you want the System L&F instead, comment out the above line and
            // uncomment the following:
            // UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exc) {
            System.err.println("Error loading L&F: " + exc);
        }

        JFrame frame = new JFrame();
        frame.setBackground(Color.lightGray);
        frame.setTitle(windowBanner);
        frame.setJMenuBar(new JDBMenuBar(env));
        frame.setContentPane(mainPanel);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                env.terminate();
            }
        });

        frame.pack();
        frame.setVisible(true);

    }

}
