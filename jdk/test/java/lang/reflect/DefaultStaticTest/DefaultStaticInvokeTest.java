/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test locating and invoking default/static method that defined
 *          in interfaces and/or in inheritance
 * @bug 7184826
 * @build helper.Mod helper.Declared DefaultStaticTestData
 * @run testng DefaultStaticInvokeTest
 * @author Yong Lu
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import org.testng.annotations.Test;

import static helper.Mod.*;
import static helper.Declared.*;
import helper.Mod;

public class DefaultStaticInvokeTest {

    @Test(dataProvider = "testCasesAll",
            dataProviderClass = DefaultStaticTestData.class)
    public void testGetMethods(String testTarget, Object param)
            throws Exception {
        // test the methods retrieved by getMethods()
        testMethods(ALL_METHODS, testTarget, param);
    }

    @Test(dataProvider = "testCasesAll",
            dataProviderClass = DefaultStaticTestData.class)
    public void testGetDeclaredMethods(String testTarget, Object param)
            throws Exception {
        // test the methods retrieved by getDeclaredMethods()
        testMethods(DECLARED_ONLY, testTarget, param);
    }

    @Test(dataProvider = "testCasesAll",
            dataProviderClass = DefaultStaticTestData.class)
    public void testMethodInvoke(String testTarget, Object param)
            throws Exception {
        Class<?> typeUnderTest = Class.forName(testTarget);
        MethodDesc[] expectedMethods = typeUnderTest.getAnnotationsByType(MethodDesc.class);

        // test the method retrieved by Class.getMethod(String, Object[])
        for (MethodDesc toTest : expectedMethods) {
            String name = toTest.name();
            Method m = getTestMethod(typeUnderTest, name, param);
            testThisMethod(toTest, m, typeUnderTest, param);
        }
    }

    @Test(dataProvider = "testCasesAll",
            dataProviderClass = DefaultStaticTestData.class)
    public void testMethodHandleInvoke(String testTarget, Object param)
            throws Throwable {
        Class<?> typeUnderTest = Class.forName(testTarget);
        MethodDesc[] expectedMethods = typeUnderTest.getAnnotationsByType(MethodDesc.class);

        for (MethodDesc toTest : expectedMethods) {
            String mName = toTest.name();
            Mod mod = toTest.mod();
            if (mod != STATIC && typeUnderTest.isInterface()) {
                return;
            }

            String result = null;
            String expectedReturn = toTest.retval();

            MethodHandle methodHandle = getTestMH(typeUnderTest, mName, param);
            if (mName.equals("staticMethod")) {
                result = (param == null)
                        ? (String) methodHandle.invoke()
                        : (String) methodHandle.invoke(param);
            } else {
                result = (param == null)
                        ? (String) methodHandle.invoke(typeUnderTest.newInstance())
                        : (String) methodHandle.invoke(typeUnderTest.newInstance(), param);
            }

            assertEquals(result, expectedReturn);
        }

    }

    @Test(dataProvider = "testClasses",
            dataProviderClass = DefaultStaticTestData.class)
    public void testIAE(String testTarget, Object param)
            throws ClassNotFoundException {

        Class<?> typeUnderTest = Class.forName(testTarget);
        MethodDesc[] expectedMethods = typeUnderTest.getAnnotationsByType(MethodDesc.class);

        for (MethodDesc toTest : expectedMethods) {
            String mName = toTest.name();
            Mod mod = toTest.mod();
            if (mod != STATIC && typeUnderTest.isInterface()) {
                return;
            }
            Exception caught = null;
            try {
                getTestMH(typeUnderTest, mName, param, true);
            } catch (Exception e) {
                caught = e;
            }
            assertTrue(caught != null);
            assertEquals(caught.getClass(), IllegalAccessException.class);
        }
    }
    private static final String[] OBJECT_METHOD_NAMES = {
        "equals",
        "hashCode",
        "getClass",
        "notify",
        "notifyAll",
        "toString",
        "wait",
        "wait",
        "wait",};
    private static final String LAMBDA_METHOD_NAMES = "lambda$";
    private static final HashSet<String> OBJECT_NAMES = new HashSet<>(Arrays.asList(OBJECT_METHOD_NAMES));
    private static final boolean DECLARED_ONLY = true;
    private static final boolean ALL_METHODS = false;

