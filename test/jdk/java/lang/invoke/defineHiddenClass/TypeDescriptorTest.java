/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8242013
 * @run junit/othervm test.TypeDescriptorTest
 * @summary Test TypeDescriptor::descriptorString for hidden classes which
 *          cannot be used to produce ConstantDesc via ClassDesc or
 *          MethodTypeDesc factory methods
 */

package test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.constant.*;
import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import static java.lang.invoke.MethodType.*;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TypeDescriptorTest {
    private static final Lookup HC_LOOKUP = defineHiddenClass();
    private static final Class<?> HC = HC_LOOKUP.lookupClass();
    private static Lookup defineHiddenClass() {
        String classes = System.getProperty("test.classes");
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(classes, "test/HiddenClass.class"));
            return MethodHandles.lookup().defineHiddenClass(bytes, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object[][] constables() throws Exception {
        Class<?> hcArray = Array.newInstance(HC, 1).getClass();
        return new Object[][] {
                new Object[] { HC },
                new Object[] { hcArray },
                new Object[] { methodType(HC) },
                new Object[] { methodType(void.class, HC) },
                new Object[] { methodType(void.class, HC, int.class) },
                new Object[] { HC_LOOKUP.findStatic(HC, "m", methodType(void.class)) },
                new Object[] { HC_LOOKUP.findStaticVarHandle(HC, "f", Object.class) }
        };
    }

    /*
     * Hidden classes have no nominal descriptor.
     * Constable::describeConstable returns empty optional.
     */
    @ParameterizedTest
    @MethodSource("constables")
    public void noNominalDescriptor(Constable constable) {
        assertTrue(constable.describeConstable().isEmpty());
    }

    /*
     * ClassDesc factory methods throws IAE with the name or descriptor string
     * from a hidden class
     */
    @Test
    public void testClassDesc() {
        assertThrows(IllegalArgumentException.class, () -> ClassDesc.ofDescriptor(HC.descriptorString()));
        assertThrows(IllegalArgumentException.class, () -> ClassDesc.ofDescriptor(HC.getName()));
        assertThrows(IllegalArgumentException.class, () -> ClassDesc.of(HC.getPackageName(), HC.getSimpleName()));
        assertThrows(IllegalArgumentException.class, () -> ClassDesc.of(HC.getName()));
    }

    private static Object[][] typeDescriptors() throws Exception {
        Class<?> hcArray = Array.newInstance(HC, 1, 1).getClass();
        return new Object[][] {
                new Object[] { HC, "Ltest/HiddenClass.0x[0-9a-f]+;"},
                new Object[] { hcArray, "\\[\\[Ltest/HiddenClass.0x[0-9a-f]+;"},
                new Object[] { methodType(HC), "\\(\\)Ltest/HiddenClass.0x[0-9a-f]+;" },
                new Object[] { methodType(void.class, HC), "\\(Ltest/HiddenClass.0x[0-9a-f]+;\\)V" },
                new Object[] { methodType(void.class, HC, int.class, Object.class), "\\(Ltest/HiddenClass.0x[0-9a-f]+;ILjava/lang/Object;\\)V" }
        };
    }

    /*
     * Hidden classes have no nominal type descriptor
     */
    @ParameterizedTest
    @MethodSource("typeDescriptors")
    public void testTypeDescriptor(TypeDescriptor td, String regex) throws Exception {
        String desc = td.descriptorString();
        assertTrue(desc.matches(regex));

        if (td instanceof Class) {
            assertThrows(IllegalArgumentException.class, () -> ClassDesc.ofDescriptor(desc));
        } else if (td instanceof MethodType) {
            assertThrows(IllegalArgumentException.class, () -> MethodTypeDesc.ofDescriptor(desc));
        }
    }

    private static Object[][] methodTypes() throws Exception {
        Class<?> hcArray = Array.newInstance(HC, 1, 1).getClass();
        return new Object[][] {
                new Object[] { methodType(HC), "\\(\\)Ltest/HiddenClass.0x[0-9a-f]+;" },
                new Object[] { methodType(void.class, hcArray), "\\(\\[\\[Ltest/HiddenClass.0x[0-9a-f]+;\\)V" },
                new Object[] { methodType(void.class, int.class, HC), "\\(ILtest/HiddenClass.0x[0-9a-f]+;\\)V" }
        };
    }

    /*
     * Test MethodType::toMethodDescriptorString with MethodType referencing to hidden class
     */
    @ParameterizedTest
    @MethodSource("methodTypes")
    public void testToMethodDescriptorString(MethodType mtype, String regex) throws Exception {
        String desc = mtype.toMethodDescriptorString();
        assertTrue(desc.matches(regex));

        assertThrows(IllegalArgumentException.class, () -> MethodType.fromMethodDescriptorString(desc, TypeDescriptorTest.class.getClassLoader()));
    }
}

class HiddenClass {
    private static final Object f = new Object();
    public static void m() {
    }
}
