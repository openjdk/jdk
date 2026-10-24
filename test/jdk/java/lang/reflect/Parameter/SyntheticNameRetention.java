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

import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8357728
 * @summary Synthetic parameter names should not be retained.
 * @modules java.base/java.lang.reflect:+open
 * @run junit SyntheticNameRetention
 */
public class SyntheticNameRetention {

    class Inner {
        Inner() {}
    }

    public interface NameGetter {
        String getRawName(Parameter parameter);
    }
    static final NameGetter GETTER;

    static {
        try {
            var lookup = MethodHandles.privateLookupIn(Parameter.class, MethodHandles.lookup());
            GETTER = MethodHandleProxies.asInterfaceInstance(NameGetter.class, lookup.findGetter(Parameter.class, "name", String.class));
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static Stream<Executable> methods() throws Throwable {
        return Stream.of(Inner.class.getDeclaredConstructor(SyntheticNameRetention.class), // Has MethodParameters with flags
                         SyntheticNameRetention.class.getDeclaredMethod("test", Executable.class)); // No MethodParameters
    }

    @ParameterizedTest
    @MethodSource("methods")
    public void test(Executable exec) {
        var params = exec.getParameters();
        for (int i = 0; i < params.length; i++) {
            var param = params[i];
            assertEquals("arg" + i, param.getName(), "name " + i);
            assertFalse(param.isNamePresent(), "name present " + i);
            assertNull(GETTER.getRawName(param), "raw name " + i);
            boolean mandated = exec instanceof Constructor<?> && i == 0;
            assertEquals(mandated, param.isImplicit(), "mandated " + i);
        }
    }
}
