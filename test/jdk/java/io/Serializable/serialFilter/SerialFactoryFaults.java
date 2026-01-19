/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.Config;
import java.io.ObjectInputStream;
import java.util.function.BinaryOperator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/* @test
 * @run junit/othervm  -Djdk.serialFilterFactory=ForcedError_NoSuchClass SerialFactoryFaults
 * @run junit/othervm  -Djdk.serialFilterFactory=SerialFactoryFaults$NoPublicConstructor SerialFactoryFaults
 * @run junit/othervm  -Djdk.serialFilterFactory=SerialFactoryFaults$ConstructorThrows SerialFactoryFaults
 * @run junit/othervm  -Djdk.serialFilterFactory=SerialFactoryFaults$FactorySetsFactory SerialFactoryFaults
 * @summary Check cases where the Filter Factory initialization from properties fails
 */

public class SerialFactoryFaults {

    // Sample the serial factory class name
    private static final String factoryName = System.getProperty("jdk.serialFilterFactory");

    static {
        // Enable logging
        System.setProperty("java.util.logging.config.file",
                System.getProperty("test.src", ".") + "/logging.properties");
    }

    // Test cases of faults
    private static Object[][] cases() {
        return new Object[][] {
                {"getSerialFilterFactory", (Executable) () -> Config.getSerialFilterFactory()},
                {"setSerialFilterFactory", (Executable) () -> Config.setSerialFilterFactory(new NoopFactory())},
                {"new ObjectInputStream(is)", (Executable) () -> new ObjectInputStream(new ByteArrayInputStream(new byte[0]))},
                {"new OISSubclass()", (Executable) () -> new OISSubclass()},
        };
    }

    /**
     * Test each method that should throw IllegalStateException based on
     * the invalid arguments it was launched with.
     */
    @ParameterizedTest
    @MethodSource("cases")
    public void initFaultTest(String name, Executable runnable) {
        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                runnable);
        final String msg = ex.getMessage();

        if (factoryName.equals("ForcedError_NoSuchClass")) {
            Assertions.assertEquals("invalid jdk.serialFilterFactory: ForcedError_NoSuchClass: java.lang.ClassNotFoundException: ForcedError_NoSuchClass", msg, "wrong exception");
        } else if (factoryName.equals("SerialFactoryFaults$NoPublicConstructor")) {
            Assertions.assertEquals("invalid jdk.serialFilterFactory: SerialFactoryFaults$NoPublicConstructor: java.lang.NoSuchMethodException: SerialFactoryFaults$NoPublicConstructor.<init>()", msg, "wrong exception");
        } else if (factoryName.equals("SerialFactoryFaults$ConstructorThrows")) {
            Assertions.assertEquals("invalid jdk.serialFilterFactory: SerialFactoryFaults$ConstructorThrows: java.lang.RuntimeException: constructor throwing a runtime exception", msg, "wrong exception");
        } else if (factoryName.equals("SerialFactoryFaults$FactorySetsFactory")) {
            Assertions.assertEquals("invalid jdk.serialFilterFactory: SerialFactoryFaults$FactorySetsFactory: java.lang.IllegalStateException: Serial filter factory initialization incomplete", msg, "wrong exception");
        } else {
            Assertions.fail("No test for filter factory: " + factoryName);
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
