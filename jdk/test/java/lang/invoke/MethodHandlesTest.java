/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary unit tests for java.lang.invoke.MethodHandles
 * @compile MethodHandlesTest.java remote/RemoteExample.java
 * @run junit/othervm/timeout=2500 -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies -esa test.java.lang.invoke.MethodHandlesTest
 */

package test.java.lang.invoke;

import test.java.lang.invoke.remote.RemoteExample;
import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;


/**
 *
 * @author jrose
 */
public class MethodHandlesTest {
    static final Class<?> THIS_CLASS = MethodHandlesTest.class;
    // How much output?
    static int verbosity = 0;
    static {
        String vstr = System.getProperty(THIS_CLASS.getSimpleName()+".verbosity");
        if (vstr == null)
            vstr = System.getProperty(THIS_CLASS.getName()+".verbosity");
        if (vstr != null)  verbosity = Integer.parseInt(vstr);
    }

    // Set this true during development if you want to fast-forward to
    // a particular new, non-working test.  Tests which are known to
    // work (or have recently worked) test this flag and return on true.
    static final boolean CAN_SKIP_WORKING;
    static {
        String vstr = System.getProperty(THIS_CLASS.getSimpleName()+".CAN_SKIP_WORKING");
        if (vstr == null)
            vstr = System.getProperty(THIS_CLASS.getName()+".CAN_SKIP_WORKING");
        CAN_SKIP_WORKING = Boolean.parseBoolean(vstr);
    }

    // Set 'true' to do about 15x fewer tests, especially those redundant with RicochetTest.
    // This might be useful with -Xcomp stress tests that compile all method handles.
    static boolean CAN_TEST_LIGHTLY = Boolean.getBoolean(THIS_CLASS.getName()+".CAN_TEST_LIGHTLY");

    @Test
    public void testFirst() throws Throwable {
        verbosity += 9; try {
            // left blank for debugging
        } finally { printCounts(); verbosity -= 9; }
    }

    static final int MAX_ARG_INCREASE = 3;

    public MethodHandlesTest() {
    }

    String testName;
    static int allPosTests, allNegTests;
    int posTests, negTests;
    @After
    public void printCounts() {
        if (verbosity >= 2 && (posTests | negTests) != 0) {
            System.out.println();
            if (posTests != 0)  System.out.println("=== "+testName+": "+posTests+" positive test cases run");
            if (negTests != 0)  System.out.println("=== "+testName+": "+negTests+" negative test cases run");
            allPosTests += posTests;
            allNegTests += negTests;
            posTests = negTests = 0;
        }
    }
    void countTest(boolean positive) {
        if (positive) ++posTests;
        else          ++negTests;
    }
    void countTest() { countTest(true); }
    void startTest(String name) {
        if (testName != null)  printCounts();
        if (verbosity >= 1)
            System.out.println(name);
        posTests = negTests = 0;
        testName = name;
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        calledLog.clear();
        calledLog.add(null);
        nextArgVal = INITIAL_ARG_VAL;
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        int posTests = allPosTests, negTests = allNegTests;
        if (verbosity >= 0 && (posTests | negTests) != 0) {
            System.out.println();
            if (posTests != 0)  System.out.println("=== "+posTests+" total positive test cases");
            if (negTests != 0)  System.out.println("=== "+negTests+" total negative test cases");
        }
    }

    static List<Object> calledLog = new ArrayList<>();
    static Object logEntry(String name, Object... args) {
        return Arrays.asList(name, Arrays.asList(args));
    }
    public static Object called(String name, Object... args) {
        Object entry = logEntry(name, args);
        calledLog.add(entry);
        return entry;
    }
    static void assertCalled(String name, Object... args) {
        Object expected = logEntry(name, args);
        Object actual   = calledLog.get(calledLog.size() - 1);
        if (expected.equals(actual) && verbosity < 9)  return;
        System.out.println("assertCalled "+name+":");
        System.out.println("expected:   "+deepToString(expected));
        System.out.println("actual:     "+actual);
        System.out.println("ex. types:  "+getClasses(expected));
        System.out.println("act. types: "+getClasses(actual));
        assertEquals("previous method call", expected, actual);
    }
    static void printCalled(MethodHandle target, String name, Object... args) {
        if (verbosity >= 3)
            System.out.println("calling MH="+target+" to "+name+deepToString(args));
    }
    static String deepToString(Object x) {
        if (x == null)  return "null";
        if (x instanceof Collection)
            x = ((Collection)x).toArray();
        if (x instanceof Object[]) {
            Object[] ax = (Object[]) x;
            ax = Arrays.copyOf(ax, ax.length, Object[].class);
            for (int i = 0; i < ax.length; i++)
                ax[i] = deepToString(ax[i]);
            x = Arrays.deepToString(ax);
        }
        if (x.getClass().isArray())
            try {
                x = Arrays.class.getMethod("toString", x.getClass()).invoke(null, x);
            } catch (ReflectiveOperationException ex) { throw new Error(ex); }
        assert(!(x instanceof Object[]));
        return x.toString();
    }

    static Object castToWrapper(Object value, Class<?> dst) {
        Object wrap = null;
        if (value instanceof Number)
            wrap = castToWrapperOrNull(((Number)value).longValue(), dst);
        if (value instanceof Character)
            wrap = castToWrapperOrNull((char)(Character)value, dst);
        if (wrap != null)  return wrap;
        return dst.cast(value);
    }

    @SuppressWarnings("cast")  // primitive cast to (long) is part of the pattern
    static Object castToWrapperOrNull(long value, Class<?> dst) {
        if (dst == int.class || dst == Integer.class)
            return (int)(value);
        if (dst == long.class || dst == Long.class)
            return (long)(value);
        if (dst == char.class || dst == Character.class)
            return (char)(value);
        if (dst == short.class || dst == Short.class)
            return (short)(value);
        if (dst == float.class || dst == Float.class)
            return (float)(value);
        if (dst == double.class || dst == Double.class)
            return (double)(value);
        if (dst == byte.class || dst == Byte.class)
            return (byte)(value);
        if (dst == boolean.class || dst == boolean.class)
            return ((value % 29) & 1) == 0;
        return null;
    }

    static final int ONE_MILLION = (1000*1000),  // first int value
                     TEN_BILLION = (10*1000*1000*1000),  // scale factor to reach upper 32 bits
                     INITIAL_ARG_VAL = ONE_MILLION << 1;  // <<1 makes space for sign bit;
    static long nextArgVal;
    static long nextArg(boolean moreBits) {
        long val = nextArgVal++;
        long sign = -(val & 1); // alternate signs
        val >>= 1;
        if (moreBits)
            // Guarantee some bits in the high word.
            // In any case keep the decimal representation simple-looking,
            // with lots of zeroes, so as not to make the printed decimal
            // strings unnecessarily noisy.
            val += (val % ONE_MILLION) * TEN_BILLION;
        return val ^ sign;
    }
    static int nextArg() {
        // Produce a 32-bit result something like ONE_MILLION+(smallint).
        // Example: 1_000_042.
        return (int) nextArg(false);
    }
    static long nextArg(Class<?> kind) {
        if (kind == long.class   || kind == Long.class ||
            kind == double.class || kind == Double.class)
            // produce a 64-bit result something like
            // ((TEN_BILLION+1) * (ONE_MILLION+(smallint)))
            // Example: 10_000_420_001_000_042.
            return nextArg(true);
        return (long) nextArg();
    }

    static Object randomArg(Class<?> param) {
        Object wrap = castToWrapperOrNull(nextArg(param), param);
        if (wrap != null) {
            return wrap;
        }
//        import sun.invoke.util.Wrapper;
//        Wrapper wrap = Wrapper.forBasicType(dst);
//        if (wrap == Wrapper.OBJECT && Wrapper.isWrapperType(dst))
//            wrap = Wrapper.forWrapperType(dst);
//        if (wrap != Wrapper.OBJECT)
//            return wrap.wrap(nextArg++);
        if (param.isInterface()) {
            for (Class<?> c : param.getClasses()) {
                if (param.isAssignableFrom(c) && !c.isInterface())
                    { param = c; break; }
            }
        }
        if (param.isArray()) {
            Class<?> ctype = param.getComponentType();
            Object arg = Array.newInstance(ctype, 2);
            Array.set(arg, 0, randomArg(ctype));
            return arg;
        }
        if (param.isInterface() && param.isAssignableFrom(List.class))
            return Arrays.asList("#"+nextArg());
        if (param.isInterface() || param.isAssignableFrom(String.class))
            return "#"+nextArg();
        else
            try {
                return param.newInstance();
            } catch (InstantiationException | IllegalAccessException ex) {
            }
        return null;  // random class not Object, String, Integer, etc.
    }
    static Object[] randomArgs(Class<?>... params) {
        Object[] args = new Object[params.length];
        for (int i = 0; i < args.length; i++)
            args[i] = randomArg(params[i]);
        return args;
    }
    static Object[] randomArgs(int nargs, Class<?> param) {
        Object[] args = new Object[nargs];
        for (int i = 0; i < args.length; i++)
            args[i] = randomArg(param);
        return args;
    }

    @SafeVarargs @SuppressWarnings("varargs")
    static <T, E extends T> T[] array(Class<T[]> atype, E... a) {
        return Arrays.copyOf(a, a.length, atype);
    }
    @SafeVarargs @SuppressWarnings("varargs")
    static <T> T[] cat(T[] a, T... b) {
        int alen = a.length, blen = b.length;
        if (blen == 0)  return a;
        T[] c = Arrays.copyOf(a, alen + blen);
        System.arraycopy(b, 0, c, alen, blen);
        return c;
    }
    static Integer[] boxAll(int... vx) {
        Integer[] res = new Integer[vx.length];
        for (int i = 0; i < res.length; i++) {
            res[i] = vx[i];
        }
        return res;
    }
    static Object getClasses(Object x) {
        if (x == null)  return x;
        if (x instanceof String)  return x;  // keep the name
        if (x instanceof List) {
            // recursively report classes of the list elements
            Object[] xa = ((List)x).toArray();
            for (int i = 0; i < xa.length; i++)
                xa[i] = getClasses(xa[i]);
            return Arrays.asList(xa);
        }
        return x.getClass().getSimpleName();
    }

    /** Return lambda(arg...[arity]) { new Object[]{ arg... } } */
    static MethodHandle varargsList(int arity) {
        return ValueConversions.varargsList(arity);
    }
    /** Return lambda(arg...[arity]) { Arrays.asList(arg...) } */
    static MethodHandle varargsArray(int arity) {
        return ValueConversions.varargsArray(arity);
    }
    static MethodHandle varargsArray(Class<?> arrayType, int arity) {
        return ValueConversions.varargsArray(arrayType, arity);
    }
    /** Variation of varargsList, but with the given rtype. */
    static MethodHandle varargsList(int arity, Class<?> rtype) {
        MethodHandle list = varargsList(arity);
        MethodType listType = list.type().changeReturnType(rtype);
        if (List.class.isAssignableFrom(rtype) || rtype == void.class || rtype == Object.class) {
            // OK
        } else if (rtype.isAssignableFrom(String.class)) {
            if (LIST_TO_STRING == null)
                try {
                    LIST_TO_STRING = PRIVATE.findStatic(PRIVATE.lookupClass(), "listToString",
                                                        MethodType.methodType(String.class, List.class));
                } catch (NoSuchMethodException | IllegalAccessException ex) { throw new RuntimeException(ex); }
            list = MethodHandles.filterReturnValue(list, LIST_TO_STRING);
        } else if (rtype.isPrimitive()) {
            if (LIST_TO_INT == null)
                try {
                    LIST_TO_INT = PRIVATE.findStatic(PRIVATE.lookupClass(), "listToInt",
                                                     MethodType.methodType(int.class, List.class));
                } catch (NoSuchMethodException | IllegalAccessException ex) { throw new RuntimeException(ex); }
            list = MethodHandles.filterReturnValue(list, LIST_TO_INT);
            list = MethodHandles.explicitCastArguments(list, listType);
        } else {
            throw new RuntimeException("varargsList: "+rtype);
        }
        return list.asType(listType);
    }
    private static MethodHandle LIST_TO_STRING, LIST_TO_INT;
    private static String listToString(List<?> x) { return x.toString(); }
    private static int listToInt(List<?> x) { return x.toString().hashCode(); }

    static MethodHandle changeArgTypes(MethodHandle target, Class<?> argType) {
        return changeArgTypes(target, 0, 999, argType);
    }
    static MethodHandle changeArgTypes(MethodHandle target,
            int beg, int end, Class<?> argType) {
        MethodType targetType = target.type();
        end = Math.min(end, targetType.parameterCount());
        ArrayList<Class<?>> argTypes = new ArrayList<>(targetType.parameterList());
        Collections.fill(argTypes.subList(beg, end), argType);
        MethodType ttype2 = MethodType.methodType(targetType.returnType(), argTypes);
        return target.asType(ttype2);
    }
    static MethodHandle addTrailingArgs(MethodHandle target, int nargs, Class<?> argClass) {
        int targetLen = target.type().parameterCount();
        int extra = (nargs - targetLen);
        if (extra <= 0)  return target;
        List<Class<?>> fakeArgs = Collections.<Class<?>>nCopies(extra, argClass);
        return MethodHandles.dropArguments(target, targetLen, fakeArgs);
    }

    // This lookup is good for all members in and under MethodHandlesTest.
    static final Lookup PRIVATE = MethodHandles.lookup();
    // This lookup is good for package-private members but not private ones.
    static final Lookup PACKAGE = PackageSibling.lookup();
    // This lookup is good for public members and protected members of PubExample
    static final Lookup SUBCLASS = RemoteExample.lookup();
    // This lookup is good only for public members.
    static final Lookup PUBLIC  = MethodHandles.publicLookup();

    // Subject methods...
    static class Example implements IntExample {
        final String name;
        public Example() { name = "Example#"+nextArg(); }
        protected Example(String name) { this.name = name; }
        @SuppressWarnings("LeakingThisInConstructor")
        protected Example(int x) { this(); called("protected <init>", this, x); }
        @Override public String toString() { return name; }

        public void            v0()     { called("v0", this); }
        protected void         pro_v0() { called("pro_v0", this); }
        void                   pkg_v0() { called("pkg_v0", this); }
        private void           pri_v0() { called("pri_v0", this); }
        public static void     s0()     { called("s0"); }
        protected static void  pro_s0() { called("pro_s0"); }
        static void            pkg_s0() { called("pkg_s0"); }
        private static void    pri_s0() { called("pri_s0"); }

        public Object          v1(Object x) { return called("v1", this, x); }
        public Object          v2(Object x, Object y) { return called("v2", this, x, y); }
        public Object          v2(Object x, int    y) { return called("v2", this, x, y); }
        public Object          v2(int    x, Object y) { return called("v2", this, x, y); }
        public Object          v2(int    x, int    y) { return called("v2", this, x, y); }
        public static Object   s1(Object x) { return called("s1", x); }
        public static Object   s2(int x)    { return called("s2", x); }
        public static Object   s3(long x)   { return called("s3", x); }
        public static Object   s4(int x, int y) { return called("s4", x, y); }
        public static Object   s5(long x, int y) { return called("s5", x, y); }
        public static Object   s6(int x, long y) { return called("s6", x, y); }
        public static Object   s7(float x, double y) { return called("s7", x, y); }

        // for testing findConstructor:
        public Example(String x, int y) { this.name = x+y; called("Example.<init>", x, y); }
        public Example(int x, String y) { this.name = x+y; called("Example.<init>", x, y); }
        public Example(int x, int    y) { this.name = x+""+y; called("Example.<init>", x, y); }
        public Example(int x, long   y) { this.name = x+""+y; called("Example.<init>", x, y); }
        public Example(int x, float  y) { this.name = x+""+y; called("Example.<init>", x, y); }
        public Example(int x, double y) { this.name = x+""+y; called("Example.<init>", x, y); }
        public Example(int x, int    y, int z) { this.name = x+""+y+""+z; called("Example.<init>", x, y, z); }
        public Example(int x, int    y, int z, int a) { this.name = x+""+y+""+z+""+a; called("Example.<init>", x, y, z, a); }

