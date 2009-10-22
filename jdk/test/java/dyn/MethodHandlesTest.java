/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableInvokeDynamic jdk.java.dyn.MethodHandlesTest
 */

package jdk.java.dyn;

import java.dyn.*;
import java.dyn.MethodHandles.Lookup;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;


/**
 *
 * @author jrose
 */
public class MethodHandlesTest {
    // How much output?
    static int verbosity = 1;

    // Set this true during development if you want to fast-forward to
    // a particular new, non-working test.  Tests which are known to
    // work (or have recently worked) test this flag and return on true.
    static boolean CAN_SKIP_WORKING = false;
    //static { CAN_SKIP_WORKING = true; }

    // Set true to test more calls.  If false, some tests are just
    // lookups, without exercising the actual method handle.
    static boolean DO_MORE_CALLS = false;


    @Test
    public void testFirst() throws Throwable {
        verbosity += 9; try {
            // left blank for debugging
        } finally { verbosity -= 9; }
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
        if (arch.equals("i386") &&
            (name.contains("Client") || name.contains("Server"))
            ) {
            platformOK = true;
        } else {
            System.err.println("Skipping tests for unsupported platform: "+Arrays.asList(vers, name, arch));
        }
        assumeTrue(platformOK);
    }

    String testName;
    int posTests, negTests;
    @After
    public void printCounts() {
        if (verbosity >= 1 && (posTests | negTests) != 0) {
            System.out.println();
            if (posTests != 0)  System.out.println("=== "+testName+": "+posTests+" positive test cases run");
            if (negTests != 0)  System.out.println("=== "+testName+": "+negTests+" negative test cases run");
        }
    }
    void countTest(boolean positive) {
        if (positive) ++posTests;
        else          ++negTests;
    }
    void countTest() { countTest(true); }
    void startTest(String name) {
        if (testName != null)  printCounts();
        if (verbosity >= 0)
            System.out.println(name);
        posTests = negTests = 0;
        testName = name;
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        calledLog.clear();
        calledLog.add(null);
        nextArg = 1000000;
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
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
        if (expected.equals(actual))  return;
        System.out.println("assertCalled "+name+":");
        System.out.println("expected:   "+expected);
        System.out.println("actual:     "+actual);
        System.out.println("ex. types:  "+getClasses(expected));
        System.out.println("act. types: "+getClasses(actual));
        assertEquals("previous method call types", expected, actual);
        assertEquals("previous method call", expected, actual);
    }
    static void printCalled(MethodHandle target, String name, Object... args) {
        if (verbosity >= 2)
            System.out.println("calling "+logEntry(name, args)+" on "+target);
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
        return null;
    }

    static int nextArg;
    static Object randomArg(Class<?> param) {
        Object wrap = castToWrapperOrNull(nextArg, param);
        if (wrap != null) {
            nextArg++;
            return wrap;
        }
//        import sun.dyn.util.Wrapper;
//        Wrapper wrap = Wrapper.forBasicType(dst);
//        if (wrap == Wrapper.OBJECT && Wrapper.isWrapperType(dst))
//            wrap = Wrapper.forWrapperType(dst);
//        if (wrap != Wrapper.OBJECT)
//            return wrap.wrap(nextArg++);
        if (param.isInterface() || param.isAssignableFrom(String.class))
            return "#"+(nextArg++);
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
        MethodType ttype2 = MethodType.make(targetType.returnType(), argTypes);
        return MethodHandles.convertArguments(target, ttype2);
    }

    // This lookup is good for all members in and under MethodHandlesTest.
    static final Lookup PRIVATE = MethodHandles.lookup();
    // This lookup is good for package-private members but not private ones.
    static final Lookup PACKAGE = PackageSibling.lookup();
    // This lookup is good only for public members.
    static final Lookup PUBLIC  = MethodHandles.Lookup.PUBLIC_LOOKUP;