    private void testMethods(boolean declaredOnly, String testTarget, Object param)
            throws Exception {
        Class<?> typeUnderTest = Class.forName(testTarget);
        Method[] methods = declaredOnly
                ? typeUnderTest.getDeclaredMethods()
                : typeUnderTest.getMethods();

        MethodDesc[] baseExpectedMethods = typeUnderTest.getAnnotationsByType(MethodDesc.class);
        MethodDesc[] expectedMethods;

        // If only declared filter out non-declared from expected result
        if (declaredOnly) {
            int nonDeclared = 0;
            for (MethodDesc desc : baseExpectedMethods) {
                if (desc.declared() == NO) {
                    nonDeclared++;
                }
            }
            expectedMethods = new MethodDesc[baseExpectedMethods.length - nonDeclared];
            int i = 0;
            for (MethodDesc desc : baseExpectedMethods) {
                if (desc.declared() == YES) {
                    expectedMethods[i++] = desc;
                }
            }
        } else {
            expectedMethods = baseExpectedMethods;
        }

        HashMap<String, Method> myMethods = new HashMap<>(methods.length);
        for (Method m : methods) {
            String mName = m.getName();
            // don't add Object methods and method created from lambda expression
            if ((!OBJECT_NAMES.contains(mName)) && (!mName.contains(LAMBDA_METHOD_NAMES))) {
                myMethods.put(mName, m);
            }
        }
        assertEquals(expectedMethods.length, myMethods.size());

        for (MethodDesc toTest : expectedMethods) {

            String name = toTest.name();
            Method candidate = myMethods.get(name);

            assertNotNull(candidate);
            myMethods.remove(name);

            testThisMethod(toTest, candidate, typeUnderTest, param);

        }

        // Should be no methods left since we remove all we expect to see
        assertTrue(myMethods.isEmpty());
    }

    private void testThisMethod(MethodDesc toTest, Method method,
            Class<?> typeUnderTest, Object param) throws Exception {
        // Test modifiers, and invoke
        Mod mod = toTest.mod();
        String expectedReturn = toTest.retval();
        switch (mod) {
            case STATIC:
                //assert candidate is static
                assertTrue(Modifier.isStatic(method.getModifiers()));
                assertFalse(method.isDefault());

                // Test invoke it
                assertEquals(tryInvoke(method, null, param), expectedReturn);
                break;
            case DEFAULT:
                // if typeUnderTest is a class then instantiate and invoke
                if (!typeUnderTest.isInterface()) {
                    assertEquals(tryInvoke(
                            method,
                            typeUnderTest,
                            param),
                            expectedReturn);
                }

                //assert candidate is default
                assertFalse(Modifier.isStatic(method.getModifiers()));
                assertTrue(method.isDefault());
                break;
            case REGULAR:
                // if typeUnderTest must be a class
                assertEquals(tryInvoke(
                        method,
                        typeUnderTest,
                        param),
                        expectedReturn);

                //assert candidate is neither default nor static
                assertFalse(Modifier.isStatic(method.getModifiers()));
                assertFalse(method.isDefault());
                break;
            case ABSTRACT:
                //assert candidate is neither default nor static
                assertFalse(Modifier.isStatic(method.getModifiers()));
                assertFalse(method.isDefault());
                break;
            default:
                assertFalse(true); //this should never happen
                break;
        }

    }

    private Object tryInvoke(Method m, Class<?> receiverType, Object param)
            throws Exception {
        Object receiver = receiverType == null ? null : receiverType.newInstance();
        Object result = null;
        if (param == null) {
            result = m.invoke(receiver);
        } else {
            result = m.invoke(receiver, param);
        }
        return result;
    }

    private Method getTestMethod(Class clazz, String methodName, Object param)
            throws NoSuchMethodException {
        Class[] paramsType = (param != null)
                ? new Class[]{Object.class}
                : new Class[]{};
        return clazz.getMethod(methodName, paramsType);
    }

    private MethodHandle getTestMH(Class clazz, String methodName, Object param)
            throws Exception {
        return getTestMH(clazz, methodName, param, false);
    }

    private MethodHandle getTestMH(Class clazz, String methodName,
            Object param, boolean isNegativeTest)
            throws Exception {
        MethodType mType = (param != null)
                ? MethodType.genericMethodType(1)
                : MethodType.methodType(String.class);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        if (!isNegativeTest) {
            return methodName.equals("staticMethod")
                    ? lookup.findStatic(clazz, methodName, mType)
                    : lookup.findVirtual(clazz, methodName, mType);
        } else {
            return methodName.equals("staticMethod")
                    ? lookup.findVirtual(clazz, methodName, mType)
                    : lookup.findStatic(clazz, methodName, mType);
        }
    }
}
