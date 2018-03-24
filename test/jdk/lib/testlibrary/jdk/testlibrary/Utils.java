/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

import static jdk.testlibrary.Asserts.assertTrue;

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
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Common library for various test helper functions.
 *
 * @deprecated This class is deprecated. Use the one from
 *             {@code <root>/test/lib/jdk/test/lib}
 */
@Deprecated
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

    /**
    * Returns the value of 'test.timeout.factor' system property
    * converted to {@code double}.
    */
    public static final double TIMEOUT_FACTOR;
    static {
        String toFactor = System.getProperty("test.timeout.factor", "1.0");
        TIMEOUT_FACTOR = Double.parseDouble(toFactor);
    }

    /**
    * Returns the value of JTREG default test timeout in milliseconds
    * converted to {@code long}.
    */
    public static final long DEFAULT_TEST_TIMEOUT = TimeUnit.SECONDS.toMillis(120);

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
     * Removes any options specifying which GC to use, for example "-XX:+UseG1GC".
     * Removes any options matching: -XX:(+/-)Use*GC
     * Used when a test need to set its own GC version. Then any
     * GC specified by the framework must first be removed.
     * @return A copy of given opts with all GC options removed.
     */
    private static final Pattern useGcPattern = Pattern.compile(
            "(?:\\-XX\\:[\\+\\-]Use.+GC)"
            + "|(?:\\-Xconcgc)");
    public static List<String> removeGcOpts(List<String> opts) {
        List<String> optsWithoutGC = new ArrayList<String>();
        for (String opt : opts) {
            if (useGcPattern.matcher(opt).matches()) {
                System.out.println("removeGcOpts: removed " + opt);
            } else {
                optsWithoutGC.add(opt);
            }
        }
        return optsWithoutGC;
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
     *
     * @return The port number
     * @throws IOException if an I/O error occurs when opening the socket
     */
    public static int getFreePort() throws IOException {
        try (ServerSocket serverSocket =
                new ServerSocket(0, 5, InetAddress.getLoopbackAddress());) {
            return serverSocket.getLocalPort();
        }
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
     * Adjusts the provided timeout value for the TIMEOUT_FACTOR
     * @param tOut the timeout value to be adjusted
     * @return The timeout value adjusted for the value of "test.timeout.factor"
     *         system property
     */
    public static long adjustTimeout(long tOut) {
        return Math.round(tOut * Utils.TIMEOUT_FACTOR);
    }

    /**
     * Wait for condition to be true
     *
     * @param condition, a condition to wait for
     */
    public static final void waitForCondition(BooleanSupplier condition) {
        waitForCondition(condition, -1L, 100L);
    }

    /**
     * Wait until timeout for condition to be true
     *
     * @param condition, a condition to wait for
     * @param timeout a time in milliseconds to wait for condition to be true
     * specifying -1 will wait forever
     * @return condition value, to determine if wait was successfull
     */
    public static final boolean waitForCondition(BooleanSupplier condition,
            long timeout) {
        return waitForCondition(condition, timeout, 100L);
    }

    /**
     * Wait until timeout for condition to be true for specified time
     *
     * @param condition, a condition to wait for
     * @param timeout a time in milliseconds to wait for condition to be true,
     * specifying -1 will wait forever
     * @param sleepTime a time to sleep value in milliseconds
     * @return condition value, to determine if wait was successfull
     */
    public static final boolean waitForCondition(BooleanSupplier condition,
            long timeout, long sleepTime) {
        long startTime = System.currentTimeMillis();
        while (!(condition.getAsBoolean() || (timeout != -1L
                && ((System.currentTimeMillis() - startTime) > timeout)))) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Error(e);
            }
        }
        return condition.getAsBoolean();
    }

    /**
     * Interface same as java.lang.Runnable but with
     * method {@code run()} able to throw any Throwable.
     */
    public static interface ThrowingRunnable {
        void run() throws Throwable;
    }

    /**
     * Filters out an exception that may be thrown by the given
     * test according to the given filter.
     *
     * @param test - method that is invoked and checked for exception.
     * @param filter - function that checks if the thrown exception matches
     *                 criteria given in the filter's implementation.
     * @return - exception that matches the filter if it has been thrown or
     *           {@code null} otherwise.
     * @throws Throwable - if test has thrown an exception that does not
     *                     match the filter.
     */
    public static Throwable filterException(ThrowingRunnable test,
            Function<Throwable, Boolean> filter) throws Throwable {
        try {
            test.run();
        } catch (Throwable t) {
            if (filter.apply(t)) {
                return t;
            } else {
                throw t;
            }
        }
        return null;
    }
}
