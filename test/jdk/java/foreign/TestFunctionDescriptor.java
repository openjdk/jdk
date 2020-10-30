/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestFunctionDescriptor
 */

import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryLayout;
import org.testng.annotations.Test;

import java.lang.constant.Constable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static jdk.incubator.foreign.CLinker.C_DOUBLE;
import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.CLinker.C_LONG_LONG;
import static jdk.incubator.foreign.CLinker.C_POINTER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestFunctionDescriptor {

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
    public void testAttribute() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_DOUBLE, C_LONG_LONG);
        fd = fd.withAttribute(DUMMY_ATTR, true);

        assertEquals(fd.argumentLayouts(), List.of(C_DOUBLE, C_LONG_LONG));
        Optional<MemoryLayout> returnLayoutOp = fd.returnLayout();
        assertTrue(returnLayoutOp.isPresent());
        assertEquals(returnLayoutOp.get(), C_INT);
        assertEquals(fd.attributes().collect(Collectors.toList()), List.of(DUMMY_ATTR));
        Optional<Constable> attr = fd.attribute(DUMMY_ATTR);
        assertTrue(attr.isPresent());
        assertEquals(attr.get(), true);
    }

    @Test
    public void testAppendArgumentLayouts() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_DOUBLE, C_LONG_LONG)
                                                  .withAttribute(DUMMY_ATTR, true);
        fd = fd.withAppendedArgumentLayouts(C_POINTER);

        assertEquals(fd.argumentLayouts(), List.of(C_DOUBLE, C_LONG_LONG, C_POINTER));
        Optional<MemoryLayout> returnLayoutOp = fd.returnLayout();
        assertTrue(returnLayoutOp.isPresent());
        assertEquals(returnLayoutOp.get(), C_INT);
        assertEquals(fd.attributes().collect(Collectors.toList()), List.of(DUMMY_ATTR));
    }

    @Test
    public void testChangeReturnLayout() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_DOUBLE, C_LONG_LONG)
                                                  .withAttribute(DUMMY_ATTR, true);
        fd = fd.withReturnLayout(C_INT);

        assertEquals(fd.argumentLayouts(), List.of(C_DOUBLE, C_LONG_LONG));
        Optional<MemoryLayout> returnLayoutOp = fd.returnLayout();
        assertTrue(returnLayoutOp.isPresent());
        assertEquals(returnLayoutOp.get(), C_INT);
        assertEquals(fd.attributes().collect(Collectors.toList()), List.of(DUMMY_ATTR));
    }

    @Test
    public void testDropReturnLayout() {
        FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_DOUBLE, C_LONG_LONG)
                                                  .withAttribute(DUMMY_ATTR, true);
        fd = fd.withVoidReturnLayout();

        assertEquals(fd.argumentLayouts(), List.of(C_DOUBLE, C_LONG_LONG));
        Optional<MemoryLayout> returnLayoutOp = fd.returnLayout();
        assertFalse(returnLayoutOp.isPresent());
        assertEquals(fd.attributes().collect(Collectors.toList()), List.of(DUMMY_ATTR));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullArgumentLayout() {
        FunctionDescriptor.ofVoid(C_INT, null, C_LONG_LONG);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullReturnLayout() {
        FunctionDescriptor.of(null, C_INT, C_LONG_LONG);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullArgumentLayoutsAppend() {
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_LONG_LONG);
        fd.withAppendedArgumentLayouts(C_DOUBLE, null); // should throw
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullReturnLayoutChange() {
        FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_INT, C_LONG_LONG);
        fd.withReturnLayout(null); // should throw
    }

}
