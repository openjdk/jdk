/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.java.testlibrary;

import static com.oracle.java.testlibrary.Asserts.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

/**
 * Common library for various test helper functions.
 */
public final class Utils {

    /**
     * Returns the sequence used by operating system to separate lines.
     */
    public static final String NEW_LINE = System.getProperty("line.separator");

    /**
     * Returns the value of 'test.vm.opts'system property.
     */
    public static final String VM_OPTIONS = System.getProperty("test.vm.opts", "").trim();

    /**
     * Returns the value of 'test.java.opts'system property.
     */
    public static final String JAVA_OPTIONS = System.getProperty("test.java.opts", "").trim();

    private static Unsafe unsafe = null;

    /**
    * Returns the value of 'test.timeout.factor' system property
    * converted to {@code double}.
    */
    public static final double TIMEOUT_FACTOR;
    static {
        String toFactor = System.getProperty("test.timeout.factor", "1.0");
        TIMEOUT_FACTOR = Double.parseDouble(toFactor);
    }

    private Utils() {
        // Private constructor to prevent class instantiation
    }

    /**
     * Returns the list of VM options.
     *
     * @return List of VM options
     */
    public static List<String> getVmOptions() {
        return Arrays.asList(safeSplitString(VM_OPTIONS));
    }

    /**
     * Returns the list of VM options with -J prefix.
     *
     * @return The list of VM options with -J prefix
     */
    public static List<String> getForwardVmOptions() {
        String[] opts = safeSplitString(VM_OPTIONS);
        for (int i = 0; i < opts.length; i++) {
            opts[i] = "-J" + opts[i];
        }
        return Arrays.asList(opts);
    }

    /**
     * Returns the default JTReg arguments for a jvm running a test.
     * This is the combination of JTReg arguments test.vm.opts and test.java.opts.
     * @return An array of options, or an empty array if no opptions.
     */
    public static String[] getTestJavaOpts() {
        List<String> opts = new ArrayList<String>();
        Collections.addAll(opts, safeSplitString(VM_OPTIONS));
        Collections.addAll(opts, safeSplitString(JAVA_OPTIONS));
        return opts.toArray(new String[0]);
    }

    /**
     * Returns the default JTReg arguments for a jvm running a test without
     * options that matches regular expressions in {@code filters}.
     * This is the combination of JTReg arguments test.vm.opts and test.java.opts.
     * @param filters Regular expressions used to filter out options.
     * @return An array of options, or an empty array if no options.
     */
    public static String[] getFilteredTestJavaOpts(String... filters) {
        String options[] = getTestJavaOpts();

        if (filters.length == 0) {
            return options;
        }

        List<String> filteredOptions = new ArrayList<String>(options.length);
        Pattern patterns[] = new Pattern[filters.length];
        for (int i = 0; i < filters.length; i++) {
            patterns[i] = Pattern.compile(filters[i]);
        }

        for (String option : options) {
            boolean matched = false;
            for (int i = 0; i < patterns.length && !matched; i++) {
                Matcher matcher = patterns[i].matcher(option);
                matched = matcher.find();
            }
            if (!matched) {
                filteredOptions.add(option);
            }
        }

        return filteredOptions.toArray(new String[filteredOptions.size()]);
    }

    /**
     * Combines given arguments with default JTReg arguments for a jvm running a test.
     * This is the combination of JTReg arguments test.vm.opts and test.java.opts
     * @return The combination of JTReg test java options and user args.
     */
    public static String[] addTestJavaOpts(String... userArgs) {
        List<String> opts = new ArrayList<String>();
        Collections.addAll(opts, getTestJavaOpts());
        Collections.addAll(opts, userArgs);
        return opts.toArray(new String[0]);
    }

    /**
     * Splits a string by white space.
     * Works like String.split(), but returns an empty array
     * if the string is null or empty.
     */
    private static String[] safeSplitString(String s) {
        if (s == null || s.trim().isEmpty()) {
            return new String[] {};
        }
        return s.trim().split("\\s+");
    }

    /**
     * @return The full command line for the ProcessBuilder.
     */
    public static String getCommandLine(ProcessBuilder pb) {
        StringBuilder cmd = new StringBuilder();
        for (String s : pb.command()) {
            cmd.append(s).append(" ");
        }
        return cmd.toString();
    }

