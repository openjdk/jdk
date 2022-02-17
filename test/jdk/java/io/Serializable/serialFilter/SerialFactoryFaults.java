/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.Config;
import java.io.ObjectInputStream;
import java.util.function.BinaryOperator;

/* @test
 * @run testng/othervm  -Djdk.serialFilterFactory=ForcedError_NoSuchClass SerialFactoryFaults
 * @run testng/othervm  -Djdk.serialFilterFactory=SerialFactoryFaults$NoPublicConstructor SerialFactoryFaults
 * @run testng/othervm  -Djdk.serialFilterFactory=SerialFactoryFaults$ConstructorThrows SerialFactoryFaults
 * @run testng/othervm  -Djdk.serialFilterFactory=SerialFactoryFaults$FactorySetsFactory SerialFactoryFaults
 * @summary Check cases where the Filter Factory initialization from properties fails
 */

@Test
public class SerialFactoryFaults {

    // Sample the serial factory class name
    private static final String factoryName = System.getProperty("jdk.serialFilterFactory");

    static {
        // Enable logging
        System.setProperty("java.util.logging.config.file",
                System.getProperty("test.src", ".") + "/logging.properties");
    }

    @DataProvider(name = "MethodsToCall")
    private Object[][] cases() {
        return new Object[][] {
                {"getSerialFilterFactory", (Assert.ThrowingRunnable) () -> Config.getSerialFilterFactory()},
                {"setSerialFilterFactory", (Assert.ThrowingRunnable) () -> Config.setSerialFilterFactory(new NoopFactory())},
                {"new ObjectInputStream(is)", (Assert.ThrowingRunnable) () -> new ObjectInputStream(new ByteArrayInputStream(new byte[0]))},
                {"new OISSubclass()", (Assert.ThrowingRunnable) () -> new OISSubclass()},
        };
    }

    /**
     * Test each method that should throw IllegalStateException based on
     * the invalid arguments it was launched with.
     */
    @Test(dataProvider = "MethodsToCall")
    public void initFaultTest(String name, Assert.ThrowingRunnable runnable) {
        IllegalStateException ex = Assert.expectThrows(IllegalStateException.class,
                runnable);
        final String msg = ex.getMessage();

        if (factoryName.equals("ForcedError_NoSuchClass")) {
            Assert.assertEquals(msg,
                    "invalid jdk.serialFilterFactory: ForcedError_NoSuchClass: java.lang.ClassNotFoundException: ForcedError_NoSuchClass", "wrong exception");
        } else if (factoryName.equals("SerialFactoryFaults$NoPublicConstructor")) {
            Assert.assertEquals(msg,
                    "invalid jdk.serialFilterFactory: SerialFactoryFaults$NoPublicConstructor: java.lang.NoSuchMethodException: SerialFactoryFaults$NoPublicConstructor.<init>()", "wrong exception");
        } else if (factoryName.equals("SerialFactoryFaults$ConstructorThrows")) {
            Assert.assertEquals(msg,
                    "invalid jdk.serialFilterFactory: SerialFactoryFaults$ConstructorThrows: java.lang.RuntimeException: constructor throwing a runtime exception", "wrong exception");
        } else if (factoryName.equals("SerialFactoryFaults$FactorySetsFactory")) {
            Assert.assertEquals(msg,
                    "invalid jdk.serialFilterFactory: SerialFactoryFaults$FactorySetsFactory: java.lang.IllegalStateException: Serial filter factory initialization incomplete", "wrong exception");
        } else {
            Assert.fail("No test for filter factory: " + factoryName);
        }
    }

    /**
     * Test factory that does not have the required public no-arg constructor.
     */
    public static final class NoPublicConstructor
            implements BinaryOperator<ObjectInputFilter> {
        private NoPublicConstructor() {
        }

        public ObjectInputFilter apply(ObjectInputFilter curr, ObjectInputFilter next) {
            throw new RuntimeException("NYI");
        }
    }

    /**
     * Test factory that has a constructor that throws a runtime exception.
     */
    public static final class ConstructorThrows
            implements BinaryOperator<ObjectInputFilter> {
        public ConstructorThrows() {
            throw new RuntimeException("constructor throwing a runtime exception");
        }

        public ObjectInputFilter apply(ObjectInputFilter curr, ObjectInputFilter next) {
            throw new RuntimeException("NYI");
        }
    }

    /**
     * Test factory that has a constructor tries to set the filter factory.
     */
    public static final class FactorySetsFactory
            implements BinaryOperator<ObjectInputFilter> {
        public FactorySetsFactory() {
            Config.setSerialFilterFactory(this);
        }

        public ObjectInputFilter apply(ObjectInputFilter curr, ObjectInputFilter next) {
            throw new RuntimeException("NYI");
        }
    }

    public static final class NoopFactory implements BinaryOperator<ObjectInputFilter> {
        public NoopFactory() {}

        public ObjectInputFilter apply(ObjectInputFilter curr, ObjectInputFilter next) {
            throw new RuntimeException("NYI");
        }
    }

    /**
     * Subclass of ObjectInputStream to test subclassing constructor.
     */
    private static class OISSubclass extends ObjectInputStream {

        protected OISSubclass() throws IOException {
        }
    }

}