    // Subject methods...
    static class Example implements IntExample {
        final String name;
        public Example() { name = "Example#"+(nextArg++); }
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
    }
    public static class PubExample extends Example {
    }
    static class SubExample extends Example {
        @Override public void  v0()     { called("Sub/v0", this); }
        @Override void         pkg_v0() { called("Sub/pkg_v0", this); }
        private      SubExample(int x)  { called("<init>", this, x); }
        public SubExample() { super("SubExample#"+(nextArg++)); }
    }
    public static interface IntExample {
        public void            v0();
        static class Impl implements IntExample {
            public void        v0()     { called("Int/v0", this); }
            final String name;
            public Impl() { name = "Example#"+(nextArg++); }
        }
    }

    static final Object[][][] ACCESS_CASES = {
        { { true,  PRIVATE } }  // only one test case at present
    };

    static Object[][] accessCases(Class<?> defc, String name) {
        return ACCESS_CASES[0];
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
        MethodType type = MethodType.make(ret, params);
        MethodHandle target = null;
        RuntimeException noAccess = null;
        try {
            target = lookup.findStatic(defc, name, type);
        } catch (NoAccessException ex) {
            noAccess = ex;
        }
        if (verbosity >= 2)
            System.out.println("findStatic "+lookup+": "+defc+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(type, target.type());
        assertTrue(target.toString().contains(name));  // rough check
        if (!DO_MORE_CALLS && lookup != PRIVATE)  return;
        Object[] args = randomArgs(params);
        printCalled(target, name, args);
        MethodHandles.invoke(target, args);
        assertCalled(name, args);
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
        MethodType type = MethodType.make(ret, params);
        MethodHandle target = null;
        RuntimeException noAccess = null;
        try {
            target = lookup.findVirtual(defc, methodName, type);
        } catch (NoAccessException ex) {
            noAccess = ex;
        }
        if (verbosity >= 2)
            System.out.println("findVirtual "+lookup+": "+defc+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        Class<?>[] paramsWithSelf = cat(array(Class[].class, (Class)defc), params);
        MethodType typeWithSelf = MethodType.make(ret, paramsWithSelf);
        MethodType ttype = target.type();
        ttype = ttype.changeParameterType(0, defc); // FIXME: test this
        assertEquals(typeWithSelf, ttype);
        assertTrue(target.toString().contains(methodName));  // rough check
        if (!DO_MORE_CALLS && lookup != PRIVATE)  return;
        Object[] argsWithSelf = randomArgs(paramsWithSelf);
        if (rcvc != defc)  argsWithSelf[0] = randomArg(rcvc);
        printCalled(target, name, argsWithSelf);
        MethodHandles.invoke(target, argsWithSelf);
        assertCalled(name, argsWithSelf);
        System.out.print(':');
    }

    @Test
    public void testFindSpecial() throws Throwable {
        if (CAN_SKIP_WORKING)  return;
        startTest("findSpecial");
        testFindSpecial(Example.class, void.class, "v0");
        testFindSpecial(Example.class, void.class, "pkg_v0");
        testFindSpecial(false, PRIVATE, Example.class, void.class, "<init>", int.class);
        testFindSpecial(false, PRIVATE, Example.class, void.class, "bogus");
    }

    void testFindSpecial(Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        testFindSpecial(true,  PRIVATE, defc, ret, name, params);
        testFindSpecial(false, PACKAGE, defc, ret, name, params);
        testFindSpecial(false, PUBLIC,  defc, ret, name, params);
    }
    void testFindSpecial(boolean positive, Lookup lookup, Class<?> defc, Class<?> ret, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        MethodType type = MethodType.make(ret, params);
        MethodHandle target = null;
        RuntimeException noAccess = null;
        try {
            target = lookup.findSpecial(defc, name, type, defc);
        } catch (NoAccessException ex) {
            noAccess = ex;
        }
        if (verbosity >= 2)
            System.out.println("findSpecial "+defc+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        Class<?>[] paramsWithSelf = cat(array(Class[].class, (Class)defc), params);
        MethodType typeWithSelf = MethodType.make(ret, paramsWithSelf);
        MethodType ttype = target.type();
        ttype = ttype.changeParameterType(0, defc); // FIXME: test this
        assertEquals(typeWithSelf, ttype);
        assertTrue(target.toString().contains(name));  // rough check
        if (!DO_MORE_CALLS && lookup != PRIVATE)  return;
        Object[] args = randomArgs(paramsWithSelf);
        printCalled(target, name, args);
        MethodHandles.invoke(target, args);
        assertCalled(name, args);
        System.out.print(':');
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
        MethodType type = MethodType.make(ret, params);
        Object receiver = randomArg(defc);
        MethodHandle target = null;
        RuntimeException noAccess = null;
        try {
            target = lookup.bind(receiver, methodName, type);
        } catch (NoAccessException ex) {
            noAccess = ex;
        }
        if (verbosity >= 2)
            System.out.println("bind "+receiver+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(type, target.type());
        Object[] args = randomArgs(params);
        printCalled(target, name, args);
        MethodHandles.invoke(target, args);
        Object[] argsWithReceiver = cat(array(Object[].class, receiver), args);
        assertCalled(name, argsWithReceiver);
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
        //testUnreflect(Example.class, true, Object.class, "s3", long.class);
        //testUnreflect(Example.class, true, Object.class, "s4", int.class, int.class);
        //testUnreflect(Example.class, true, Object.class, "s5", long.class, int.class);
        //testUnreflect(Example.class, true, Object.class, "s6", int.class, long.class);

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
            testUnreflect((Boolean)ac[0], (Lookup)ac[1], defc, isStatic, ret, name, params);
        }
    }
    void testUnreflect(boolean positive, Lookup lookup, Class<?> defc, boolean isStatic, Class<?> ret, String name, Class<?>... params) throws Throwable {
        countTest(positive);
        MethodType type = MethodType.make(ret, params);
        Method rmethod = null;
        MethodHandle target = null;
        RuntimeException noAccess = null;
        try {
            rmethod = defc.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException ex) {
            throw new NoAccessException(ex);
        }
        assertEquals(isStatic, Modifier.isStatic(rmethod.getModifiers()));
        try {
            target = lookup.unreflect(rmethod);
        } catch (NoAccessException ex) {
            noAccess = ex;
        }
        if (verbosity >= 2)
            System.out.println("unreflect "+defc+"."+name+"/"+type+" => "+target
                    +(noAccess == null ? "" : " !! "+noAccess));
        if (positive && noAccess != null)  throw noAccess;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        Class<?>[] paramsMaybeWithSelf = params;
        if (!isStatic) {
            paramsMaybeWithSelf = cat(array(Class[].class, (Class)defc), params);
        }
        MethodType typeMaybeWithSelf = MethodType.make(ret, paramsMaybeWithSelf);
        MethodType ttype = target.type();
        if (!isStatic)
            ttype = ttype.changeParameterType(0, defc); // FIXME: test this
        assertEquals(typeMaybeWithSelf, ttype);
        Object[] argsMaybeWithSelf = randomArgs(paramsMaybeWithSelf);
        printCalled(target, name, argsMaybeWithSelf);
        MethodHandles.invoke(target, argsMaybeWithSelf);
        assertCalled(name, argsMaybeWithSelf);
        System.out.print(':');
    }

    @Test @Ignore("unimplemented")
    public void testUnreflectSpecial() throws Throwable {
        Lookup lookup = PRIVATE;  // FIXME: test more lookups than this one
        startTest("unreflectSpecial");
        Method m = null;
        MethodHandle expResult = null;
        MethodHandle result = lookup.unreflectSpecial(m, Example.class);
        assertEquals(expResult, result);
        fail("The test case is a prototype.");
    }

    @Test @Ignore("unimplemented")
    public void testUnreflectGetter() throws Throwable {
        Lookup lookup = PRIVATE;  // FIXME: test more lookups than this one
        startTest("unreflectGetter");
        Field f = null;
        MethodHandle expResult = null;
        MethodHandle result = lookup.unreflectGetter(f);
        assertEquals(expResult, result);
        fail("The test case is a prototype.");
    }

    @Test @Ignore("unimplemented")
    public void testUnreflectSetter() throws Throwable {
        Lookup lookup = PRIVATE;  // FIXME: test more lookups than this one
        startTest("unreflectSetter");
        Field f = null;
        MethodHandle expResult = null;
        MethodHandle result = lookup.unreflectSetter(f);
        assertEquals(expResult, result);
        fail("The test case is a prototype.");
    }

    @Test @Ignore("unimplemented")
    public void testArrayElementGetter() throws Throwable {
        startTest("arrayElementGetter");
        Class<?> arrayClass = null;
        MethodHandle expResult = null;
        MethodHandle result = MethodHandles.arrayElementGetter(arrayClass);
        assertEquals(expResult, result);
        fail("The test case is a prototype.");
    }

    @Test @Ignore("unimplemented")
    public void testArrayElementSetter() throws Throwable {
        startTest("arrayElementSetter");
        Class<?> arrayClass = null;
        MethodHandle expResult = null;
        MethodHandle result = MethodHandles.arrayElementSetter(arrayClass);
        assertEquals(expResult, result);
        fail("The test case is a prototype.");
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
                return ofType(MethodType.make(rtype, Object[].class));
            return ofType(MethodType.makeGeneric(n).changeReturnType(rtype));
        }
        static MethodHandle ofType(Class<?> rtype, Class<?>... ptypes) {
            return ofType(MethodType.make(rtype, ptypes));
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
        MethodType newType = MethodType.make(rtype, params);
        Object[] args = randomArgs(newType.parameterArray());
        Object[] convArgs = args.clone();
        for (int i = 0; i < args.length; i++) {
            Class<?> src = newType.parameterType(i);
            Class<?> dst = idType.parameterType(i);
            if (src != dst)
                convArgs[i] = castToWrapper(convArgs[i], dst);
        }
        Object convResult = MethodHandles.invoke(id, convArgs);
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
        if (verbosity >= 2)
            System.out.println("convert "+id+ " to "+newType+" => "+target
                    +(error == null ? "" : " !! "+error));
        if (positive && error != null)  throw error;
        assertEquals(positive ? "positive test" : "negative test erroneously passed", positive, target != null);
        if (!positive)  return; // negative test failed as expected
        assertEquals(newType, target.type());
        printCalled(target, id.toString(), args);
        Object result = MethodHandles.invoke(target, args);
        assertCalled(name, convArgs);
        assertEquals(convResult, result);
        System.out.print(':');
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
        if (pos != 0 || ins != 1)  return;  // temp. restriction until MHs.insertArguments
        countTest();
        MethodHandle target = ValueConversions.varargsArray(nargs + ins);
        Object[] args = randomArgs(target.type().parameterArray());
        List<Object> resList = Arrays.asList(args);
        List<Object> argsToPass = new ArrayList<Object>(resList);
        List<Object> argsToInsert = argsToPass.subList(pos, pos + ins);
        if (verbosity >= 2)
            System.out.println("insert: "+argsToInsert+" into "+target);
        MethodHandle target2 = MethodHandles.insertArgument(target, pos,
                argsToInsert.get(0));
        argsToInsert.clear();  // remove from argsToInsert
        Object res2 = MethodHandles.invoke(target2, argsToPass.toArray());
        Object res2List = Arrays.asList((Object[])res2);
        if (verbosity >= 2)
            System.out.println("result: "+res2List);
        //if (!resList.equals(res2List))
        //    System.out.println("*** fail at n/p/i = "+nargs+"/"+pos+"/"+ins+": "+resList+" => "+res2List);
        assertEquals(resList, res2List);
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
            MethodType type = MethodType.makeGeneric(nargs).changeReturnType(Object[].class);
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