        static final Lookup EXAMPLE = MethodHandles.lookup();  // for testing findSpecial
    }
    static final Lookup EXAMPLE = Example.EXAMPLE;
    public static class PubExample extends Example {
        public PubExample() { this("PubExample"); }
        protected PubExample(String prefix) { super(prefix+"#"+nextArg()); }
        protected void         pro_v0() { called("Pub/pro_v0", this); }
        protected static void  pro_s0() { called("Pub/pro_s0"); }
    }
    static class SubExample extends Example {
        @Override public void  v0()     { called("Sub/v0", this); }
        @Override void         pkg_v0() { called("Sub/pkg_v0", this); }
        @SuppressWarnings("LeakingThisInConstructor")
        private      SubExample(int x)  { called("<init>", this, x); }
        public SubExample() { super("SubExample#"+nextArg()); }
    }
    public static interface IntExample {
        public void            v0();
        public static class Impl implements IntExample {
            public void        v0()     { called("Int/v0", this); }
            final String name;
            public Impl() { name = "Impl#"+nextArg(); }
            @Override public String toString() { return name; }
        }
    }
    static interface SubIntExample extends IntExample { }

    static final Object[][][] ACCESS_CASES = {
        { { false, PUBLIC }, { false, SUBCLASS }, { false, PACKAGE }, { false, PRIVATE }, { false, EXAMPLE } }, //[0]: all false
        { { false, PUBLIC }, { false, SUBCLASS }, { false, PACKAGE }, { true,  PRIVATE }, { true,  EXAMPLE } }, //[1]: only PRIVATE
        { { false, PUBLIC }, { false, SUBCLASS }, { true,  PACKAGE }, { true,  PRIVATE }, { true,  EXAMPLE } }, //[2]: PUBLIC false
        { { false, PUBLIC }, { true,  SUBCLASS }, { true,  PACKAGE }, { true,  PRIVATE }, { true,  EXAMPLE } }, //[3]: subclass OK
        { { true,  PUBLIC }, { true,  SUBCLASS }, { true,  PACKAGE }, { true,  PRIVATE }, { true,  EXAMPLE } }, //[4]: all true
    };

    static Object[][] accessCases(Class<?> defc, String name, boolean isSpecial) {
        Object[][] cases;
        if (name.contains("pri_") || isSpecial) {
            cases = ACCESS_CASES[1]; // PRIVATE only
        } else if (name.contains("pkg_") || !Modifier.isPublic(defc.getModifiers())) {
            cases = ACCESS_CASES[2]; // not PUBLIC
        } else if (name.contains("pro_")) {
            cases = ACCESS_CASES[3]; // PUBLIC class, protected member
        } else {
            assertTrue(name.indexOf('_') < 0 || name.contains("fin_"));
            boolean pubc = Modifier.isPublic(defc.getModifiers());
            if (pubc)
                cases = ACCESS_CASES[4]; // all access levels
            else
                cases = ACCESS_CASES[2]; // PACKAGE but not PUBLIC
        }
        if (defc != Example.class && cases[cases.length-1][1] == EXAMPLE)
            cases = Arrays.copyOfRange(cases, 0, cases.length-1);
        return cases;
    }
    static Object[][] accessCases(Class<?> defc, String name) {
        return accessCases(defc, name, false);
    }

    static Lookup maybeMoveIn(Lookup lookup, Class<?> defc) {
        if (lookup == PUBLIC || lookup == SUBCLASS || lookup == PACKAGE)
            // external views stay external
            return lookup;
        return lookup.in(defc);
    }

    @Test
    public void testFindStatic() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findStatic");
        testFindStatic(PubExample.class, void.class, "s0");
        testFindStatic(Example.class, void.class, "s0");
        testFindStatic(Example.class, void.class, "pkg_s0");
        testFindStatic(Example.class, void.class, "pri_s0");
        testFindStatic(Example.class, void.class, "pro_s0");
        testFindStatic(PubExample.class, void.class, "Pub/pro_s0");

        testFindStatic(Example.class, Object.class, "s1", Object.class);
        testFindStatic(Example.class, Object.class, "s2", int.class);
        testFindStatic(Example.class, Object.class, "s3", long.class);
        testFindStatic(Example.class, Object.class, "s4", int.class, int.class);
        testFindStatic(Example.class, Object.class, "s5", long.class, int.class);
        testFindStatic(Example.class, Object.class, "s6", int.class, long.class);
        testFindStatic(Example.class, Object.class, "s7", float.class, double.class);

