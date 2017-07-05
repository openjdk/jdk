/*
 * Copyright (c) 1996, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

/**
 * <P> This class generates seeds for the cryptographically strong random
 * number generator.
 * <P> The seed is produced using one of two techniques, via a computation
 * of current system activity or from an entropy gathering device.
 * <p> In the default technique the seed is  produced by counting the
 * number of times the VM manages to loop in a given period. This number
 * roughly reflects the machine load at that point in time.
 * The samples are translated using a permutation (s-box)
 * and then XORed together. This process is non linear and
 * should prevent the samples from "averaging out". The s-box
 * was designed to have even statistical distribution; it's specific
 * values are not crucial for the security of the seed.
 * We also create a number of sleeper threads which add entropy
 * to the system by keeping the scheduler busy.
 * Twenty such samples should give us roughly 160 bits of randomness.
 * <P> These values are gathered in the background by a daemon thread
 * thus allowing the system to continue performing it's different
 * activites, which in turn add entropy to the random seed.
 * <p> The class also gathers miscellaneous system information, some
 * machine dependent, some not. This information is then hashed together
 * with the 20 seed bytes.
 * <P> The alternative to the above approach is to acquire seed material
 * from an entropy gathering device, such as /dev/random. This can be
 * accomplished by setting the value of the "securerandom.source"
 * security property (in the Java security properties file) to a URL
 * specifying the location of the entropy gathering device.
 * In the event the specified URL cannot be accessed the default
 * mechanism is used.
 * The Java security properties file is located in the file named
 * &lt;JAVA_HOME&gt;/lib/security/java.security.
 * &lt;JAVA_HOME&gt; refers to the value of the java.home system property,
 * and specifies the directory where the JRE is installed.
 *
 * @author Joshua Bloch
 * @author Gadi Guy
 */

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.security.*;
import java.io.*;
import java.util.Properties;
import java.util.Enumeration;
import java.net.*;
import java.util.Random;
import sun.security.util.Debug;

abstract class SeedGenerator {

    // Static instance is created at link time
    private static SeedGenerator instance;

    private static final Debug debug = Debug.getInstance("provider");

    final static String URL_DEV_RANDOM = SunEntries.URL_DEV_RANDOM;
    final static String URL_DEV_URANDOM = SunEntries.URL_DEV_URANDOM;

    // Static initializer to hook in selected or best performing generator
    static {
        String egdSource = SunEntries.getSeedSource();

        // Try the URL specifying the source
        // e.g. file:/dev/random
        //
        // The URL file:/dev/random or file:/dev/urandom is used to indicate
        // the SeedGenerator using OS support, if available.
        // On Windows, the causes MS CryptoAPI to be used.
        // On Solaris and Linux, this is the identical to using
        // URLSeedGenerator to read from /dev/random

        if (egdSource.equals(URL_DEV_RANDOM) || egdSource.equals(URL_DEV_URANDOM)) {
            try {
                instance = new NativeSeedGenerator();
                if (debug != null) {
                    debug.println("Using operating system seed generator");
                }
            } catch (IOException e) {
                if (debug != null) {
                    debug.println("Failed to use operating system seed "
                                  + "generator: " + e.toString());
                }
            }
        } else if (egdSource.length() != 0) {
            try {
                instance = new URLSeedGenerator(egdSource);
                if (debug != null) {
                    debug.println("Using URL seed generator reading from "
                                  + egdSource);
                }
            } catch (IOException e) {
                if (debug != null)
                    debug.println("Failed to create seed generator with "
                                  + egdSource + ": " + e.toString());
            }
        }

        // Fall back to ThreadedSeedGenerator
        if (instance == null) {
            if (debug != null) {
                debug.println("Using default threaded seed generator");
            }
            instance = new ThreadedSeedGenerator();
        }
    }

    /**
     * Fill result with bytes from the queue. Wait for it if it isn't ready.
     */
    static public void generateSeed(byte[] result) {
        instance.getSeedBytes(result);
    }

    void getSeedBytes(byte[] result) {
        for (int i = 0; i < result.length; i++) {
          result[i] = getSeedByte();
        }
    }

    abstract byte getSeedByte();

