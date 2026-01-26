/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8343064
 * @summary Test about when lambda caller/target class is a hidden class
 * @run junit LambdaHiddenCaller
 */
public class LambdaHiddenCaller {
    static Hooks hiddenCaller;

    @BeforeAll
    static void setup() throws Throwable {
        byte[] bytes;
        try (var in = LambdaHiddenCaller.class.getResourceAsStream("HiddenHooks.class")) {
            bytes = in.readAllBytes();
        }
        var hiddenClassLookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
        hiddenCaller = (Hooks) hiddenClassLookup.findConstructor(hiddenClassLookup.lookupClass(), MethodType.methodType(void.class))
                .asType(MethodType.methodType(Hooks.class)).invokeExact();
    }

    @Test
    void testStaticMethod() {
        var is = hiddenCaller.callStaticMethodLambda();
        assertEquals(42, is.getAsInt());
    }

    @Test
    void testSerializableLambda() {
        var is = hiddenCaller.callSerializableLambda();
        assertEquals(42, is.getAsInt());
        assertInstanceOf(Serializable.class, is);
        // We do not guarantee serialization functionalities yet
    }
}

/**
 * Hooks to call hidden class methods easily.
 */
interface Hooks {
    IntSupplier callStaticMethodLambda();

    IntSupplier callSerializableLambda();
}

class HiddenHooks implements Hooks {
    private static int compute() {
        return 42;
    }

    @Override
    public IntSupplier callStaticMethodLambda() {
        return HiddenHooks::compute;
    }

    @Override
    public IntSupplier callSerializableLambda() {
        return (IntSupplier & Serializable) HiddenHooks::compute;
    }
}