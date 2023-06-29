/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *          initialization mechanism..
 * @run junit LazyInitializingTest
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true LazyInitializingTest
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false LazyInitializingTest
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true
 *                    -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false LazyInitializingTest
 */

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class LazyInitializingTest {

    record SampleData(Runnable callback, int initialValue) {
        private static final Runnable FAIL_ON_CLINIT_CALLBACK = () -> {
            throw new AssertionError("Class shouldn't be initialized");
        };
        static final SampleData FAIL_ON_CLINIT = new SampleData();

        SampleData() {
            this(FAIL_ON_CLINIT_CALLBACK, 0);
        }
    }
    record ClassInfo(MethodHandles.Lookup definingLookup, VarHandle vh) {}

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * Meta test to ensure the testing mechanism to check initialization is correct.
     */
    @Test
    public void testMeta() throws IllegalAccessException {
        boolean[] val = new boolean[1];
        var v0 = createSampleClass(new SampleData(() -> val[0] = true, 0));
        assertFalse(val[0], "callback run before class init");
        v0.definingLookup.ensureInitialized(v0.definingLookup.lookupClass());
        assertTrue(val[0], "callback not run at class init");
    }

    @Test
    public void testUninitializedOperations() {
        var ci = createSampleClass(SampleData.FAIL_ON_CLINIT);
        var vh = ci.vh;
        vh.describeConstable();
        vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_XOR_ACQUIRE);
        vh.withInvokeExactBehavior();
        vh.withInvokeBehavior();
        vh.toMethodHandle(VarHandle.AccessMode.GET_AND_BITWISE_XOR_ACQUIRE);
        vh.hasInvokeExactBehavior();
        vh.accessModeType(VarHandle.AccessMode.GET_AND_BITWISE_XOR_ACQUIRE);
    }

    @Test
    public void testInitializationOnVarHandleUse() {
        var initialized = new boolean[1];
        var ci = createSampleClass(new SampleData(() -> initialized[0] = true, 42));
        var vh = ci.vh;

        assertEquals(42, (int) vh.get(), "VH does not read value set in class initializer");
        assertTrue(initialized[0], "class initialization not captured");
    }

    @Test
    public void testInitializationOnToMethodHandleUse() throws Throwable {
        var initialized = new boolean[1];
        var ci = createSampleClass(new SampleData(() -> initialized[0] = true, 42));
        var mh = ci.vh.toMethodHandle(VarHandle.AccessMode.GET);

        assertEquals(42, (int) mh.invokeExact(), "VH does not read value set in class initializer");
        assertTrue(initialized[0], "class initialization not captured");
    }

    @Test
    public void testParentChildLoading() throws Throwable {
        // ChildSample: ensure only ParentSample (field declarer) is initialized
        var l = new ParentChildLoader();
        var childSampleClass = l.childClass();
        var lookup = MethodHandles.privateLookupIn(childSampleClass, LOOKUP);
        var childVh = lookup.findStaticVarHandle(childSampleClass, "f", int.class);

        assertEquals(3, (int) childVh.get(), "Child class initialized unnecessarily");

        lookup.ensureInitialized(childSampleClass);

        assertEquals(6, (int) childVh.get(), "Child class was not initialized");
    }

    static ClassInfo createSampleClass(SampleData sampleData) {
        try {
            var lookup = LOOKUP.defineHiddenClassWithClassData(sampleClassBytes(), sampleData, false);
            var vh = lookup.findStaticVarHandle(lookup.lookupClass(), "f", int.class);
            return new ClassInfo(lookup, vh);
        } catch (IllegalAccessException | NoSuchFieldException ex) {
            throw new AssertionError(ex);
        }
    }

    private static byte[] sampleClassBytes;

    private static byte[] sampleClassBytes() {
        var bytes = sampleClassBytes;
        if (bytes != null)
            return bytes;

        try (var in = LazyInitializingTest.class.getResourceAsStream("LazyInitializingSample.class")) {
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
class LazyInitializingSample {
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

class ParentChildLoader extends ClassLoader {
    ParentChildLoader() {
        super(LazyInitializingTest.class.getClassLoader().getParent());
    }

    Class<?> parentClass() {
        try {
            return loadClass("ParentSample");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    Class<?> childClass() {
        try {
            return loadClass("ChildSample");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try (var stream = switch (name) {
            case "ParentSample", "ChildSample" -> LazyInitializingTest.class.getResourceAsStream(name + ".class");
            default -> throw new ClassNotFoundException(name);
        }) {
            if (stream == null)
                throw new AssertionError();
            var b = stream.readAllBytes();
            return defineClass(name, b, 0, b.length);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }
}

class ParentSample {
    static int f;

    static {
        f = 3;
    }
}

class ChildSample extends ParentSample {
    static {
        f = 6;
    }
}