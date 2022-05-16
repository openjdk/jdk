/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestClassLoaderFindNative
 */

import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import org.testng.annotations.Test;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.testng.Assert.*;

// FYI this test is run on 64-bit platforms only for now,
// since the windows 32-bit linker fails and there
// is some fallback behaviour to use the 64-bit linker,
// where cygwin gets in the way and we accidentally pick up its
// link.exe
public class TestClassLoaderFindNative {
    static {
        System.loadLibrary("LookupTest");
    }

    @Test
    public void testSimpleLookup() {
        assertFalse(SymbolLookup.loaderLookup().lookup("f").isEmpty());
    }

    @Test
    public void testInvalidSymbolLookup() {
        assertTrue(SymbolLookup.loaderLookup().lookup("nonExistent").isEmpty());
    }

    @Test
    public void testVariableSymbolLookup() {
        MemorySegment segment = MemorySegment.ofAddress(
                SymbolLookup.loaderLookup().lookup("c").get().address(),
                ValueLayout.JAVA_INT.byteSize(),
                MemorySession.global());
        assertEquals(segment.get(JAVA_BYTE, 0), 42);
    }
}
