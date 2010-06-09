/*
 * Copyright 2009-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

/* @test
 * @summary unit tests for java.dyn.MethodHandles
 * @compile -XDinvokedynamic MethodHandlesTest.java
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableInvokeDynamic test.java.dyn.MethodHandlesTest
 */

package test.java.dyn;

import java.dyn.*;
import java.dyn.MethodHandles.Lookup;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;


/**
 *
 * @author jrose
 */
public class MethodHandlesTest {
    // How much output?
    static int verbosity = 0;
    static {
        String vstr = System.getProperty("test.java.dyn.MethodHandlesTest.verbosity");
        if (vstr != null)  verbosity = Integer.parseInt(vstr);
    }

    // Set this true during development if you want to fast-forward to
    // a particular new, non-working test.  Tests which are known to
    // work (or have recently worked) test this flag and return on true.
    static boolean CAN_SKIP_WORKING = false;
    //static { CAN_SKIP_WORKING = true; }

    // Set true to test more calls.  If false, some tests are just
    // lookups, without exercising the actual method handle.
    static boolean DO_MORE_CALLS = true;


    @Test
    public void testFirst() throws Throwable {
        verbosity += 9; try {
            // left blank for debugging
        } finally { printCounts(); verbosity -= 9; }
    }

    // current failures
    @Test @Ignore("failure in call to makeRawRetypeOnly in ToGeneric")
    public void testFail_1() throws Throwable {
        // AMH.<init>: IllegalArgumentException: bad adapter (conversion=0xfffab300): adapter pushes too many parameters
        testSpreadArguments(int.class, 0, 6);
    }
    @Test @Ignore("failure in JVM when expanding the stack using asm stub for _adapter_spread_args")
    public void testFail_2() throws Throwable {
        // if CONV_OP_IMPLEMENTED_MASK includes OP_SPREAD_ARGS, this crashes:
        testSpreadArguments(Object.class, 0, 2);
    }
    @Test @Ignore("IllArgEx failure in call to ToGeneric.make")
    public void testFail_3() throws Throwable {
        // ToGeneric.<init>: UnsupportedOperationException: NYI: primitive parameters must follow references; entryType = (int,java.lang.Object)java.lang.Object
        testSpreadArguments(int.class, 1, 2);
    }
    @Test @Ignore("IllArgEx failure in call to ToGeneric.make")
    public void testFail_4() throws Throwable {
        // ToGeneric.<init>: UnsupportedOperationException: NYI: primitive parameters must follow references; entryType = (int,java.lang.Object)java.lang.Object
        testCollectArguments(int.class, 1, 2);
    }
    @Test @Ignore("cannot collect leading primitive types")
    public void testFail_5() throws Throwable {
        // ToGeneric.<init>: UnsupportedOperationException: NYI: primitive parameters must follow references; entryType = (int,java.lang.Object)java.lang.Object
        testInvokers(MethodType.genericMethodType(2).changeParameterType(0, int.class));
    }
    @Test @Ignore("should not insert arguments beyond MethodHandlePushLimit")
    public void testFail_6() throws Throwable {
        // ValueConversions.varargsArray: UnsupportedOperationException: NYI: cannot form a varargs array of length 13
        testInsertArguments(0, 0, MAX_ARG_INCREASE+10);
    }
    static final int MAX_ARG_INCREASE = 3;

    public MethodHandlesTest() {
    }

    @Before
    public void checkImplementedPlatform() {
        boolean platformOK = false;
        Properties properties = System.getProperties();
        String vers = properties.getProperty("java.vm.version");
        String name = properties.getProperty("java.vm.name");
        String arch = properties.getProperty("os.arch");
        if ((arch.equals("amd64") || arch.equals("i386") || arch.equals("x86") ||
             arch.equals("sparc") || arch.equals("sparcv9")) &&
            (name.contains("Client") || name.contains("Server"))
            ) {
            platformOK = true;
        } else {
            System.err.println("Skipping tests for unsupported platform: "+Arrays.asList(vers, name, arch));
        }
        assumeTrue(platformOK);
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
        if (verbosity >= 2 && (posTests | negTests) != 0) {
            System.out.println();
            if (posTests != 0)  System.out.println("=== "+posTests+" total positive test cases");
            if (negTests != 0)  System.out.println("=== "+negTests+" total negative test cases");
        }
    }

