/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native
 *   --enable-native-access=ALL-UNNAMED
 *   TestUpcallHighArity
 */

import java.lang.foreign.Addressable;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;

public class TestUpcallHighArity extends CallGeneratorHelper {
    static final MethodHandle MH_do_upcall;
    static final MethodHandle MH_passAndSave;
    static final Linker LINKER = Linker.nativeLinker();

    // struct S_PDI { void* p0; double p1; int p2; };
    static final MemoryLayout S_PDI_LAYOUT = MemoryLayout.structLayout(
        C_POINTER.withName("p0"),
        C_DOUBLE.withName("p1"),
        C_INT.withName("p2"),
        MemoryLayout.paddingLayout(32)
    );

    static {
        try {
            System.loadLibrary("TestUpcallHighArity");
            MH_do_upcall = LINKER.downcallHandle(
                    findNativeOrThrow("do_upcall"),
                    FunctionDescriptor.ofVoid(C_POINTER,
                    S_PDI_LAYOUT, C_INT, C_DOUBLE, C_POINTER,
                    S_PDI_LAYOUT, C_INT, C_DOUBLE, C_POINTER,
                    S_PDI_LAYOUT, C_INT, C_DOUBLE, C_POINTER,
                    S_PDI_LAYOUT, C_INT, C_DOUBLE, C_POINTER)
            );
            MH_passAndSave = MethodHandles.lookup().findStatic(TestUpcallHighArity.class, "passAndSave",
                    MethodType.methodType(void.class, Object[].class, AtomicReference.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    static void passAndSave(Object[] o, AtomicReference<Object[]> ref) {
        for (int i = 0; i < o.length; i++) {
            if (o[i] instanceof MemorySegment) {
                MemorySegment ms = (MemorySegment) o[i];
                MemorySegment copy = MemorySegment.allocateNative(ms.byteSize(), MemorySession.openImplicit());
                copy.copyFrom(ms);
                o[i] = copy;
            }
        }
        ref.set(o);
    }

    @Test(dataProvider = "args")
    public void testUpcall(MethodHandle downcall, MethodType upcallType,
                           FunctionDescriptor upcallDescriptor) throws Throwable {
        AtomicReference<Object[]> capturedArgs = new AtomicReference<>();
        MethodHandle target = MethodHandles.insertArguments(MH_passAndSave, 1, capturedArgs)
                                         .asCollector(Object[].class, upcallType.parameterCount())
                                         .asType(upcallType);
        try (MemorySession session = MemorySession.openConfined()) {
            Addressable upcallStub = LINKER.upcallStub(target, upcallDescriptor, session);
            Object[] args = new Object[upcallType.parameterCount() + 1];
            args[0] = upcallStub;
            List<MemoryLayout> argLayouts = upcallDescriptor.argumentLayouts();
            for (int i = 1; i < args.length; i++) {
                args[i] = makeArg(argLayouts.get(i - 1), null, false);
            }

            downcall.invokeWithArguments(args);

            Object[] capturedArgsArr = capturedArgs.get();
            for (int i = 0; i < capturedArgsArr.length; i++) {
                if (upcallType.parameterType(i) == MemorySegment.class) {
                    assertStructEquals((MemorySegment) capturedArgsArr[i], (MemorySegment) args[i + 1], argLayouts.get(i));
                } else {
                    assertEquals(capturedArgsArr[i], args[i + 1], "For index " + i);
                }
            }
        }
    }

    @DataProvider
    public static Object[][] args() {
        return new Object[][]{
            { MH_do_upcall,
                MethodType.methodType(void.class,
                    MemorySegment.class, int.class, double.class, MemoryAddress.class,
                    MemorySegment.class, int.class, double.class, MemoryAddress.class,
                    MemorySegment.class, int.class, double.class, MemoryAddress.class,
                    MemorySegment.class, int.class, double.class, MemoryAddress.class),
                FunctionDescriptor.ofVoid(
                    S_PDI_LAYOUT, C_INT, C_DOUBLE, C_POINTER,
                    S_PDI_LAYOUT, C_INT, C_DOUBLE, C_POINTER,
                    S_PDI_LAYOUT, C_INT, C_DOUBLE, C_POINTER,
                    S_PDI_LAYOUT, C_INT, C_DOUBLE, C_POINTER)
            }
        };
    }

}