    /**
     * Retrieve some system information, hashed.
     */
    static byte[] getSystemEntropy() {
        byte[] ba;
        final MessageDigest md;

        try {
            md = MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException nsae) {
            throw new InternalError("internal error: SHA-1 not available.");
        }

        // The current time in millis
        byte b =(byte)System.currentTimeMillis();
        md.update(b);

        java.security.AccessController.doPrivileged
            (new java.security.PrivilegedAction<Void>() {
                public Void run() {

                    try {
                        // System properties can change from machine to machine
                        String s;
                        Properties p = System.getProperties();
                        Enumeration<?> e = p.propertyNames();
                        while (e.hasMoreElements()) {
                            s =(String)e.nextElement();
                            md.update(s.getBytes());
                            md.update(p.getProperty(s).getBytes());
                        }

                        md.update
                            (InetAddress.getLocalHost().toString().getBytes());

                        // The temporary dir
                        File f = new File(p.getProperty("java.io.tmpdir"));

                        // Go thru files in the tmp dir using NIO's
                        // DirectoryStream. Fallback to File.list()
                        // if NIO is not available.
                        if (NIODirectoryStream.isAvailable()) {
                            int count = 0;
                            Iterable<?> stream =
                                    NIODirectoryStream.newDirectoryStream(f);
                            // We use a Random object to choose what file names
                            // should be used. Otherwise on a machine with too
                            // many files, the same first 1024 files always get
                            // used. Any, We make sure the first 512 files are
                            // always used.
                            Random r = new Random();
                            try {
                                for (Object entry: stream) {
                                    if (count < 512 || r.nextBoolean()) {
                                        md.update(NIODirectoryStream.getName(
                                                entry).getBytes());
                                    }
                                    if (count++ > 1024) {
                                        break;
                                    }
                                }
                            } finally {
                                ((Closeable)stream).close();
                            }
                        } else {
                            String[] sa = f.list();
                            for(int i = 0; i < sa.length; i++) {
                                md.update(sa[i].getBytes());
                            }
                        }
                    } catch (Exception ex) {
                        md.update((byte)ex.hashCode());
                    }

                    // get Runtime memory stats
                    Runtime rt = Runtime.getRuntime();
                    byte[] memBytes = longToByteArray(rt.totalMemory());
                    md.update(memBytes, 0, memBytes.length);
                    memBytes = longToByteArray(rt.freeMemory());
                    md.update(memBytes, 0, memBytes.length);

                    return null;
                }
            });
        return md.digest();
    }

    /**
     * Helper function to convert a long into a byte array (least significant
     * byte first).
     */
    private static byte[] longToByteArray(long l) {
        byte[] retVal = new byte[8];

        for (int i=0; i<8; i++) {
            retVal[i] = (byte) l;
            l >>= 8;
        }

        return retVal;
    }

    /*
    // This method helps the test utility receive unprocessed seed bytes.
    public static int genTestSeed() {
        return myself.getByte();
    }
    */


    private static class ThreadedSeedGenerator extends SeedGenerator implements Runnable {
        // Queue is used to collect seed bytes
        private byte[] pool;
        private int start, end, count;

        // Thread group for our threads
        ThreadGroup seedGroup;

        /**
     * The constructor is only called once to construct the one
     * instance we actually use. It instantiates the message digest
     * and starts the thread going.
     */

        ThreadedSeedGenerator() {
            pool = new byte[20];
            start = end = 0;

            MessageDigest digest;

            try {
                digest = MessageDigest.getInstance("SHA");
            } catch (NoSuchAlgorithmException e) {
                throw new InternalError("internal error: SHA-1 not available.");
            }

            final ThreadGroup[] finalsg = new ThreadGroup[1];
            Thread t = java.security.AccessController.doPrivileged
                (new java.security.PrivilegedAction<Thread>() {
                        public Thread run() {
                            ThreadGroup parent, group =
                                Thread.currentThread().getThreadGroup();
                            while ((parent = group.getParent()) != null)
                                group = parent;
                            finalsg[0] = new ThreadGroup
                                (group, "SeedGenerator ThreadGroup");
                            Thread newT = new Thread(finalsg[0],
                                                     ThreadedSeedGenerator.this,
                                                     "SeedGenerator Thread");
                            newT.setPriority(Thread.MIN_PRIORITY);
                            newT.setDaemon(true);
                            return newT;
                        }
                    });
            seedGroup = finalsg[0];
            t.start();
        }

