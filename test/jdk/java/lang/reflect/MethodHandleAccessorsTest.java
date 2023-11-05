/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8271820 8300924
 * @modules java.base/jdk.internal.reflect
 * @summary Test compliance of ConstructorAccessor, FieldAccessor, MethodAccessor implementations
 * @run testng/othervm --add-exports java.base/jdk.internal.reflect=ALL-UNNAMED -XX:-ShowCodeDetailsInExceptionMessages MethodHandleAccessorsTest
 */

import jdk.internal.reflect.ConstructorAccessor;
import jdk.internal.reflect.FieldAccessor;
import jdk.internal.reflect.MethodAccessor;
import jdk.internal.reflect.Reflection;
import jdk.internal.reflect.ReflectionFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class MethodHandleAccessorsTest {
    public static void public_static_V() {}

    public static int public_static_I() { return 42; }

    public void public_V() {}

    public int public_I() { return 42; }

    public static void public_static_I_V(int i) {}

    public static int public_static_I_I(int i) { return i; }

    public static void public_static_V_L3(Object o1, Object o2, Object o3) { }

    public static void public_static_V_L4(Object o1, Object o2, Object o3, Object o4) { }

    public static void public_V_L5(Object o1, Object o2, Object o3, Object o4, Object o5) { }

    public void public_I_V(int i) {}

    public int public_I_I(int i) { return i; }

    private static void private_static_V() {}

    private static int private_static_I() { return 42; }

    private void private_V() {}

    private int private_I() { return 42; }

    private static void private_static_I_V(int i) {}

    private static int private_static_I_I(int i) { return i; }

    private void private_I_V(int i) {}

    private int private_I_I(int i) { return i; }

    public static int varargs(int... values) {
        int sum = 0;
        for (int i : values) sum += i;
        return sum;

    }
    public static int varargs_primitive(int first, int... rest) {
        int sum = first;
        if (rest != null) {
            sum *= 100;
            for (int i : rest) sum += i;
        }
        return sum;
    }

    public static String varargs_object(String first, String... rest) {
        StringBuilder sb = new StringBuilder(first);
        if (rest != null) {
            sb.append(Stream.of(rest).collect(Collectors.joining(",", "[", "]")));
        }
        return sb.toString();
    }

    public static final class Public {
        public static final int STATIC_FINAL = 1;
        private final int i;
        private final String s;
        private static String name = "name";
        private byte b = 9;

        public Public() {
            this.i = 0;
            this.s = null;
        }

        public Public(int i) {
            this.i = i;
            this.s = null;
        }

        public Public(String s) {
            this.i = 0;
            this.s = s;
        }
        public Public(byte b) {
            this.b = b;
            this.i = 0;
            this.s = null;
        }

        public Public(int first, int... rest) {
            this(varargs_primitive(first, rest));
        }

        public Public(String first, String... rest) {
            this(varargs_object(first, rest));
        }

        public Public(Object o1, Object o2, Object o3) {
            this("3-arg constructor");
        }

        public Public(Object o1, Object o2, Object o3, Object o4) {
            this("4-arg constructor");
        }

        public Public(RuntimeException exc) {
            throw exc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Public other = (Public) o;
            return i == other.i &&
                   Objects.equals(s, other.s);
        }

        @Override
        public int hashCode() {
            return Objects.hash(i, s);
        }

        @Override
        public String toString() {
            return "Public{" +
                   "i=" + i +
                   ", s='" + s + '\'' +
                   ", b=" + b +
                   '}';
        }
    }

    static final class Private {
        private final int i;

        private Private() {
            this.i = 0;
        }

        private Private(int i) {
            this.i = i;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Private other = (Private) o;
            return i == other.i;
        }

        @Override
        public int hashCode() {
            return Objects.hash(i);
        }

        @Override
        public String toString() {
            return "Private{" +
                   "i=" + i +
                   '}';
        }
    }

    static final class Thrower {
        public Thrower(RuntimeException exc) {
            throw exc;
        }
        public static void throws_exception(RuntimeException exc) {
            throw exc;
        }
    }

    public static abstract class Abstract {
        public Abstract() {
        }
    }

    /**
     * Tests if MethodAccessor::invoke implementation returns the expected
     * result or exceptions.
     */
    static void doTestAccessor(Method m, MethodAccessor ma, Object target, Object[] args,
                               Object expectedReturn, Throwable... expectedExceptions) {
        Object ret;
        Throwable exc;
        try {
            ret = ma.invoke(target, args);
            exc = null;
        } catch (Throwable e) {
            ret = null;
            exc = e;
        }
        System.out.println("\n" + m + ", invoked with target: " + target + ", args: " + Arrays.toString(args));

        chechResult(ret, expectedReturn, exc, expectedExceptions);
    }

    /**
     * Tests if ConstructorAccessor::newInstance implementation returns the
     * expected result or exceptions.
     */
    static void doTestAccessor(Constructor c, ConstructorAccessor ca, Object[] args,
                               Object expectedReturn, Throwable... expectedExceptions) {
        Object ret;
        Throwable exc;
        try {
            ret = ca.newInstance(args);
            exc = null;
        } catch (Throwable e) {
            ret = null;
            exc = e;
        }
        System.out.println("\n" + c + ", invoked with args: " + Arrays.toString(args));
        chechResult(ret, expectedReturn, exc, expectedExceptions);
    }

    /**
     * Tests if FieldAccessor::get implementation returns the
     * expected result or exceptions.
     */
    static void doTestAccessor(Field f, FieldAccessor fa, Object target,
                               Object expectedValue, Throwable... expectedExceptions) {
        Object ret;
        Throwable exc;
        try {
            ret = fa.get(target);
            exc = null;
        } catch (Throwable e) {
            ret = null;
            exc = e;
        }
        System.out.println("\n" + f + ", invoked with target: " + target + ", value: " + ret);
        chechResult(ret, expectedValue, exc, expectedExceptions);

    }

    /**
     * Tests if FieldAccessor::set implementation returns the
     * expected result or exceptions.
     */
    static void doTestAccessor(Field f, FieldAccessor fa, Object target, Object oldValue,
                               Object newValue, Throwable... expectedExceptions) {
        Object ret;
        Throwable exc;
            try {
                fa.set(target, newValue);
                exc = null;
                ret = fa.get(target);
            } catch (Throwable e) {
                ret = null;
                exc = e;
            }
            System.out.println("\n" + f + ", invoked with target: " + target + ", value: " + ret);
            chechResult(ret, newValue, exc, expectedExceptions);
    }

    static void chechResult(Object ret, Object expectedReturn, Throwable exc, Throwable... expectedExceptions) {
        if (exc != null) {
            checkException(exc, expectedExceptions);
        } else if (expectedExceptions.length > 0) {
            fail(exc, expectedExceptions);
        } else if (!Objects.equals(ret, expectedReturn)) {
            throw new AssertionError("Expected return:\n " + expectedReturn + "\ngot:\n " + ret);
        } else {
            System.out.println("    Got expected return: " + ret);
        }
    }

    static void checkException(Throwable exc, Throwable... expectedExceptions) {
        boolean match = false;
        for (Throwable expected : expectedExceptions) {
            if (exceptionMatches(exc, expected)) {
                match = true;
                break;
            }
        }
        if (match) {
            System.out.println("    Got expected exception: " + exc);
            if (exc.getCause() != null) {
                System.out.println("                with cause: " + exc.getCause());
            }
        } else {
            fail(exc, expectedExceptions);
        }
    }

    static boolean exceptionMatches(Throwable exc, Throwable expected) {
        return expected.getClass().isInstance(exc) &&
                (Objects.equals(expected.getMessage(), exc.getMessage()) ||
                        (exc.getMessage() != null && expected.getMessage() != null &&
                         exc.getMessage().startsWith(expected.getMessage()))) &&
                (expected.getCause() == null || exceptionMatches(exc.getCause(), expected.getCause()));
    }

    static void fail(Throwable thrownException, Throwable... expectedExceptions) {
        String msg;
        if (thrownException == null) {
            msg = "No exception thrown but there were expected exceptions (see suppressed)";
        } else if (expectedExceptions.length == 0) {
            msg = "Exception thrown (see cause) but there were no expected exceptions";
        } else {
            msg = "Exception thrown (see cause) but expected exceptions were different (see suppressed)";
        }
        AssertionError error = new AssertionError(msg, thrownException);
        Stream.of(expectedExceptions).forEach(error::addSuppressed);
        throw error;
    }

    static void doTest(Method m, Object target, Object[] args, Object expectedReturn, Throwable... expectedException) {
        MethodAccessor ma = ReflectionFactory.getReflectionFactory().newMethodAccessor(m, Reflection.isCallerSensitive(m));
        try {
            doTestAccessor(m, ma, target, args, expectedReturn, expectedException);
        } catch (Throwable e) {
            throw new RuntimeException(ma.getClass().getName() + " for method: " + m + " test failure", e);
        }
    }

    static void doTest(Constructor c, Object[] args, Object expectedReturn, Throwable... expectedExceptions) {
        ConstructorAccessor ca = ReflectionFactory.getReflectionFactory().newConstructorAccessor(c);
        try {
            doTestAccessor(c, ca, args, expectedReturn, expectedExceptions);
        } catch (Throwable e) {
            throw new RuntimeException(ca.getClass().getName() + " for constructor: " + c + " test failure", e);
        }
    }
    static void doTest(Field f, Object target, Object expectedValue, Throwable... expectedExceptions) {
        FieldAccessor fa = ReflectionFactory.getReflectionFactory().newFieldAccessor(f, false);
        try {
            doTestAccessor(f, fa, target, expectedValue, expectedExceptions);
        } catch (Throwable e) {
            throw new RuntimeException(fa.getClass().getName() + " for field: " + f + " test failure", e);
        }
    }
    static void doTest(Field f, Object target, Object oldValue, Object newValue, Throwable... expectedExceptions) {
        FieldAccessor fa = ReflectionFactory.getReflectionFactory().newFieldAccessor(f, true);
        try {
            doTestAccessor(f, fa, target, oldValue, newValue, expectedExceptions);
        } catch (Throwable e) {
            throw new RuntimeException(fa.getClass().getName() + " for field: " + f + " test failure", e);
        }
    }

    private static final Throwable[] noException = new Throwable[0];
    private static final Throwable[] mismatched_argument_type = new Throwable[] {
            new IllegalArgumentException("argument type mismatch")
    };
    private static final Throwable[] mismatched_target_type = new Throwable[] {
            new IllegalArgumentException("argument type mismatch"),
            new IllegalArgumentException("object of type java.lang.Object is not an instance of MethodHandleAccessorsTest"),
    };
    private static final Throwable[] cannot_get_field = new Throwable[] {
            new IllegalArgumentException("Can not get")
    };
    private static final Throwable[] cannot_set_field = new Throwable[] {
            new IllegalArgumentException("Can not set")
    };
    private static final Throwable[] mismatched_field_type = new Throwable[] {
            new IllegalArgumentException("Can not set")
    };
    private static final Throwable[] wrong_argument_count_no_details = new Throwable[] {
            new IllegalArgumentException("wrong number of arguments")
    };

    private static final Throwable[] wrong_argument_count_zero_args = new Throwable[] {
            new IllegalArgumentException("wrong number of arguments: 0 expected:")
    };
    private static final Throwable[] wrong_argument_count = new Throwable[] {
            new IllegalArgumentException("wrong number of arguments"),
            new IllegalArgumentException("array is not of length 1")
    };
    private static final Throwable[] null_argument = new Throwable[] {
            new IllegalArgumentException("wrong number of arguments"),
            new IllegalArgumentException("null array reference")
    };
    private static final Throwable[] null_argument_value = new Throwable[] {
            new IllegalArgumentException()
    };
    private static final Throwable[] null_argument_value_npe = new Throwable[] {
            new IllegalArgumentException("java.lang.NullPointerException"),
            new NullPointerException()
    };
    private static final Throwable[] null_target = new Throwable[] {
            new NullPointerException()
    };
    private static final Throwable[] wrapped_npe_no_msg = new Throwable[]{
            new InvocationTargetException(new NullPointerException())
    };
    private static final Throwable[] wrapped_npe = new Throwable[]{
            new InvocationTargetException(new NullPointerException("NPE"))
    };
    private static final Throwable[] wrapped_cce = new Throwable[]{
            new InvocationTargetException(new ClassCastException("CCE"))
    };
    private static final Throwable[] wrapped_iae = new Throwable[]{
            new InvocationTargetException(new IllegalArgumentException("IAE"))
    };


    @DataProvider(name = "testNoArgMethods")
    private Object[][] testNoArgMethods() {
        MethodHandleAccessorsTest inst = new MethodHandleAccessorsTest();
        Object[] emptyArgs = new Object[]{};
        return new Object[][] {
             new Object[] {"public_static_V",   null, emptyArgs, null, noException},
             new Object[] {"public_static_V",   null, null, null, noException},
             new Object[] {"public_static_I",   null, emptyArgs, 42, noException},
             new Object[] {"public_static_I",   null, null, 42, noException},
             new Object[] {"public_V",          inst, emptyArgs, null, noException},
             new Object[] {"public_V",          inst, null, null, noException},
             new Object[] {"public_I",          inst, emptyArgs, 42, noException},
             new Object[] {"public_I",          inst, null, 42, noException},
             new Object[] {"private_static_V",  null, emptyArgs, null, noException},
             new Object[] {"private_static_I",  null, emptyArgs, 42, noException},
             new Object[] {"private_V",         inst, emptyArgs, null, noException},
             new Object[] {"private_I",         inst, emptyArgs, 42, noException},
             new Object[] {"public_V",          null, null, null, null_target},
        };
    }

    @DataProvider(name = "testOneArgMethods")
    private Object[][] testOneArgMethods() {
        MethodHandleAccessorsTest inst = new MethodHandleAccessorsTest();
        Object wrongInst = new Object();
        return new Object[][]{
            new Object[] {"public_static_I_V",  int.class, null, new Object[]{12}, null, noException},
            new Object[] {"public_static_I_I",  int.class, null, new Object[]{12}, 12, noException},
            new Object[] {"public_I_V",         int.class, inst, new Object[]{12}, null, noException},
            new Object[] {"public_I_I",         int.class, inst, new Object[]{12}, 12, noException},
            new Object[] {"private_static_I_V", int.class, null, new Object[]{12}, null, noException},
            new Object[] {"private_static_I_I", int.class, null, new Object[]{12}, 12, noException},
            new Object[] {"private_I_V",        int.class, inst, new Object[]{12}, null, noException},
            new Object[] {"private_I_I",        int.class, inst, new Object[]{12}, 12, noException},

            new Object[] {"public_static_I_I", int.class, null, new Object[]{"a"}, null, mismatched_argument_type},
            new Object[] {"public_I_I",        int.class, inst, new Object[]{"a"}, null, mismatched_argument_type},
            new Object[] {"public_static_I_I", int.class, null, new Object[]{12, 13}, null, wrong_argument_count_no_details},
            new Object[] {"public_I_I",        int.class, inst, new Object[]{12, 13}, null, wrong_argument_count_no_details},
            new Object[] {"public_I_I",        int.class, wrongInst, new Object[]{12}, 12, mismatched_target_type},
            new Object[] {"public_I_I",        int.class, null, new Object[]{12}, 12, null_target},

            new Object[] {"public_static_I_V", int.class, null, null, null, wrong_argument_count_no_details},
            new Object[] {"public_static_I_V", int.class, null, new Object[]{null}, null, null_argument_value_npe},
            new Object[] {"public_I_I",        int.class, inst, null, null, null_argument},

            new Object[] {"public_I_I", int.class, inst, new Object[]{null}, null, null_argument_value_npe},
        };
    }

    @DataProvider(name = "testMultiArgMethods")
    private Object[][] testMultiArgMethods() {
        MethodHandleAccessorsTest inst = new MethodHandleAccessorsTest();
        Class<?>[] params_L3 = new Class<?>[] { Object.class, Object.class, Object.class};
        Class<?>[] params_L4 = new Class<?>[] { Object.class, Object.class, Object.class, Object.class};
        Class<?>[] params_L5 = new Class<?>[] { Object.class, Object.class, Object.class, Object.class, Object.class};

        return new Object[][]{
                new Object[]{"public_static_V_L3", params_L3, null, new Object[3], null, noException},
                new Object[]{"public_static_V_L4", params_L4, null, new Object[4], null, noException},
                new Object[]{"public_V_L5", params_L5, inst, new Object[5], null, noException},
                // wrong arguments
                new Object[]{"public_static_V_L3", params_L3, null, null, null, wrong_argument_count_zero_args},
                new Object[]{"public_static_V_L4", params_L4, null, new Object[0], null, wrong_argument_count_zero_args},
                new Object[]{"public_V_L5", params_L5, inst, null, null, wrong_argument_count_zero_args},
        };
    }

    @DataProvider(name = "testMethodsWithVarargs")
    private Object[][] testMethodsWithVarargs() {
        Class<?>[] paramTypes = new Class<?>[] { int[].class };
        Class<?>[] I_paramTypes = new Class<?>[] { int.class, int[].class };
        Class<?>[] L_paramTypes = new Class<?>[] { String.class, String[].class };
        return new Object[][]{
            new Object[] {"varargs", paramTypes, null, new Object[]{new int[]{1, 2, 3}}, 6, noException},
            new Object[] {"varargs", paramTypes, null, new Object[]{new int[]{}}, 0, noException},
            new Object[] {"varargs", paramTypes, null, new Object[]{null}, 0, wrapped_npe_no_msg},
            new Object[] {"varargs_primitive", I_paramTypes, null, new Object[]{1, new int[]{2, 3}}, 105, noException},
            new Object[] {"varargs_primitive", I_paramTypes, null, new Object[]{1, new int[]{}}, 100, noException},
            new Object[] {"varargs_primitive", I_paramTypes, null, new Object[]{1, null}, 1, noException},
            new Object[] {"varargs_object", L_paramTypes,    null, new Object[]{"a", new String[]{"b", "c"}}, "a[b,c]", noException},
            new Object[] {"varargs_object", L_paramTypes,    null, new Object[]{"a", new String[]{}}, "a[]", noException},
            new Object[] {"varargs_object", L_paramTypes,    null, new Object[]{"a", null}, "a", noException},
        };
    }

    @Test(dataProvider = "testNoArgMethods")
    public void testNoArgMethod(String methodname, Object target, Object[] args,
                                Object expectedReturn, Throwable[] expectedExpections) throws Exception {
        doTest(MethodHandleAccessorsTest.class.getDeclaredMethod(methodname), target, args, expectedReturn, expectedExpections);
    }

    @Test(dataProvider = "testOneArgMethods")
    public void testOneArgMethod(String methodname, Class<?> paramType, Object target, Object[] args,
                                 Object expectedReturn, Throwable[] expectedExpections) throws Exception {
        doTest(MethodHandleAccessorsTest.class.getDeclaredMethod(methodname, paramType), target, args, expectedReturn, expectedExpections);
    }

    @Test(dataProvider = "testMultiArgMethods")
    public void testMultiArgMethod(String methodname, Class<?>[] paramTypes, Object target, Object[] args,
                                 Object expectedReturn, Throwable[] expectedExpections) throws Exception {
        doTest(MethodHandleAccessorsTest.class.getDeclaredMethod(methodname, paramTypes), target, args, expectedReturn, expectedExpections);
    }

    @Test(dataProvider = "testMethodsWithVarargs")
    public void testMethodsWithVarargs(String methodname, Class<?>[] paramTypes, Object target, Object[] args,
                                       Object expectedReturn, Throwable[] expectedExpections) throws Exception {
        doTest(MethodHandleAccessorsTest.class.getDeclaredMethod(methodname, paramTypes), target, args, expectedReturn, expectedExpections);
    }

    @DataProvider(name = "testConstructors")
    private Object[][] testConstructors() {
        return new Object[][]{
                new Object[]{null, new Object[]{}, new Public(), noException},
                new Object[]{null, null, new Public(), noException},
                new Object[]{new Class<?>[]{int.class}, new Object[]{12}, new Public(12), noException},
                new Object[]{new Class<?>[]{String.class}, new Object[]{"a"}, new Public("a"), noException},


                new Object[]{new Class<?>[]{int.class, int[].class}, new Object[]{1, new int[]{2, 3}}, new Public(105), noException},
                new Object[]{new Class<?>[]{int.class, int[].class}, new Object[]{1, new int[]{}}, new Public(100), noException},
                new Object[]{new Class<?>[]{int.class, int[].class}, new Object[]{1, null}, new Public(1), noException},

                new Object[]{new Class<?>[]{String.class, String[].class}, new Object[]{"a", new String[]{"b", "c"}}, new Public("a[b,c]"), noException},
                new Object[]{new Class<?>[]{String.class, String[].class}, new Object[]{"a", new String[]{}}, new Public("a[]"), noException},
                new Object[]{new Class<?>[]{String.class, String[].class}, new Object[]{"a", null}, new Public("a"), noException},

                // test ConstructorAccessor exceptions thrown
                new Object[]{new Class<?>[]{int.class}, new Object[]{"a"}, null, mismatched_argument_type},
                new Object[]{new Class<?>[]{int.class}, new Object[]{12, 13}, null, wrong_argument_count},
                new Object[]{new Class<?>[]{int.class}, null, null, null_argument},
                new Object[]{new Class<?>[]{RuntimeException.class}, new Object[]{new NullPointerException("NPE")}, null, wrapped_npe},
                new Object[]{new Class<?>[]{RuntimeException.class}, new Object[]{new IllegalArgumentException("IAE")}, null, wrapped_iae},
                new Object[]{new Class<?>[]{RuntimeException.class}, new Object[]{new ClassCastException("CCE")}, null, wrapped_cce},
        };
    }

    @Test(dataProvider = "testConstructors")
    public void testPublicConstructors(Class<?>[] paramTypes, Object[] args, Object expectedReturn, Throwable[] expectedExpections) throws Exception {
        doTest(Public.class.getDeclaredConstructor(paramTypes), args, expectedReturn, expectedExpections);
    }

    @DataProvider(name = "testMultiArgConstructors")
    private Object[][] testMultiArgConstructors() {
        Class<?>[] params_L3 = new Class<?>[] { Object.class, Object.class, Object.class};
        Class<?>[] params_L4 = new Class<?>[] { Object.class, Object.class, Object.class, Object.class};
        Object o = "arg";
        return new Object[][]{
                new Object[]{params_L3, new Object[3], new Public(o, o, o), noException},
                new Object[]{params_L4, new Object[4], new Public(o, o, o, o), noException},
                new Object[]{params_L3, new Object[]{}, null, wrong_argument_count_zero_args},
                new Object[]{params_L4, null, null, wrong_argument_count_zero_args},
        };
    }

    @Test(dataProvider = "testMultiArgConstructors")
    public void testMultiArgConstructors(Class<?>[] paramTypes, Object[] args, Object expectedReturn, Throwable[] expectedExpections) throws Exception {
        doTest(Public.class.getDeclaredConstructor(paramTypes), args, expectedReturn, expectedExpections);
    }

    @Test
    public void testOtherConstructors() throws Exception {
        doTest(Private.class.getDeclaredConstructor(), new Object[]{}, new Private());
        doTest(Private.class.getDeclaredConstructor(), null, new Private());
        doTest(Private.class.getDeclaredConstructor(int.class), new Object[]{12}, new Private(12));

        doTest(Abstract.class.getDeclaredConstructor(), null, null, new InstantiationException());
    }

    @DataProvider(name = "throwException")
    private Object[][] throwException() {
        return new Object[][]{
                new Object[] {new NullPointerException("NPE"), wrapped_npe},
                new Object[] {new IllegalArgumentException("IAE"), wrapped_iae},
                new Object[] {new ClassCastException("CCE"), wrapped_cce},
        };
    }

    /*
     * Test Method::invoke and Constructor::newInstance to wrap NPE/CCE/IAE
     * thrown by the member
     */
    @Test(dataProvider = "throwException")
    public void testInvocationTargetException(Throwable ex, Throwable[] expectedExpections) throws Exception {
        Object[] args = new Object[] { ex };
        // test static method
        doTest(Thrower.class.getDeclaredMethod("throws_exception", RuntimeException.class), null, args, null, expectedExpections);
        // test constructor
        doTest(Thrower.class.getDeclaredConstructor(RuntimeException.class), args, null, expectedExpections);
    }

    @Test
    public void testLambdaProxyClass() throws Exception {
        // test MethodAccessor on methods declared by hidden classes
        IntUnaryOperator intUnaryOp = i -> i;
        Method applyAsIntMethod = intUnaryOp.getClass().getDeclaredMethod("applyAsInt", int.class);
        doTest(applyAsIntMethod, intUnaryOp, new Object[]{12}, 12);
    }

    @DataProvider(name = "readAccess")
    private Object[][] readAccess() {
        String wrongInst = new String();
        return new Object[][]{
                new Object[]{"i", new Public(100), 100, noException},
                new Object[]{"s", new Public("test"), "test", noException},
                new Object[]{"s", null, "test", null_target},
                new Object[]{"s", wrongInst, "test", cannot_get_field},
                new Object[]{"b", wrongInst, 0, cannot_get_field},
        };
    }
    @DataProvider(name = "writeAccess")
    private Object[][] writeAccess() {
        Object o = new Object();
        byte b = 1;
        return new Object[][]{
                new Object[]{"i", new Public(100), 100, 200, noException},
                new Object[]{"i", new Public(100), 100, Integer.valueOf(10), noException},
                new Object[]{"s", new Public("test"), "test", "newValue", noException},
                new Object[]{"s", null, "test", "dummy", null_target},
                new Object[]{"b", new Public(b), b, null, mismatched_field_type},
                new Object[]{"b", new Public(b), b, Long.valueOf(10), mismatched_field_type},
                new Object[]{"name", null, "name", o, mismatched_field_type},
                new Object[]{"i", new Public(100), 100, o, mismatched_field_type},
        };
    }

    @Test(dataProvider = "readAccess")
    public void testFieldReadAccess(String name, Object target, Object expectedValue, Throwable[] expectedExpections) throws Exception {
        Field f = Public.class.getDeclaredField(name);
        f.setAccessible(true);
        doTest(f, target, expectedValue, expectedExpections);
    }

    @Test(dataProvider = "writeAccess")
    public void testFieldWriteAccess(String name, Object target, Object oldValue, Object newValue, Throwable[] expectedExpections) throws Exception {
        Field f = Public.class.getDeclaredField(name);
        f.setAccessible(true);
        doTest(f, target, oldValue, newValue, expectedExpections);
    }

    // test static final field with read-only access
    @Test
    public void testStaticFinalFields() throws Exception {
        Field f = Public.class.getDeclaredField("STATIC_FINAL");
        doTest(f, new Public(), 1, noException);

        try {
            f.setInt(null, 100);
        } catch (IllegalAccessException e) { }
    }
}
