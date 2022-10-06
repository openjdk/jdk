/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 *
 * @run testng/othervm -Dos.arch=unknown -Dos.name=unknown --enable-native-access=ALL-UNNAMED TestUnsupportedLinker
 */

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySession;
import java.lang.foreign.VaList;
import java.lang.foreign.ValueLayout;

import org.testng.annotations.Test;

public class TestUnsupportedLinker {

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testLinker() {
        Linker.nativeLinker();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testEmptyVaList() {
        VaList.empty();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testNonEmptyVaList() {
        VaList.make(builder -> builder.addVarg(ValueLayout.JAVA_INT, 42), MemorySession.openImplicit());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testUnsafeVaList() {
        VaList.ofAddress(MemoryAddress.NULL, MemorySession.openImplicit());
    }
}