    static List<Object> calledLog = new ArrayList<Object>();
    static Object logEntry(String name, Object... args) {
        return Arrays.asList(name, Arrays.asList(args));
    }
    static Object called(String name, Object... args) {
        Object entry = logEntry(name, args);
        calledLog.add(entry);
        return entry;
    }
    static void assertCalled(String name, Object... args) {
        Object expected = logEntry(name, args);
        Object actual   = calledLog.get(calledLog.size() - 1);
        if (expected.equals(actual) && verbosity < 9)  return;
        System.out.println("assertCalled "+name+":");
        System.out.println("expected:   "+expected);
        System.out.println("actual:     "+actual);
        System.out.println("ex. types:  "+getClasses(expected));
        System.out.println("act. types: "+getClasses(actual));
        assertEquals("previous method call", expected, actual);
    }
    static void printCalled(MethodHandle target, String name, Object... args) {
        if (verbosity >= 3)
            System.out.println("calling MH="+target+" to "+name+Arrays.toString(args));
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
//        import sun.dyn.util.Wrapper;
//        Wrapper wrap = Wrapper.forBasicType(dst);
//        if (wrap == Wrapper.OBJECT && Wrapper.isWrapperType(dst))
//            wrap = Wrapper.forWrapperType(dst);
//        if (wrap != Wrapper.OBJECT)
//            return wrap.wrap(nextArg++);
        if (param.isInterface() || param.isAssignableFrom(String.class))
            return "#"+nextArg();
        else
            try {
                return param.newInstance();
            } catch (InstantiationException ex) {
            } catch (IllegalAccessException ex) {
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

    static <T, E extends T> T[] array(Class<T[]> atype, E... a) {
        return Arrays.copyOf(a, a.length, atype);
    }
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

    static MethodHandle changeArgTypes(MethodHandle target, Class<?> argType) {
        return changeArgTypes(target, 0, 999, argType);
    }
    static MethodHandle changeArgTypes(MethodHandle target,
            int beg, int end, Class<?> argType) {
        MethodType targetType = target.type();
        end = Math.min(end, targetType.parameterCount());
        ArrayList<Class<?>> argTypes = new ArrayList<Class<?>>(targetType.parameterList());
        Collections.fill(argTypes.subList(beg, end), argType);
        MethodType ttype2 = MethodType.methodType(targetType.returnType(), argTypes);
        return MethodHandles.convertArguments(target, ttype2);
    }

    // This lookup is good for all members in and under MethodHandlesTest.
    static final Lookup PRIVATE = MethodHandles.lookup();
    // This lookup is good for package-private members but not private ones.
    static final Lookup PACKAGE = PackageSibling.lookup();
    // This lookup is good only for public members.
    static final Lookup PUBLIC  = MethodHandles.publicLookup();

    // Subject methods...
    static class Example implements IntExample {
        final String name;
        public Example() { name = "Example#"+nextArg(); }
        protected Example(String name) { this.name = name; }
        protected Example(int x) { this(); called("protected <init>", this, x); }
        @Override public String toString() { return name; }

        public void            v0()     { called("v0", this); }
        void                   pkg_v0() { called("pkg_v0", this); }
        private void           pri_v0() { called("pri_v0", this); }
        public static void     s0()     { called("s0"); }
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

        static final Lookup EXAMPLE = MethodHandles.lookup();  // for testing findSpecial
    }
    static final Lookup EXAMPLE = Example.EXAMPLE;
    public static class PubExample extends Example {
        public PubExample() { super("PubExample#"+nextArg()); }
    }
    static class SubExample extends Example {
        @Override public void  v0()     { called("Sub/v0", this); }
        @Override void         pkg_v0() { called("Sub/pkg_v0", this); }
        private      SubExample(int x)  { called("<init>", this, x); }
        public SubExample() { super("SubExample#"+nextArg()); }
    }
    public static interface IntExample {
        public void            v0();
        static class Impl implements IntExample {
            public void        v0()     { called("Int/v0", this); }
            final String name;
            public Impl() { name = "Impl#"+nextArg(); }
            @Override public String toString() { return name; }
        }
    }

    static final Object[][][] ACCESS_CASES = {
        { { false, PUBLIC }, { false, PACKAGE }, { false, PRIVATE }, { false, EXAMPLE } }, //[0]: all false
        { { false, PUBLIC }, { false, PACKAGE }, { true,  PRIVATE }, { true,  EXAMPLE } }, //[1]: only PRIVATE
        { { false, PUBLIC }, { true,  PACKAGE }, { true,  PRIVATE }, { true,  EXAMPLE } }, //[2]: PUBLIC false
        { { true,  PUBLIC }, { true,  PACKAGE }, { true,  PRIVATE }, { true,  EXAMPLE } }, //[3]: all true
    };

    static Object[][] accessCases(Class<?> defc, String name, boolean isSpecial) {
        Object[][] cases;
        if (name.contains("pri_") || isSpecial) {
            cases = ACCESS_CASES[1]; // PRIVATE only
        } else if (name.contains("pkg_") || !Modifier.isPublic(defc.getModifiers())) {
            cases = ACCESS_CASES[2]; // not PUBLIC
        } else {
            assertTrue(name.indexOf('_') < 0);
            boolean pubc = Modifier.isPublic(defc.getModifiers());
            if (pubc)
                cases = ACCESS_CASES[3]; // all access levels
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

    @Test
    public void testFindStatic() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findStatic");
        testFindStatic(PubExample.class, void.class, "s0");
        testFindStatic(Example.class, void.class, "s0");
        testFindStatic(Example.class, void.class, "pkg_s0");
        testFindStatic(Example.class, void.class, "pri_s0");

        testFindStatic(Example.class, Object.class, "s1", Object.class);
        testFindStatic(Example.class, Object.class, "s2", int.class);
        testFindStatic(Example.class, Object.class, "s3", long.class);
        testFindStatic(Example.class, Object.class, "s4", int.class, int.class);
        testFindStatic(Example.class, Object.class, "s5", long.class, int.class);
        testFindStatic(Example.class, Object.class, "s6", int.class, long.class);
        testFindStatic(Example.class, Object.class, "s7", float.class, double.class);

        testFindStatic(false, PRIVATE, Example.class, void.class, "bogus");
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
        MethodType type = MethodType.methodType(ret, params);
        MethodHandle target = null;
        RuntimeException noAccess = null;
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            target = lookup.findStatic(defc, name, type);
        } catch (NoAccessException ex) {
            noAccess = ex;
        }
        if (verbosity >= 3)
            System.out.println("findStatic "+lookup+": "+defc.getName()+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(type, target.type());
        assertTrue(target.toString().contains(name));  // rough check
        if (!DO_MORE_CALLS && lookup != PRIVATE)  return;
        Object[] args = randomArgs(params);
        printCalled(target, name, args);
        target.invokeVarargs(args);
        assertCalled(name, args);
        if (verbosity >= 1)
            System.out.print(':');
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
        testFindVirtual(false, PRIVATE, Example.class, Example.class, void.class, "bogus");
        // test dispatch
        testFindVirtual(SubExample.class,      SubExample.class, void.class, "Sub/v0");
        testFindVirtual(SubExample.class,         Example.class, void.class, "Sub/v0");
        testFindVirtual(SubExample.class,      IntExample.class, void.class, "Sub/v0");
        testFindVirtual(SubExample.class,      SubExample.class, void.class, "Sub/pkg_v0");
        testFindVirtual(SubExample.class,         Example.class, void.class, "Sub/pkg_v0");
        testFindVirtual(Example.class,         IntExample.class, void.class, "v0");
        testFindVirtual(IntExample.Impl.class, IntExample.class, void.class, "Int/v0");
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
    void testFindVirtual(boolean positive, Lookup lookup, Class<?> rcvc, Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        String methodName = name.substring(1 + name.indexOf('/'));  // foo/bar => foo
        MethodType type = MethodType.methodType(ret, params);
        MethodHandle target = null;
        RuntimeException noAccess = null;
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            target = lookup.findVirtual(defc, methodName, type);
        } catch (NoAccessException ex) {
            noAccess = ex;
        }
        if (verbosity >= 3)
            System.out.println("findVirtual "+lookup+": "+defc.getName()+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        Class<?>[] paramsWithSelf = cat(array(Class[].class, (Class)defc), params);
        MethodType typeWithSelf = MethodType.methodType(ret, paramsWithSelf);
        assertEquals(typeWithSelf, target.type());
        assertTrue(target.toString().contains(methodName));  // rough check
        if (!DO_MORE_CALLS && lookup != PRIVATE)  return;
        Object[] argsWithSelf = randomArgs(paramsWithSelf);
        if (rcvc != defc)  argsWithSelf[0] = randomArg(rcvc);
        printCalled(target, name, argsWithSelf);
        target.invokeVarargs(argsWithSelf);
        assertCalled(name, argsWithSelf);
        if (verbosity >= 1)
            System.out.print(':');
    }

    @Test
    public void testFindSpecial() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findSpecial");
        testFindSpecial(SubExample.class, Example.class, void.class, "v0");
        testFindSpecial(SubExample.class, Example.class, void.class, "pkg_v0");
        // Do some negative testing:
        for (Lookup lookup : new Lookup[]{ PRIVATE, EXAMPLE, PACKAGE, PUBLIC }) {
            testFindSpecial(false, lookup, Object.class, Example.class, void.class, "v0");
            testFindSpecial(false, lookup, SubExample.class, Example.class, void.class, "<init>", int.class);
            testFindSpecial(false, lookup, SubExample.class, Example.class, void.class, "s0");
            testFindSpecial(false, lookup, SubExample.class, Example.class, void.class, "bogus");
        }
    }

    void testFindSpecial(Class<?> specialCaller,
                         Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        testFindSpecial(true,  EXAMPLE, specialCaller, defc, ret, name, params);
        testFindSpecial(true,  PRIVATE, specialCaller, defc, ret, name, params);
        testFindSpecial(false, PACKAGE, specialCaller, defc, ret, name, params);
        testFindSpecial(false, PUBLIC,  specialCaller, defc, ret, name, params);
    }
    void testFindSpecial(boolean positive, Lookup lookup, Class<?> specialCaller,
                         Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        MethodType type = MethodType.methodType(ret, params);
        MethodHandle target = null;
        RuntimeException noAccess = null;
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            target = lookup.findSpecial(defc, name, type, specialCaller);
        } catch (NoAccessException ex) {
            noAccess = ex;
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
        assertTrue(target.toString().contains(name));  // rough check
        if (!DO_MORE_CALLS && lookup != PRIVATE && lookup != EXAMPLE)  return;
        Object[] args = randomArgs(paramsWithSelf);
        printCalled(target, name, args);
        target.invokeVarargs(args);
        assertCalled(name, args);
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
        RuntimeException noAccess = null;
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            target = lookup.bind(receiver, methodName, type);
        } catch (NoAccessException ex) {
            noAccess = ex;
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
        target.invokeVarargs(args);
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
        MethodType type = MethodType.methodType(ret, params);
        Method rmethod = null;
        MethodHandle target = null;
        RuntimeException noAccess = null;
        try {
            rmethod = defc.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException ex) {
            throw new NoAccessException(ex);
        }
        boolean isStatic = (rcvc == null);
        boolean isSpecial = (specialCaller != null);
        try {
            if (verbosity >= 4)  System.out.println("lookup via "+lookup+" of "+defc+" "+name+type);
            if (isSpecial)
                target = lookup.unreflectSpecial(rmethod, specialCaller);
            else
                target = lookup.unreflect(rmethod);
        } catch (NoAccessException ex) {
            noAccess = ex;
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
        target.invokeVarargs(argsMaybeWithSelf);
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
            ArrayList<Object[]> cases = new ArrayList<Object[]>();
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
                    } catch (Exception ex) {
                        throw new InternalError("no field HasFields."+name);
                    }
                    try {
                        value = field.get(fields);
                    } catch (Exception ex) {
                        throw new InternalError("cannot fetch field HasFields."+name);
                    }
                    if (type == float.class) {
                        float v = 'F';
                        if (isStatic)  v++;
                        assert(value.equals(v));
                    }
                    assert(name.equals(field.getName()));
                    assert(type.equals(field.getType()));
                    assert(isStatic == (Modifier.isStatic(field.getModifiers())));
                    cases.add(new Object[]{ field, value });
                }
            }
            CASES = cases.toArray(new Object[0][]);
        }
    }