        /**
         * This method does the actual work. It collects random bytes and
         * pushes them into the queue.
         */
        final public void run() {
            try {
                while (true) {
                    // Queue full? Wait till there's room.
                    synchronized(this) {
                        while (count >= pool.length)
                            wait();
                    }

                    int counter, quanta;
                    byte v = 0;

                    // Spin count must not be under 64000
                    for (counter = quanta = 0; (counter < 64000) && (quanta < 6);
                         quanta++) {

                        // Start some noisy threads
                        try {
                            BogusThread bt = new BogusThread();
                            Thread t = new Thread
                                (seedGroup, bt, "SeedGenerator Thread");
                            t.start();
                        } catch (Exception e) {
                            throw new InternalError("internal error: " +
                                                    "SeedGenerator thread creation error.");
                        }

                        // We wait 250milli quanta, so the minimum wait time
                        // cannot be under 250milli.
                        int latch = 0;
                        latch = 0;
                        long l = System.currentTimeMillis() + 250;
                        while (System.currentTimeMillis() < l) {
                            synchronized(this){};
                            latch++;
                        }

                        // Translate the value using the permutation, and xor
                        // it with previous values gathered.
                        v ^= rndTab[latch % 255];
                        counter += latch;
                    }

                    // Push it into the queue and notify anybody who might
                    // be waiting for it.
                    synchronized(this) {
                        pool[end] = v;
                        end++;
                        count++;
                        if (end >= pool.length)
                            end = 0;

                        notifyAll();
                    }
                }
            } catch (Exception e) {
                throw new InternalError("internal error: " +
                                        "SeedGenerator thread generated an exception.");
            }
        }

        byte getSeedByte() {
            byte b = 0;

            try {
                // Wait for it...
                synchronized(this) {
                    while (count <= 0)
                        wait();
                }
            } catch (Exception e) {
                if (count <= 0)
                    throw new InternalError("internal error: " +
                                            "SeedGenerator thread generated an exception.");
            }

            synchronized(this) {
                // Get it from the queue
                b = pool[start];
                pool[start] = 0;
                start++;
                count--;
                if (start == pool.length)
                    start = 0;

                // Notify the daemon thread, just in case it is
                // waiting for us to make room in the queue.
                notifyAll();
            }

            return b;
        }

        // The permutation was calculated by generating 64k of random
        // data and using it to mix the trivial permutation.
        // It should be evenly distributed. The specific values
        // are not crucial to the security of this class.
        private static byte[] rndTab = {
            56, 30, -107, -6, -86, 25, -83, 75, -12, -64,
            5, -128, 78, 21, 16, 32, 70, -81, 37, -51,
            -43, -46, -108, 87, 29, 17, -55, 22, -11, -111,
            -115, 84, -100, 108, -45, -15, -98, 72, -33, -28,
            31, -52, -37, -117, -97, -27, 93, -123, 47, 126,
            -80, -62, -93, -79, 61, -96, -65, -5, -47, -119,
            14, 89, 81, -118, -88, 20, 67, -126, -113, 60,
            -102, 55, 110, 28, 85, 121, 122, -58, 2, 45,
            43, 24, -9, 103, -13, 102, -68, -54, -101, -104,
            19, 13, -39, -26, -103, 62, 77, 51, 44, 111,
            73, 18, -127, -82, 4, -30, 11, -99, -74, 40,
            -89, 42, -76, -77, -94, -35, -69, 35, 120, 76,
            33, -73, -7, 82, -25, -10, 88, 125, -112, 58,
            83, 95, 6, 10, 98, -34, 80, 15, -91, 86,
            -19, 52, -17, 117, 49, -63, 118, -90, 36, -116,
            -40, -71, 97, -53, -109, -85, 109, -16, -3, 104,
            -95, 68, 54, 34, 26, 114, -1, 106, -121, 3,
            66, 0, 100, -84, 57, 107, 119, -42, 112, -61,
            1, 48, 38, 12, -56, -57, 39, -106, -72, 41,
            7, 71, -29, -59, -8, -38, 79, -31, 124, -124,
            8, 91, 116, 99, -4, 9, -36, -78, 63, -49,
            -67, -87, 59, 101, -32, 92, 94, 53, -41, 115,
            -66, -70, -122, 50, -50, -22, -20, -18, -21, 23,
            -2, -48, 96, 65, -105, 123, -14, -110, 69, -24,
            -120, -75, 74, 127, -60, 113, 90, -114, 105, 46,
            27, -125, -23, -44, 64
        };

