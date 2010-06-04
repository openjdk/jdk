/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 *
 */

package bench.serial;

import bench.ConfigFormatException;
import bench.Harness;
import bench.HtmlReporter;
import bench.Reporter;
import bench.TextReporter;
import bench.XmlReporter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Object serialization benchmark mainline.
 */
public class Main {

    static final String CONFFILE = "/bench/serial/config";
    static final String VERSION = "1.3";

    static final int TEXT = 0;
    static final int HTML = 1;
    static final int XML = 2;

    static boolean verbose;
    static boolean list;
    static boolean exitOnTimer;
    static int testDurationSeconds;
    static volatile boolean exitRequested;
    static Timer timer;
    static int format = TEXT;
    static InputStream confstr;
    static OutputStream repstr;
    static Harness harness;
    static Reporter reporter;

    /**
     * Print help message.
     */
    static void usage() {
        PrintStream p = System.err;
        p.println("\nUsage: java -jar serialbench.jar [-options]");
        p.println("\nwhere options are:");
        p.println("  -h              print this message");
        p.println("  -v              verbose mode");
        p.println("  -l              list configuration file");
        p.println("  -t <num hours>  repeat benchmarks for specified number of hours");
        p.println("  -o <file>       specify output file");
        p.println("  -c <file>       specify (non-default) configuration file");
        p.println("  -html           format output as html (default is text)");
        p.println("  -xml            format output as xml");
    }

    /**
     * Print error message and exit.
     */
    static void die(String mesg) {
        System.err.println(mesg);
        System.exit(1);
    }

    /**
     * Mainline parses command line, then hands off to benchmark harness.
     */
    public static void main(String[] args) {
        parseArgs(args);
        setupStreams();
        if (list) {
            listConfig();
        } else {
            setupHarness();
            setupReporter();
            if (exitOnTimer) {
                setupTimer(testDurationSeconds);
                while (true) {
                    runBenchmarks();
                    if (exitRequested) {
                        System.exit(0);
                    }
                }
            } else {
                runBenchmarks();
                System.exit(0);
            }
        }
    }

    /**
     * Parse command-line arguments.
     */
    static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-h")) {
                usage();
                System.exit(0);
            } else if (args[i].equals("-v")) {
                verbose = true;
            } else if (args[i].equals("-l")) {
                list = true;
            } else if (args[i].equals("-t")) {
                if (++i >= args.length)
                    die("Error: no timeout value specified");
                try {
                    exitOnTimer = true;
                    testDurationSeconds = Integer.parseInt(args[i]) * 3600;
                } catch (Exception e) {
                    die("Error: unable to determine timeout value");
                }
            } else if (args[i].equals("-o")) {
                if (++i >= args.length)
                    die("Error: no output file specified");
                try {
                    repstr = new FileOutputStream(args[i]);
                } catch (IOException e) {
                    die("Error: unable to open \"" + args[i] + "\"");
                }
            } else if (args[i].equals("-c")) {
                if (++i >= args.length)
                    die("Error: no config file specified");
                try {
                    confstr = new FileInputStream(args[i]);
                } catch (IOException e) {
                    die("Error: unable to open \"" + args[i] + "\"");
                }
            } else if (args[i].equals("-html")) {
                if (format != TEXT)
                    die("Error: conflicting formats");
                format = HTML;
            } else if (args[i].equals("-xml")) {
                if (format != TEXT)
                    die("Error: conflicting formats");
                format = XML;
            } else {
                System.err.println("Illegal option: \"" + args[i] + "\"");
                usage();
                System.exit(1);
            }
        }
    }

    /**
     * Set up configuration file and report streams, if not set already.
     */
    static void setupStreams() {
        if (repstr == null)
            repstr = System.out;
        if (confstr == null)
            confstr = (new Main()).getClass().getResourceAsStream(CONFFILE);
        if (confstr == null)
            die("Error: unable to find default config file");
    }

    /**
     * Print contents of configuration file to selected output stream.
     */
    static void listConfig() {
        try {
            byte[] buf = new byte[256];
            int len;
            while ((len = confstr.read(buf)) != -1)
                repstr.write(buf, 0, len);
        } catch (IOException e) {
            die("Error: failed to list config file");
        }
    }

    /**
     * Set up the timer to end the test.
     *
     * @param delay the amount of delay, in seconds, before requesting
     * the process exit
     */
    static void setupTimer(int delay) {
        timer = new Timer(true);
        timer.schedule(
            new TimerTask() {
                public void run() {
                    exitRequested = true;
                }
            },
            delay * 1000);
    }

    /**
     * Set up benchmark harness.
     */
    static void setupHarness() {
        try {
            harness = new Harness(confstr);
        } catch (ConfigFormatException e) {
            String errmsg = e.getMessage();
            if (errmsg != null) {
                die("Error parsing config file: " + errmsg);
            } else {
                die("Error: illegal config file syntax");
            }
        } catch (IOException e) {
            die("Error: failed to read config file");
        }
    }

    /**
     * Setup benchmark reporter.
     */
    static void setupReporter() {
        String title = "Object Serialization Benchmark, v" + VERSION;
        switch (format) {
            case TEXT:
                reporter = new TextReporter(repstr, title);
                break;

            case HTML:
                reporter = new HtmlReporter(repstr, title);
                break;

            case XML:
                reporter = new XmlReporter(repstr, title);
                break;

            default:
                die("Error: unrecognized format type");
        }
    }

    /**
     * Run benchmarks.
     */
    static void runBenchmarks() {
        harness.runBenchmarks(reporter, verbose);
    }
}