    static final int TEST_UNREFLECT = 1, TEST_FIND_FIELD = 2, TEST_FIND_STATIC_FIELD = 3;
    static boolean testModeMatches(int testMode, boolean isStatic) {
        switch (testMode) {
        case TEST_FIND_STATIC_FIELD:    return isStatic;
        case TEST_FIND_FIELD:           return !isStatic;
        default:                        return true;  // unreflect matches both
        }
    }

    @Test
    public void testUnreflectGetter() throws Throwable {
        startTest("unreflectGetter");
        testGetter(TEST_UNREFLECT);
    }
    @Test
    public void testFindGetter() throws Throwable {
        startTest("findGetter");
        testGetter(TEST_FIND_FIELD);
    }
    @Test
    public void testFindStaticGetter() throws Throwable {
        startTest("findStaticGetter");
        testGetter(TEST_FIND_STATIC_FIELD);
    }
    public void testGetter(int testMode) throws Throwable {
        Lookup lookup = PRIVATE;  // FIXME: test more lookups than this one
        for (Object[] c : HasFields.CASES) {
            Field f = (Field)c[0];
            Object value = c[1];
            Class<?> type = f.getType();
            testGetter(lookup, f, type, value, testMode);
        }
    }
    public void testGetter(MethodHandles.Lookup lookup,
            Field f, Class<?> type, Object value, int testMode) throws Throwable {
        boolean isStatic = Modifier.isStatic(f.getModifiers());
        Class<?> fclass = f.getDeclaringClass();
        String   fname  = f.getName();
        Class<?> ftype  = f.getType();
        if (!testModeMatches(testMode, isStatic))  return;
        countTest(true);
        MethodType expType = MethodType.methodType(type, HasFields.class);
        if (isStatic)  expType = expType.dropParameterTypes(0, 1);
        MethodHandle mh = lookup.unreflectGetter(f);
        assertSame(mh.type(), expType);
        assertEquals(mh.toString(), fname);
        HasFields fields = new HasFields();
        Object sawValue;
        Class<?> rtype = type;
        if (type != int.class)  rtype = Object.class;
        mh = MethodHandles.convertArguments(mh, mh.type().generic().changeReturnType(rtype));
        Object expValue = value;
        for (int i = 0; i <= 1; i++) {
            if (isStatic) {
                if (type == int.class)
                    sawValue = mh.<int>invokeExact();  // do these exactly
                else
                    sawValue = mh.invokeExact();
            } else {
                if (type == int.class)
                    sawValue = mh.<int>invokeExact((Object) fields);
                else
                    sawValue = mh.invokeExact((Object) fields);
            }
            assertEquals(sawValue, expValue);
            Object random = randomArg(type);
            f.set(fields, random);
            expValue = random;
        }
        f.set(fields, value);  // put it back
    }


