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

/*
 * @test
 * @bug 8365203
 * @summary Tests guarding of ByteBuffers in ClassLoader::defineClass
 * @run junit TestGuardByteBuffer
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

final class TestGuardByteBuffer {

    @Test
    void guardCrash() {
        final byte[] classBytes = getClassBytes(); // get bytes of a valid class
        final var cl = new ClassLoader() {
            void tryCrash() {
                var arena = Arena.ofConfined();
                var byteBuffer = arena.allocate(classBytes.length).asByteBuffer();
                // Close the arena underneath
                arena.close();
                // expected to always fail because the arena
                // from which the ByteBuffer was constructed
                // has been closed
                assertThrows(IllegalStateException.class,
                        () -> defineClass(null, byteBuffer, null));
            }
        };
        for (int i = 0; i < 10_000; i++) {
            cl.tryCrash();
        }
    }

    private static byte[] getClassBytes() {
        // unused. this is here just for reference
        final String source = """
                    public class NoOp {}
                """;
        // (externally) compiled content of the above "source", represented as hex
        final String classBytesHex = """
                cafebabe00000044000d0a000200030700040c000500060100106a6176612f
                6c616e672f4f626a6563740100063c696e69743e0100032829560700080100
                044e6f4f70010004436f646501000f4c696e654e756d6265725461626c6501
                000a536f7572636546696c650100094e6f4f702e6a61766100210007000200
                0000000001000100050006000100090000001d00010001000000052ab70001
                b100000001000a000000060001000000010001000b00000002000c
                """;

        return HexFormat.of().parseHex(classBytesHex.replaceAll("\n", ""));
    }

}