    /**
     * Returns the free port on the local host.
     * The function will spin until a valid port number is found.
     *
     * @return The port number
     * @throws InterruptedException if any thread has interrupted the current thread
     * @throws IOException if an I/O error occurs when opening the socket
     */
    public static int getFreePort() throws InterruptedException, IOException {
        int port = -1;

        while (port <= 0) {
            Thread.sleep(100);

            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(0);
                port = serverSocket.getLocalPort();
            } finally {
                serverSocket.close();
            }
        }

        return port;
    }

    /**
     * Returns the name of the local host.
     *
     * @return The host name
     * @throws UnknownHostException if IP address of a host could not be determined
     */
    public static String getHostname() throws UnknownHostException {
        InetAddress inetAddress = InetAddress.getLocalHost();
        String hostName = inetAddress.getHostName();

        assertTrue((hostName != null && !hostName.isEmpty()),
                "Cannot get hostname");

        return hostName;
    }

    /**
     * Uses "jcmd -l" to search for a jvm pid. This function will wait
     * forever (until jtreg timeout) for the pid to be found.
     * @param key Regular expression to search for
     * @return The found pid.
     */
    public static int waitForJvmPid(String key) throws Throwable {
        final long iterationSleepMillis = 250;
        System.out.println("waitForJvmPid: Waiting for key '" + key + "'");
        System.out.flush();
        while (true) {
            int pid = tryFindJvmPid(key);
            if (pid >= 0) {
                return pid;
            }
            Thread.sleep(iterationSleepMillis);
        }
    }

    /**
     * Searches for a jvm pid in the output from "jcmd -l".
     *
     * Example output from jcmd is:
     * 12498 sun.tools.jcmd.JCmd -l
     * 12254 /tmp/jdk8/tl/jdk/JTwork/classes/com/sun/tools/attach/Application.jar
     *
     * @param key A regular expression to search for.
     * @return The found pid, or -1 if Enot found.
     * @throws Exception If multiple matching jvms are found.
     */
    public static int tryFindJvmPid(String key) throws Throwable {
        OutputAnalyzer output = null;
        try {
            JDKToolLauncher jcmdLauncher = JDKToolLauncher.create("jcmd");
            jcmdLauncher.addToolArg("-l");
            output = ProcessTools.executeProcess(jcmdLauncher.getCommand());
            output.shouldHaveExitValue(0);

            // Search for a line starting with numbers (pid), follwed by the key.
            Pattern pattern = Pattern.compile("([0-9]+)\\s.*(" + key + ").*\\r?\\n");
            Matcher matcher = pattern.matcher(output.getStdout());

            int pid = -1;
            if (matcher.find()) {
                pid = Integer.parseInt(matcher.group(1));
                System.out.println("findJvmPid.pid: " + pid);
                if (matcher.find()) {
                    throw new Exception("Found multiple JVM pids for key: " + key);
                }
            }
            return pid;
        } catch (Throwable t) {
            System.out.println(String.format("Utils.findJvmPid(%s) failed: %s", key, t));
            throw t;
        }
    }

    /**
     * Returns file content as a list of strings
     *
     * @param file File to operate on
     * @return List of strings
     * @throws IOException
     */
    public static List<String> fileAsList(File file) throws IOException {
        assertTrue(file.exists() && file.isFile(),
                file.getAbsolutePath() + " does not exist or not a file");
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.getAbsolutePath()))) {
            while (reader.ready()) {
                output.add(reader.readLine().replace(NEW_LINE, ""));
            }
        }
        return output;
    }

    /**
     * @return Unsafe instance.
     */
    public static synchronized Unsafe getUnsafe() {
        if (unsafe == null) {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                unsafe = (Unsafe) f.get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Unable to get Unsafe instance.", e);
            }
        }
        return unsafe;
    }
    private static final char[] hexArray = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * Returns hex view of byte array
     *
     * @param bytes byte array to process
     * @return Space separated hexadecimal string representation of bytes
     */

    public static String toHexString(byte[] bytes) {
        char[] hexView = new char[bytes.length * 3];
        int i = 0;
        for (byte b : bytes) {
            hexView[i++] = hexArray[(b >> 4) & 0x0F];
            hexView[i++] = hexArray[b & 0x0F];
            hexView[i++] = ' ';
        }
        return new String(hexView);
    }
}
