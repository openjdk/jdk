/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class Arrrghs {

    /**
     * A group of tests to ensure that arguments are passed correctly to
     * a child java process upon a re-exec, this typically happens when
     * a version other than the one being executed is requested by the user.
     *
     * History: these set of tests  were part of Arrrghs.sh. The MKS shell
     * implementations are notoriously buggy. Implementing these tests purely
     * in Java is not only portable but also robust.
     *
     */

    /* Do not instantiate */
    private Arrrghs() {}

    static String javaCmd;

    // The version string to force a re-exec
    final static String VersionStr = "-version:1.1+";

    // The Cookie or the pattern we match in the debug output.
    final static String Cookie = "ReExec Args: ";

    private static boolean _debug = Boolean.getBoolean("Arrrghs.Debug");
    private static boolean isWindows = System.getProperty("os.name", "unknown").startsWith("Windows");
    private static int exitValue = 0;

    private static void doUsage(String message) {
        if (message != null) System.out.println("Error: " + message);
        System.out.println("Usage: Arrrghs path_to_java");
        System.exit(1);
    }

    /*
     * SIGH, On Windows all strings are quoted, we need to unwrap it
     */
    private static String removeExtraQuotes(String in) {
        if (isWindows) {
            // Trim the string and remove the enclosed quotes if any.
            in = in.trim();
            if (in.startsWith("\"") && in.endsWith("\"")) {
                return in.substring(1, in.length()-1);
            }
        }
        return in;
    }


    /*
     * This method detects the cookie in the output stream of the process.
     */
    private static boolean detectCookie(InputStream istream, String expectedArguments) throws IOException {
        BufferedReader rd = new BufferedReader(new InputStreamReader(istream));
        boolean retval = false;

        String in = rd.readLine();
        while (in != null) {
            if (_debug) System.out.println(in);
            if (in.startsWith(Cookie)) {
                String detectedArgument = removeExtraQuotes(in.substring(Cookie.length()));
                if (expectedArguments.equals(detectedArgument)) {
                    retval = true;
                } else {
                    System.out.println("Error: Expected Arguments\t:'" + expectedArguments + "'");
                    System.out.println(" Detected Arguments\t:'" + detectedArgument + "'");
                }
                // Return the value asap if not in debug mode.
                if (!_debug) {
                    rd.close();
                    istream.close();
                    return retval;
                }
            }
            in = rd.readLine();
        }
        return retval;
    }

    private static boolean doExec0(ProcessBuilder pb, String expectedArguments) {
        boolean retval = false;
        try {
            pb.redirectErrorStream(true);
            Process p = pb.start();
            retval = detectCookie(p.getInputStream(), expectedArguments);
            p.waitFor();
            p.destroy();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex.getMessage());
        }
        return retval;
    }

    /**
     * This method return true  if the expected and detected arguments are the same.
     * Quoting could cause dissimilar testArguments and expected arguments.
     */
    static boolean doExec(String testArguments, String expectedPattern) {
        ProcessBuilder pb = new ProcessBuilder(javaCmd, VersionStr, testArguments);

        Map<String, String> env = pb.environment();
        env.put("_JAVA_LAUNCHER_DEBUG", "true");
        return doExec0(pb, testArguments);
    }

    /**
     * A convenience method for identical test pattern and expected arguments
     */
    static boolean doExec(String testPattern) {
        return doExec(testPattern, testPattern);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1 && args[0] == null) {
            doUsage("Invalid number of arguments");
        }

        javaCmd = args[0];

        if (!new File(javaCmd).canExecute()) {
            if (isWindows && new File(javaCmd + ".exe").canExecute()) {
                javaCmd = javaCmd + ".exe";
            } else {
                doUsage("The java executable must exist");
            }
        }

        if (_debug) System.out.println("Starting Arrrghs tests");
        // Basic test
        if (!doExec("-a -b -c -d")) exitValue++;

        // Basic test with many spaces
        if (!doExec("-a    -b      -c       -d")) exitValue++;

        // Quoted whitespace does matter ?
        if (!doExec("-a \"\"-b      -c\"\" -d")) exitValue++;

        // Escaped quotes outside of quotes as literals
        if (!doExec("-a \\\"-b -c\\\" -d")) exitValue++;

        // Check for escaped quotes inside of quotes as literal
        if (!doExec("-a \"-b \\\"stuff\\\"\" -c -d")) exitValue++;

        // A quote preceeded by an odd number of slashes is a literal quote
        if (!doExec("-a -b\\\\\\\" -c -d")) exitValue++;

        // A quote preceeded by an even number of slashes is a literal quote
        // see 6214916.
        if (!doExec("-a -b\\\\\\\\\" -c -d")) exitValue++;

        // Make sure that whitespace doesn't interfere with the removal of the
        // appropriate tokens. (space-tab-space preceeds -jre-restict-search).
        if (!doExec("-a -b  \t -jre-restrict-search -c -d","-a -b -c -d")) exitValue++;

        // Make sure that the mJRE tokens being stripped, aren't stripped if
        // they happen to appear as arguments to the main class.
        if (!doExec("foo -version:1.1+")) exitValue++;

        System.out.println("Completed Arrrghs arguments quoting/matching tests with " + exitValue + " errors");
        System.exit(exitValue);
    }

}