        /**
         * This inner thread causes the thread scheduler to become 'noisy',
         * thus adding entropy to the system load.
         * At least one instance of this class is generated for every seed byte.
         */

        private static class BogusThread implements Runnable {
            final public void run() {
                try {
                    for(int i = 0; i < 5; i++)
                        Thread.sleep(50);
                    // System.gc();
                } catch (Exception e) {
                }
            }
        }
    }

    static class URLSeedGenerator extends SeedGenerator {

        private String deviceName;
        private BufferedInputStream devRandom;


        /**
         * The constructor is only called once to construct the one
         * instance we actually use. It opens the entropy gathering device
         * which will supply the randomness.
         */

        URLSeedGenerator(String egdurl) throws IOException {
            if (egdurl == null) {
                throw new IOException("No random source specified");
            }
            deviceName = egdurl;
            init();
        }

        URLSeedGenerator() throws IOException {
            this(SeedGenerator.URL_DEV_RANDOM);
        }

        private void init() throws IOException {
            final URL device = new URL(deviceName);
            devRandom = java.security.AccessController.doPrivileged
                (new java.security.PrivilegedAction<BufferedInputStream>() {
                        public BufferedInputStream run() {
                            try {
                                return new BufferedInputStream(device.openStream());
                            } catch (IOException ioe) {
                                return null;
                            }
                        }
                    });

            if (devRandom == null) {
                throw new IOException("failed to open " + device);
            }
        }

        byte getSeedByte() {
            byte b[] = new byte[1];
            int stat;
            try {
                stat = devRandom.read(b, 0, b.length);
            } catch (IOException ioe) {
                throw new InternalError("URLSeedGenerator " + deviceName +
                                        " generated exception: " +
                                        ioe.getMessage());
            }
            if (stat == b.length) {
                return b[0];
            } else if (stat == -1) {
                throw new InternalError("URLSeedGenerator " + deviceName +
                                           " reached end of file");
            } else {
                throw new InternalError("URLSeedGenerator " + deviceName +
                                           " failed read");
            }
        }

    }

    /**
     * A wrapper of NIO DirectoryStream using reflection.
     */
    private static class NIODirectoryStream {
        private static final Class<?> pathClass =
                getClass("java.nio.file.Path");

        private static final Method toPathMethod =
                (pathClass == null) ? null : getMethod(File.class, "toPath");
        private static final Method getNameMethod =
                getMethod(pathClass, "getName");
        private static final Method newDirectoryStreamMethod =
                getMethod(pathClass, "newDirectoryStream");

        private static Class<?> getClass(String name) {
            try {
                return Class.forName(name, true, null);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        private static Method getMethod(Class<?> clazz,
                                        String name,
                                        Class<?>... paramTypes) {
            if (clazz != null) {
                try {
                    return clazz.getMethod(name, paramTypes);
                } catch (NoSuchMethodException e) {
                    throw new AssertionError(e);
                }
            } else {
                return null;
            }
        }

        static boolean isAvailable() {
            return pathClass != null;
        }

        static Iterable<?> newDirectoryStream(File dir) throws IOException {
            assert pathClass != null;
            try {
                Object path = toPathMethod.invoke(dir);
                return (Iterable<?>)newDirectoryStreamMethod.invoke(path);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException)
                    throw (IOException)cause;
                if (cause instanceof RuntimeException)
                    throw (RuntimeException)cause;
                if (cause instanceof Error)
                    throw (Error)cause;
                throw new AssertionError(e);
            } catch (IllegalAccessException iae) {
                throw new AssertionError(iae);
            }
        }

        static String getName(Object path) {
            assert pathClass != null;
            try {
                Object name = getNameMethod.invoke(path);
                return name.toString();
            } catch (InvocationTargetException e) {
                throw new AssertionError(e);
            } catch (IllegalAccessException iae) {
                throw new AssertionError(iae);
            }
        }
    }
}

