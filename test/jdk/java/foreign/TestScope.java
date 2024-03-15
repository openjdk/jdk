/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestScope
 */

import org.testng.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HexFormat;
import java.util.stream.LongStream;

import static org.testng.Assert.*;

public class TestScope {

    static {
        System.loadLibrary("LookupTest");
    }

    @Test
    public void testDifferentArrayScope() {
        MemorySegment.Scope scope1 = MemorySegment.ofArray(new byte[10]).scope();
        MemorySegment.Scope scope2 = MemorySegment.ofArray(new byte[10]).scope();
        assertNotEquals(scope1, scope2);
    }

    @Test
    public void testDifferentBufferScope() {
        MemorySegment.Scope scope1 = MemorySegment.ofBuffer(ByteBuffer.allocateDirect(10)).scope();
        MemorySegment.Scope scope2 = MemorySegment.ofBuffer(ByteBuffer.allocateDirect(10)).scope();
        assertNotEquals(scope1, scope2);
    }

    @Test
    public void testDifferentArenaScope() {
        MemorySegment.Scope scope1 = Arena.ofAuto().allocate(10).scope();
        MemorySegment.Scope scope2 = Arena.ofAuto().allocate(10).scope();
        assertNotEquals(scope1, scope2);
    }

    @Test
    public void testSameArrayScope() {
        byte[] arr = new byte[10];
        assertEquals(MemorySegment.ofArray(arr).scope(), MemorySegment.ofArray(arr).scope());
        ByteBuffer buf = ByteBuffer.wrap(arr);
        assertEquals(MemorySegment.ofArray(arr).scope(), MemorySegment.ofBuffer(buf).scope());
        testDerivedBufferScope(MemorySegment.ofArray(arr));
    }

    @Test
    public void testSameBufferScope() {
        ByteBuffer buf = ByteBuffer.allocateDirect(10);
        assertEquals(MemorySegment.ofBuffer(buf).scope(), MemorySegment.ofBuffer(buf).scope());
        testDerivedBufferScope(MemorySegment.ofBuffer(buf));
    }

    @Test
    public void testSameArenaScope() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment1 = arena.allocate(10);
            MemorySegment segment2 = arena.allocate(10);
            assertEquals(segment1.scope(), segment2.scope());
            testDerivedBufferScope(segment1);
        }
    }

    @Test
    public void testSameNativeScope() {
        MemorySegment segment1 = MemorySegment.ofAddress(42);
        MemorySegment segment2 = MemorySegment.ofAddress(43);
        assertEquals(segment1.scope(), segment2.scope());
        assertEquals(segment1.scope(), segment2.reinterpret(10).scope());
        assertEquals(segment1.scope(), Arena.global().scope());
        testDerivedBufferScope(segment1.reinterpret(10));
    }

    @Test
    public void testSameLookupScope() {
        SymbolLookup loaderLookup = SymbolLookup.loaderLookup();
        MemorySegment segment1 = loaderLookup.find("f").get();
        MemorySegment segment2 = loaderLookup.find("c").get();
        assertEquals(segment1.scope(), segment2.scope());
        testDerivedBufferScope(segment1.reinterpret(10));
    }

    @Test
    public void testZeroedOfAuto() {
        testZeroed(Arena.ofAuto());
    }

    @Test
    public void testZeroedGlobal() {
        testZeroed(Arena.global());
    }

    @Test
    public void testZeroedOfConfined() {
        try (Arena arena = Arena.ofConfined()) {
            testZeroed(arena);
        }
    }

    @Test
    public void testZeroedOfShared() {
        try (Arena arena = Arena.ofShared()) {
            testZeroed(arena);
        }
    }

    void testDerivedBufferScope(MemorySegment segment) {
        ByteBuffer buffer = segment.asByteBuffer();
        MemorySegment.Scope expectedScope = segment.scope();
        assertEquals(MemorySegment.ofBuffer(buffer).scope(), expectedScope);
        // buffer slices should have same scope
        ByteBuffer slice = buffer.slice(0, 2);
        assertEquals(expectedScope, MemorySegment.ofBuffer(slice).scope());
        // buffer views should have same scope
        IntBuffer view = buffer.asIntBuffer();
        assertEquals(expectedScope, MemorySegment.ofBuffer(view).scope());
    }

    private static final MemorySegment ZEROED_MEMORY = MemorySegment.ofArray(new byte[8102]);

    void testZeroed(Arena arena) {
        long byteSize = ZEROED_MEMORY.byteSize();
        var segment = arena.allocate(byteSize, Long.BYTES);
        long mismatch = ZEROED_MEMORY.mismatch(segment);
        assertEquals(mismatch, -1);
    }

}