    @Test
    public void testUnreflectSetter() throws Throwable {
        startTest("unreflectSetter");
        testSetter(TEST_UNREFLECT);
    }
    @Test
    public void testFindSetter() throws Throwable {
        startTest("findSetter");
        testSetter(TEST_FIND_FIELD);
    }
    @Test
    public void testFindStaticSetter() throws Throwable {
        startTest("findStaticSetter");
        testSetter(TEST_FIND_STATIC_FIELD);
    }
    public void testSetter(int testMode) throws Throwable {
        Lookup lookup = PRIVATE;  // FIXME: test more lookups than this one
        startTest("unreflectSetter");
        for (Object[] c : HasFields.CASES) {
            Field f = (Field)c[0];
            Object value = c[1];
            Class<?> type = f.getType();
            testSetter(lookup, f, type, value, testMode);
        }
    }
    public void testSetter(MethodHandles.Lookup lookup,
            Field f, Class<?> type, Object value, int testMode) throws Throwable {
        boolean isStatic = Modifier.isStatic(f.getModifiers());
        Class<?> fclass = f.getDeclaringClass();
        String   fname  = f.getName();
        Class<?> ftype  = f.getType();
        if (!testModeMatches(testMode, isStatic))  return;
        countTest(true);
        MethodType expType = MethodType.methodType(void.class, HasFields.class, type);
        if (isStatic)  expType = expType.dropParameterTypes(0, 1);
        MethodHandle mh;
        if (testMode == TEST_UNREFLECT)
            mh = lookup.unreflectSetter(f);
        else if (testMode == TEST_FIND_FIELD)
            mh = lookup.findSetter(fclass, fname, ftype);
        else if (testMode == TEST_FIND_STATIC_FIELD)
            mh = lookup.findStaticSetter(fclass, fname, ftype);
        else  throw new InternalError();
        assertSame(mh.type(), expType);
        assertEquals(mh.toString(), fname);
        HasFields fields = new HasFields();
        Object sawValue;
        Class<?> vtype = type;
        if (type != int.class)  vtype = Object.class;
        int last = mh.type().parameterCount() - 1;
        mh = MethodHandles.convertArguments(mh, mh.type().generic().changeReturnType(void.class).changeParameterType(last, vtype));
        assertEquals(f.get(fields), value);  // clean to start with
        for (int i = 0; i <= 1; i++) {
            Object putValue = randomArg(type);
            if (isStatic) {
                if (type == int.class)
                    mh.<void>invokeExact((int)(Integer)putValue);  // do these exactly
                else
                    mh.<void>invokeExact(putValue);
            } else {
                if (type == int.class)
                    mh.<void>invokeExact((Object) fields, (int)(Integer)putValue);
                else
                    mh.<void>invokeExact((Object) fields, putValue);
            }
            assertEquals(f.get(fields), putValue);
        }
        f.set(fields, value);  // put it back
    }

    @Test
    public void testArrayElementGetter() throws Throwable {
        startTest("arrayElementGetter");
        testArrayElementGetterSetter(false);
    }

    @Test
    public void testArrayElementSetter() throws Throwable {
        startTest("arrayElementSetter");
        testArrayElementGetterSetter(true);
    }

    public void testArrayElementGetterSetter(boolean testSetter) throws Throwable {
        testArrayElementGetterSetter(new Object[10], testSetter);
        testArrayElementGetterSetter(new String[10], testSetter);
        testArrayElementGetterSetter(new boolean[10], testSetter);
        testArrayElementGetterSetter(new byte[10], testSetter);
        testArrayElementGetterSetter(new char[10], testSetter);
        testArrayElementGetterSetter(new short[10], testSetter);
        testArrayElementGetterSetter(new int[10], testSetter);
        testArrayElementGetterSetter(new float[10], testSetter);
        testArrayElementGetterSetter(new long[10], testSetter);
        testArrayElementGetterSetter(new double[10], testSetter);
    }

