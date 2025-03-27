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

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @bug 8352536
 * @summary Test parsing and generating non-array class data
 * @run junit BuildAndParseSegments
 */
class BuildAndParseSegments {

    @Test
    public void testParseAndGenerate() throws IOException {
        ClassFile classFile = ClassFile.of();
        // use our own class, why not?
        byte[] originalBytes;
        try (InputStream is = BuildAndParseSegments.class.getResourceAsStream("BuildAndParseSegments.class")) {
            originalBytes = is.readAllBytes();
        }

        testWithModel(classFile, classFile.parse(originalBytes));
        testWithModel(classFile, classFile.parse(MemorySegment.ofArray(originalBytes)));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_BYTE, originalBytes.length);
            segment.copyFrom(MemorySegment.ofArray(originalBytes));
            testWithModel(classFile, classFile.parse(segment));
        }
    }

    private static void testWithModel(final ClassFile classFile, final ClassModel model) {
        try (Arena arena = Arena.ofConfined()) {
            // transform to an array, buffers, and segments, and compare them all for equality
            MemorySegment asSegment = classFile.transformClass(arena, model, ClassTransform.ACCEPT_ALL);
            byte[] asArray = classFile.transformClass(model, ClassTransform.ACCEPT_ALL);

            Assertions.assertEquals(-1, asSegment.mismatch(MemorySegment.ofArray(asArray)));
        }
    }
}
