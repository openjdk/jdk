/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.util.zip;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

class Snippets {

    void deflaterInflaterExample() {
        // @start region="DeflaterInflaterExample"

        // Encode a String into bytes
        String inputString = "blahblahblah\u20AC\u20AC";
        byte[] input = inputString.getBytes(StandardCharsets.UTF_8);

        // Compress the bytes
        ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        Deflater compressor = new Deflater();
        try {
            compressor.setInput(input);
            // Let the compressor know that the complete input
            // has been made available
            compressor.finish();
            // Keep compressing the input till the compressor
            // is finished compressing
            while (!compressor.finished()) {
                // Use some reasonable size for the temporary buffer
                // based on the data being compressed
                byte[] tmpBuffer = new byte[100];
                int numCompressed = compressor.deflate(tmpBuffer);
                // Copy over the compressed bytes from the temporary
                // buffer into the final byte array
                compressedBaos.write(tmpBuffer, 0, numCompressed);
            }
        } finally {
            // Release the resources held by the compressor
            compressor.end();
        }

        // Decompress the bytes
        Inflater decompressor = new Inflater();
        ByteArrayOutputStream decompressedBaos = new ByteArrayOutputStream();
        try {
            byte[] compressed = compressedBaos.toByteArray();
            decompressor.setInput(compressed, 0, compressed.length);
            while (!decompressor.finished()) {
                // Use some reasonable size for the temporary buffer,
                // based on the data being decompressed; in this example,
                // we use a small buffer size
                byte[] tmpBuffer = new byte[100];
                int numDecompressed = 0;
                try {
                    numDecompressed = decompressor.inflate(tmpBuffer);
                } catch (DataFormatException dfe) {
                    // Handle the exception suitably, in this example
                    // we just rethrow it
                    throw new RuntimeException(dfe);
                }
                // Copy over the decompressed bytes from the temporary
                // buffer into the final byte array
                decompressedBaos.write(tmpBuffer, 0, numDecompressed);
            }
        } finally {
            // Release the resources held by the decompressor
            decompressor.end();
        }
        // Decode the bytes into a String
        String outputString = decompressedBaos.toString(StandardCharsets.UTF_8);

        // @end
    }

}