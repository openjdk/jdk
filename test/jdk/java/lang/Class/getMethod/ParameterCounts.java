/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class ParameterCounts {
    public static class ConcreteClass extends SuperClass implements TestIntf {
        @Override
        public void a_noiseMethod(byte[] bytes) {
        }

        public void a_noiseMethod(String s) {
        }

        @Override
        public void b_noiseMethod(byte[] bytes) {
        }

        public void b_noiseMethod(String s) {
        }

        public void c_noiseMethod(String s) {
        }

        public void d_noiseMethod(String s) {
        }

        public void fiveArgs(Object one, Object two, Object three, Object four, Object five) {
        }

        public void fiveArgs(Integer one, Integer two, Integer three, Integer four, Integer five) {
        }

        public void noArgs() {
        }

        public void x_noiseMethod(String s) {
        }

        public void y_noiseMethod(String s) {
        }

        public void z_noiseMethod(String s) {
        }

    }

    public static class SuperClass {
        public void a_superNoiseMethod(String s) {
        }

        public void b_superNoiseMethod(String s) {
        }

        public void c_superNoiseMethod(String s) {
        }

        public void d_superNoiseMethod(String s) {
        }

        public void superFiveArgs(Object one, Object two, Object three, Object four, Object five) {
        }

        public void superFiveArgs(Integer one, Integer two, Integer three, Integer four, Integer five) {
        }

        public void superNoArgs() {
        }

        public void x_superNoiseMethod(String s) {
        }

        public void y_superNoiseMethod(String s) {
        }

        public void z_superNoiseMethod(String s) {
        }

    }

    public interface TestIntf {
        public void a_noiseMethod(byte[] bytes);

        public void b_noiseMethod(byte[] bytes);

        default void defaultIntfFiveArgs(Object a, Object b, Object c, Object d, Object e) {
        }

        default void defaultIntfFiveArgs(Integer a, Integer b, Integer c, Integer d, Integer e) {
        }

        default void defaultIntfNoArgs() {
        }

        default void y_noiseMethod(byte[] bytes) {
        }

        default void z_noiseMethod(byte[] bytes) {
        }
    }

    private static final Class<?>[] FIVE_ARG_CLASSES = new Class<?>[] { Object.class, Object.class, Object.class,
            Object.class, Object.class };

    /**
     * Verifies correct lookup of a declared method with 5 params.
     *
     * @throws NoSuchMethodException
     * @throws SecurityException
     */
    @Test
    public void getConcreteFiveArg() throws NoSuchMethodException, SecurityException {
        Method method = ConcreteClass.class.getMethod("fiveArgs", FIVE_ARG_CLASSES);
        Assert.assertNotNull(method);
        Assert.assertEquals("fiveArgs", method.getName());
        Assert.assertEquals(5, method.getParameterCount());
        Assert.assertEquals(ConcreteClass.class, method.getDeclaringClass());
    }

    /**
     * Verifies correct lookup of a declared method with 0 params.
     *
     * @throws NoSuchMethodException
     * @throws SecurityException
     */
    @Test
    public void getConcreteNoArg() throws NoSuchMethodException, SecurityException {
        Method method = ConcreteClass.class.getMethod("noArgs");
        Assert.assertNotNull(method);
        Assert.assertEquals("noArgs", method.getName());
        Assert.assertEquals(0, method.getParameterCount());
        Assert.assertEquals(ConcreteClass.class, method.getDeclaringClass());
    }

    /**
     * Verifies correct lookup of an interface method with 5 params.
     *
     * @throws NoSuchMethodException
     * @throws SecurityException
     */
    @Test
    public void getIntfFiveArg() throws NoSuchMethodException, SecurityException {
        Method method = ConcreteClass.class.getMethod("defaultIntfFiveArgs", FIVE_ARG_CLASSES);
        Assert.assertNotNull(method);
        Assert.assertEquals("defaultIntfFiveArgs", method.getName());
        Assert.assertEquals(5, method.getParameterCount());
        Assert.assertEquals(TestIntf.class, method.getDeclaringClass());
    }

    /**
     * Verifies correct lookup of an interface method with 0 params.
     *
     * @throws NoSuchMethodException
     * @throws SecurityException
     */
    @Test
    public void getIntfNoArg() throws NoSuchMethodException, SecurityException {
        Method method = ConcreteClass.class.getMethod("defaultIntfNoArgs");
        Assert.assertNotNull(method);
        Assert.assertEquals("defaultIntfNoArgs", method.getName());
        Assert.assertEquals(0, method.getParameterCount());
        Assert.assertEquals(TestIntf.class, method.getDeclaringClass());
    }

    /**
     * Verifies correct lookup of a nonexistent method.
     *
     * @throws NoSuchMethodException
     * @throws SecurityException
     */
    @Test
    public void getNoSuchMethod() throws NoSuchMethodException, SecurityException {
        try {
            ConcreteClass.class.getMethod("noSuchMethod");
            Assert.fail("Should've thrown an exception");
        } catch (NoSuchMethodException nsme) {
        }
    }

    /**
     * Verifies correct lookup of a superclass method with 5 params.
     *
     * @throws NoSuchMethodException
     * @throws SecurityException
     */
    @Test
    public void getSuperFiveArg() throws NoSuchMethodException, SecurityException {
        Method method = ConcreteClass.class.getMethod("superFiveArgs", FIVE_ARG_CLASSES);
        Assert.assertNotNull(method);
        Assert.assertEquals("superFiveArgs", method.getName());
        Assert.assertEquals(5, method.getParameterCount());
        Assert.assertEquals(SuperClass.class, method.getDeclaringClass());
    }

    /**
     * Verifies correct lookup of a superclass method with 0 params.
     *
     * @throws NoSuchMethodException
     * @throws SecurityException
     */
    @Test
    public void getSuperNoArg() throws NoSuchMethodException, SecurityException {
        Method method = ConcreteClass.class.getMethod("superNoArgs");
        Assert.assertNotNull(method);
        Assert.assertEquals("superNoArgs", method.getName());
        Assert.assertEquals(0, method.getParameterCount());
        Assert.assertEquals(SuperClass.class, method.getDeclaringClass());
    }

}
