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

/*
 * @test
 * @bug 8291065
 * @summary Checks interaction of static field VarHandle with class
 *          initialization mechanism.
 * @enablePreview
 * @library java.base /test/lib
 * @build java.base/java.lang.invoke.*
 *        LazyInitializingTest ${test.main.class}
 * @run driver jdk.test.lib.helpers.StrictProcessor StrictInitializingSample StrictStaticFinalHolder
 * @run junit ${test.main.class}
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true ${test.main.class}
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false ${test.main.class}
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true
 *                    -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false ${test.main.class}
 */

import java.io.IOException;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.LookupHelper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

import jdk.test.lib.helpers.StrictInit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class StrictInitializingTest {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    @Test
    public void testOperationsDuringInit() throws Throwable {
        VarHandle[] handle = new VarHandle[1];
        boolean[] executed = {false};
        var ci = createSampleClass(new LazyInitializingTest.SampleData(() -> {
            assertThrows(IllegalStateException.class, () -> {
                int _ = (int) handle[0].get();
            }, "get before write");
            assertThrows(IllegalStateException.class, () -> {
                boolean _ = (boolean) handle[0].compareAndSet(7, 3);
            }, "compareAndSet before write");
            assertThrows(IllegalStateException.class, () -> {
                int _ = (int) handle[0].getAndAdd(3);
            }, "getAndAdd before write");
            handle[0].set(12);
            assertEquals(12, (int) handle[0].get());
            assertEquals(12, (int) handle[0].getAndAddAcquire(3));
            assertEquals(15, (int) handle[0].getVolatile());
            executed[0] = true;
        }, 24));
        handle[0] = ci.vh();

        ci.definingLookup().ensureInitialized(ci.definingLookup().lookupClass());
        assertTrue(executed[0]);
    }

    @Test
    public void testOperationsOnMethodHandleDuringInit() throws Throwable {
        MethodHandle[] handle = new MethodHandle[7];
        boolean[] executed = {false};
        var ci = createSampleClass(new LazyInitializingTest.SampleData(() -> {
            assertThrows(IllegalStateException.class, () -> {
                int _ = (int) handle[0].invoke();
            }, "get before write");
            assertThrows(IllegalStateException.class, () -> {
                boolean _ = (boolean) handle[1].invoke(7, 3);
            }, "compareAndSet before write");
            assertThrows(IllegalStateException.class, () -> {
                int _ = (int) handle[2].invoke(3);
            }, "getAndAdd before write");
            assertDoesNotThrow(() -> {
                handle[3].invokeExact(12);
                assertEquals(12, (int) handle[4].invokeExact());
                assertEquals(12, (int) handle[5].invokeExact((int) 3));
                assertEquals(15, (int) handle[6].invokeExact());
            });
            executed[0] = true;
        }, 24));
        var vh = ci.vh();
        handle[0] = vh.toMethodHandle(VarHandle.AccessMode.GET);
        handle[1] = vh.toMethodHandle(VarHandle.AccessMode.COMPARE_AND_SET);
        handle[2] = vh.toMethodHandle(VarHandle.AccessMode.GET_AND_ADD);
        handle[3] = vh.toMethodHandle(VarHandle.AccessMode.SET);
        handle[4] = vh.toMethodHandle(VarHandle.AccessMode.GET);
        handle[5] = vh.toMethodHandle(VarHandle.AccessMode.GET_AND_ADD_ACQUIRE);
        handle[6] = vh.toMethodHandle(VarHandle.AccessMode.GET_VOLATILE);

        ci.definingLookup().ensureInitialized(ci.definingLookup().lookupClass());
        assertTrue(executed[0]);
    }

    private static MethodHandles.Lookup[] lookups() {
        return new MethodHandles.Lookup[] {
                LookupHelper.IMPL_LOOKUP,
                LOOKUP,
        };
    }

    @ParameterizedTest
    @MethodSource("lookups")
    public void testSetAccessOnStaticFinal(MethodHandles.Lookup lookup) throws Throwable {
        var strictStaticFinal = lookup.findStaticVarHandle(StrictStaticFinalHolder.class, "strictStaticFinal", Object.class);
        assertFalse(strictStaticFinal.isAccessModeSupported(VarHandle.AccessMode.SET));
        var trustedStaticFinal = lookup.findStaticVarHandle(StrictStaticFinalHolder.class, "trustedStaticFinal", Object.class);
        assertFalse(trustedStaticFinal.isAccessModeSupported(VarHandle.AccessMode.SET));
        var strictInstanceFinal = lookup.findVarHandle(StrictStaticFinalHolder.class, "strictFinal", Object.class);
        assertFalse(strictInstanceFinal.isAccessModeSupported(VarHandle.AccessMode.SET));
    }

    static LazyInitializingTest.ClassInfo createSampleClass(LazyInitializingTest.SampleData sampleData) {
        try {
            var lookup = LOOKUP.defineHiddenClassWithClassData(sampleClassBytes(), sampleData, false);
            var vh = lookup.findStaticVarHandle(lookup.lookupClass(), "f", int.class);
            return new LazyInitializingTest.ClassInfo(lookup, vh);
        } catch (IllegalAccessException | NoSuchFieldException ex) {
            throw new AssertionError(ex);
        }
    }

    private static byte[] sampleClassBytes;

    private static byte[] sampleClassBytes() {
        var bytes = sampleClassBytes;
        if (bytes != null)
            return bytes;

        try (var in = StrictInitializingTest.class.getResourceAsStream("StrictInitializingSample.class")) {
            if (in == null)
                throw new AssertionError("class file not found");
            return sampleClassBytes = in.readAllBytes();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }
}

// This is used as a template class, whose bytes are used to define
// hidden classes instead
class StrictInitializingSample {
    @StrictInit
    static int f;

    static {
        try {
            var data = MethodHandles.classData(MethodHandles.lookup(), ConstantDescs.DEFAULT_NAME,
                    LazyInitializingTest.SampleData.class);
            Objects.requireNonNull(data);

            data.callback().run();
            f = data.initialValue();
        } catch (IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}

final class StrictStaticFinalHolder {
    @StrictInit
    static final Object strictStaticFinal = 5;
    static final Object trustedStaticFinal = new Object();

    @StrictInit
    final Object strictFinal;

    StrictStaticFinalHolder(Object strictFinal) {
        this.strictFinal = strictFinal;
        super();
    }
}