    public void testArrayElementGetterSetter(Object array, boolean testSetter) throws Throwable {
        countTest(true);
        if (verbosity >= 2)  System.out.println("array type = "+array.getClass().getComponentType().getName()+"["+Array.getLength(array)+"]");
        Class<?> arrayType = array.getClass();
        Class<?> elemType = arrayType.getComponentType();
        MethodType expType = !testSetter
                ? MethodType.methodType(elemType,   arrayType, int.class)
                : MethodType.methodType(void.class, arrayType, int.class, elemType);
        MethodHandle mh = !testSetter
                ? MethodHandles.arrayElementGetter(arrayType)
                : MethodHandles.arrayElementSetter(arrayType);
        assertSame(mh.type(), expType);
        if (elemType != int.class && elemType != boolean.class) {
            MethodType gtype;
            if (true) { // FIXME: remove this path (and remove <void> below in the mh.invokes)
                gtype = mh.type().changeParameterType(0, Object.class);
                if (testSetter)
                    gtype = gtype.changeParameterType(2, Object.class);
                else
                    gtype = gtype.changeReturnType(Object.class);
            } else
                // FIXME: This simpler path hits a bug in convertArguments => ToGeneric
                gtype = mh.type().generic().changeParameterType(1, int.class);
            mh = MethodHandles.convertArguments(mh, gtype);
        }
        Object sawValue, expValue;
        List<Object> model = array2list(array);
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            // update array element
            Object random = randomArg(elemType);
            model.set(i, random);
            if (testSetter) {
                if (elemType == int.class)
                    mh.<void>invokeExact((int[]) array, i, (int)(Integer)random);
                else if (elemType == boolean.class)
                    mh.<void>invokeExact((boolean[]) array, i, (boolean)(Boolean)random);
                else
                    mh.<void>invokeExact(array, i, random);
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
                if (elemType == int.class)
                    sawValue = mh.<int>invokeExact((int[]) array, i);
                else if (elemType == boolean.class)
                    sawValue = mh.<boolean>invokeExact((boolean[]) array, i);
                else
                    sawValue = mh.invokeExact(array, i);
                assertEquals(sawValue, expValue);
                assertEquals(model, array2list(array));
            }
        }
    }

    List<Object> array2list(Object array) {
        int length = Array.getLength(array);
        ArrayList<Object> model = new ArrayList<Object>(length);
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
            return PRIVATE.findStatic(Callee.class, name, type);
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

    void testConvert(boolean positive, MethodHandle id, Class<?> rtype, String name, Class<?>... params) throws Throwable {
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
        Object convResult = id.invokeVarargs(convArgs);
        {
            Class<?> dst = newType.returnType();
            Class<?> src = idType.returnType();
            if (src != dst)
                convResult = castToWrapper(convResult, dst);
        }
        MethodHandle target = null;
        RuntimeException error = null;
        try {
            target = MethodHandles.convertArguments(id, newType);
        } catch (RuntimeException ex) {
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
        Object result = target.invokeVarargs(args);
        assertCalled(name, convArgs);
        assertEquals(convResult, result);
        if (verbosity >= 1)
            System.out.print(':');
    }

    @Test
    public void testPermuteArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("permuteArguments");
        testPermuteArguments(4, Integer.class,  2, String.class,  0);
        //testPermuteArguments(6, Integer.class,  0, null,         30);
        //testPermuteArguments(4, Integer.class,  1, int.class,     6);
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
                int[] reorder = new int[outargs];
                int casStep = dilution + 1;
                // Avoid some common factors:
                while ((casStep > 2 && casStep % 2 == 0 && inargs % 2 == 0) ||
                       (casStep > 3 && casStep % 3 == 0 && inargs % 3 == 0))
                    casStep++;
                for (int cas = 0; cas < numcases; cas += casStep) {
                    for (int i = 0, c = cas; i < outargs; i++) {
                        reorder[i] = c % inargs;
                        c /= inargs;
                    }
                    testPermuteArguments(args, types, reorder);
                }
                numcases *= inargs;
                if (dilution > 10 && outargs >= 4) {
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
        assert(inargs == types.length);
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
        MethodHandle target = MethodHandles.convertArguments(ValueConversions.varargsList(outargs), outType);
        MethodHandle newTarget = MethodHandles.permuteArguments(target, inType, reorder);
        Object result = newTarget.invokeVarargs(args);
        Object expected = Arrays.asList(permArgs);
        assertEquals(expected, result);
    }


    @Test
    public void testSpreadArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("spreadArguments");
        for (Class<?> argType : new Class[]{Object.class, Integer.class, int.class}) {
            if (verbosity >= 3)
                System.out.println("spreadArguments "+argType);
            // FIXME: enable _adapter_spread_args and fix Fail_2
            for (int nargs = 0; nargs < 10; nargs++) {
                if (argType == int.class && nargs >= 6)  continue; // FIXME Fail_1
                for (int pos = 0; pos < nargs; pos++) {
                    if (argType == int.class && pos > 0)  continue; // FIXME Fail_3
                     testSpreadArguments(argType, pos, nargs);
                }
            }
        }
    }
    public void testSpreadArguments(Class<?> argType, int pos, int nargs) throws Throwable {
        countTest();
        MethodHandle target = ValueConversions.varargsArray(nargs);
        MethodHandle target2 = changeArgTypes(target, argType);
        if (verbosity >= 3)
            System.out.println("spread into "+target2+" ["+pos+".."+nargs+"]");
        Object[] args = randomArgs(target2.type().parameterArray());
        // make sure the target does what we think it does:
        if (pos == 0 && nargs < 5) {
            Object[] check = (Object[]) target.invokeVarargs(args);
            assertArrayEquals(args, check);
            switch (nargs) {
                case 0:
                    check = target.<Object[]>invokeExact();
                    assertArrayEquals(args, check);
                    break;
                case 1:
                    check = target.<Object[]>invokeExact(args[0]);
                    assertArrayEquals(args, check);
                    break;
                case 2:
                    check = target.<Object[]>invokeExact(args[0], args[1]);
                    assertArrayEquals(args, check);
                    break;
            }
        }
        List<Class<?>> newParams = new ArrayList<Class<?>>(target2.type().parameterList());
        {   // modify newParams in place
            List<Class<?>> spreadParams = newParams.subList(pos, nargs);
            spreadParams.clear(); spreadParams.add(Object[].class);
        }
        MethodType newType = MethodType.methodType(Object.class, newParams);
        MethodHandle result = MethodHandles.spreadArguments(target2, newType);
        Object[] returnValue;
        if (pos == 0) {
            returnValue = (Object[]) result.invokeExact(args);
        } else {
            Object[] args1 = Arrays.copyOfRange(args, 0, pos+1);
            args1[pos] = Arrays.copyOfRange(args, pos, args.length);
            returnValue = (Object[]) result.invokeVarargs(args1);
        }
        assertArrayEquals(args, returnValue);
    }

    @Test
    public void testCollectArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("collectArguments");
        for (Class<?> argType : new Class[]{Object.class, Integer.class, int.class}) {
            if (verbosity >= 3)
                System.out.println("collectArguments "+argType);
            for (int nargs = 0; nargs < 10; nargs++) {
                for (int pos = 0; pos < nargs; pos++) {
                    if (argType == int.class)  continue; // FIXME Fail_4
                    testCollectArguments(argType, pos, nargs);
                }
            }
        }
    }
    public void testCollectArguments(Class<?> argType, int pos, int nargs) throws Throwable {
        countTest();
        // fake up a MH with the same type as the desired adapter:
        MethodHandle fake = ValueConversions.varargsArray(nargs);
        fake = changeArgTypes(fake, argType);
        MethodType newType = fake.type();
        Object[] args = randomArgs(newType.parameterArray());
        // here is what should happen:
        Object[] collectedArgs = Arrays.copyOfRange(args, 0, pos+1);
        collectedArgs[pos] = Arrays.copyOfRange(args, pos, args.length);
        // here is the MH which will witness the collected argument tail:
        MethodHandle target = ValueConversions.varargsArray(pos+1);
        target = changeArgTypes(target, 0, pos, argType);
        target = changeArgTypes(target, pos, pos+1, Object[].class);
        if (verbosity >= 3)
            System.out.println("collect from "+Arrays.asList(args)+" ["+pos+".."+nargs+"]");
        MethodHandle result = MethodHandles.collectArguments(target, newType);
        Object[] returnValue = (Object[]) result.invokeVarargs(args);
//        assertTrue(returnValue.length == pos+1 && returnValue[pos] instanceof Object[]);
//        returnValue[pos] = Arrays.asList((Object[]) returnValue[pos]);
//        collectedArgs[pos] = Arrays.asList((Object[]) collectedArgs[pos]);
        assertArrayEquals(collectedArgs, returnValue);
    }

    @Test
    public void testInsertArguments() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("insertArguments");
        for (int nargs = 0; nargs <= 4; nargs++) {
            for (int ins = 0; ins <= 4; ins++) {
                if (ins > MAX_ARG_INCREASE)  continue;  // FIXME Fail_6
                for (int pos = 0; pos <= nargs; pos++) {
                    testInsertArguments(nargs, pos, ins);
                }
            }
        }
    }

    void testInsertArguments(int nargs, int pos, int ins) throws Throwable {
        countTest();
        MethodHandle target = ValueConversions.varargsArray(nargs + ins);
        Object[] args = randomArgs(target.type().parameterArray());
        List<Object> resList = Arrays.asList(args);
        List<Object> argsToPass = new ArrayList<Object>(resList);
        List<Object> argsToInsert = argsToPass.subList(pos, pos + ins);
        if (verbosity >= 3)
            System.out.println("insert: "+argsToInsert+" into "+target);
        MethodHandle target2 = MethodHandles.insertArguments(target, pos,
                (Object[]) argsToInsert.toArray());
        argsToInsert.clear();  // remove from argsToInsert
        Object res2 = target2.invokeVarargs(argsToPass);
        Object res2List = Arrays.asList((Object[])res2);
        if (verbosity >= 3)
            System.out.println("result: "+res2List);
        //if (!resList.equals(res2List))
        //    System.out.println("*** fail at n/p/i = "+nargs+"/"+pos+"/"+ins+": "+resList+" => "+res2List);
        assertEquals(resList, res2List);
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
        MethodHandle target = ValueConversions.varargsList(nargs);
        MethodHandle filter = ValueConversions.varargsList(1);
        filter = MethodHandles.convertArguments(filter, filter.type().generic());
        Object[] argsToPass = randomArgs(nargs, Object.class);
        if (verbosity >= 3)
            System.out.println("filter "+target+" at "+pos+" with "+filter);
        MethodHandle[] filters = new MethodHandle[pos*2+1];
        filters[pos] = filter;
        MethodHandle target2 = MethodHandles.filterArguments(target, filters);
        // Simulate expected effect of filter on arglist:
        Object[] filteredArgs = argsToPass.clone();
        filteredArgs[pos] = filter.invokeExact(filteredArgs[pos]);
        List<Object> expected = Arrays.asList(filteredArgs);
        Object result = target2.invokeVarargs(argsToPass);
        if (verbosity >= 3)
            System.out.println("result: "+result);
        if (!expected.equals(result))
            System.out.println("*** fail at n/p = "+nargs+"/"+pos+": "+argsToPass+" => "+result);
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
        MethodHandle target = ValueConversions.varargsList(1 + nargs);
        MethodHandle combine = ValueConversions.varargsList(fold);
        List<Object> argsToPass = Arrays.asList(randomArgs(nargs, Object.class));
        if (verbosity >= 3)
            System.out.println("fold "+target+" with "+combine);
        MethodHandle target2 = MethodHandles.foldArguments(target, combine);
        // Simulate expected effect of combiner on arglist:
        List<Object> expected = new ArrayList<Object>(argsToPass);
        List<Object> argsToFold = expected.subList(pos, pos + fold);
        if (verbosity >= 3)
            System.out.println("fold: "+argsToFold+" into "+target2);
        Object foldedArgs = combine.invokeVarargs(argsToFold);
        argsToFold.add(0, foldedArgs);
        Object result = target2.invokeVarargs(argsToPass);
        if (verbosity >= 3)
            System.out.println("result: "+result);
        if (!expected.equals(result))
            System.out.println("*** fail at n/p/f = "+nargs+"/"+pos+"/"+fold+": "+argsToPass+" => "+result);
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
        MethodHandle target = ValueConversions.varargsArray(nargs);
        Object[] args = randomArgs(target.type().parameterArray());
        MethodHandle target2 = MethodHandles.dropArguments(target, pos,
                Collections.nCopies(drop, Object.class).toArray(new Class[0]));
        List<Object> resList = Arrays.asList(args);
        List<Object> argsToDrop = new ArrayList<Object>(resList);
        for (int i = drop; i > 0; i--) {
            argsToDrop.add(pos, "blort#"+i);
        }
        Object res2 = target2.invokeVarargs(argsToDrop);
        Object res2List = Arrays.asList((Object[])res2);
        //if (!resList.equals(res2List))
        //    System.out.println("*** fail at n/p/d = "+nargs+"/"+pos+"/"+drop+": "+argsToDrop+" => "+res2List);
        assertEquals(resList, res2List);
    }

    @Test
    public void testInvokers() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("exactInvoker, genericInvoker, varargsInvoker, dynamicInvoker");
        // exactInvoker, genericInvoker, varargsInvoker[0..N], dynamicInvoker
        Set<MethodType> done = new HashSet<MethodType>();
        for (int i = 0; i <= 6; i++) {
            MethodType gtype = MethodType.genericMethodType(i);
            for (Class<?> argType : new Class[]{Object.class, Integer.class, int.class}) {
                for (int j = -1; j < i; j++) {
                    MethodType type = gtype;
                    if (j < 0)
                        type = type.changeReturnType(argType);
                    else if (argType == void.class)
                        continue;
                    else
                        type = type.changeParameterType(j, argType);
                    if (argType.isPrimitive() && j != i-1)  continue; // FIXME Fail_5
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
        target = MethodHandles.collectArguments(target, type);
        Object[] args = randomArgs(type.parameterArray());
        List<Object> targetPlusArgs = new ArrayList<Object>(Arrays.asList(args));
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
        result = inv.invokeVarargs(targetPlusArgs);
        if (testRetCode)  assertEquals(code, result);
        assertCalled("invokee", args);
        // generic invoker
        countTest();
        inv = MethodHandles.genericInvoker(type);
        if (nargs <= 3) {
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
        result = inv.invokeVarargs(targetPlusArgs);
        if (testRetCode)  assertEquals(code, result);
        assertCalled("invokee", args);
        // varargs invoker #0
        calledLog.clear();
        inv = MethodHandles.varargsInvoker(type, 0);
        result = inv.invokeExact(target, args);
        if (testRetCode)  assertEquals(code, result);
        assertCalled("invokee", args);
        if (nargs >= 1) {
            // varargs invoker #1
            calledLog.clear();
            inv = MethodHandles.varargsInvoker(type, 1);
            result = inv.invokeExact(target, args[0], Arrays.copyOfRange(args, 1, nargs));
            if (testRetCode)  assertEquals(code, result);
            assertCalled("invokee", args);
        }
        if (nargs >= 2) {
            // varargs invoker #2
            calledLog.clear();
            inv = MethodHandles.varargsInvoker(type, 2);
            result = inv.invokeExact(target, args[0], args[1], Arrays.copyOfRange(args, 2, nargs));
            if (testRetCode)  assertEquals(code, result);
            assertCalled("invokee", args);
        }
        if (nargs >= 3) {
            // varargs invoker #3
            calledLog.clear();
            inv = MethodHandles.varargsInvoker(type, 3);
            result = inv.invokeExact(target, args[0], args[1], args[2], Arrays.copyOfRange(args, 3, nargs));
            if (testRetCode)  assertEquals(code, result);
            assertCalled("invokee", args);
        }
        for (int k = 0; k <= nargs; k++) {
            // varargs invoker #0..N
            countTest();
            calledLog.clear();
            inv = MethodHandles.varargsInvoker(type, k);
            List<Object> targetPlusVarArgs = new ArrayList<Object>(targetPlusArgs);
            List<Object> tailList = targetPlusVarArgs.subList(1+k, 1+nargs);
            Object[] tail = tailList.toArray();
            tailList.clear(); tailList.add(tail);
            result = inv.invokeVarargs(targetPlusVarArgs);
            if (testRetCode)  assertEquals(code, result);
            assertCalled("invokee", args);
        }
        // dynamic invoker
        countTest();
        CallSite site = new CallSite(MethodHandlesTest.class, "foo", type);
        inv = MethodHandles.dynamicInvoker(site);
        site.setTarget(target);
        calledLog.clear();
        result = inv.invokeVarargs(args);
        if (testRetCode)  assertEquals(code, result);
        assertCalled("invokee", args);
    }

    static Object invokee(Object... args) {
        return called("invokee", args).hashCode();
    }

    private static final String MISSING_ARG = "missingArg";
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
        for (int nargs = 0; nargs <= 3; nargs++) {
            if (nargs != 2)  continue;  // FIXME: test more later
            testGuardWithTest(nargs, Object.class);
            testGuardWithTest(nargs, String.class);
        }
    }
    void testGuardWithTest(int nargs, Class<?> argClass) throws Throwable {
        countTest();
        MethodHandle test = PRIVATE.findVirtual(Object.class, "equals", MethodType.methodType(boolean.class, Object.class));
        MethodHandle target = PRIVATE.findStatic(MethodHandlesTest.class, "targetIfEquals", MethodType.genericMethodType(nargs));
        MethodHandle fallback = PRIVATE.findStatic(MethodHandlesTest.class, "fallbackIfNotEquals", MethodType.genericMethodType(nargs));
        while (test.type().parameterCount() < nargs)
            test = MethodHandles.dropArguments(test, test.type().parameterCount()-1, Object.class);
        while (test.type().parameterCount() > nargs)
            test = MethodHandles.insertArguments(test, 0, MISSING_ARG);
        if (argClass != Object.class) {
            test = changeArgTypes(test, argClass);
            target = changeArgTypes(target, argClass);
            fallback = changeArgTypes(fallback, argClass);
        }
        MethodHandle mh = MethodHandles.guardWithTest(test, target, fallback);
        assertEquals(target.type(), mh.type());
        Object[][] argLists = {
            { },
            { "foo" }, { MISSING_ARG },
            { "foo", "foo" }, { "foo", "bar" },
            { "foo", "foo", "baz" }, { "foo", "bar", "baz" }
        };
        for (Object[] argList : argLists) {
            if (argList.length != nargs)  continue;
            boolean equals;
            switch (nargs) {
            case 0:   equals = true; break;
            case 1:   equals = MISSING_ARG.equals(argList[0]); break;
            default:  equals = argList[0].equals(argList[1]); break;
            }
            String willCall = (equals ? "targetIfEquals" : "fallbackIfNotEquals");
            if (verbosity >= 3)
                System.out.println(logEntry(willCall, argList));
            Object result = mh.invokeVarargs(argList);
            assertCalled(willCall, argList);
        }
    }

    @Test
    public void testCatchException() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("catchException");
        for (int nargs = 2; nargs <= 6; nargs++) {
            for (int ti = 0; ti <= 1; ti++) {
                boolean throwIt = (ti != 0);
                testCatchException(int.class, new ClassCastException("testing"), throwIt, nargs);
                testCatchException(void.class, new java.io.IOException("testing"), throwIt, nargs);
                testCatchException(String.class, new LinkageError("testing"), throwIt, nargs);
            }
        }
    }

    private static <T extends Throwable>
    Object throwOrReturn(Object normal, T exception) throws T {
        if (exception != null)  throw exception;
        return normal;
    }

    void testCatchException(Class<?> returnType, Throwable thrown, boolean throwIt, int nargs) throws Throwable {
        countTest();
        if (verbosity >= 3)
            System.out.println("catchException rt="+returnType+" throw="+throwIt+" nargs="+nargs);
        Class<? extends Throwable> exType = thrown.getClass();
        MethodHandle throwOrReturn
                = PRIVATE.findStatic(MethodHandlesTest.class, "throwOrReturn",
                    MethodType.methodType(Object.class, Object.class, Throwable.class));
        MethodHandle thrower = throwOrReturn;
        while (thrower.type().parameterCount() < nargs)
            thrower = MethodHandles.dropArguments(thrower, thrower.type().parameterCount(), Object.class);
        MethodHandle target = MethodHandles.catchException(thrower,
                thrown.getClass(), ValueConversions.varargsList(1+nargs));
        assertEquals(thrower.type(), target.type());
        //System.out.println("catching with "+target+" : "+throwOrReturn);
        Object[] args = randomArgs(nargs, Object.class);
        args[1] = (throwIt ? thrown : null);
        Object returned = target.invokeVarargs(args);
        //System.out.println("return from "+target+" : "+returned);
        if (!throwIt) {
            assertSame(args[0], returned);
        } else {
            List<Object> catchArgs = new ArrayList<Object>(Arrays.asList(args));
            catchArgs.add(0, thrown);
            assertEquals(catchArgs, returned);
        }
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
        target = MethodHandles.convertArguments(target, target.type().generic());
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
    public void testCastFailure() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("testCastFailure");
        testCastFailure("cast/argument", 11000);
        testCastFailure("unbox/argument", 11000);
        testCastFailure("cast/return", 11000);
        testCastFailure("unbox/return", 11000);
    }

    static class Surprise extends JavaMethodHandle {
        Surprise() { super("value"); }
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
        static MethodHandle REF_IDENTITY = PRIVATE.findStatic(
                Surprise.class, "refIdentity",
                    MethodType.methodType(Object.class, Object.class));
        static MethodHandle BOX_IDENTITY = PRIVATE.findStatic(
                Surprise.class, "boxIdentity",
                    MethodType.methodType(Integer.class, Integer.class));
        static MethodHandle INT_IDENTITY = PRIVATE.findStatic(
                Surprise.class, "intIdentity",
                    MethodType.methodType(int.class, int.class));
    }

    void testCastFailure(String mode, int okCount) throws Throwable {
        countTest(false);
        if (verbosity > 2)  System.out.println("mode="+mode);
        Surprise boo = new Surprise();
        MethodHandle identity = Surprise.REF_IDENTITY, surprise = boo;
        if (mode.endsWith("/return")) {
            if (mode.equals("unbox/return")) {
                // fail on return to ((Integer)surprise).intValue
                surprise = MethodHandles.convertArguments(surprise, MethodType.methodType(int.class, Object.class));
                identity = MethodHandles.convertArguments(identity, MethodType.methodType(int.class, Object.class));
            } else if (mode.equals("cast/return")) {
                // fail on return to (Integer)surprise
                surprise = MethodHandles.convertArguments(surprise, MethodType.methodType(Integer.class, Object.class));
                identity = MethodHandles.convertArguments(identity, MethodType.methodType(Integer.class, Object.class));
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
                callee = MethodHandles.convertArguments(callee, MethodType.genericMethodType(1));
                surprise = MethodHandles.filterArguments(callee, surprise);
                identity = MethodHandles.filterArguments(callee, identity);
            }
        }
        assertNotSame(mode, surprise, boo);
        identity = MethodHandles.convertArguments(identity, MethodType.genericMethodType(1));
        surprise = MethodHandles.convertArguments(surprise, MethodType.genericMethodType(1));
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
        } catch (Exception ex) {
            if (verbosity > 2)
                System.out.println("caught "+ex);
            if (verbosity > 3)
                ex.printStackTrace();
            assertTrue(ex instanceof ClassCastException
                    // FIXME: accept only one of the two for any given unit test
                    || ex instanceof WrongMethodTypeException
                    );
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
        mh.invokeVarargs(args);
        assertCalled(name, args);

        // Try a virtual method.
        name = "v2";
        mt = MethodType.methodType(Object.class, Object.class, int.class);
        mh = lookup.findVirtual(Example.class, name, mt);
        assertEquals(mt, mh.type().dropParameterTypes(0,1));
        assertTrue(mh.type().parameterList().contains(Example.class));
        args = randomArgs(mh.type().parameterArray());
        mh.invokeVarargs(args);
        assertCalled(name, args);
    }
}
// Local abbreviated copy of sun.dyn.util.ValueConversions
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
        ArrayList<MethodHandle> arrays = new ArrayList<MethodHandle>();
        MethodHandles.Lookup lookup = IMPL_LOOKUP;
        for (;;) {
            int nargs = arrays.size();
            MethodType type = MethodType.genericMethodType(nargs).changeReturnType(Object[].class);
            String name = "array";
            MethodHandle array = null;
            try {
                array = lookup.findStatic(ValueConversions.class, name, type);
            } catch (NoAccessException ex) {
            }
            if (array == null)  break;
            arrays.add(array);
        }
        assert(arrays.size() == 11);  // current number of methods
        return arrays.toArray(new MethodHandle[0]);
    }
    static final MethodHandle[] ARRAYS = makeArrays();

    /** Return a method handle that takes the indicated number of Object
     *  arguments and returns an Object array of them, as if for varargs.
     */
    public static MethodHandle varargsArray(int nargs) {
        if (nargs < ARRAYS.length)
            return ARRAYS[nargs];
        // else need to spin bytecode or do something else fancy
        throw new UnsupportedOperationException("NYI: cannot form a varargs array of length "+nargs);
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
        ArrayList<MethodHandle> arrays = new ArrayList<MethodHandle>();
        MethodHandles.Lookup lookup = IMPL_LOOKUP;
        for (;;) {
            int nargs = arrays.size();
            MethodType type = MethodType.genericMethodType(nargs).changeReturnType(List.class);
            String name = "list";
            MethodHandle array = null;
            try {
                array = lookup.findStatic(ValueConversions.class, name, type);
            } catch (NoAccessException ex) {
            }
            if (array == null)  break;
            arrays.add(array);
        }
        assert(arrays.size() == 11);  // current number of methods
        return arrays.toArray(new MethodHandle[0]);
    }
    static final MethodHandle[] LISTS = makeLists();

    /** Return a method handle that takes the indicated number of Object
     *  arguments and returns List.
     */
    public static MethodHandle varargsList(int nargs) {
        if (nargs < LISTS.length)
            return LISTS[nargs];
        // else need to spin bytecode or do something else fancy
        throw new UnsupportedOperationException("NYI");
    }
}
// This guy tests access from outside the same package member, but inside
// the package itself.
class PackageSibling {
    static Lookup lookup() {
        return MethodHandles.lookup();
    }
}
