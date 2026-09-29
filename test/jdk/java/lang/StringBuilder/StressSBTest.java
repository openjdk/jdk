/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8351443
 * @summary Randomized test of StringBuilder
 * @run main StressSBTest
 */

import java.lang.System.Logger;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.CharBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;


public class StressSBTest {

    // Logger for info and errors
    static final Logger LOG = System.getLogger("StressSBTest");
    // Number of concurrent platform threads
    private final static int N_THREADS = 11;
    // Base length of test run
    private final static Duration N_Duration = Duration.ofSeconds(2);
    // Cache jtreg timeout factor to allow test to be run as a standalone main()
    private static final double TIMEOUT_FACTOR =
            Double.parseDouble(System.getProperty("test.timeout.factor", "1.0"));

    /**
     * Run the stress test with a fixed set of parameters.
     *
     * @param ignored
     */
    public static void main(String[] ignored) {
        Duration duration = Duration.ofMillis((long) (N_Duration.toMillis() * TIMEOUT_FACTOR));
        new StressSBTest().stress(N_THREADS, duration);
    }

    /**
     * Stress test using a number of platform threads for a duration.
     * Success is marked by a lack of exceptions.
     * Logging output indicates the number of operations performed by each thread
     *
     * @param numThreads the number of threads
     * @param duration   a Duration, typically a few seconds
     */
    public void stress(int numThreads, Duration duration) {
        StressOps stressOps = new StressOps();

        final StringBuilder sb = new StringBuilder("abcdefghijklmnopqrstuvwxyz");
        // A single StringBuilder is used to exercise the API in the face of racy behavior
        List<Thread> threads = IntStream.range(0, numThreads).mapToObj(i -> {
                    try {
                        Thread t = Thread.ofPlatform().start(() -> stressOps.randomStress(sb, duration));
                        LOG.log(Logger.Level.DEBUG, t);
                        return t;
                    } catch (Throwable t) {
                        LOG.log(Logger.Level.ERROR, "Thread.ofPlatform", t);
                    }
                    return null;
                })
                .filter(t -> t != null)
                .toList();
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException ie) {
                // ignore
            }
        });
        LOG.log(Logger.Level.INFO, "Completed: threads: %d, duration: %s".formatted(numThreads, duration));
    }


    /**
     * Methods and support for stress testing StringBuilder.
     */
    private static class StressOps {

        private static final Random randomGen = new Random();
        private static final StringBuilder stringBuilder = new StringBuilder("wxyz");
        private static final StringBuffer stringBuffer = new StringBuffer("wxyz");
        private static final CharBuffer charBuffer = CharBuffer.allocate(10).append("charBuffer");
        private static final char[] charArray = "xyz".toCharArray();
        private static final CharSequence charSequence = "now is the time";
        // Cache of mappings of all StringBuilder public instand methods mh -> Method
        private static Map<MethodHandle, Method> testMethodsCache;
        // Active map of SB methods mh -> Method
        private final Map<MethodHandle, Method> testMethods;

        public StressOps() {
            if (testMethodsCache == null) {
                testMethodsCache = findTestMethods();
            }
            testMethods = testMethodsCache;
        }

        /**
         * Stress test randomizes methods acting on a StringBuilder.
         * Exceptions are logged but do no affect success/failure result.
         *
         * @param sb       a StringBuilder
         * @param duration a Duration of time to stress
         */
        public void randomStress(StringBuilder sb, Duration duration) {
            MethodHandle[] ops = testMethods.keySet().toArray(new MethodHandle[0]);
            int count = 0;
            int worked = 0;
            Instant end = Instant.now().plus(duration);
            while (Instant.now().isBefore(end)) {
                int j = randomGen.nextInt(ops.length);
                var mh = ops[j];
                String name = testMethods.get(mh).getName();
                MethodType mt = mh.type();
                try {
                    count++;
                    if (invokeByMethodNameAndType(name, mt, sb)) {
                        worked++;
                    }

                } catch (Throwable t) {
                    LOG.log(Logger.Level.DEBUG, "Exception testing " + name + mt, t);
                }
            }
            LOG.log(Logger.Level.INFO, "StringBuilder method calls: %d, successful: %d".formatted(count, worked));
        }


        /**
         * {@return Return a map of all the public instance methods of java.lang.StringBuilder
         * mapping MethodHandle to the Method}
         */
        private Map<MethodHandle, Method> findTestMethods() {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Method[] methods = StringBuilder.class.getDeclaredMethods();
            Map<MethodHandle, Method> map = new HashMap<>(methods.length);
            Arrays.stream(methods)
                    .filter(m -> Modifier.isPublic(m.getModifiers()) &&
                            !Modifier.isStatic(m.getModifiers()))
                    .forEach(m -> {
                        try {
                            MethodHandle mh = lookup.unreflect(m);
                            map.put(mh, m);
                        } catch (IllegalAccessException iae) {
                            LOG.log(Logger.Level.DEBUG, "AsbTest annotated method not accessible", iae);
                        }
                    });
            LOG.log(Logger.Level.DEBUG, "%d StringBuilder test methods".formatted(map.size()));
            return map;
        }

        /**
         * Invoke the indicated method on the StringBuilder
         *
         * @param name the method name
         * @param mt   the method type
         * @param sb   a StringBuilder
         * @return true if the method returned a non-null result
         */
        private boolean invokeByMethodNameAndType(String name, MethodType mt, StringBuilder sb) {
            String s = name + mt.toString();
            Object o = switch (s) {
                case "length(StringBuilder)int" -> sb.length();
                case "toString(StringBuilder)String" -> sb.toString();
                case "append(StringBuilder,StringBuffer)AbstractStringBuilder" ->
                        sb.append(stringBuffer);
                case "append(StringBuilder,StringBuffer)StringBuilder" -> sb.append(stringBuffer);
                case "append(StringBuilder,CharSequence)AbstractStringBuilder" ->
                        sb.append(charBuffer);
                case "append(StringBuilder,CharSequence)StringBuilder" -> sb.append(charBuffer);
                case "append(StringBuilder,CharSequence)Appendable" -> sb.append(charBuffer);
                case "append(StringBuilder,CharSequence,int,int)AbstractStringBuilder" ->
                        sb.append("abc", 1, 2);
                case "append(StringBuilder,CharSequence,int,int)StringBuilder" ->
                        sb.append("abc", 1, 2);
                case "append(StringBuilder,CharSequence,int,int)Appendable" ->
                        sb.append("abc", 1, 2);
                case "append(StringBuilder,char[])AbstractStringBuilder" -> sb.append(charArray);
                case "append(StringBuilder,char[])StringBuilder" -> sb.append(charArray);
                case "append(StringBuilder,String)AbstractStringBuilder" -> sb.append("Abcdefg");
                case "append(StringBuilder,String)StringBuilder" -> sb.append("abcdefg");
                case "append(StringBuilder,Object)AbstractStringBuilder" -> new Object();
                case "append(StringBuilder,Object)StringBuilder" -> sb.append(new Object());
                case "append(StringBuilder,char)Appendable" -> sb.append('\uff21');
                case "append(StringBuilder,char)AbstractStringBuilder" -> sb.append('A');
                case "append(StringBuilder,char)StringBuilder" -> sb.append('B');
                case "append(StringBuilder,int)AbstractStringBuilder" -> sb.append(987654321);
                case "append(StringBuilder,int)StringBuilder" -> sb.append(987654321);
                case "append(StringBuilder,long)AbstractStringBuilder" ->
                        sb.append(987654321987654321L);
                case "append(StringBuilder,long)StringBuilder" -> sb.append(987654321987654321L);
                case "append(StringBuilder,float)AbstractStringBuilder" -> sb.append(Math.PI);
                case "append(StringBuilder,float)StringBuilder" -> sb.append(Math.PI);
                case "append(StringBuilder,double)AbstractStringBuilder" -> sb.append(Math.TAU);
                case "append(StringBuilder,double)StringBuilder" -> sb.append(Math.TAU);
                case "append(StringBuilder,boolean)AbstractStringBuilder" -> sb.append(true);
                case "append(StringBuilder,boolean)StringBuilder" -> sb.append(false);
                case "append(StringBuilder,char[],int,int)AbstractStringBuilder" ->
                        sb.append(charArray, 3, 4);
                case "append(StringBuilder,char[],int,int)StringBuilder" ->
                        sb.append(charArray, 4, 3);
                case "reverse(StringBuilder)StringBuilder" -> sb.reverse();
                case "reverse(StringBuilder)AbstractStringBuilder" -> sb.reverse();
                case "getChars(StringBuilder,int,int,char[],int)void" -> {
                    sb.getChars(2, 4, charArray, 3);
                    yield sb;
                }
                case "compareTo(StringBuilder,Object)int" -> sb.compareTo(stringBuilder);
                case "compareTo(StringBuilder,StringBuilder)int" -> sb.compareTo(stringBuilder);
                case "indexOf(StringBuilder,String,int)int" -> sb.indexOf("A", 2);
                case "indexOf(StringBuilder,String)int" -> sb.indexOf("B");
                case "insert(StringBuilder,int,CharSequence)StringBuilder" ->
                        sb.insert(2, charSequence);
                case "insert(StringBuilder,int,CharSequence)AbstractStringBuilder" ->
                        sb.insert(2, charSequence);
                case "insert(StringBuilder,int,String)StringBuilder" -> sb.insert(2, "Now");
                case "insert(StringBuilder,int,String)AbstractStringBuilder" ->
                        sb.insert(2, "Then");
                case "insert(StringBuilder,int,char[])StringBuilder" -> sb.insert(4, charArray);
                case "insert(StringBuilder,int,char[])AbstractStringBuilder" ->
                        sb.insert(4, charArray);
                case "insert(StringBuilder,int,Object)AbstractStringBuilder" ->
                        sb.insert(3, new Object());
                case "insert(StringBuilder,int,Object)StringBuilder" -> sb.insert(4, "RUST");
                case "insert(StringBuilder,int,char[],int,int)AbstractStringBuilder" ->
                        sb.insert(2, charArray, 0, 4);
                case "insert(StringBuilder,int,char[],int,int)StringBuilder" ->
                        sb.insert(2, charArray, 0, 4);
                case "insert(StringBuilder,int,int)StringBuilder" -> sb.insert(2, 46000);
                case "insert(StringBuilder,int,int)AbstractStringBuilder" -> sb.insert(3, 47000);
                case "insert(StringBuilder,int,double)StringBuilder" -> sb.insert(2, Math.PI);
                case "insert(StringBuilder,int,double)AbstractStringBuilder" ->
                        sb.insert(2, Math.PI);
                case "insert(StringBuilder,int,float)StringBuilder" -> sb.insert(2, Math.TAU);
                case "insert(StringBuilder,int,float)AbstractStringBuilder" ->
                        sb.insert(2, Math.TAU);
                case "insert(StringBuilder,int,long)StringBuilder" -> sb.insert(2, 42L);
                case "insert(StringBuilder,int,long)AbstractStringBuilder" -> sb.insert(2, 42L);
                case "insert(StringBuilder,int,char)StringBuilder" -> sb.insert(2, 'Z');
                case "insert(StringBuilder,int,char)AbstractStringBuilder" -> sb.insert(2, 'Z');
                case "insert(StringBuilder,int,boolean)StringBuilder" -> sb.insert(2, true);
                case "insert(StringBuilder,int,boolean)AbstractStringBuilder" ->
                        sb.insert(3, false);
                case "insert(StringBuilder,int,CharSequence,int,int)StringBuilder" ->
                        sb.insert(4, charSequence, 4, 5);
                case "insert(StringBuilder,int,CharSequence,int,int)AbstractStringBuilder" ->
                        sb.insert(4, charSequence, 5, 4);
                case "charAt(StringBuilder,int)char" -> sb.charAt(5);
                case "codePointAt(StringBuilder,int)int" -> sb.codePointAt(4);
                case "codePointBefore(StringBuilder,int)int" -> sb.codePointBefore(3);
                case "codePointCount(StringBuilder,int,int)int" -> sb.codePointCount(3, 9);
                case "offsetByCodePoints(StringBuilder,int,int)int" -> sb.offsetByCodePoints(3, 7);
                case "lastIndexOf(StringBuilder,String,int)int" -> sb.lastIndexOf("A", 45);
                case "lastIndexOf(StringBuilder,String)int" -> sb.lastIndexOf("B");
                case "substring(StringBuilder,int)String" -> sb.substring(6);
                case "substring(StringBuilder,int,int)String" -> sb.substring(6, 9);
                case "replace(StringBuilder,int,int,String)StringBuilder" ->
                        sb.replace(2, 5, "xyz");
                case "replace(StringBuilder,int,int,String)AbstractStringBuilder" ->
                        sb.replace(2, 5, "XYZ");
                case "repeat(StringBuilder,CharSequence,int)StringBuilder" -> sb.repeat('Z', 6);
                case "repeat(StringBuilder,CharSequence,int)AbstractStringBuilder" ->
                        sb.repeat('X', 42);
                case "repeat(StringBuilder,int,int)AbstractStringBuilder" -> sb.repeat(101, 25);
                case "repeat(StringBuilder,int,int)StringBuilder" -> sb.repeat(102, 24);
                case "codePoints(StringBuilder)IntStream" -> sb.codePoints().count();
                case "subSequence(StringBuilder,int,int)CharSequence" -> sb.subSequence(3, 9);
                case "chars(StringBuilder)IntStream" -> sb.chars().count();
                case "setLength(StringBuilder,int)void" -> {
                    sb.setLength(45);
                    yield 45;
                }
                case "capacity(StringBuilder)int" -> sb.capacity();
                case "ensureCapacity(StringBuilder,int)void" -> {
                    sb.ensureCapacity(150);
                    yield 150;
                }
                case "trimToSize(StringBuilder)void" -> {
                    sb.trimToSize();
                    yield sb.length();
                }
                case "setCharAt(StringBuilder,int,char)void" -> {
                    sb.setCharAt(6, 'T');
                    yield 'T';
                }
                case "appendCodePoint(StringBuilder,int)StringBuilder" ->
                        sb.appendCodePoint(0xff21);
                case "appendCodePoint(StringBuilder,int)AbstractStringBuilder" ->
                        sb.appendCodePoint(0xff21);
                case "delete(StringBuilder,int,int)StringBuilder" -> sb.delete(5, 20);
                case "delete(StringBuilder,int,int)AbstractStringBuilder" -> sb.delete(6, 21);
                case "deleteCharAt(StringBuilder,int)AbstractStringBuilder" -> sb.deleteCharAt(7);
                case "deleteCharAt(StringBuilder,int)StringBuilder" -> sb.deleteCharAt(8);
                case "equals(Object,Object)boolean" -> false;
                case "hashCode(Object)int" -> sb.hashCode();
                case "getClass(Object)Class" -> sb.getClass();
                case "isEmpty(CharSequence)boolean" -> null;
                default -> {
                    System.out.println("not executing: " + s);
                    yield null;
                }
            };
            return o != null;
        }
    }
}
