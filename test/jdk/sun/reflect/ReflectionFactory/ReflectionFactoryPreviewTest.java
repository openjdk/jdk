/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.value.ValueClass;
import jdk.test.lib.helpers.StrictInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8137058 8164908 8168980 8275137 8333796
 * @summary Basic test for the unsupported ReflectionFactory
 * @modules jdk.unsupported
 * @modules java.base/jdk.internal.value
 * @enablePreview
 * @library /test/lib
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             ReflectionFactoryPreviewTest$StrictSerializableChild
 * @run junit ${test.main.class}
 */

public class ReflectionFactoryPreviewTest {

    // Initialized by init()
    static ReflectionFactory factory;

    @BeforeAll
    static void init() {
        factory = ReflectionFactory.getReflectionFactory();
    }

    abstract static value class AvcSuperType {
        int bar;

        static {
            var f = assertDoesNotThrow(() -> AvcSuperType.class.getDeclaredField("bar"));
            assertTrue(f.isStrictInit());
        }

        AvcSuperType(int bar) {
            this.bar = bar;
        }

        AvcSuperType() {
            this(42);
        }
    }

    static class SerializableChild extends AvcSuperType implements Serializable {}

    static class StrictSerializableChild extends AvcSuperType implements Serializable {
        @StrictInit int baz;

        static {
            var f = assertDoesNotThrow(() -> StrictSerializableChild.class.getDeclaredField("baz"));
            assertTrue(f.isStrictInit());
        }

        StrictSerializableChild(int baz) {
            this.baz = baz;
            super();
        }

        StrictSerializableChild() {
            this(76);
        }
    }

    @Test
    void checkNewConstructor() throws Exception {
        MethodHandles.lookup().ensureInitialized(StrictSerializableChild.class);
        assertNull(factory.newConstructorForSerialization(Integer.class));
        assertNull(factory.newConstructorForSerialization(AvcSuperType.class));
        assertSame(AvcSuperType.class, factory.newConstructorForSerialization(SerializableChild.class).getDeclaringClass());
        assertNull(factory.newConstructorForSerialization(StrictSerializableChild.class));

        assertThrows(UnsupportedOperationException.class, () ->
                factory.newConstructorForSerialization(Integer.class, Number.class.getDeclaredConstructor()));
        assertTrue(ValueClass.hasStrictInstanceField(StrictSerializableChild.class));
        var avcCtor = AvcSuperType.class.getDeclaredConstructor();
        assertThrows(UnsupportedOperationException.class, () ->
                factory.newConstructorForSerialization(StrictSerializableChild.class, avcCtor));
        assertSame(AvcSuperType.class, factory.newConstructorForSerialization(SerializableChild.class, avcCtor).getDeclaringClass());

        assertThrows(UnsupportedOperationException.class, () ->
                factory.newConstructorForSerialization(AvcSuperType.class, Object.class.getDeclaredConstructor()));
    }
}
