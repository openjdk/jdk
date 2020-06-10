/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jdk.test.lib.Asserts.assertTrue;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * Common library for various test helper functions.
 */
public final class Utils {

    /**
     * Returns the value of 'test.class.path' system property.
     */
    public static final String TEST_CLASS_PATH = System.getProperty("test.class.path", ".");

    /**
     * Returns the sequence used by operating system to separate lines.
     */
    public static final String NEW_LINE = System.getProperty("line.separator");

    /**
     * Returns the value of 'test.vm.opts' system property.
     */
    public static final String VM_OPTIONS = System.getProperty("test.vm.opts", "").trim();

    /**
     * Returns the value of 'test.java.opts' system property.
     */
    public static final String JAVA_OPTIONS = System.getProperty("test.java.opts", "").trim();

    /**
     * Returns the value of 'test.src' system property.
     */
    public static final String TEST_SRC = System.getProperty("test.src", "").trim();

    /**
     * Returns the value of 'test.root' system property.
     */
    public static final String TEST_ROOT = System.getProperty("test.root", "").trim();

    /*
     * Returns the value of 'test.jdk' system property
     */
    public static final String TEST_JDK = System.getProperty("test.jdk");

    /*
     * Returns the value of 'compile.jdk' system property
     */
    public static final String COMPILE_JDK = System.getProperty("compile.jdk", TEST_JDK);

    /**
     * Returns the value of 'test.classes' system property
     */
    public static final String TEST_CLASSES = System.getProperty("test.classes", ".");

    /**
     * Defines property name for seed value.
     */
    public static final String SEED_PROPERTY_NAME = "jdk.test.lib.random.seed";

    /* (non-javadoc)
     * Random generator with (or without) predefined seed. Depends on
     * "jdk.test.lib.random.seed" property value.
     */
    private static volatile Random RANDOM_GENERATOR;

    /**
     * Maximum number of attempts to get free socket
     */
    private static final int MAX_SOCKET_TRIES = 10;

    /**
     * Contains the seed value used for {@link java.util.Random} creation.
     */
    public static final long SEED = Long.getLong(SEED_PROPERTY_NAME, new Random().nextLong());
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
     * @return An array of options, or an empty array if no options.
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
    public static String[] prependTestJavaOpts(String... userArgs) {
        List<String> opts = new ArrayList<String>();
        Collections.addAll(opts, getTestJavaOpts());
        Collections.addAll(opts, userArgs);
        return opts.toArray(new String[0]);
    }

    /**
     * Combines given arguments with default JTReg arguments for a jvm running a test.
     * This is the combination of JTReg arguments test.vm.opts and test.java.opts
     * @return The combination of JTReg test java options and user args.
     */
    public static String[] appendTestJavaOpts(String... userArgs) {
        List<String> opts = new ArrayList<String>();
        Collections.addAll(opts, userArgs);
        Collections.addAll(opts, getTestJavaOpts());
        return opts.toArray(new String[0]);
    }

    /**
     * Combines given arguments with default JTReg arguments for a jvm running a test.
     * This is the combination of JTReg arguments test.vm.opts and test.java.opts
     * @return The combination of JTReg test java options and user args.
     */
    public static String[] addTestJavaOpts(String... userArgs) {
        return prependTestJavaOpts(userArgs);
    }

    /**
     * Removes any options specifying which GC to use, for example "-XX:+UseG1GC".
     * Removes any options matching: -XX:(+/-)Use*GC
     * Used when a test need to set its own GC version. Then any
     * GC specified by the framework must first be removed.
     * @return A copy of given opts with all GC options removed.
     */
    private static final Pattern useGcPattern = Pattern.compile(
            "(?:\\-XX\\:[\\+\\-]Use.+GC)");
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
     * Returns the socket address of an endpoint that refuses connections. The
     * endpoint is an InetSocketAddress where the address is the loopback address
     * and the port is a system port (1-1023 range).
     * This method is a better choice than getFreePort for tests that need
     * an endpoint that refuses connections.
     */
    public static InetSocketAddress refusingEndpoint() {
        InetAddress lb = InetAddress.getLoopbackAddress();
        int port = 1;
        while (port < 1024) {
            InetSocketAddress sa = new InetSocketAddress(lb, port);
            try {
                SocketChannel.open(sa).close();
            } catch (IOException ioe) {
                return sa;
            }
            port++;
        }
        throw new RuntimeException("Unable to find system port that is refusing connections");
    }

