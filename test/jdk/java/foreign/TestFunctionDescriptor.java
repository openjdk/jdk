/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestFunctionDescriptor
 */

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
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
        FunctionDescriptor fd_va1 = FunctionDescriptor.of(C_INT).asVariadic(C_INT, C_INT);
        FunctionDescriptor fd_va2 = FunctionDescriptor.of(C_INT, C_INT).asVariadic(C_INT);
        assertEquals(fd, fd);
        assertNotEquals(fd, fd_va1);
        assertNotEquals(fd, fd_va2);
    }
}
