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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8370839
 * @summary Behavior of protected methods in java.lang.Object
 * @modules java.base/java.lang:+open
 * @run junit ProtectedObjectMethods
 */
public class ProtectedObjectMethods {

    static final MethodHandle OBJECT_CLONE;
    static final MethodHandle OBJECT_FINALIZE;

    static {
        try {
            var lookup = MethodHandles.privateLookupIn(Object.class, MethodHandles.lookup());
            OBJECT_CLONE = lookup.findVirtual(Object.class, "clone", MethodType.methodType(Object.class));
            OBJECT_FINALIZE = lookup.findVirtual(Object.class, "finalize", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    interface FakeClone {
        FakeClone clone();
    }

    interface TrueClone {
        Object clone();
    }

    interface PrimitiveClone {
        int clone();
    }

    // Does not duplicate with Object::clone so it is not proxied
    @Test
    void testDistinctClone() throws Throwable {
        {
            var fake = (FakeClone) Proxy.newProxyInstance(FakeClone.class.getClassLoader(), new Class[] { FakeClone.class }, (p, _, _) -> p);
            assertSame(fake, fake.clone());
            assertThrows(CloneNotSupportedException.class, () -> {
                var _ = (Object) OBJECT_CLONE.invoke((Object) fake);
            });
        }

        {
            var fake = (FakeClone) Proxy.newProxyInstance(FakeClone.class.getClassLoader(), new Class[] { FakeClone.class, Cloneable.class }, (p, _, _) -> p);
            assertSame(fake, fake.clone());
            var fakeClone = (Object) OBJECT_CLONE.invoke((Object) fake);
            assertNotSame(fake, fakeClone);
            assertSame(fake.getClass(), fakeClone.getClass());
            assertSame(fakeClone, ((FakeClone) fakeClone).clone());
        }

        {
            var instance = (PrimitiveClone) Proxy.newProxyInstance(PrimitiveClone.class.getClassLoader(), new Class[] { PrimitiveClone.class }, (_, _, _) -> 42);
            assertEquals(42, instance.clone());
            assertThrows(CloneNotSupportedException.class, () -> {
                var _ = (Object) OBJECT_CLONE.invoke((Object) instance);
            });
        }

        {
            var instance = (PrimitiveClone) Proxy.newProxyInstance(PrimitiveClone.class.getClassLoader(), new Class[] { PrimitiveClone.class, Cloneable.class }, (_, _, _) -> 76);
            assertEquals(76, instance.clone());
            var clone = (Object) OBJECT_CLONE.invoke((Object) instance);
            assertNotSame(instance, clone);
            assertSame(instance.getClass(), clone.getClass());
            assertEquals(76, ((PrimitiveClone) clone).clone());
        }
    }

    // Duplicates with Object::clone so it is proxied
    @Test
    void testDuplicateClone() throws Throwable {
        {
            // TrueClone::clone accidentally overrides Object::clone
            var instance = (TrueClone) Proxy.newProxyInstance(TrueClone.class.getClassLoader(), new Class[] { TrueClone.class }, (p, _, _) -> p);
            assertSame(instance, instance.clone());
            assertSame(instance, (Object) OBJECT_CLONE.invoke((Object) instance));
        }

        {
            // Use the TrueClone to bridge Object::clone and FakeClone::clone
            var instance = Proxy.newProxyInstance(TrueClone.class.getClassLoader(), new Class[] { TrueClone.class, FakeClone.class }, (p, _, _) -> p);
            assertSame(instance, ((FakeClone) instance).clone());
            assertSame(instance, ((TrueClone) instance).clone());
            assertSame(instance, (Object) OBJECT_CLONE.invoke((Object) instance));
        }
    }

    interface FalseFinalize {
        int finalize();
    }

    interface TrueFinalize {
        void finalize();
    }

    @Test
    void testDistinctFinalize() throws Throwable {
        AtomicInteger invokeCount = new AtomicInteger();
        var instance = Proxy.newProxyInstance(FalseFinalize.class.getClassLoader(), new Class[] { FalseFinalize.class }, (_, _, _) -> invokeCount.incrementAndGet());
        OBJECT_FINALIZE.invoke(instance);
        assertEquals(0, invokeCount.get());
        assertEquals(1, ((FalseFinalize) instance).finalize());
    }

    @Test
    void testDuplicateFinalize() throws Throwable {
        AtomicInteger invokeCount = new AtomicInteger();
        var instance = Proxy.newProxyInstance(TrueFinalize.class.getClassLoader(), new Class[] { TrueFinalize.class }, (_, _, _) -> invokeCount.incrementAndGet());
        OBJECT_FINALIZE.invoke(instance);
        assertEquals(1, invokeCount.get());
        ((TrueFinalize) instance).finalize();
        assertEquals(2, invokeCount.get());
    }
}