    /**
     * Returns local addresses with symbolic and numeric scopes
     */
    public static List<InetAddress> getAddressesWithSymbolicAndNumericScopes() {
        List<InetAddress> result = new LinkedList<>();
        try {
            NetworkConfiguration conf = NetworkConfiguration.probe();
            conf.ip4Addresses().forEach(result::add);
            // Java reports link local addresses with symbolic scope,
            // but on Windows java.net.NetworkInterface generates its own scope names
            // which are incompatible with native Windows routines.
            // So on Windows test only addresses with numeric scope.
            // On other platforms test both symbolic and numeric scopes.
            conf.ip6Addresses().forEach(addr6 -> {
                try {
                    result.add(Inet6Address.getByAddress(null, addr6.getAddress(), addr6.getScopeId()));
                } catch (UnknownHostException e) {
                    // cannot happen!
                    throw new RuntimeException("Unexpected", e);
                }
                if (!Platform.isWindows()) {
                    result.add(addr6);
                }
            });
        } catch (IOException e) {
            // cannot happen!
            throw new RuntimeException("Unexpected", e);
        }
        return result;
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
     * Returns the free unreserved port on the local host.
     *
     * @param reservedPorts reserved ports
     * @return The port number or -1 if failed to find a free port
     */
    public static int findUnreservedFreePort(int... reservedPorts) {
        int numTries = 0;
        while (numTries++ < MAX_SOCKET_TRIES) {
            int port = -1;
            try {
                port = getFreePort();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (port > 0 && !isReserved(port, reservedPorts)) {
                return port;
            }
        }
        return -1;
    }

    private static boolean isReserved(int port, int[] reservedPorts) {
        for (int p : reservedPorts) {
            if (p == port) {
                return true;
            }
        }
        return false;
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
     * @return The found pid, or -1 if not found.
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
     * Adjusts the provided timeout value for the TIMEOUT_FACTOR
     * @param tOut the timeout value to be adjusted
     * @return The timeout value adjusted for the value of "test.timeout.factor"
     *         system property
     */
    public static long adjustTimeout(long tOut) {
        return Math.round(tOut * Utils.TIMEOUT_FACTOR);
    }

    /**
     * Return the contents of the named file as a single String,
     * or null if not found.
     * @param filename name of the file to read
     * @return String contents of file, or null if file not found.
     * @throws  IOException
     *          if an I/O error occurs reading from the file or a malformed or
     *          unmappable byte sequence is read
     */
    public static String fileAsString(String filename) throws IOException {
        Path filePath = Paths.get(filename);
        if (!Files.exists(filePath)) return null;
        return new String(Files.readAllBytes(filePath));
    }

    private static final char[] hexArray = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * Returns hex view of byte array
     *
     * @param bytes byte array to process
     * @return space separated hexadecimal string representation of bytes
     */
     public static String toHexString(byte[] bytes) {
         char[] hexView = new char[bytes.length * 3 - 1];
         for (int i = 0; i < bytes.length - 1; i++) {
             hexView[i * 3] = hexArray[(bytes[i] >> 4) & 0x0F];
             hexView[i * 3 + 1] = hexArray[bytes[i] & 0x0F];
             hexView[i * 3 + 2] = ' ';
         }
         hexView[hexView.length - 2] = hexArray[(bytes[bytes.length - 1] >> 4) & 0x0F];
         hexView[hexView.length - 1] = hexArray[bytes[bytes.length - 1] & 0x0F];
         return new String(hexView);
     }

     /**
      * Returns byte array of hex view
      *
      * @param hex hexadecimal string representation
      * @return byte array
      */
     public static byte[] toByteArray(String hex) {
         int length = hex.length();
         byte[] bytes = new byte[length / 2];
         for (int i = 0; i < length; i += 2) {
             bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                     + Character.digit(hex.charAt(i + 1), 16));
         }
         return bytes;
     }

    /**
     * Returns {@link java.util.Random} generator initialized with particular seed.
     * The seed could be provided via system property {@link Utils#SEED_PROPERTY_NAME}
     * In case no seed is provided, the method uses a random number.
     * The used seed printed to stdout.
     * @return {@link java.util.Random} generator with particular seed.
     */
    public static Random getRandomInstance() {
        if (RANDOM_GENERATOR == null) {
            synchronized (Utils.class) {
                if (RANDOM_GENERATOR == null) {
                    RANDOM_GENERATOR = new Random(SEED);
                    System.out.printf("For random generator using seed: %d%n", SEED);
                    System.out.printf("To re-run test with same seed value please add \"-D%s=%d\" to command line.%n", SEED_PROPERTY_NAME, SEED);
                }
            }
        }
        return RANDOM_GENERATOR;
    }

    /**
     * Returns random element of non empty collection
     *
     * @param <T> a type of collection element
     * @param collection collection of elements
     * @return random element of collection
     * @throws IllegalArgumentException if collection is empty
     */
    public static <T> T getRandomElement(Collection<T> collection)
            throws IllegalArgumentException {
        if (collection.isEmpty()) {
            throw new IllegalArgumentException("Empty collection");
        }
        Random random = getRandomInstance();
        int elementIndex = 1 + random.nextInt(collection.size() - 1);
        Iterator<T> iterator = collection.iterator();
        while (--elementIndex != 0) {
            iterator.next();
        }
        return iterator.next();
    }

    /**
     * Returns random element of non empty array
     *
     * @param <T> a type of array element
     * @param array array of elements
     * @return random element of array
     * @throws IllegalArgumentException if array is empty
     */
    public static <T> T getRandomElement(T[] array)
            throws IllegalArgumentException {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Empty or null array");
        }
        Random random = getRandomInstance();
        return array[random.nextInt(array.length)];
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
     * @return condition value, to determine if wait was successful
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
     * @return condition value, to determine if wait was successful
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

    /**
     * Ensures a requested class is loaded
     * @param aClass class to load
     */
    public static void ensureClassIsLoaded(Class<?> aClass) {
        if (aClass == null) {
            throw new Error("Requested null class");
        }
        try {
            Class.forName(aClass.getName(), /* initialize = */ true,
                    ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            throw new Error("Class not found", e);
        }
    }
    /**
     * @param parent a class loader to be the parent for the returned one
     * @return an UrlClassLoader with urls made of the 'test.class.path' jtreg
     *         property and with the given parent
     */
    public static URLClassLoader getTestClassPathURLClassLoader(ClassLoader parent) {
        URL[] urls = Arrays.stream(TEST_CLASS_PATH.split(File.pathSeparator))
                .map(Paths::get)
                .map(Path::toUri)
                .map(x -> {
                    try {
                        return x.toURL();
                    } catch (MalformedURLException ex) {
                        throw new Error("Test issue. JTREG property"
                                + " 'test.class.path'"
                                + " is not defined correctly", ex);
                    }
                }).toArray(URL[]::new);
        return new URLClassLoader(urls, parent);
    }

    /**
     * Runs runnable and checks that it throws expected exception. If exceptionException is null it means
     * that we expect no exception to be thrown.
     * @param runnable what we run
     * @param expectedException expected exception
     */
    public static void runAndCheckException(ThrowingRunnable runnable, Class<? extends Throwable> expectedException) {
        runAndCheckException(runnable, t -> {
            if (t == null) {
                if (expectedException != null) {
                    throw new AssertionError("Didn't get expected exception " + expectedException.getSimpleName());
                }
            } else {
                String message = "Got unexpected exception " + t.getClass().getSimpleName();
                if (expectedException == null) {
                    throw new AssertionError(message, t);
                } else if (!expectedException.isAssignableFrom(t.getClass())) {
                    message += " instead of " + expectedException.getSimpleName();
                    throw new AssertionError(message, t);
                }
            }
        });
    }

    /**
     * Runs runnable and makes some checks to ensure that it throws expected exception.
     * @param runnable what we run
     * @param checkException a consumer which checks that we got expected exception and raises a new exception otherwise
     */
    public static void runAndCheckException(ThrowingRunnable runnable, Consumer<Throwable> checkException) {
        Throwable throwable = null;
        try {
            runnable.run();
        } catch (Throwable t) {
            throwable = t;
        }
        checkException.accept(throwable);
    }

    /**
     * Converts to VM type signature
     *
     * @param type Java type to convert
     * @return string representation of VM type
     */
    public static String toJVMTypeSignature(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class) {
                return "Z";
            } else if (type == byte.class) {
                return "B";
            } else if (type == char.class) {
                return "C";
            } else if (type == double.class) {
                return "D";
            } else if (type == float.class) {
                return "F";
            } else if (type == int.class) {
                return "I";
            } else if (type == long.class) {
                return "J";
            } else if (type == short.class) {
                return "S";
            } else if (type == void.class) {
                return "V";
            } else {
                throw new Error("Unsupported type: " + type);
            }
        }
        String result = type.getName().replaceAll("\\.", "/");
        if (!type.isArray()) {
            return "L" + result + ";";
        }
        return result;
    }

    public static Object[] getNullValues(Class<?>... types) {
        Object[] result = new Object[types.length];
        int i = 0;
        for (Class<?> type : types) {
            result[i++] = NULL_VALUES.get(type);
        }
        return result;
    }
    private static Map<Class<?>, Object> NULL_VALUES = new HashMap<>();
    static {
        NULL_VALUES.put(boolean.class, false);
        NULL_VALUES.put(byte.class, (byte) 0);
        NULL_VALUES.put(short.class, (short) 0);
        NULL_VALUES.put(char.class, '\0');
        NULL_VALUES.put(int.class, 0);
        NULL_VALUES.put(long.class, 0L);
        NULL_VALUES.put(float.class, 0.0f);
        NULL_VALUES.put(double.class, 0.0d);
    }

    /**
     * Returns mandatory property value
     * @param propName is a name of property to request
     * @return a String with requested property value
     */
    public static String getMandatoryProperty(String propName) {
        Objects.requireNonNull(propName, "Requested null property");
        String prop = System.getProperty(propName);
        Objects.requireNonNull(prop,
                String.format("A mandatory property '%s' isn't set", propName));
        return prop;
    }

    /*
     * Run uname with specified arguments.
     */
    public static OutputAnalyzer uname(String... args) throws Throwable {
        String[] cmds = new String[args.length + 1];
        cmds[0] = "uname";
        System.arraycopy(args, 0, cmds, 1, args.length);
        return ProcessTools.executeCommand(cmds);
    }

    /*
     * Returns the system distro.
     */
    public static String distro() {
        try {
            return uname("-v").asLines().get(0);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to determine distro.", t);
        }
    }

    // This method is intended to be called from a jtreg test.
    // It will identify the name of the test by means of stack walking.
    // It can handle both jtreg tests and a testng tests wrapped inside jtreg tests.
    // For jtreg tests the name of the test will be searched by stack-walking
    // until the method main() is found; the class containing that method is the
    // main test class and will be returned as the name of the test.
    // Special handling is used for testng tests.
    @SuppressWarnings("unchecked")
    public static String getTestName() {
        String result = null;
        // If we are using testng, then we should be able to load the "Test" annotation.
        Class<? extends Annotation> testClassAnnotation, junitTestClassAnnotation;

        try {
            testClassAnnotation = (Class<? extends Annotation>)Class.forName("org.testng.annotations.Test");
        } catch (ClassNotFoundException e) {
            testClassAnnotation = null;
        }

        // If we are using junit, then we should be able to load the "Test" annotation.
        try {
            junitTestClassAnnotation = (Class<? extends Annotation>)Class.forName("org.junit.Test");
        } catch (ClassNotFoundException e) {
            junitTestClassAnnotation = null;
        }

        StackTraceElement[] elms = (new Throwable()).getStackTrace();
        for (StackTraceElement n: elms) {
            String className = n.getClassName();

            // If this is a "main" method, then use its class name, but only
            // if we are not using testng or junit.
            if (testClassAnnotation == null && junitTestClassAnnotation == null &&
                "main".equals(n.getMethodName())) {
                result = className;
                break;
            }

            // If this is a testng test, the test will have no "main" method. We can
            // detect a testng test class by looking for the org.testng.annotations.Test
            // annotation. If present, then use the name of this class.
            if (testClassAnnotation != null) {
                try {
                    Class<?> c = Class.forName(className);
                    if (c.isAnnotationPresent(testClassAnnotation)) {
                        result = className;
                        break;
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Unexpected exception: " + e, e);
                }
            }

            // If this is a junit test, the test will have no "main" method. We can
            // detect a junit test class by going through all the methods and
            // check if the method has the org.junit.Test annotation. If present,
            // then use the name of this class.
            if (junitTestClassAnnotation != null) {
                try {
                    Class<?> c = Class.forName(className);
                    Method[] methods = c.getMethods();
                    for (Method method : methods) {
                        if (method.getName().equals(n.getMethodName()) &&
                            method.isAnnotationPresent(junitTestClassAnnotation)) {
                                result = className;
                                break;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Unexpected exception: " + e, e);
                }
            }
        }

        if (result == null) {
            throw new RuntimeException("Couldn't find main test class in stack trace");
        }

        return result;
    }

    /**
     * Creates an empty file in "user.dir" if the property set.
     * <p>
     * This method is meant as a replacement for {@code Files#createTempFile(String, String, FileAttribute...)}
     * that doesn't leave files behind in /tmp directory of the test machine
     * <p>
     * If the property "user.dir" is not set, "." will be used.
     *
     * @param prefix
     * @param suffix
     * @param attrs
     * @return the path to the newly created file that did not exist before this
     *         method was invoked
     * @throws IOException
     *
     * @see {@link Files#createTempFile(String, String, FileAttribute...)}
     */
    public static Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        Path dir = Paths.get(System.getProperty("user.dir", "."));
        return Files.createTempFile(dir, prefix, suffix);
    }

    /**
     * Creates an empty directory in "user.dir" or "."
     * <p>
     * This method is meant as a replacement for {@code Files#createTempDirectory(String, String, FileAttribute...)}
     * that doesn't leave files behind in /tmp directory of the test machine
     * <p>
     * If the property "user.dir" is not set, "." will be used.
     *
     * @param prefix
     * @param attrs
     * @return the path to the newly created directory
     * @throws IOException
     *
     * @see {@link Files#createTempDirectory(String, String, FileAttribute...)}
     */
    public static Path createTempDirectory(String prefix, FileAttribute<?>... attrs) throws IOException {
        Path dir = Paths.get(System.getProperty("user.dir", "."));
        return Files.createTempDirectory(dir, prefix);
    }
}
