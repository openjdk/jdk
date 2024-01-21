/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestFunctionDescriptor
 */

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class TestFunctionDescriptor extends NativeTestHelper {

    static final String DUMMY_ATTR = "dummy";

    @Test
    public void testOf() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_DOUBLE, C_LONG_LONG);

        assertEquals(fd.argumentLayouts(), List.of(C_DOUBLE, C_LONG_LONG));
        Optional<MemoryLayout> returnLayoutOp = fd.returnLayout();
        assertTrue(returnLayoutOp.isPresent());
        assertEquals(returnLayoutOp.get(), C_INT);
    }

    @Test
    public void testOfVoid() {
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_DOUBLE, C_LONG_LONG);

        assertEquals(fd.argumentLayouts(), List.of(C_DOUBLE, C_LONG_LONG));
        Optional<MemoryLayout> returnLayoutOp = fd.returnLayout();
        assertFalse(returnLayoutOp.isPresent());
    }

    @Test
    public void testAppendArgumentLayouts() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_DOUBLE, C_LONG_LONG);
        fd = fd.appendArgumentLayouts(C_POINTER);

        assertEquals(fd.argumentLayouts(), List.of(C_DOUBLE, C_LONG_LONG, C_POINTER));
        Optional<MemoryLayout> returnLayoutOp = fd.returnLayout();
        assertTrue(returnLayoutOp.isPresent());
        assertEquals(returnLayoutOp.get(), C_INT);
    }

    @Test
    public void testChangeReturnLayout() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_DOUBLE, C_LONG_LONG);
        fd = fd.changeReturnLayout(C_INT);

        assertEquals(fd.argumentLayouts(), List.of(C_DOUBLE, C_LONG_LONG));
        Optional<MemoryLayout> returnLayoutOp = fd.returnLayout();
        assertTrue(returnLayoutOp.isPresent());
        assertEquals(returnLayoutOp.get(), C_INT);
    }

    @Test
    public void testDropReturnLayout() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_DOUBLE, C_LONG_LONG);
        fd = fd.dropReturnLayout();

        assertEquals(fd.argumentLayouts(), List.of(C_DOUBLE, C_LONG_LONG));
        Optional<MemoryLayout> returnLayoutOp = fd.returnLayout();
        assertFalse(returnLayoutOp.isPresent());
    }

    @Test
    public void testEquals() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_INT, C_INT);
        assertEquals(fd, fd);
        FunctionDescriptor fdReturnSomethingElse = FunctionDescriptor.of(C_LONG_LONG, C_INT, C_INT);
        FunctionDescriptor fdOtherArguments = FunctionDescriptor.of(C_INT, C_INT);
        assertFalse(fd.equals(fdReturnSomethingElse));
        assertFalse(fd.equals(fdOtherArguments));
        assertFalse(fd.equals(null));
        assertFalse(fd.equals("A"));
    }

    @Test
    public void testCarrierMethodType() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT,
                C_INT,
                MemoryLayout.structLayout(C_INT, C_INT),
                MemoryLayout.sequenceLayout(3, C_INT));
        MethodType cmt = fd.toMethodType();
        assertEquals(cmt, MethodType.methodType(int.class, int.class, MemorySegment.class, MemorySegment.class));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadCarrierMethodType() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT,
                C_INT,
                MemoryLayout.structLayout(C_INT, C_INT),
                MemoryLayout.sequenceLayout(3, C_INT),
                MemoryLayout.paddingLayout(4));
        fd.toMethodType(); // should throw
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testIllegalInsertArgNegIndex() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT);
        fd.insertArgumentLayouts(-1, C_INT);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testIllegalInsertArgOutOfBounds() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT);
        fd.insertArgumentLayouts(2, C_INT);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPaddingInVoidFunction() {
        FunctionDescriptor.ofVoid(MemoryLayout.paddingLayout(1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPaddingInNonVoidFunction() {
        FunctionDescriptor.of(MemoryLayout.paddingLayout(1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPaddingInAppendArgLayouts() {
        FunctionDescriptor.ofVoid().appendArgumentLayouts(MemoryLayout.paddingLayout(1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPaddingInInsertArgLayouts() {
        FunctionDescriptor.ofVoid().insertArgumentLayouts(0, MemoryLayout.paddingLayout(1));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPaddingInChangeRetLayout() {
        FunctionDescriptor.ofVoid().changeReturnLayout(MemoryLayout.paddingLayout(1));
    }

}