        testFindStatic(false, PRIVATE, Example.class, void.class, "bogus");
        testFindStatic(false, PRIVATE, Example.class, void.class, "v0");
    }

    void testFindStatic(Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        for (Object[] ac : accessCases(defc, name)) {
            testFindStatic((Boolean)ac[0], (Lookup)ac[1], defc, ret, name, params);
        }
    }
    void testFindStatic(Lookup lookup, Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        testFindStatic(true, lookup, defc, ret, name, params);
    }
    void testFindStatic(boolean positive, Lookup lookup, Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        String methodName = name.substring(1 + name.indexOf('/'));  // foo/bar => foo
        MethodType type = MethodType.methodType(ret, params);
        MethodHandle target = null;
        Exception noAccess = null;
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            target = maybeMoveIn(lookup, defc).findStatic(defc, methodName, type);
        } catch (ReflectiveOperationException ex) {
            noAccess = ex;
            if (verbosity >= 5)  ex.printStackTrace(System.out);
            if (name.contains("bogus"))
                assertTrue(noAccess instanceof NoSuchMethodException);
            else
                assertTrue(noAccess instanceof IllegalAccessException);
        }
        if (verbosity >= 3)
            System.out.println("findStatic "+lookup+": "+defc.getName()+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(type, target.type());
        assertNameStringContains(target, methodName);
        Object[] args = randomArgs(params);
        printCalled(target, name, args);
        target.invokeWithArguments(args);
        assertCalled(name, args);
        if (verbosity >= 1)
            System.out.print(':');
    }

    static final boolean DEBUG_METHOD_HANDLE_NAMES = Boolean.getBoolean("java.lang.invoke.MethodHandle.DEBUG_NAMES");

    // rough check of name string
    static void assertNameStringContains(MethodHandle x, String s) {
        if (!DEBUG_METHOD_HANDLE_NAMES) {
            // ignore s
            assertEquals("MethodHandle"+x.type(), x.toString());
            return;
        }
        if (x.toString().contains(s))  return;
        assertEquals(s, x);
    }

    @Test
    public void testFindVirtual() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findVirtual");
        testFindVirtual(Example.class, void.class, "v0");
        testFindVirtual(Example.class, void.class, "pkg_v0");
        testFindVirtual(Example.class, void.class, "pri_v0");
        testFindVirtual(Example.class, Object.class, "v1", Object.class);
        testFindVirtual(Example.class, Object.class, "v2", Object.class, Object.class);
        testFindVirtual(Example.class, Object.class, "v2", Object.class, int.class);
        testFindVirtual(Example.class, Object.class, "v2", int.class, Object.class);
        testFindVirtual(Example.class, Object.class, "v2", int.class, int.class);
        testFindVirtual(Example.class, void.class, "pro_v0");
        testFindVirtual(PubExample.class, void.class, "Pub/pro_v0");

        testFindVirtual(false, PRIVATE, Example.class, Example.class, void.class, "bogus");
        testFindVirtual(false, PRIVATE, Example.class, Example.class, void.class, "s0");

        // test dispatch
        testFindVirtual(SubExample.class,      SubExample.class, void.class, "Sub/v0");
        testFindVirtual(SubExample.class,         Example.class, void.class, "Sub/v0");
        testFindVirtual(SubExample.class,      IntExample.class, void.class, "Sub/v0");
        testFindVirtual(SubExample.class,      SubExample.class, void.class, "Sub/pkg_v0");
        testFindVirtual(SubExample.class,         Example.class, void.class, "Sub/pkg_v0");
        testFindVirtual(Example.class,         IntExample.class, void.class, "v0");
        testFindVirtual(IntExample.Impl.class, IntExample.class, void.class, "Int/v0");
    }

    @Test
    public void testFindVirtualClone() throws Throwable {
        // test some ad hoc system methods
        testFindVirtual(false, PUBLIC, Object.class, Object.class, "clone");
        testFindVirtual(true, PUBLIC, Object[].class, Object.class, "clone");
        testFindVirtual(true, PUBLIC, int[].class, Object.class, "clone");
        for (Class<?> cls : new Class<?>[]{ boolean[].class, long[].class, float[].class, char[].class })
            testFindVirtual(true, PUBLIC, cls, Object.class, "clone");
    }

    void testFindVirtual(Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        Class<?> rcvc = defc;
        testFindVirtual(rcvc, defc, ret, name, params);
    }
    void testFindVirtual(Class<?> rcvc, Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        for (Object[] ac : accessCases(defc, name)) {
            testFindVirtual((Boolean)ac[0], (Lookup)ac[1], rcvc, defc, ret, name, params);
        }
    }
    void testFindVirtual(Lookup lookup, Class<?> rcvc, Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        testFindVirtual(true, lookup, rcvc, defc, ret, name, params);
    }
    void testFindVirtual(boolean positive, Lookup lookup, Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        testFindVirtual(positive, lookup, defc, defc, ret, name, params);
    }
    void testFindVirtual(boolean positive, Lookup lookup, Class<?> rcvc, Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        String methodName = name.substring(1 + name.indexOf('/'));  // foo/bar => foo
        MethodType type = MethodType.methodType(ret, params);
        MethodHandle target = null;
        Exception noAccess = null;
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            target = maybeMoveIn(lookup, defc).findVirtual(defc, methodName, type);
        } catch (ReflectiveOperationException ex) {
            noAccess = ex;
            if (verbosity >= 5)  ex.printStackTrace(System.out);
            if (name.contains("bogus"))
                assertTrue(noAccess instanceof NoSuchMethodException);
            else
                assertTrue(noAccess instanceof IllegalAccessException);
        }
        if (verbosity >= 3)
            System.out.println("findVirtual "+lookup+": "+defc.getName()+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        Class<?> selfc = defc;
        // predict receiver type narrowing:
        if (lookup == SUBCLASS &&
                name.contains("pro_") &&
                selfc.isAssignableFrom(lookup.lookupClass())) {
            selfc = lookup.lookupClass();
            if (name.startsWith("Pub/"))  name = "Rem/"+name.substring(4);
        }
        Class<?>[] paramsWithSelf = cat(array(Class[].class, (Class)selfc), params);
        MethodType typeWithSelf = MethodType.methodType(ret, paramsWithSelf);
        assertEquals(typeWithSelf, target.type());
        assertNameStringContains(target, methodName);
        Object[] argsWithSelf = randomArgs(paramsWithSelf);
        if (selfc.isAssignableFrom(rcvc) && rcvc != selfc)  argsWithSelf[0] = randomArg(rcvc);
        printCalled(target, name, argsWithSelf);
        Object res = target.invokeWithArguments(argsWithSelf);
        if (Example.class.isAssignableFrom(defc) || IntExample.class.isAssignableFrom(defc)) {
            assertCalled(name, argsWithSelf);
        } else if (name.equals("clone")) {
            // Ad hoc method call outside Example.  For Object[].clone.
            printCalled(target, name, argsWithSelf);
            assertEquals(MethodType.methodType(Object.class, rcvc), target.type());
            Object orig = argsWithSelf[0];
            assertEquals(orig.getClass(), res.getClass());
            if (res instanceof Object[])
                assertArrayEquals((Object[])res, (Object[])argsWithSelf[0]);
            assert(Arrays.deepEquals(new Object[]{res}, new Object[]{argsWithSelf[0]}));
        } else {
            assert(false) : Arrays.asList(positive, lookup, rcvc, defc, ret, name, deepToString(params));
        }
        if (verbosity >= 1)
            System.out.print(':');
    }

    @Test
    public void testFindSpecial() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findSpecial");
        testFindSpecial(SubExample.class, Example.class, void.class, "v0");
        testFindSpecial(SubExample.class, Example.class, void.class, "pkg_v0");
        testFindSpecial(RemoteExample.class, PubExample.class, void.class, "Pub/pro_v0");
        // Do some negative testing:
        testFindSpecial(false, EXAMPLE, SubExample.class, Example.class, void.class, "bogus");
        testFindSpecial(false, PRIVATE, SubExample.class, Example.class, void.class, "bogus");
        for (Lookup lookup : new Lookup[]{ PRIVATE, EXAMPLE, PACKAGE, PUBLIC }) {
            testFindSpecial(false, lookup, Object.class, Example.class, void.class, "v0");
            testFindSpecial(false, lookup, SubExample.class, Example.class, void.class, "<init>", int.class);
            testFindSpecial(false, lookup, SubExample.class, Example.class, void.class, "s0");
        }
    }

    void testFindSpecial(Class<?> specialCaller,
                         Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        if (specialCaller == RemoteExample.class) {
            testFindSpecial(false, EXAMPLE,  specialCaller, defc, ret, name, params);
            testFindSpecial(false, PRIVATE,  specialCaller, defc, ret, name, params);
            testFindSpecial(false, PACKAGE,  specialCaller, defc, ret, name, params);
            testFindSpecial(true,  SUBCLASS, specialCaller, defc, ret, name, params);
            testFindSpecial(false, PUBLIC,   specialCaller, defc, ret, name, params);
            return;
        }
        testFindSpecial(true,  EXAMPLE,  specialCaller, defc, ret, name, params);
        testFindSpecial(true,  PRIVATE,  specialCaller, defc, ret, name, params);
        testFindSpecial(false, PACKAGE,  specialCaller, defc, ret, name, params);
        testFindSpecial(false, SUBCLASS, specialCaller, defc, ret, name, params);
        testFindSpecial(false, PUBLIC,   specialCaller, defc, ret, name, params);
    }
    void testFindSpecial(boolean positive, Lookup lookup, Class<?> specialCaller,
                         Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        String methodName = name.substring(1 + name.indexOf('/'));  // foo/bar => foo
        MethodType type = MethodType.methodType(ret, params);
        MethodHandle target = null;
        Exception noAccess = null;
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            if (verbosity >= 5)  System.out.println("  lookup => "+maybeMoveIn(lookup, specialCaller));
            target = maybeMoveIn(lookup, specialCaller).findSpecial(defc, methodName, type, specialCaller);
        } catch (ReflectiveOperationException ex) {
            noAccess = ex;
            if (verbosity >= 5)  ex.printStackTrace(System.out);
            if (name.contains("bogus"))
                assertTrue(noAccess instanceof NoSuchMethodException);
            else
                assertTrue(noAccess instanceof IllegalAccessException);
        }
        if (verbosity >= 3)
            System.out.println("findSpecial from "+specialCaller.getName()+" to "+defc.getName()+"."+name+"/"+type+" => "+target
                               +(target == null ? "" : target.type())
                               +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(specialCaller, target.type().parameterType(0));
        assertEquals(type,          target.type().dropParameterTypes(0,1));
        Class<?>[] paramsWithSelf = cat(array(Class[].class, (Class)specialCaller), params);
        MethodType typeWithSelf = MethodType.methodType(ret, paramsWithSelf);
        assertNameStringContains(target, methodName);
        Object[] args = randomArgs(paramsWithSelf);
        printCalled(target, name, args);
        target.invokeWithArguments(args);
        assertCalled(name, args);
    }

    @Test
    public void testFindConstructor() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findConstructor");
        testFindConstructor(true, EXAMPLE, Example.class);
        testFindConstructor(true, EXAMPLE, Example.class, int.class);
        testFindConstructor(true, EXAMPLE, Example.class, int.class, int.class);
        testFindConstructor(true, EXAMPLE, Example.class, int.class, long.class);
        testFindConstructor(true, EXAMPLE, Example.class, int.class, float.class);
        testFindConstructor(true, EXAMPLE, Example.class, int.class, double.class);
        testFindConstructor(true, EXAMPLE, Example.class, String.class);
        testFindConstructor(true, EXAMPLE, Example.class, int.class, int.class, int.class);
        testFindConstructor(true, EXAMPLE, Example.class, int.class, int.class, int.class, int.class);
    }
    void testFindConstructor(boolean positive, Lookup lookup,
                             Class<?> defc, Class<?>... params) throws Throwable {
        countTest(positive);
        MethodType type = MethodType.methodType(void.class, params);
        MethodHandle target = null;
        Exception noAccess = null;
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" <init>"+type);
            target = lookup.findConstructor(defc, type);
        } catch (ReflectiveOperationException ex) {
            noAccess = ex;
            assertTrue(noAccess instanceof IllegalAccessException);
        }
        if (verbosity >= 3)
            System.out.println("findConstructor "+defc.getName()+".<init>/"+type+" => "+target
                               +(target == null ? "" : target.type())
                               +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(type.changeReturnType(defc), target.type());
        Object[] args = randomArgs(params);
        printCalled(target, defc.getSimpleName(), args);
        Object obj = target.invokeWithArguments(args);
        if (!(defc == Example.class && params.length < 2))
            assertCalled(defc.getSimpleName()+".<init>", args);
        assertTrue("instance of "+defc.getName(), defc.isInstance(obj));
    }

    @Test
    public void testBind() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("bind");
        testBind(Example.class, void.class, "v0");
        testBind(Example.class, void.class, "pkg_v0");
        testBind(Example.class, void.class, "pri_v0");
        testBind(Example.class, Object.class, "v1", Object.class);
        testBind(Example.class, Object.class, "v2", Object.class, Object.class);
        testBind(Example.class, Object.class, "v2", Object.class, int.class);
        testBind(Example.class, Object.class, "v2", int.class, Object.class);
        testBind(Example.class, Object.class, "v2", int.class, int.class);
        testBind(false, PRIVATE, Example.class, void.class, "bogus");
        testBind(SubExample.class, void.class, "Sub/v0");
        testBind(SubExample.class, void.class, "Sub/pkg_v0");
        testBind(IntExample.Impl.class, void.class, "Int/v0");
    }

    void testBind(Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        for (Object[] ac : accessCases(defc, name)) {
            testBind((Boolean)ac[0], (Lookup)ac[1], defc, ret, name, params);
        }
    }

    void testBind(boolean positive, Lookup lookup, Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        String methodName = name.substring(1 + name.indexOf('/'));  // foo/bar => foo
        MethodType type = MethodType.methodType(ret, params);
        Object receiver = randomArg(defc);
        MethodHandle target = null;
        Exception noAccess = null;
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            target = maybeMoveIn(lookup, defc).bind(receiver, methodName, type);
        } catch (ReflectiveOperationException ex) {
            noAccess = ex;
            if (verbosity >= 5)  ex.printStackTrace(System.out);
            if (name.contains("bogus"))
                assertTrue(noAccess instanceof NoSuchMethodException);
            else
                assertTrue(noAccess instanceof IllegalAccessException);
        }
        if (verbosity >= 3)
            System.out.println("bind "+receiver+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(type, target.type());
        Object[] args = randomArgs(params);
        printCalled(target, name, args);
        target.invokeWithArguments(args);
        Object[] argsWithReceiver = cat(array(Object[].class, receiver), args);
        assertCalled(name, argsWithReceiver);
        if (verbosity >= 1)
            System.out.print(':');
    }

    @Test
    public void testUnreflect() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("unreflect");
        testUnreflect(Example.class, true, void.class, "s0");
        testUnreflect(Example.class, true, void.class, "pro_s0");
        testUnreflect(Example.class, true, void.class, "pkg_s0");
        testUnreflect(Example.class, true, void.class, "pri_s0");

        testUnreflect(Example.class, true, Object.class, "s1", Object.class);
        testUnreflect(Example.class, true, Object.class, "s2", int.class);
        testUnreflect(Example.class, true, Object.class, "s3", long.class);
        testUnreflect(Example.class, true, Object.class, "s4", int.class, int.class);
        testUnreflect(Example.class, true, Object.class, "s5", long.class, int.class);
        testUnreflect(Example.class, true, Object.class, "s6", int.class, long.class);

        testUnreflect(Example.class, false, void.class, "v0");
        testUnreflect(Example.class, false, void.class, "pkg_v0");
        testUnreflect(Example.class, false, void.class, "pri_v0");
        testUnreflect(Example.class, false, Object.class, "v1", Object.class);
        testUnreflect(Example.class, false, Object.class, "v2", Object.class, Object.class);
        testUnreflect(Example.class, false, Object.class, "v2", Object.class, int.class);
        testUnreflect(Example.class, false, Object.class, "v2", int.class, Object.class);
        testUnreflect(Example.class, false, Object.class, "v2", int.class, int.class);

        // Test a public final member in another package:
        testUnreflect(RemoteExample.class, false, void.class, "Rem/fin_v0");
    }

    void testUnreflect(Class<?> defc, boolean isStatic, Class<?> ret, String name, Class<?>... params) throws Throwable {
        for (Object[] ac : accessCases(defc, name)) {
            testUnreflectMaybeSpecial(null, (Boolean)ac[0], (Lookup)ac[1], defc, (isStatic ? null : defc), ret, name, params);
        }
    }
    void testUnreflect(Class<?> defc, Class<?> rcvc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        for (Object[] ac : accessCases(defc, name)) {
            testUnreflectMaybeSpecial(null, (Boolean)ac[0], (Lookup)ac[1], defc, rcvc, ret, name, params);
        }
    }
    void testUnreflectMaybeSpecial(Class<?> specialCaller,
                                   boolean positive, Lookup lookup,
                                   Class<?> defc, Class<?> rcvc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        String methodName = name.substring(1 + name.indexOf('/'));  // foo/bar => foo
        MethodType type = MethodType.methodType(ret, params);
        Method rmethod = defc.getDeclaredMethod(methodName, params);
        MethodHandle target = null;
        Exception noAccess = null;
        boolean isStatic = (rcvc == null);
        boolean isSpecial = (specialCaller != null);
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            if (isSpecial)
                target = maybeMoveIn(lookup, specialCaller).unreflectSpecial(rmethod, specialCaller);
            else
                target = maybeMoveIn(lookup, defc).unreflect(rmethod);
        } catch (ReflectiveOperationException ex) {
            noAccess = ex;
            if (verbosity >= 5)  ex.printStackTrace(System.out);
            if (name.contains("bogus"))
                assertTrue(noAccess instanceof NoSuchMethodException);
            else
                assertTrue(noAccess instanceof IllegalAccessException);
        }
        if (verbosity >= 3)
            System.out.println("unreflect"+(isSpecial?"Special":"")+" "+defc.getName()+"."+name+"/"+type
                               +(!isSpecial ? "" : " specialCaller="+specialCaller)
                               +( isStatic  ? "" : " receiver="+rcvc)
                               +" => "+target
                               +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(isStatic, Modifier.isStatic(rmethod.getModifiers()));
        Class<?>[] paramsMaybeWithSelf = params;
        if (!isStatic) {
            paramsMaybeWithSelf = cat(array(Class[].class, (Class)rcvc), params);
        }
        MethodType typeMaybeWithSelf = MethodType.methodType(ret, paramsMaybeWithSelf);
        if (isStatic) {
            assertEquals(typeMaybeWithSelf, target.type());
        } else {
            if (isSpecial)
                assertEquals(specialCaller, target.type().parameterType(0));
            else
                assertEquals(defc, target.type().parameterType(0));
            assertEquals(typeMaybeWithSelf, target.type().changeParameterType(0, rcvc));
        }
        Object[] argsMaybeWithSelf = randomArgs(paramsMaybeWithSelf);
        printCalled(target, name, argsMaybeWithSelf);
        target.invokeWithArguments(argsMaybeWithSelf);
        assertCalled(name, argsMaybeWithSelf);
        if (verbosity >= 1)
            System.out.print(':');
    }

    void testUnreflectSpecial(Class<?> defc, Class<?> rcvc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        for (Object[] ac : accessCases(defc, name, true)) {
            Class<?> specialCaller = rcvc;
            testUnreflectMaybeSpecial(specialCaller, (Boolean)ac[0], (Lookup)ac[1], defc, rcvc, ret, name, params);
        }
    }

    @Test
    public void testUnreflectSpecial() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("unreflectSpecial");
        testUnreflectSpecial(Example.class,    Example.class, void.class, "v0");
        testUnreflectSpecial(Example.class, SubExample.class, void.class, "v0");
        testUnreflectSpecial(Example.class,    Example.class, void.class, "pkg_v0");
        testUnreflectSpecial(Example.class, SubExample.class, void.class, "pkg_v0");
        testUnreflectSpecial(Example.class,    Example.class, Object.class, "v2", int.class, int.class);
        testUnreflectSpecial(Example.class, SubExample.class, Object.class, "v2", int.class, int.class);
        testUnreflectMaybeSpecial(Example.class, false, PRIVATE, Example.class, Example.class, void.class, "s0");
    }

    public static class HasFields {
        boolean fZ = false;
        byte fB = (byte)'B';
        short fS = (short)'S';
        char fC = 'C';
        int fI = 'I';
        long fJ = 'J';
        float fF = 'F';
        double fD = 'D';
        static boolean sZ = true;
        static byte sB = 1+(byte)'B';
        static short sS = 1+(short)'S';
        static char sC = 1+'C';
        static int sI = 1+'I';
        static long sJ = 1+'J';
        static float sF = 1+'F';
        static double sD = 1+'D';

        Object fL = 'L';
        String fR = "R";
        static Object sL = 'M';
        static String sR = "S";

        static final Object[][] CASES;
        static {
            ArrayList<Object[]> cases = new ArrayList<>();
            Object types[][] = {
                {'L',Object.class}, {'R',String.class},
                {'I',int.class}, {'J',long.class},
                {'F',float.class}, {'D',double.class},
                {'Z',boolean.class}, {'B',byte.class},
                {'S',short.class}, {'C',char.class},
            };
            HasFields fields = new HasFields();
            for (Object[] t : types) {
                for (int kind = 0; kind <= 1; kind++) {
                    boolean isStatic = (kind != 0);
                    char btc = (Character)t[0];
                    String name = (isStatic ? "s" : "f") + btc;
                    Class<?> type = (Class<?>) t[1];
                    Object value;
                    Field field;
                        try {
                        field = HasFields.class.getDeclaredField(name);
                    } catch (NoSuchFieldException | SecurityException ex) {
                        throw new InternalError("no field HasFields."+name);
                    }
                    try {
                        value = field.get(fields);
                    } catch (IllegalArgumentException | IllegalAccessException ex) {
                        throw new InternalError("cannot fetch field HasFields."+name);
                    }
                    if (type == float.class) {
                        float v = 'F';
                        if (isStatic)  v++;
                        assertTrue(value.equals(v));
                    }
                    assertTrue(name.equals(field.getName()));
                    assertTrue(type.equals(field.getType()));
                    assertTrue(isStatic == (Modifier.isStatic(field.getModifiers())));
                    cases.add(new Object[]{ field, value });
                }
            }
            cases.add(new Object[]{ new Object[]{ false, HasFields.class, "bogus_fD", double.class }, Error.class });
            cases.add(new Object[]{ new Object[]{ true,  HasFields.class, "bogus_sL", Object.class }, Error.class });
            CASES = cases.toArray(new Object[0][]);
        }
    }

    static final int TEST_UNREFLECT = 1, TEST_FIND_FIELD = 2, TEST_FIND_STATIC = 3, TEST_SETTER = 0x10, TEST_BOUND = 0x20, TEST_NPE = 0x40;
    static boolean testModeMatches(int testMode, boolean isStatic) {
        switch (testMode) {
        case TEST_FIND_STATIC:          return isStatic;
        case TEST_FIND_FIELD:           return !isStatic;
        case TEST_UNREFLECT:            return true;  // unreflect matches both
        }
        throw new InternalError("testMode="+testMode);
    }

    @Test
    public void testUnreflectGetter() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("unreflectGetter");
        testGetter(TEST_UNREFLECT);
    }
    @Test
    public void testFindGetter() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findGetter");
        testGetter(TEST_FIND_FIELD);
        testGetter(TEST_FIND_FIELD | TEST_BOUND);
    }
    @Test
    public void testFindStaticGetter() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findStaticGetter");
        testGetter(TEST_FIND_STATIC);
    }
    public void testGetter(int testMode) throws Throwable {
        Lookup lookup = PRIVATE;  // FIXME: test more lookups than this one
        for (Object[] c : HasFields.CASES) {
            boolean positive = (c[1] != Error.class);
            testGetter(positive, lookup, c[0], c[1], testMode);
            if (positive)
                testGetter(positive, lookup, c[0], c[1], testMode | TEST_NPE);
        }
        testGetter(true, lookup,
                   new Object[]{ true,  System.class, "out", java.io.PrintStream.class },
                   System.out, testMode);
        for (int isStaticN = 0; isStaticN <= 1; isStaticN++) {
            testGetter(false, lookup,
                       new Object[]{ (isStaticN != 0), System.class, "bogus", char.class },
                       null, testMode);
        }
    }
    public void testGetter(boolean positive, MethodHandles.Lookup lookup,
                           Object fieldRef, Object value, int testMode) throws Throwable {
        testAccessor(positive, lookup, fieldRef, value, testMode);
    }

    public void testAccessor(boolean positive0, MethodHandles.Lookup lookup,
                             Object fieldRef, Object value, int testMode0) throws Throwable {
        if (verbosity >= 4)
            System.out.println("testAccessor"+Arrays.deepToString(new Object[]{positive0, lookup, fieldRef, value, testMode0}));
        boolean isGetter = ((testMode0 & TEST_SETTER) == 0);
        boolean doBound  = ((testMode0 & TEST_BOUND) != 0);
        boolean testNPE  = ((testMode0 & TEST_NPE) != 0);
        int testMode = testMode0 & ~(TEST_SETTER | TEST_BOUND | TEST_NPE);
        boolean positive = positive0 && !testNPE;
        boolean isStatic;
        Class<?> fclass;
        String   fname;
        Class<?> ftype;
        Field f = (fieldRef instanceof Field ? (Field)fieldRef : null);
        if (f != null) {
            isStatic = Modifier.isStatic(f.getModifiers());
            fclass   = f.getDeclaringClass();
            fname    = f.getName();
            ftype    = f.getType();
        } else {
            Object[] scnt = (Object[]) fieldRef;
            isStatic = (Boolean)  scnt[0];
            fclass   = (Class<?>) scnt[1];
            fname    = (String)   scnt[2];
            ftype    = (Class<?>) scnt[3];
            try {
                f = fclass.getDeclaredField(fname);
            } catch (ReflectiveOperationException ex) {
                f = null;
            }
        }
        if (!testModeMatches(testMode, isStatic))  return;
        if (f == null && testMode == TEST_UNREFLECT)  return;
        if (testNPE && isStatic)  return;
        countTest(positive);
        MethodType expType;
        if (isGetter)
            expType = MethodType.methodType(ftype, HasFields.class);
        else
            expType = MethodType.methodType(void.class, HasFields.class, ftype);
        if (isStatic)  expType = expType.dropParameterTypes(0, 1);
        Exception noAccess = null;
        MethodHandle mh;
        try {
            switch (testMode0 & ~(TEST_BOUND | TEST_NPE)) {
            case TEST_UNREFLECT:   mh = lookup.unreflectGetter(f);                      break;
            case TEST_FIND_FIELD:  mh = lookup.findGetter(fclass, fname, ftype);        break;
            case TEST_FIND_STATIC: mh = lookup.findStaticGetter(fclass, fname, ftype);  break;
            case TEST_SETTER|
                 TEST_UNREFLECT:   mh = lookup.unreflectSetter(f);                      break;
            case TEST_SETTER|
                 TEST_FIND_FIELD:  mh = lookup.findSetter(fclass, fname, ftype);        break;
            case TEST_SETTER|
                 TEST_FIND_STATIC: mh = lookup.findStaticSetter(fclass, fname, ftype);  break;
            default:
                throw new InternalError("testMode="+testMode);
            }
        } catch (ReflectiveOperationException ex) {
            mh = null;
            noAccess = ex;
            if (verbosity >= 5)  ex.printStackTrace(System.out);
            if (fname.contains("bogus"))
                assertTrue(noAccess instanceof NoSuchFieldException);
            else
                assertTrue(noAccess instanceof IllegalAccessException);
        }
        if (verbosity >= 3)
            System.out.println("find"+(isStatic?"Static":"")+(isGetter?"Getter":"Setter")+" "+fclass.getName()+"."+fname+"/"+ftype
                               +" => "+mh
                               +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && !testNPE && noAccess != null)  throw new RuntimeException(noAccess);
        assertEquals(positive0 ? "positive test" : "negative test erroneously passed", positive0, mh != null);
        if (!positive && !testNPE)  return; // negative access test failed as expected
        assertEquals((isStatic ? 0 : 1)+(isGetter ? 0 : 1), mh.type().parameterCount());


        assertSame(mh.type(), expType);
        //assertNameStringContains(mh, fname);  // This does not hold anymore with LFs
        HasFields fields = new HasFields();
        HasFields fieldsForMH = fields;
        if (testNPE)  fieldsForMH = null;  // perturb MH argument to elicit expected error
        if (doBound)
            mh = mh.bindTo(fieldsForMH);
        Object sawValue;
        Class<?> vtype = ftype;
        if (ftype != int.class)  vtype = Object.class;
        if (isGetter) {
            mh = mh.asType(mh.type().generic()
                           .changeReturnType(vtype));
        } else {
            int last = mh.type().parameterCount() - 1;
            mh = mh.asType(mh.type().generic()
                           .changeReturnType(void.class)
                           .changeParameterType(last, vtype));
        }
        if (f != null && f.getDeclaringClass() == HasFields.class) {
            assertEquals(f.get(fields), value);  // clean to start with
        }
        Throwable caughtEx = null;
        if (isGetter) {
            Object expValue = value;
            for (int i = 0; i <= 1; i++) {
                sawValue = null;  // make DA rules happy under try/catch
                try {
                    if (isStatic || doBound) {
                        if (ftype == int.class)
                            sawValue = (int) mh.invokeExact();  // do these exactly
                        else
                            sawValue = mh.invokeExact();
                    } else {
                        if (ftype == int.class)
                            sawValue = (int) mh.invokeExact((Object) fieldsForMH);
                        else
                            sawValue = mh.invokeExact((Object) fieldsForMH);
                    }
                } catch (RuntimeException ex) {
                    if (ex instanceof NullPointerException && testNPE) {
                        caughtEx = ex;
                        break;
                    }
                }
                assertEquals(sawValue, expValue);
                if (f != null && f.getDeclaringClass() == HasFields.class
                    && !Modifier.isFinal(f.getModifiers())) {
                    Object random = randomArg(ftype);
                    f.set(fields, random);
                    expValue = random;
                } else {
                    break;
                }
            }
        } else {
            for (int i = 0; i <= 1; i++) {
                Object putValue = randomArg(ftype);
                try {
                    if (isStatic || doBound) {
                        if (ftype == int.class)
                            mh.invokeExact((int)putValue);  // do these exactly
                        else
                            mh.invokeExact(putValue);
                    } else {
                        if (ftype == int.class)
                            mh.invokeExact((Object) fieldsForMH, (int)putValue);
                        else
                            mh.invokeExact((Object) fieldsForMH, putValue);
                    }
                } catch (RuntimeException ex) {
                    if (ex instanceof NullPointerException && testNPE) {
                        caughtEx = ex;
                        break;
                    }
                }
                if (f != null && f.getDeclaringClass() == HasFields.class) {
                    assertEquals(f.get(fields), putValue);
                }
            }
        }
        if (f != null && f.getDeclaringClass() == HasFields.class) {
            f.set(fields, value);  // put it back
        }
        if (testNPE) {
            if (caughtEx == null || !(caughtEx instanceof NullPointerException))
                throw new RuntimeException("failed to catch NPE exception"+(caughtEx == null ? " (caughtEx=null)" : ""), caughtEx);
            caughtEx = null;  // nullify expected exception
        }
        if (caughtEx != null) {
            throw new RuntimeException("unexpected exception", caughtEx);
        }
    }


    @Test
    public void testUnreflectSetter() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("unreflectSetter");
        testSetter(TEST_UNREFLECT);
    }
    @Test
    public void testFindSetter() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findSetter");
        testSetter(TEST_FIND_FIELD);
        testSetter(TEST_FIND_FIELD | TEST_BOUND);
    }
    @Test
    public void testFindStaticSetter() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findStaticSetter");
        testSetter(TEST_FIND_STATIC);
    }
    public void testSetter(int testMode) throws Throwable {
        Lookup lookup = PRIVATE;  // FIXME: test more lookups than this one
        startTest("unreflectSetter");
        for (Object[] c : HasFields.CASES) {
            boolean positive = (c[1] != Error.class);
            testSetter(positive, lookup, c[0], c[1], testMode);
            if (positive)
                testSetter(positive, lookup, c[0], c[1], testMode | TEST_NPE);
        }
        for (int isStaticN = 0; isStaticN <= 1; isStaticN++) {
            testSetter(false, lookup,
                       new Object[]{ (isStaticN != 0), System.class, "bogus", char.class },
                       null, testMode);
        }
    }
    public void testSetter(boolean positive, MethodHandles.Lookup lookup,
                           Object fieldRef, Object value, int testMode) throws Throwable {
        testAccessor(positive, lookup, fieldRef, value, testMode | TEST_SETTER);
    }

    @Test
    public void testArrayElementGetter() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("arrayElementGetter");
        testArrayElementGetterSetter(false);
    }

    @Test
    public void testArrayElementSetter() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("arrayElementSetter");
        testArrayElementGetterSetter(true);
    }

    private static final int TEST_ARRAY_NONE = 0, TEST_ARRAY_NPE = 1, TEST_ARRAY_OOB = 2, TEST_ARRAY_ASE = 3;

    public void testArrayElementGetterSetter(boolean testSetter) throws Throwable {
        testArrayElementGetterSetter(testSetter, TEST_ARRAY_NONE);
    }

    @Test
    public void testArrayElementErrors() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("arrayElementErrors");
        testArrayElementGetterSetter(false, TEST_ARRAY_NPE);
        testArrayElementGetterSetter(true, TEST_ARRAY_NPE);
        testArrayElementGetterSetter(false, TEST_ARRAY_OOB);
        testArrayElementGetterSetter(true, TEST_ARRAY_OOB);
        testArrayElementGetterSetter(new Object[10], true, TEST_ARRAY_ASE);
        testArrayElementGetterSetter(new Example[10], true, TEST_ARRAY_ASE);
        testArrayElementGetterSetter(new IntExample[10], true, TEST_ARRAY_ASE);
    }

    public void testArrayElementGetterSetter(boolean testSetter, int negTest) throws Throwable {
        testArrayElementGetterSetter(new String[10], testSetter, negTest);
        testArrayElementGetterSetter(new Iterable<?>[10], testSetter, negTest);
        testArrayElementGetterSetter(new Example[10], testSetter, negTest);
        testArrayElementGetterSetter(new IntExample[10], testSetter, negTest);
        testArrayElementGetterSetter(new Object[10], testSetter, negTest);
        testArrayElementGetterSetter(new boolean[10], testSetter, negTest);
        testArrayElementGetterSetter(new byte[10], testSetter, negTest);
        testArrayElementGetterSetter(new char[10], testSetter, negTest);
        testArrayElementGetterSetter(new short[10], testSetter, negTest);
        testArrayElementGetterSetter(new int[10], testSetter, negTest);
        testArrayElementGetterSetter(new float[10], testSetter, negTest);
        testArrayElementGetterSetter(new long[10], testSetter, negTest);
        testArrayElementGetterSetter(new double[10], testSetter, negTest);
    }

    public void testArrayElementGetterSetter(Object array, boolean testSetter, int negTest) throws Throwable {
        boolean positive = (negTest == TEST_ARRAY_NONE);
        int length = java.lang.reflect.Array.getLength(array);
        Class<?> arrayType = array.getClass();
        Class<?> elemType = arrayType.getComponentType();
        Object arrayToMH = array;
        // this stanza allows negative tests to make argument perturbations:
        switch (negTest) {
        case TEST_ARRAY_NPE:
            arrayToMH = null;
            break;
        case TEST_ARRAY_OOB:
            assert(length > 0);
            arrayToMH = java.lang.reflect.Array.newInstance(elemType, 0);
            break;
        case TEST_ARRAY_ASE:
            assert(testSetter && !elemType.isPrimitive());
            if (elemType == Object.class)
                arrayToMH = new StringBuffer[length];  // very random subclass of Object!
            else if (elemType == Example.class)
                arrayToMH = new SubExample[length];
            else if (elemType == IntExample.class)
                arrayToMH = new SubIntExample[length];
            else
                return;  // can't make an ArrayStoreException test
            assert(arrayType.isInstance(arrayToMH))
                : Arrays.asList(arrayType, arrayToMH.getClass(), testSetter, negTest);
            break;
        }
        countTest(positive);
        if (verbosity > 2)  System.out.println("array type = "+array.getClass().getComponentType().getName()+"["+length+"]"+(positive ? "" : " negative test #"+negTest+" using "+Arrays.deepToString(new Object[]{arrayToMH})));
        MethodType expType = !testSetter
                ? MethodType.methodType(elemType,   arrayType, int.class)
                : MethodType.methodType(void.class, arrayType, int.class, elemType);
        MethodHandle mh = !testSetter
                ? MethodHandles.arrayElementGetter(arrayType)
                : MethodHandles.arrayElementSetter(arrayType);
        assertSame(mh.type(), expType);
        if (elemType != int.class && elemType != boolean.class) {
            MethodType gtype = mh.type().generic().changeParameterType(1, int.class);
            if (testSetter)  gtype = gtype.changeReturnType(void.class);
            mh = mh.asType(gtype);
        }
        Object sawValue, expValue;
        List<Object> model = array2list(array);
        Throwable caughtEx = null;
        for (int i = 0; i < length; i++) {
            // update array element
            Object random = randomArg(elemType);
            model.set(i, random);
            if (testSetter) {
                try {
                    if (elemType == int.class)
                        mh.invokeExact((int[]) arrayToMH, i, (int)random);
                    else if (elemType == boolean.class)
                        mh.invokeExact((boolean[]) arrayToMH, i, (boolean)random);
                    else
                        mh.invokeExact(arrayToMH, i, random);
                } catch (RuntimeException ex) {
                    caughtEx = ex;
                    break;
                }
                assertEquals(model, array2list(array));
            } else {
                Array.set(array, i, random);
            }
            if (verbosity >= 5) {
                List<Object> array2list = array2list(array);
                System.out.println("a["+i+"]="+random+" => "+array2list);
                if (!array2list.equals(model))
                    System.out.println("***   != "+model);
            }
            // observe array element
            sawValue = Array.get(array, i);
            if (!testSetter) {
                expValue = sawValue;
                try {
                    if (elemType == int.class)
                        sawValue = (int) mh.invokeExact((int[]) arrayToMH, i);
                    else if (elemType == boolean.class)
                        sawValue = (boolean) mh.invokeExact((boolean[]) arrayToMH, i);
                    else
                        sawValue = mh.invokeExact(arrayToMH, i);
                } catch (RuntimeException ex) {
                    caughtEx = ex;
                    break;
                }
                assertEquals(sawValue, expValue);
                assertEquals(model, array2list(array));
            }
        }
        if (!positive) {
            if (caughtEx == null)
                throw new RuntimeException("failed to catch exception for negTest="+negTest);
            // test the kind of exception
            Class<?> reqType = null;
            switch (negTest) {
            case TEST_ARRAY_ASE:  reqType = ArrayStoreException.class; break;
            case TEST_ARRAY_OOB:  reqType = ArrayIndexOutOfBoundsException.class; break;
            case TEST_ARRAY_NPE:  reqType = NullPointerException.class; break;
            default:              assert(false);
            }
            if (reqType.isInstance(caughtEx)) {
                caughtEx = null;  // nullify expected exception
            }
        }
        if (caughtEx != null) {
            throw new RuntimeException("unexpected exception", caughtEx);
        }
    }

    List<Object> array2list(Object array) {
        int length = Array.getLength(array);
        ArrayList<Object> model = new ArrayList<>(length);
        for (int i = 0; i < length; i++)
            model.add(Array.get(array, i));
        return model;
    }

    static class Callee {
        static Object id() { return called("id"); }
        static Object id(Object x) { return called("id", x); }
        static Object id(Object x, Object y) { return called("id", x, y); }
        static Object id(Object x, Object y, Object z) { return called("id", x, y, z); }
        static Object id(Object... vx) { return called("id", vx); }
        static MethodHandle ofType(int n) {
            return ofType(Object.class, n);
        }
        static MethodHandle ofType(Class<?> rtype, int n) {
            if (n == -1)
                return ofType(MethodType.methodType(rtype, Object[].class));
            return ofType(MethodType.genericMethodType(n).changeReturnType(rtype));
        }
        static MethodHandle ofType(Class<?> rtype, Class<?>... ptypes) {
            return ofType(MethodType.methodType(rtype, ptypes));
        }
        static MethodHandle ofType(MethodType type) {
            Class<?> rtype = type.returnType();
            String pfx = "";
            if (rtype != Object.class)
                pfx = rtype.getSimpleName().substring(0, 1).toLowerCase();
            String name = pfx+"id";
            try {
                return PRIVATE.findStatic(Callee.class, name, type);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Test
    public void testConvertArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("convertArguments");
        testConvert(Callee.ofType(1), null, "id", int.class);
        testConvert(Callee.ofType(1), null, "id", String.class);
        testConvert(Callee.ofType(1), null, "id", Integer.class);
        testConvert(Callee.ofType(1), null, "id", short.class);
        testConvert(Callee.ofType(1), null, "id", char.class);
        testConvert(Callee.ofType(1), null, "id", byte.class);
    }

    void testConvert(MethodHandle id, Class<?> rtype, String name, Class<?>... params) throws Throwable {
        testConvert(true, id, rtype, name, params);
    }

    void testConvert(boolean positive,
                     MethodHandle id, Class<?> rtype, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        MethodType idType = id.type();
        if (rtype == null)  rtype = idType.returnType();
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null)  params[i] = idType.parameterType(i);
        }
        // simulate the pairwise conversion
        MethodType newType = MethodType.methodType(rtype, params);
        Object[] args = randomArgs(newType.parameterArray());
        Object[] convArgs = args.clone();
        for (int i = 0; i < args.length; i++) {
            Class<?> src = newType.parameterType(i);
            Class<?> dst = idType.parameterType(i);
            if (src != dst)
                convArgs[i] = castToWrapper(convArgs[i], dst);
        }
        Object convResult = id.invokeWithArguments(convArgs);
        {
            Class<?> dst = newType.returnType();
            Class<?> src = idType.returnType();
            if (src != dst)
                convResult = castToWrapper(convResult, dst);
        }
        MethodHandle target = null;
        RuntimeException error = null;
        try {
            target = id.asType(newType);
        } catch (WrongMethodTypeException ex) {
            error = ex;
        }
        if (verbosity >= 3)
            System.out.println("convert "+id+ " to "+newType+" => "+target
                    +(error == null ? "" : " !! "+error));
        if (positive && error != null)  throw error;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(newType, target.type());
        printCalled(target, id.toString(), args);
        Object result = target.invokeWithArguments(args);
        assertCalled(name, convArgs);
        assertEquals(convResult, result);
        if (verbosity >= 1)
            System.out.print(':');
    }

    @Test
    public void testVarargsCollector() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("varargsCollector");
        MethodHandle vac0 = PRIVATE.findStatic(MethodHandlesTest.class, "called",
                               MethodType.methodType(Object.class, String.class, Object[].class));
        vac0 = vac0.bindTo("vac");
        MethodHandle vac = vac0.asVarargsCollector(Object[].class);
        testConvert(true, vac.asType(MethodType.genericMethodType(0)), null, "vac");
        testConvert(true, vac.asType(MethodType.genericMethodType(0)), null, "vac");
        for (Class<?> at : new Class<?>[] { Object.class, String.class, Integer.class }) {
            testConvert(true, vac.asType(MethodType.genericMethodType(1)), null, "vac", at);
            testConvert(true, vac.asType(MethodType.genericMethodType(2)), null, "vac", at, at);
        }
    }

    @Test  // SLOW
    public void testPermuteArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("permuteArguments");
        testPermuteArguments(4, Integer.class,  2, long.class,    6);
        if (CAN_TEST_LIGHTLY)  return;
        testPermuteArguments(4, Integer.class,  2, String.class,  0);
        testPermuteArguments(6, Integer.class,  0, null,         30);
    }
    public void testPermuteArguments(int max, Class<?> type1, int t2c, Class<?> type2, int dilution) throws Throwable {
        if (verbosity >= 2)
            System.out.println("permuteArguments "+max+"*"+type1.getName()
                    +(t2c==0?"":"/"+t2c+"*"+type2.getName())
                    +(dilution > 0 ? " with dilution "+dilution : ""));
        int t2pos = t2c == 0 ? 0 : 1;
        for (int inargs = t2pos+1; inargs <= max; inargs++) {
            Class<?>[] types = new Class<?>[inargs];
            Arrays.fill(types, type1);
            if (t2c != 0) {
                // Fill in a middle range with type2:
                Arrays.fill(types, t2pos, Math.min(t2pos+t2c, inargs), type2);
            }
            Object[] args = randomArgs(types);
            int numcases = 1;
            for (int outargs = 0; outargs <= max; outargs++) {
                if (outargs - inargs >= MAX_ARG_INCREASE)  continue;
                int casStep = dilution + 1;
                // Avoid some common factors:
                while ((casStep > 2 && casStep % 2 == 0 && inargs % 2 == 0) ||
                       (casStep > 3 && casStep % 3 == 0 && inargs % 3 == 0))
                    casStep++;
                testPermuteArguments(args, types, outargs, numcases, casStep);
                numcases *= inargs;
                if (CAN_TEST_LIGHTLY && outargs < max-2)  continue;
                if (dilution > 10 && outargs >= 4) {
                    if (CAN_TEST_LIGHTLY)  continue;
                    int[] reorder = new int[outargs];
                    // Do some special patterns, which we probably missed.
                    // Replication of a single argument or argument pair.
                    for (int i = 0; i < inargs; i++) {
                        Arrays.fill(reorder, i);
                        testPermuteArguments(args, types, reorder);
                        for (int d = 1; d <= 2; d++) {
                            if (i + d >= inargs)  continue;
                            for (int j = 1; j < outargs; j += 2)
                                reorder[j] += 1;
                            testPermuteArguments(args, types, reorder);
                            testPermuteArguments(args, types, reverse(reorder));
                        }
                    }
                    // Repetition of a sequence of 3 or more arguments.
                    for (int i = 1; i < inargs; i++) {
                        for (int len = 3; len <= inargs; len++) {
                            for (int j = 0; j < outargs; j++)
                                reorder[j] = (i + (j % len)) % inargs;
                            testPermuteArguments(args, types, reorder);
                            testPermuteArguments(args, types, reverse(reorder));
                        }
                    }
                }
            }
        }
    }

    public void testPermuteArguments(Object[] args, Class<?>[] types,
                                     int outargs, int numcases, int casStep) throws Throwable {
        int inargs = args.length;
        int[] reorder = new int[outargs];
        for (int cas = 0; cas < numcases; cas += casStep) {
            for (int i = 0, c = cas; i < outargs; i++) {
                reorder[i] = c % inargs;
                c /= inargs;
            }
            if (CAN_TEST_LIGHTLY && outargs >= 3 && (reorder[0] == reorder[1] || reorder[1] == reorder[2]))  continue;
            testPermuteArguments(args, types, reorder);
        }
    }

    static int[] reverse(int[] reorder) {
        reorder = reorder.clone();
        for (int i = 0, imax = reorder.length / 2; i < imax; i++) {
            int j = reorder.length - 1 - i;
            int tem = reorder[i];
            reorder[i] = reorder[j];
            reorder[j] = tem;
        }
        return reorder;
    }

    void testPermuteArguments(Object[] args, Class<?>[] types, int[] reorder) throws Throwable {
        countTest();
        if (args == null && types == null) {
            int max = 0;
            for (int j : reorder) {
                if (max < j)  max = j;
            }
            args = randomArgs(max+1, Integer.class);
        }
        if (args == null) {
            args = randomArgs(types);
        }
        if (types == null) {
            types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++)
                types[i] = args[i].getClass();
        }
        int inargs = args.length, outargs = reorder.length;
        assertTrue(inargs == types.length);
        if (verbosity >= 3)
            System.out.println("permuteArguments "+Arrays.toString(reorder));
        Object[] permArgs = new Object[outargs];
        Class<?>[] permTypes = new Class<?>[outargs];
        for (int i = 0; i < outargs; i++) {
            permArgs[i] = args[reorder[i]];
            permTypes[i] = types[reorder[i]];
        }
        if (verbosity >= 4) {
            System.out.println("in args:   "+Arrays.asList(args));
            System.out.println("out args:  "+Arrays.asList(permArgs));
            System.out.println("in types:  "+Arrays.asList(types));
            System.out.println("out types: "+Arrays.asList(permTypes));
        }
        MethodType inType  = MethodType.methodType(Object.class, types);
        MethodType outType = MethodType.methodType(Object.class, permTypes);
        MethodHandle target = varargsList(outargs).asType(outType);
        MethodHandle newTarget = MethodHandles.permuteArguments(target, inType, reorder);
        if (verbosity >= 5)  System.out.println("newTarget = "+newTarget);
        Object result = newTarget.invokeWithArguments(args);
        Object expected = Arrays.asList(permArgs);
        if (!expected.equals(result)) {
            System.out.println("*** failed permuteArguments "+Arrays.toString(reorder)+" types="+Arrays.asList(types));
            System.out.println("in args:   "+Arrays.asList(args));
            System.out.println("out args:  "+expected);
            System.out.println("bad args:  "+result);
        }
        assertEquals(expected, result);
    }


    @Test  // SLOW
    public void testSpreadArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("spreadArguments");
        for (Class<?> argType : new Class<?>[]{Object.class, Integer.class, int.class}) {
            if (verbosity >= 3)
                System.out.println("spreadArguments "+argType);
            for (int nargs = 0; nargs < 50; nargs++) {
                if (CAN_TEST_LIGHTLY && nargs > 11)  break;
                for (int pos = 0; pos <= nargs; pos++) {
                    if (CAN_TEST_LIGHTLY && pos > 2 && pos < nargs-2)  continue;
                    if (nargs > 10 && pos > 4 && pos < nargs-4 && pos % 10 != 3)
                        continue;
                    testSpreadArguments(argType, pos, nargs);
                }
            }
        }
    }
    public void testSpreadArguments(Class<?> argType, int pos, int nargs) throws Throwable {
        countTest();
        Class<?> arrayType = java.lang.reflect.Array.newInstance(argType, 0).getClass();
        MethodHandle target2 = varargsArray(arrayType, nargs);
        MethodHandle target = target2.asType(target2.type().generic());
        if (verbosity >= 3)
            System.out.println("spread into "+target2+" ["+pos+".."+nargs+"]");
        Object[] args = randomArgs(target2.type().parameterArray());
        // make sure the target does what we think it does:
        if (pos == 0 && nargs < 5 && !argType.isPrimitive()) {
            Object[] check = (Object[]) target.invokeWithArguments(args);
            assertArrayEquals(args, check);
            switch (nargs) {
                case 0:
                    check = (Object[]) (Object) target.invokeExact();
                    assertArrayEquals(args, check);
                    break;
                case 1:
                    check = (Object[]) (Object) target.invokeExact(args[0]);
                    assertArrayEquals(args, check);
                    break;
                case 2:
                    check = (Object[]) (Object) target.invokeExact(args[0], args[1]);
                    assertArrayEquals(args, check);
                    break;
            }
        }
        List<Class<?>> newParams = new ArrayList<>(target2.type().parameterList());
        {   // modify newParams in place
            List<Class<?>> spreadParams = newParams.subList(pos, nargs);
            spreadParams.clear(); spreadParams.add(arrayType);
        }
        MethodType newType = MethodType.methodType(arrayType, newParams);
        MethodHandle result = target2.asSpreader(arrayType, nargs-pos);
        assert(result.type() == newType) : Arrays.asList(result, newType);
        result = result.asType(newType.generic());
        Object returnValue;
        if (pos == 0) {
            Object args2 = ValueConversions.changeArrayType(arrayType, Arrays.copyOfRange(args, pos, args.length));
            returnValue = result.invokeExact(args2);
        } else {
            Object[] args1 = Arrays.copyOfRange(args, 0, pos+1);
            args1[pos] = ValueConversions.changeArrayType(arrayType, Arrays.copyOfRange(args, pos, args.length));
            returnValue = result.invokeWithArguments(args1);
        }
        String argstr = Arrays.toString(args);
        if (!argType.isPrimitive()) {
            Object[] rv = (Object[]) returnValue;
            String rvs = Arrays.toString(rv);
            if (!Arrays.equals(args, rv)) {
                System.out.println("method:   "+result);
                System.out.println("expected: "+argstr);
                System.out.println("returned: "+rvs);
                assertArrayEquals(args, rv);
            }
        } else if (argType == int.class) {
            String rvs = Arrays.toString((int[]) returnValue);
            if (!argstr.equals(rvs)) {
                System.out.println("method:   "+result);
                System.out.println("expected: "+argstr);
                System.out.println("returned: "+rvs);
                assertEquals(argstr, rvs);
            }
        } else if (argType == long.class) {
            String rvs = Arrays.toString((long[]) returnValue);
            if (!argstr.equals(rvs)) {
                System.out.println("method:   "+result);
                System.out.println("expected: "+argstr);
                System.out.println("returned: "+rvs);
                assertEquals(argstr, rvs);
            }
        } else {
            // cannot test...
        }
    }

    @Test  // SLOW
    public void testCollectArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("collectArguments");
        for (Class<?> argType : new Class<?>[]{Object.class, Integer.class, int.class}) {
            if (verbosity >= 3)
                System.out.println("collectArguments "+argType);
            for (int nargs = 0; nargs < 50; nargs++) {
                if (CAN_TEST_LIGHTLY && nargs > 11)  break;
                for (int pos = 0; pos <= nargs; pos++) {
                    if (CAN_TEST_LIGHTLY && pos > 2 && pos < nargs-2)  continue;
                    if (nargs > 10 && pos > 4 && pos < nargs-4 && pos % 10 != 3)
                        continue;
                    testCollectArguments(argType, pos, nargs);
                }
            }
        }
    }
    public void testCollectArguments(Class<?> argType, int pos, int nargs) throws Throwable {
        countTest();
        // fake up a MH with the same type as the desired adapter:
        MethodHandle fake = varargsArray(nargs);
        fake = changeArgTypes(fake, argType);
        MethodType newType = fake.type();
        Object[] args = randomArgs(newType.parameterArray());
        // here is what should happen:
        Object[] collectedArgs = Arrays.copyOfRange(args, 0, pos+1);
        collectedArgs[pos] = Arrays.copyOfRange(args, pos, args.length);
        // here is the MH which will witness the collected argument tail:
        MethodHandle target = varargsArray(pos+1);
        target = changeArgTypes(target, 0, pos, argType);
        target = changeArgTypes(target, pos, pos+1, Object[].class);
        if (verbosity >= 3)
            System.out.println("collect from "+Arrays.asList(args)+" ["+pos+".."+nargs+"]");
        MethodHandle result = target.asCollector(Object[].class, nargs-pos).asType(newType);
        Object[] returnValue = (Object[]) result.invokeWithArguments(args);
//        assertTrue(returnValue.length == pos+1 && returnValue[pos] instanceof Object[]);
//        returnValue[pos] = Arrays.asList((Object[]) returnValue[pos]);
//        collectedArgs[pos] = Arrays.asList((Object[]) collectedArgs[pos]);
        assertArrayEquals(collectedArgs, returnValue);
    }

    @Test  // SLOW
    public void testInsertArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("insertArguments");
        for (int nargs = 0; nargs < 50; nargs++) {
            if (CAN_TEST_LIGHTLY && nargs > 11)  break;
            for (int ins = 0; ins <= nargs; ins++) {
                if (nargs > 10 && ins > 4 && ins < nargs-4 && ins % 10 != 3)
                    continue;
                for (int pos = 0; pos <= nargs; pos++) {
                    if (nargs > 10 && pos > 4 && pos < nargs-4 && pos % 10 != 3)
                        continue;
                    if (CAN_TEST_LIGHTLY && pos > 2 && pos < nargs-2)  continue;
                    testInsertArguments(nargs, pos, ins);
                }
            }
        }
    }

    void testInsertArguments(int nargs, int pos, int ins) throws Throwable {
        countTest();
        MethodHandle target = varargsArray(nargs + ins);
        Object[] args = randomArgs(target.type().parameterArray());
        List<Object> resList = Arrays.asList(args);
        List<Object> argsToPass = new ArrayList<>(resList);
        List<Object> argsToInsert = argsToPass.subList(pos, pos + ins);
        if (verbosity >= 3)
            System.out.println("insert: "+argsToInsert+" @"+pos+" into "+target);
        @SuppressWarnings("cast")  // cast to spread Object... is helpful
        MethodHandle target2 = MethodHandles.insertArguments(target, pos,
                (Object[]/*...*/) argsToInsert.toArray());
        argsToInsert.clear();  // remove from argsToInsert
        Object res2 = target2.invokeWithArguments(argsToPass);
        Object res2List = Arrays.asList((Object[])res2);
        if (verbosity >= 3)
            System.out.println("result: "+res2List);
        //if (!resList.equals(res2List))
        //    System.out.println("*** fail at n/p/i = "+nargs+"/"+pos+"/"+ins+": "+resList+" => "+res2List);
        assertEquals(resList, res2List);
    }

    @Test
    public void testFilterReturnValue() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("filterReturnValue");
        Class<?> classOfVCList = varargsList(1).invokeWithArguments(0).getClass();
        assertTrue(List.class.isAssignableFrom(classOfVCList));
        for (int nargs = 0; nargs <= 3; nargs++) {
            for (Class<?> rtype : new Class<?>[] { Object.class,
                                                List.class,
                                                int.class,
                                                byte.class,
                                                long.class,
                                                CharSequence.class,
                                                String.class }) {
                testFilterReturnValue(nargs, rtype);
            }
        }
    }

    void testFilterReturnValue(int nargs, Class<?> rtype) throws Throwable {
        countTest();
        MethodHandle target = varargsList(nargs, rtype);
        MethodHandle filter;
        if (List.class.isAssignableFrom(rtype) || rtype.isAssignableFrom(List.class))
            filter = varargsList(1);  // add another layer of list-ness
        else
            filter = MethodHandles.identity(rtype);
        filter = filter.asType(MethodType.methodType(target.type().returnType(), rtype));
        Object[] argsToPass = randomArgs(nargs, Object.class);
        if (verbosity >= 3)
            System.out.println("filter "+target+" to "+rtype.getSimpleName()+" with "+filter);
        MethodHandle target2 = MethodHandles.filterReturnValue(target, filter);
        if (verbosity >= 4)
            System.out.println("filtered target: "+target2);
        // Simulate expected effect of filter on return value:
        Object unfiltered = target.invokeWithArguments(argsToPass);
        Object expected = filter.invokeWithArguments(unfiltered);
        if (verbosity >= 4)
            System.out.println("unfiltered: "+unfiltered+" : "+unfiltered.getClass().getSimpleName());
        if (verbosity >= 4)
            System.out.println("expected: "+expected+" : "+expected.getClass().getSimpleName());
        Object result = target2.invokeWithArguments(argsToPass);
        if (verbosity >= 3)
            System.out.println("result: "+result+" : "+result.getClass().getSimpleName());
        if (!expected.equals(result))
            System.out.println("*** fail at n/rt = "+nargs+"/"+rtype.getSimpleName()+": "+Arrays.asList(argsToPass)+" => "+result+" != "+expected);
        assertEquals(expected, result);
    }

    @Test
    public void testFilterArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("filterArguments");
        for (int nargs = 1; nargs <= 6; nargs++) {
            for (int pos = 0; pos < nargs; pos++) {
                testFilterArguments(nargs, pos);
            }
        }
    }

    void testFilterArguments(int nargs, int pos) throws Throwable {
        countTest();
        MethodHandle target = varargsList(nargs);
        MethodHandle filter = varargsList(1);
        filter = filter.asType(filter.type().generic());
        Object[] argsToPass = randomArgs(nargs, Object.class);
        if (verbosity >= 3)
            System.out.println("filter "+target+" at "+pos+" with "+filter);
        MethodHandle target2 = MethodHandles.filterArguments(target, pos, filter);
        // Simulate expected effect of filter on arglist:
        Object[] filteredArgs = argsToPass.clone();
        filteredArgs[pos] = filter.invokeExact(filteredArgs[pos]);
        List<Object> expected = Arrays.asList(filteredArgs);
        Object result = target2.invokeWithArguments(argsToPass);
        if (verbosity >= 3)
            System.out.println("result: "+result);
        if (!expected.equals(result))
            System.out.println("*** fail at n/p = "+nargs+"/"+pos+": "+Arrays.asList(argsToPass)+" => "+result+" != "+expected);
        assertEquals(expected, result);
    }

    @Test
    public void testFoldArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("foldArguments");
        for (int nargs = 0; nargs <= 4; nargs++) {
            for (int fold = 0; fold <= nargs; fold++) {
                for (int pos = 0; pos <= nargs; pos++) {
                    testFoldArguments(nargs, pos, fold);
                }
            }
        }
    }

    void testFoldArguments(int nargs, int pos, int fold) throws Throwable {
        if (pos != 0)  return;  // can fold only at pos=0 for now
        countTest();
        MethodHandle target = varargsList(1 + nargs);
        MethodHandle combine = varargsList(fold).asType(MethodType.genericMethodType(fold));
        List<Object> argsToPass = Arrays.asList(randomArgs(nargs, Object.class));
        if (verbosity >= 3)
            System.out.println("fold "+target+" with "+combine);
        MethodHandle target2 = MethodHandles.foldArguments(target, combine);
        // Simulate expected effect of combiner on arglist:
        List<Object> expected = new ArrayList<>(argsToPass);
        List<Object> argsToFold = expected.subList(pos, pos + fold);
        if (verbosity >= 3)
            System.out.println("fold: "+argsToFold+" into "+target2);
        Object foldedArgs = combine.invokeWithArguments(argsToFold);
        argsToFold.add(0, foldedArgs);
        Object result = target2.invokeWithArguments(argsToPass);
        if (verbosity >= 3)
            System.out.println("result: "+result);
        if (!expected.equals(result))
            System.out.println("*** fail at n/p/f = "+nargs+"/"+pos+"/"+fold+": "+argsToPass+" => "+result+" != "+expected);
        assertEquals(expected, result);
    }

    @Test
    public void testDropArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("dropArguments");
        for (int nargs = 0; nargs <= 4; nargs++) {
            for (int drop = 1; drop <= 4; drop++) {
                for (int pos = 0; pos <= nargs; pos++) {
                    testDropArguments(nargs, pos, drop);
                }
            }
        }
    }

    void testDropArguments(int nargs, int pos, int drop) throws Throwable {
        countTest();
        MethodHandle target = varargsArray(nargs);
        Object[] args = randomArgs(target.type().parameterArray());
        MethodHandle target2 = MethodHandles.dropArguments(target, pos,
                Collections.nCopies(drop, Object.class).toArray(new Class<?>[0]));
        List<Object> resList = Arrays.asList(args);
        List<Object> argsToDrop = new ArrayList<>(resList);
        for (int i = drop; i > 0; i--) {
            argsToDrop.add(pos, "blort#"+i);
        }
        Object res2 = target2.invokeWithArguments(argsToDrop);
        Object res2List = Arrays.asList((Object[])res2);
        //if (!resList.equals(res2List))
        //    System.out.println("*** fail at n/p/d = "+nargs+"/"+pos+"/"+drop+": "+argsToDrop+" => "+res2List);
        assertEquals(resList, res2List);
    }

    @Test  // SLOW
    public void testInvokers() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("exactInvoker, genericInvoker, varargsInvoker, dynamicInvoker");
        // exactInvoker, genericInvoker, varargsInvoker[0..N], dynamicInvoker
        Set<MethodType> done = new HashSet<>();
        for (int i = 0; i <= 6; i++) {
            if (CAN_TEST_LIGHTLY && i > 3)  break;
            MethodType gtype = MethodType.genericMethodType(i);
            for (Class<?> argType : new Class<?>[]{Object.class, Integer.class, int.class}) {
                for (int j = -1; j < i; j++) {
                    MethodType type = gtype;
                    if (j < 0)
                        type = type.changeReturnType(argType);
                    else if (argType == void.class)
                        continue;
                    else
                        type = type.changeParameterType(j, argType);
                    if (done.add(type))
                        testInvokers(type);
                    MethodType vtype = type.changeReturnType(void.class);
                    if (done.add(vtype))
                        testInvokers(vtype);
                }
            }
        }
    }

    public void testInvokers(MethodType type) throws Throwable {
        if (verbosity >= 3)
            System.out.println("test invokers for "+type);
        int nargs = type.parameterCount();
        boolean testRetCode = type.returnType() != void.class;
        MethodHandle target = PRIVATE.findStatic(MethodHandlesTest.class, "invokee",
                                MethodType.genericMethodType(0, true));
        assertTrue(target.isVarargsCollector());
        target = target.asType(type);
        Object[] args = randomArgs(type.parameterArray());
        List<Object> targetPlusArgs = new ArrayList<>(Arrays.asList(args));
        targetPlusArgs.add(0, target);
        int code = (Integer) invokee(args);
        Object log = logEntry("invokee", args);
        assertEquals(log.hashCode(), code);
        assertCalled("invokee", args);
        MethodHandle inv;
        Object result;
        // exact invoker
        countTest();
        calledLog.clear();
        inv = MethodHandles.exactInvoker(type);
        result = inv.invokeWithArguments(targetPlusArgs);
        if (testRetCode)  assertEquals(code, result);
        assertCalled("invokee", args);
        // generic invoker
        countTest();
        inv = MethodHandles.invoker(type);
        if (nargs <= 3 && type == type.generic()) {
            calledLog.clear();
            switch (nargs) {
            case 0:
                result = inv.invokeExact(target);
                break;
            case 1:
                result = inv.invokeExact(target, args[0]);
                break;
            case 2:
                result = inv.invokeExact(target, args[0], args[1]);
                break;
            case 3:
                result = inv.invokeExact(target, args[0], args[1], args[2]);
                break;
            }
            if (testRetCode)  assertEquals(code, result);
            assertCalled("invokee", args);
        }
        calledLog.clear();
        result = inv.invokeWithArguments(targetPlusArgs);
        if (testRetCode)  assertEquals(code, result);
        assertCalled("invokee", args);
        // varargs invoker #0
        calledLog.clear();
        inv = MethodHandles.spreadInvoker(type, 0);
        if (type.returnType() == Object.class) {
            result = inv.invokeExact(target, args);
        } else if (type.returnType() == void.class) {
            result = null; inv.invokeExact(target, args);
        } else {
            result = inv.invokeWithArguments(target, (Object) args);
        }
        if (testRetCode)  assertEquals(code, result);
        assertCalled("invokee", args);
        if (nargs >= 1 && type == type.generic()) {
            // varargs invoker #1
            calledLog.clear();
            inv = MethodHandles.spreadInvoker(type, 1);
            result = inv.invokeExact(target, args[0], Arrays.copyOfRange(args, 1, nargs));
            if (testRetCode)  assertEquals(code, result);
            assertCalled("invokee", args);
        }
        if (nargs >= 2 && type == type.generic()) {
            // varargs invoker #2
            calledLog.clear();
            inv = MethodHandles.spreadInvoker(type, 2);
            result = inv.invokeExact(target, args[0], args[1], Arrays.copyOfRange(args, 2, nargs));
            if (testRetCode)  assertEquals(code, result);
            assertCalled("invokee", args);
        }
        if (nargs >= 3 && type == type.generic()) {
            // varargs invoker #3
            calledLog.clear();
            inv = MethodHandles.spreadInvoker(type, 3);
            result = inv.invokeExact(target, args[0], args[1], args[2], Arrays.copyOfRange(args, 3, nargs));
            if (testRetCode)  assertEquals(code, result);
            assertCalled("invokee", args);
        }
        for (int k = 0; k <= nargs; k++) {
            // varargs invoker #0..N
            if (CAN_TEST_LIGHTLY && (k > 1 || k < nargs - 1))  continue;
            countTest();
            calledLog.clear();
            inv = MethodHandles.spreadInvoker(type, k);
            MethodType expType = (type.dropParameterTypes(k, nargs)
                                  .appendParameterTypes(Object[].class)
                                  .insertParameterTypes(0, MethodHandle.class));
            assertEquals(expType, inv.type());
            List<Object> targetPlusVarArgs = new ArrayList<>(targetPlusArgs);
            List<Object> tailList = targetPlusVarArgs.subList(1+k, 1+nargs);
            Object[] tail = tailList.toArray();
            tailList.clear(); tailList.add(tail);
            result = inv.invokeWithArguments(targetPlusVarArgs);
            if (testRetCode)  assertEquals(code, result);
            assertCalled("invokee", args);
        }

        // dynamic invoker
        countTest();
        CallSite site = new MutableCallSite(type);
        inv = site.dynamicInvoker();

        // see if we get the result of the original target:
        try {
            result = inv.invokeWithArguments(args);
            assertTrue("should not reach here", false);
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            assertTrue(msg, msg.contains("site"));
        }

        // set new target after invoker is created, to make sure we track target
        site.setTarget(target);
        calledLog.clear();
        result = inv.invokeWithArguments(args);
        if (testRetCode)  assertEquals(code, result);
        assertCalled("invokee", args);
    }

    static Object invokee(Object... args) {
        return called("invokee", args).hashCode();
    }

    private static final String MISSING_ARG = "missingArg";
    private static final String MISSING_ARG_2 = "missingArg#2";
    static Object targetIfEquals() {
        return called("targetIfEquals");
    }
    static Object fallbackIfNotEquals() {
        return called("fallbackIfNotEquals");
    }
    static Object targetIfEquals(Object x) {
        assertEquals(x, MISSING_ARG);
        return called("targetIfEquals", x);
    }
    static Object fallbackIfNotEquals(Object x) {
        assertFalse(x.toString(), x.equals(MISSING_ARG));
        return called("fallbackIfNotEquals", x);
    }
    static Object targetIfEquals(Object x, Object y) {
        assertEquals(x, y);
        return called("targetIfEquals", x, y);
    }
    static Object fallbackIfNotEquals(Object x, Object y) {
        assertFalse(x.toString(), x.equals(y));
        return called("fallbackIfNotEquals", x, y);
    }
    static Object targetIfEquals(Object x, Object y, Object z) {
        assertEquals(x, y);
        return called("targetIfEquals", x, y, z);
    }
    static Object fallbackIfNotEquals(Object x, Object y, Object z) {
        assertFalse(x.toString(), x.equals(y));
        return called("fallbackIfNotEquals", x, y, z);
    }

    @Test
    public void testGuardWithTest() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("guardWithTest");
        for (int nargs = 0; nargs <= 50; nargs++) {
            if (CAN_TEST_LIGHTLY && nargs > 7)  break;
            testGuardWithTest(nargs, Object.class);
            testGuardWithTest(nargs, String.class);
        }
    }
    void testGuardWithTest(int nargs, Class<?> argClass) throws Throwable {
        testGuardWithTest(nargs, 0, argClass);
        if (nargs <= 5 || nargs % 10 == 3) {
            for (int testDrops = 1; testDrops <= nargs; testDrops++)
                testGuardWithTest(nargs, testDrops, argClass);
        }
    }
    void testGuardWithTest(int nargs, int testDrops, Class<?> argClass) throws Throwable {
        countTest();
        int nargs1 = Math.min(3, nargs);
        MethodHandle test = PRIVATE.findVirtual(Object.class, "equals", MethodType.methodType(boolean.class, Object.class));
        MethodHandle target = PRIVATE.findStatic(MethodHandlesTest.class, "targetIfEquals", MethodType.genericMethodType(nargs1));
        MethodHandle fallback = PRIVATE.findStatic(MethodHandlesTest.class, "fallbackIfNotEquals", MethodType.genericMethodType(nargs1));
        while (test.type().parameterCount() > nargs)
            // 0: test = constant(MISSING_ARG.equals(MISSING_ARG))
            // 1: test = lambda (_) MISSING_ARG.equals(_)
            test = MethodHandles.insertArguments(test, 0, MISSING_ARG);
        if (argClass != Object.class) {
            test = changeArgTypes(test, argClass);
            target = changeArgTypes(target, argClass);
            fallback = changeArgTypes(fallback, argClass);
        }
        int testArgs = nargs - testDrops;
        assert(testArgs >= 0);
        test = addTrailingArgs(test, Math.min(testArgs, nargs), argClass);
        target = addTrailingArgs(target, nargs, argClass);
        fallback = addTrailingArgs(fallback, nargs, argClass);
        Object[][] argLists = {
            { },
            { "foo" }, { MISSING_ARG },
            { "foo", "foo" }, { "foo", "bar" },
            { "foo", "foo", "baz" }, { "foo", "bar", "baz" }
        };
        for (Object[] argList : argLists) {
            Object[] argList1 = argList;
            if (argList.length != nargs) {
                if (argList.length != nargs1)  continue;
                argList1 = Arrays.copyOf(argList, nargs);
                Arrays.fill(argList1, nargs1, nargs, MISSING_ARG_2);
            }
            MethodHandle test1 = test;
            if (test1.type().parameterCount() > testArgs) {
                int pc = test1.type().parameterCount();
                test1 = MethodHandles.insertArguments(test, testArgs, Arrays.copyOfRange(argList1, testArgs, pc));
            }
            MethodHandle mh = MethodHandles.guardWithTest(test1, target, fallback);
            assertEquals(target.type(), mh.type());
            boolean equals;
            switch (nargs) {
            case 0:   equals = true; break;
            case 1:   equals = MISSING_ARG.equals(argList[0]); break;
            default:  equals = argList[0].equals(argList[1]); break;
            }
            String willCall = (equals ? "targetIfEquals" : "fallbackIfNotEquals");
            if (verbosity >= 3)
                System.out.println(logEntry(willCall, argList));
            Object result = mh.invokeWithArguments(argList1);
            assertCalled(willCall, argList);
        }
    }

    @Test
    public void testCatchException() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("catchException");
        for (int nargs = 0; nargs < 40; nargs++) {
            if (CAN_TEST_LIGHTLY && nargs > 11)  break;
            for (int throwMode = 0; throwMode < THROW_MODE_LIMIT; throwMode++) {
                testCatchException(int.class, new ClassCastException("testing"), throwMode, nargs);
                if (CAN_TEST_LIGHTLY && nargs > 3)  continue;
                testCatchException(void.class, new java.io.IOException("testing"), throwMode, nargs);
                testCatchException(String.class, new LinkageError("testing"), throwMode, nargs);
            }
        }
    }

    static final int THROW_NOTHING = 0, THROW_CAUGHT = 1, THROW_UNCAUGHT = 2, THROW_THROUGH_ADAPTER = 3, THROW_MODE_LIMIT = 4;

    void testCatchException(Class<?> returnType, Throwable thrown, int throwMode, int nargs) throws Throwable {
        testCatchException(returnType, thrown, throwMode, nargs, 0);
        if (nargs <= 5 || nargs % 10 == 3) {
            for (int catchDrops = 1; catchDrops <= nargs; catchDrops++)
                testCatchException(returnType, thrown, throwMode, nargs, catchDrops);
        }
    }

    private static <T extends Throwable>
    Object throwOrReturn(Object normal, T exception) throws T {
        if (exception != null) {
            called("throwOrReturn/throw", normal, exception);
            throw exception;
        }
        called("throwOrReturn/normal", normal, exception);
        return normal;
    }
    private int fakeIdentityCount;
    private Object fakeIdentity(Object x) {
        System.out.println("should throw through this!");
        fakeIdentityCount++;
        return x;
    }

    void testCatchException(Class<?> returnType, Throwable thrown, int throwMode, int nargs, int catchDrops) throws Throwable {
        countTest();
        if (verbosity >= 3)
            System.out.println("catchException rt="+returnType+" throw="+throwMode+" nargs="+nargs+" drops="+catchDrops);
        Class<? extends Throwable> exType = thrown.getClass();
        if (throwMode > THROW_CAUGHT)  thrown = new UnsupportedOperationException("do not catch this");
        MethodHandle throwOrReturn
                = PRIVATE.findStatic(MethodHandlesTest.class, "throwOrReturn",
                    MethodType.methodType(Object.class, Object.class, Throwable.class));
        if (throwMode == THROW_THROUGH_ADAPTER) {
            MethodHandle fakeIdentity
                = PRIVATE.findVirtual(MethodHandlesTest.class, "fakeIdentity",
                    MethodType.methodType(Object.class, Object.class)).bindTo(this);
            for (int i = 0; i < 10; i++)
                throwOrReturn = MethodHandles.filterReturnValue(throwOrReturn, fakeIdentity);
        }
        int nargs1 = Math.max(2, nargs);
        MethodHandle thrower = throwOrReturn.asType(MethodType.genericMethodType(2));
        thrower = addTrailingArgs(thrower, nargs, Object.class);
        int catchArgc = 1 + nargs - catchDrops;
        MethodHandle catcher = varargsList(catchArgc).asType(MethodType.genericMethodType(catchArgc));
        Object[] args = randomArgs(nargs, Object.class);
        Object arg0 = MISSING_ARG;
        Object arg1 = (throwMode == THROW_NOTHING) ? (Throwable) null : thrown;
        if (nargs > 0)  arg0 = args[0];
        if (nargs > 1)  args[1] = arg1;
        assertEquals(nargs1, thrower.type().parameterCount());
        if (nargs < nargs1) {
            Object[] appendArgs = { arg0, arg1 };
            appendArgs = Arrays.copyOfRange(appendArgs, nargs, nargs1);
            thrower = MethodHandles.insertArguments(thrower, nargs, appendArgs);
        }
        assertEquals(nargs, thrower.type().parameterCount());
        MethodHandle target = MethodHandles.catchException(thrower, exType, catcher);
        assertEquals(thrower.type(), target.type());
        assertEquals(nargs, target.type().parameterCount());
        //System.out.println("catching with "+target+" : "+throwOrReturn);
        Object returned;
        try {
            returned = target.invokeWithArguments(args);
        } catch (Throwable ex) {
            assertSame("must get the out-of-band exception", thrown, ex);
            if (throwMode <= THROW_CAUGHT)
                assertEquals(THROW_UNCAUGHT, throwMode);
            returned = ex;
        }
        assertCalled("throwOrReturn/"+(throwMode == THROW_NOTHING ? "normal" : "throw"), arg0, arg1);
        //System.out.println("return from "+target+" : "+returned);
        if (throwMode == THROW_NOTHING) {
            assertSame(arg0, returned);
        } else if (throwMode == THROW_CAUGHT) {
            List<Object> catchArgs = new ArrayList<>(Arrays.asList(args));
            // catcher receives an initial subsequence of target arguments:
            catchArgs.subList(nargs - catchDrops, nargs).clear();
            // catcher also receives the exception, prepended:
            catchArgs.add(0, thrown);
            assertEquals(catchArgs, returned);
        }
        assertEquals(0, fakeIdentityCount);
    }

    @Test
    public void testThrowException() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("throwException");
        testThrowException(int.class, new ClassCastException("testing"));
        testThrowException(void.class, new java.io.IOException("testing"));
        testThrowException(String.class, new LinkageError("testing"));
    }

    void testThrowException(Class<?> returnType, Throwable thrown) throws Throwable {
        countTest();
        Class<? extends Throwable> exType = thrown.getClass();
        MethodHandle target = MethodHandles.throwException(returnType, exType);
        //System.out.println("throwing with "+target+" : "+thrown);
        MethodType expectedType = MethodType.methodType(returnType, exType);
        assertEquals(expectedType, target.type());
        target = target.asType(target.type().generic());
        Throwable caught = null;
        try {
            Object res = target.invokeExact((Object) thrown);
            fail("got "+res+" instead of throwing "+thrown);
        } catch (Throwable ex) {
            if (ex != thrown) {
                if (ex instanceof Error)  throw (Error)ex;
                if (ex instanceof RuntimeException)  throw (RuntimeException)ex;
            }
            caught = ex;
        }
        assertSame(thrown, caught);
    }

    @Test
    public void testInterfaceCast() throws Throwable {
        //if (CAN_SKIP_WORKING)  return;
        startTest("interfaceCast");
        assert( (((Object)"foo") instanceof CharSequence));
        assert(!(((Object)"foo") instanceof Iterable));
        for (MethodHandle mh : new MethodHandle[]{
            MethodHandles.identity(String.class),
            MethodHandles.identity(CharSequence.class),
            MethodHandles.identity(Iterable.class)
        }) {
            if (verbosity > 0)  System.out.println("-- mh = "+mh);
            for (Class<?> ctype : new Class<?>[]{
                Object.class, String.class, CharSequence.class,
                Number.class, Iterable.class
            }) {
                if (verbosity > 0)  System.out.println("---- ctype = "+ctype.getName());
                //                           doret  docast
                testInterfaceCast(mh, ctype, false, false);
                testInterfaceCast(mh, ctype, true,  false);
                testInterfaceCast(mh, ctype, false, true);
                testInterfaceCast(mh, ctype, true,  true);
            }
        }
    }
    private static Class<?> i2o(Class<?> c) {
        return (c.isInterface() ? Object.class : c);
    }
    public void testInterfaceCast(MethodHandle mh, Class<?> ctype,
                                                   boolean doret, boolean docast) throws Throwable {
        MethodHandle mh0 = mh;
        if (verbosity > 1)
            System.out.println("mh="+mh+", ctype="+ctype.getName()+", doret="+doret+", docast="+docast);
        String normalRetVal = "normal return value";
        MethodType mt = mh.type();
        MethodType mt0 = mt;
        if (doret)  mt = mt.changeReturnType(ctype);
        else        mt = mt.changeParameterType(0, ctype);
        if (docast) mh = MethodHandles.explicitCastArguments(mh, mt);
        else        mh = mh.asType(mt);
        assertEquals(mt, mh.type());
        MethodType mt1 = mt;
        // this bit is needed to make the interface types disappear for invokeWithArguments:
        mh = MethodHandles.explicitCastArguments(mh, mt.generic());
        Class<?>[] step = {
            mt1.parameterType(0),  // param as passed to mh at first
            mt0.parameterType(0),  // param after incoming cast
            mt0.returnType(),      // return value before cast
            mt1.returnType(),      // return value after outgoing cast
        };
        // where might a checkCast occur?
        boolean[] checkCast = new boolean[step.length];
        // the string value must pass each step without causing an exception
        if (!docast) {
            if (!doret) {
                if (step[0] != step[1])
                    checkCast[1] = true;  // incoming value is cast
            } else {
                if (step[2] != step[3])
                    checkCast[3] = true;  // outgoing value is cast
            }
        }
        boolean expectFail = false;
        for (int i = 0; i < step.length; i++) {
            Class<?> c = step[i];
            if (!checkCast[i])  c = i2o(c);
            if (!c.isInstance(normalRetVal)) {
                if (verbosity > 3)
                    System.out.println("expect failure at step "+i+" in "+Arrays.toString(step)+Arrays.toString(checkCast));
                expectFail = true;
                break;
            }
        }
        countTest(!expectFail);
        if (verbosity > 2)
            System.out.println("expectFail="+expectFail+", mt="+mt);
        Object res;
        try {
            res = mh.invokeWithArguments(normalRetVal);
        } catch (Exception ex) {
            res = ex;
        }
        boolean sawFail = !(res instanceof String);
        if (sawFail != expectFail) {
            System.out.println("*** testInterfaceCast: mh0 = "+mh0);
            System.out.println("  retype using "+(docast ? "explicitCastArguments" : "asType")+" to "+mt+" => "+mh);
            System.out.println("  call returned "+res);
            System.out.println("  expected "+(expectFail ? "an exception" : normalRetVal));
        }
        if (!expectFail) {
            assertFalse(res.toString(), sawFail);
            assertEquals(normalRetVal, res);
        } else {
            assertTrue(res.toString(), sawFail);
        }
    }

    @Test  // SLOW
    public void testCastFailure() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("testCastFailure");
        testCastFailure("cast/argument", 11000);
        if (CAN_TEST_LIGHTLY)  return;
        testCastFailure("unbox/argument", 11000);
        testCastFailure("cast/return", 11000);
        testCastFailure("unbox/return", 11000);
    }

    static class Surprise {
        public MethodHandle asMethodHandle() {
            return VALUE.bindTo(this);
        }
        Object value(Object x) {
            trace("value", x);
            if (boo != null)  return boo;
            return x;
        }
        Object boo;
        void boo(Object x) { boo = x; }

        static void trace(String x, Object y) {
            if (verbosity > 8) System.out.println(x+"="+y);
        }
        static Object  refIdentity(Object x)  { trace("ref.x", x); return x; }
        static Integer boxIdentity(Integer x) { trace("box.x", x); return x; }
        static int     intIdentity(int x)     { trace("int.x", x); return x; }
        static MethodHandle VALUE, REF_IDENTITY, BOX_IDENTITY, INT_IDENTITY;
        static {
            try {
                VALUE = PRIVATE.findVirtual(
                    Surprise.class, "value",
                        MethodType.methodType(Object.class, Object.class));
                REF_IDENTITY = PRIVATE.findStatic(
                    Surprise.class, "refIdentity",
                        MethodType.methodType(Object.class, Object.class));
                BOX_IDENTITY = PRIVATE.findStatic(
                    Surprise.class, "boxIdentity",
                        MethodType.methodType(Integer.class, Integer.class));
                INT_IDENTITY = PRIVATE.findStatic(
                    Surprise.class, "intIdentity",
                        MethodType.methodType(int.class, int.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @SuppressWarnings("ConvertToStringSwitch")
    void testCastFailure(String mode, int okCount) throws Throwable {
        countTest(false);
        if (verbosity > 2)  System.out.println("mode="+mode);
        Surprise boo = new Surprise();
        MethodHandle identity = Surprise.REF_IDENTITY, surprise0 = boo.asMethodHandle(), surprise = surprise0;
        if (mode.endsWith("/return")) {
            if (mode.equals("unbox/return")) {
                // fail on return to ((Integer)surprise).intValue
                surprise = surprise.asType(MethodType.methodType(int.class, Object.class));
                identity = identity.asType(MethodType.methodType(int.class, Object.class));
            } else if (mode.equals("cast/return")) {
                // fail on return to (Integer)surprise
                surprise = surprise.asType(MethodType.methodType(Integer.class, Object.class));
                identity = identity.asType(MethodType.methodType(Integer.class, Object.class));
            }
        } else if (mode.endsWith("/argument")) {
            MethodHandle callee = null;
            if (mode.equals("unbox/argument")) {
                // fail on handing surprise to int argument
                callee = Surprise.INT_IDENTITY;
            } else if (mode.equals("cast/argument")) {
                // fail on handing surprise to Integer argument
                callee = Surprise.BOX_IDENTITY;
            }
            if (callee != null) {
                callee = callee.asType(MethodType.genericMethodType(1));
                surprise = MethodHandles.filterArguments(callee, 0, surprise);
                identity = MethodHandles.filterArguments(callee, 0, identity);
            }
        }
        assertNotSame(mode, surprise, surprise0);
        identity = identity.asType(MethodType.genericMethodType(1));
        surprise = surprise.asType(MethodType.genericMethodType(1));
        Object x = 42;
        for (int i = 0; i < okCount; i++) {
            Object y = identity.invokeExact(x);
            assertEquals(x, y);
            Object z = surprise.invokeExact(x);
            assertEquals(x, z);
        }
        boo.boo("Boo!");
        Object y = identity.invokeExact(x);
        assertEquals(x, y);
        try {
            Object z = surprise.invokeExact(x);
            System.out.println("Failed to throw; got z="+z);
            assertTrue(false);
        } catch (ClassCastException ex) {
            if (verbosity > 2)
                System.out.println("caught "+ex);
            if (verbosity > 3)
                ex.printStackTrace(System.out);
            assertTrue(true);  // all is well
        }
    }

    static Example userMethod(Object o, String s, int i) {
        called("userMethod", o, s, i);
        return null;
    }

    @Test
    public void testUserClassInSignature() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("testUserClassInSignature");
        Lookup lookup = MethodHandles.lookup();
        String name; MethodType mt; MethodHandle mh;
        Object[] args;

        // Try a static method.
        name = "userMethod";
        mt = MethodType.methodType(Example.class, Object.class, String.class, int.class);
        mh = lookup.findStatic(lookup.lookupClass(), name, mt);
        assertEquals(mt, mh.type());
        assertEquals(Example.class, mh.type().returnType());
        args = randomArgs(mh.type().parameterArray());
        mh.invokeWithArguments(args);
        assertCalled(name, args);

        // Try a virtual method.
        name = "v2";
        mt = MethodType.methodType(Object.class, Object.class, int.class);
        mh = lookup.findVirtual(Example.class, name, mt);
        assertEquals(mt, mh.type().dropParameterTypes(0,1));
        assertTrue(mh.type().parameterList().contains(Example.class));
        args = randomArgs(mh.type().parameterArray());
        mh.invokeWithArguments(args);
        assertCalled(name, args);
    }

    static void runForRunnable() {
        called("runForRunnable");
    }
    public interface Fooable {
        // overloads:
        Object  foo(Object x, String y);
        List<?> foo(String x, int y);
        Object  foo(String x);
    }
    static Object fooForFooable(String x, Object... y) {
        return called("fooForFooable/"+x, y);
    }
    @SuppressWarnings("serial")  // not really a public API, just a test case
    public static class MyCheckedException extends Exception {
    }
    public interface WillThrow {
        void willThrow() throws MyCheckedException;
    }
    /*non-public*/ interface PrivateRunnable {
        public void run();
    }

    @Test
    public void testAsInterfaceInstance() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("asInterfaceInstance");
        Lookup lookup = MethodHandles.lookup();
        // test typical case:  Runnable.run
        {
            countTest();
            if (verbosity >= 2)  System.out.println("Runnable");
            MethodType mt = MethodType.methodType(void.class);
            MethodHandle mh = lookup.findStatic(MethodHandlesTest.class, "runForRunnable", mt);
            Runnable proxy = MethodHandleProxies.asInterfaceInstance(Runnable.class, mh);
            proxy.run();
            assertCalled("runForRunnable");
        }
        // well known single-name overloaded interface:  Appendable.append
        {
            countTest();
            if (verbosity >= 2)  System.out.println("Appendable");
            ArrayList<List<?>> appendResults = new ArrayList<>();
            MethodHandle append = lookup.bind(appendResults, "add", MethodType.methodType(boolean.class, Object.class));
            append = append.asType(MethodType.methodType(void.class, List.class)); // specialize the type
            MethodHandle asList = lookup.findStatic(Arrays.class, "asList", MethodType.methodType(List.class, Object[].class));
            MethodHandle mh = MethodHandles.filterReturnValue(asList, append).asVarargsCollector(Object[].class);
            Appendable proxy = MethodHandleProxies.asInterfaceInstance(Appendable.class, mh);
            proxy.append("one");
            proxy.append("two", 3, 4);
            proxy.append('5');
            assertEquals(Arrays.asList(Arrays.asList("one"),
                                       Arrays.asList("two", 3, 4),
                                       Arrays.asList('5')),
                         appendResults);
            if (verbosity >= 3)  System.out.println("appendResults="+appendResults);
            appendResults.clear();
            Formatter formatter = new Formatter(proxy);
            String fmt = "foo str=%s char='%c' num=%d";
            Object[] fmtArgs = { "str!", 'C', 42 };
            String expect = String.format(fmt, fmtArgs);
            formatter.format(fmt, fmtArgs);
            String actual = "";
            if (verbosity >= 3)  System.out.println("appendResults="+appendResults);
            for (List<?> l : appendResults) {
                Object x = l.get(0);
                switch (l.size()) {
                case 1:  actual += x; continue;
                case 3:  actual += ((String)x).substring((int)(Object)l.get(1), (int)(Object)l.get(2)); continue;
                }
                actual += l;
            }
            if (verbosity >= 3)  System.out.println("expect="+expect);
            if (verbosity >= 3)  System.out.println("actual="+actual);
            assertEquals(expect, actual);
        }
        // test case of an single name which is overloaded:  Fooable.foo(...)
        {
            if (verbosity >= 2)  System.out.println("Fooable");
            MethodHandle mh = lookup.findStatic(MethodHandlesTest.class, "fooForFooable",
                                                MethodType.methodType(Object.class, String.class, Object[].class));
            Fooable proxy = MethodHandleProxies.asInterfaceInstance(Fooable.class, mh);
            for (Method m : Fooable.class.getDeclaredMethods()) {
                countTest();
                assertSame("foo", m.getName());
                if (verbosity > 3)
                    System.out.println("calling "+m);
                MethodHandle invoker = lookup.unreflect(m);
                MethodType mt = invoker.type();
                Class<?>[] types = mt.parameterArray();
                types[0] = int.class;  // placeholder
                Object[] args = randomArgs(types);
                args[0] = proxy;
                if (verbosity > 3)
                    System.out.println("calling "+m+" on "+Arrays.asList(args));
                Object result = invoker.invokeWithArguments(args);
                if (verbosity > 4)
                    System.out.println("result = "+result);
                String name = "fooForFooable/"+args[1];
                Object[] argTail = Arrays.copyOfRange(args, 2, args.length);
                assertCalled(name, argTail);
                assertEquals(result, logEntry(name, argTail));
            }
        }
        // test processing of thrown exceptions:
        for (Throwable ex : new Throwable[] { new NullPointerException("ok"),
                                              new InternalError("ok"),
                                              new Throwable("fail"),
                                              new Exception("fail"),
                                              new MyCheckedException()
                                            }) {
            MethodHandle mh = MethodHandles.throwException(void.class, Throwable.class);
            mh = MethodHandles.insertArguments(mh, 0, ex);
            WillThrow proxy = MethodHandleProxies.asInterfaceInstance(WillThrow.class, mh);
            try {
                countTest();
                proxy.willThrow();
                System.out.println("Failed to throw: "+ex);
                assertTrue(false);
            } catch (Throwable ex1) {
                if (verbosity > 3) {
                    System.out.println("throw "+ex);
                    System.out.println("catch "+(ex == ex1 ? "UNWRAPPED" : ex1));
                }
                if (ex instanceof RuntimeException ||
                    ex instanceof Error) {
                    assertSame("must pass unchecked exception out without wrapping", ex, ex1);
                } else if (ex instanceof MyCheckedException) {
                    assertSame("must pass declared exception out without wrapping", ex, ex1);
                } else {
                    assertNotSame("must pass undeclared checked exception with wrapping", ex, ex1);
                    if (!(ex1 instanceof UndeclaredThrowableException) || ex1.getCause() != ex) {
                        ex1.printStackTrace(System.out);
                    }
                    assertSame(ex, ex1.getCause());
                    UndeclaredThrowableException utex = (UndeclaredThrowableException) ex1;
                }
            }
        }
        // Test error checking on bad interfaces:
        for (Class<?> nonSMI : new Class<?>[] { Object.class,
                                             String.class,
                                             CharSequence.class,
                                             java.io.Serializable.class,
                                             PrivateRunnable.class,
                                             Example.class }) {
            if (verbosity > 2)  System.out.println(nonSMI.getName());
            try {
                countTest(false);
                MethodHandleProxies.asInterfaceInstance(nonSMI, varargsArray(0));
                assertTrue("Failed to throw on "+nonSMI.getName(), false);
            } catch (IllegalArgumentException ex) {
                if (verbosity > 2)  System.out.println(nonSMI.getSimpleName()+": "+ex);
                // Object: java.lang.IllegalArgumentException:
                //     not a public interface: java.lang.Object
                // String: java.lang.IllegalArgumentException:
                //     not a public interface: java.lang.String
                // CharSequence: java.lang.IllegalArgumentException:
                //     not a single-method interface: java.lang.CharSequence
                // Serializable: java.lang.IllegalArgumentException:
                //     not a single-method interface: java.io.Serializable
                // PrivateRunnable: java.lang.IllegalArgumentException:
                //     not a public interface: test.java.lang.invoke.MethodHandlesTest$PrivateRunnable
                // Example: java.lang.IllegalArgumentException:
                //     not a public interface: test.java.lang.invoke.MethodHandlesTest$Example
            }
        }
        // Test error checking on interfaces with the wrong method type:
        for (Class<?> intfc : new Class<?>[] { Runnable.class /*arity 0*/,
                                            Fooable.class /*arity 1 & 2*/ }) {
            int badArity = 1;  // known to be incompatible
            if (verbosity > 2)  System.out.println(intfc.getName());
            try {
                countTest(false);
                MethodHandleProxies.asInterfaceInstance(intfc, varargsArray(badArity));
                assertTrue("Failed to throw on "+intfc.getName(), false);
            } catch (WrongMethodTypeException ex) {
                if (verbosity > 2)  System.out.println(intfc.getSimpleName()+": "+ex);
                // Runnable: java.lang.invoke.WrongMethodTypeException:
                //     cannot convert MethodHandle(Object)Object[] to ()void
                // Fooable: java.lang.invoke.WrongMethodTypeException:
                //     cannot convert MethodHandle(Object)Object[] to (Object,String)Object
            }
        }
    }

    @Test
    public void testRunnableProxy() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("testRunnableProxy");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle run = lookup.findStatic(lookup.lookupClass(), "runForRunnable", MethodType.methodType(void.class));
        Runnable r = MethodHandleProxies.asInterfaceInstance(Runnable.class, run);
        testRunnableProxy(r);
        assertCalled("runForRunnable");
    }
    private static void testRunnableProxy(Runnable r) {
        //7058630: JSR 292 method handle proxy violates contract for Object methods
        r.run();
        Object o = r;
        r = null;
        boolean eq = (o == o);
        int     hc = System.identityHashCode(o);
        String  st = o.getClass().getName() + "@" + Integer.toHexString(hc);
        Object expect = Arrays.asList(st, eq, hc);
        if (verbosity >= 2)  System.out.println("expect st/eq/hc = "+expect);
        Object actual = Arrays.asList(o.toString(), o.equals(o), o.hashCode());
        if (verbosity >= 2)  System.out.println("actual st/eq/hc = "+actual);
        assertEquals(expect, actual);
    }
}
// Local abbreviated copy of sun.invoke.util.ValueConversions
// This guy tests access from outside the same package member, but inside
// the package itself.
class ValueConversions {
    private static final Lookup IMPL_LOOKUP = MethodHandles.lookup();
    private static final Object[] NO_ARGS_ARRAY = {};
    private static Object[] makeArray(Object... args) { return args; }
    private static Object[] array() { return NO_ARGS_ARRAY; }
    private static Object[] array(Object a0)
                { return makeArray(a0); }
    private static Object[] array(Object a0, Object a1)
                { return makeArray(a0, a1); }
    private static Object[] array(Object a0, Object a1, Object a2)
                { return makeArray(a0, a1, a2); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3)
                { return makeArray(a0, a1, a2, a3); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4)
                { return makeArray(a0, a1, a2, a3, a4); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5)
                { return makeArray(a0, a1, a2, a3, a4, a5); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6)
                { return makeArray(a0, a1, a2, a3, a4, a5, a6); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6, Object a7)
                { return makeArray(a0, a1, a2, a3, a4, a5, a6, a7); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6, Object a7,
                                  Object a8)
                { return makeArray(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
    private static Object[] array(Object a0, Object a1, Object a2, Object a3,
                                  Object a4, Object a5, Object a6, Object a7,
                                  Object a8, Object a9)
                { return makeArray(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
    static MethodHandle[] makeArrays() {
        ArrayList<MethodHandle> arrays = new ArrayList<>();
        MethodHandles.Lookup lookup = IMPL_LOOKUP;
        for (;;) {
            int nargs = arrays.size();
            MethodType type = MethodType.genericMethodType(nargs).changeReturnType(Object[].class);
            String name = "array";
            MethodHandle array = null;
            try {
                array = lookup.findStatic(ValueConversions.class, name, type);
            } catch (ReflectiveOperationException ex) {
                // break from loop!
            }
            if (array == null)  break;
            arrays.add(array);
        }
        assertTrue(arrays.size() == 11);  // current number of methods
        return arrays.toArray(new MethodHandle[0]);
    }
    static final MethodHandle[] ARRAYS = makeArrays();

    /** Return a method handle that takes the indicated number of Object
     *  arguments and returns an Object array of them, as if for varargs.
     */
    public static MethodHandle varargsArray(int nargs) {
        if (nargs < ARRAYS.length)
            return ARRAYS[nargs];
        return MethodHandles.identity(Object[].class).asCollector(Object[].class, nargs);
    }
    public static MethodHandle varargsArray(Class<?> arrayType, int nargs) {
        Class<?> elemType = arrayType.getComponentType();
        MethodType vaType = MethodType.methodType(arrayType, Collections.<Class<?>>nCopies(nargs, elemType));
        MethodHandle mh = varargsArray(nargs);
        if (arrayType != Object[].class)
            mh = MethodHandles.filterReturnValue(mh, CHANGE_ARRAY_TYPE.bindTo(arrayType));
        return mh.asType(vaType);
    }
    static Object changeArrayType(Class<?> arrayType, Object[] a) {
        Class<?> elemType = arrayType.getComponentType();
        if (!elemType.isPrimitive())
            return Arrays.copyOf(a, a.length, arrayType.asSubclass(Object[].class));
        Object b = java.lang.reflect.Array.newInstance(elemType, a.length);
        for (int i = 0; i < a.length; i++)
            java.lang.reflect.Array.set(b, i, a[i]);
        return b;
    }
    private static final MethodHandle CHANGE_ARRAY_TYPE;
    static {
        try {
            CHANGE_ARRAY_TYPE = IMPL_LOOKUP.findStatic(ValueConversions.class, "changeArrayType",
                                                       MethodType.methodType(Object.class, Class.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            Error err = new InternalError("uncaught exception");
            err.initCause(ex);
            throw err;
        }
    }

    private static final List<Object> NO_ARGS_LIST = Arrays.asList(NO_ARGS_ARRAY);
    private static List<Object> makeList(Object... args) { return Arrays.asList(args); }
    private static List<Object> list() { return NO_ARGS_LIST; }
    private static List<Object> list(Object a0)
                { return makeList(a0); }
    private static List<Object> list(Object a0, Object a1)
                { return makeList(a0, a1); }
    private static List<Object> list(Object a0, Object a1, Object a2)
                { return makeList(a0, a1, a2); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3)
                { return makeList(a0, a1, a2, a3); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4)
                { return makeList(a0, a1, a2, a3, a4); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5)
                { return makeList(a0, a1, a2, a3, a4, a5); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5, Object a6)
                { return makeList(a0, a1, a2, a3, a4, a5, a6); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5, Object a6, Object a7)
                { return makeList(a0, a1, a2, a3, a4, a5, a6, a7); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5, Object a6, Object a7,
                                     Object a8)
                { return makeList(a0, a1, a2, a3, a4, a5, a6, a7, a8); }
    private static List<Object> list(Object a0, Object a1, Object a2, Object a3,
                                     Object a4, Object a5, Object a6, Object a7,
                                     Object a8, Object a9)
                { return makeList(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9); }
    static MethodHandle[] makeLists() {
        ArrayList<MethodHandle> lists = new ArrayList<>();
        MethodHandles.Lookup lookup = IMPL_LOOKUP;
        for (;;) {
            int nargs = lists.size();
            MethodType type = MethodType.genericMethodType(nargs).changeReturnType(List.class);
            String name = "list";
            MethodHandle list = null;
            try {
                list = lookup.findStatic(ValueConversions.class, name, type);
            } catch (ReflectiveOperationException ex) {
                // break from loop!
            }
            if (list == null)  break;
            lists.add(list);
        }
        assertTrue(lists.size() == 11);  // current number of methods
        return lists.toArray(new MethodHandle[0]);
    }
    static final MethodHandle[] LISTS = makeLists();
    static final MethodHandle AS_LIST;
    static {
        try {
            AS_LIST = IMPL_LOOKUP.findStatic(Arrays.class, "asList", MethodType.methodType(List.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException ex) { throw new RuntimeException(ex); }
    }

    /** Return a method handle that takes the indicated number of Object
     *  arguments and returns List.
     */
    public static MethodHandle varargsList(int nargs) {
        if (nargs < LISTS.length)
            return LISTS[nargs];
        return AS_LIST.asCollector(Object[].class, nargs);
    }
}
// This guy tests access from outside the same package member, but inside
// the package itself.
class PackageSibling {
    static Lookup lookup() {
        return MethodHandles.lookup();
    }
}
