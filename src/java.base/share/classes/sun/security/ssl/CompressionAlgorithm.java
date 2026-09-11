/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.ByteArrayOutputStream;
import java.util.AbstractMap;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Enum for TLS certificate compression algorithms.
 * This class also defines internally supported inflate/deflate functions.
 */

enum CompressionAlgorithm {
    ZLIB(1);  // Currently only ZLIB is supported.

    final int id;

    CompressionAlgorithm(int id) {
        this.id = id;
    }

    static CompressionAlgorithm nameOf(int id) {
        for (CompressionAlgorithm ca : CompressionAlgorithm.values()) {
            if (ca.id == id) {
                return ca;
            }
        }

        return null;
    }

    // Return the size of a compression algorithms structure in TLS record.
    static int sizeInRecord() {
        return 2;
    }

    // The size of compression/decompression buffer.
    private static final int BUF_SIZE = 1024;

    private static final Map<Integer, Function<byte[], byte[]>> DEFLATORS =
            Map.of(ZLIB.id, (input) -> {
                try (Deflater deflater = new Deflater();
                        ByteArrayOutputStream outputStream =
                                new ByteArrayOutputStream(input.length)) {

                    deflater.setInput(input);
                    deflater.finish();
                    byte[] buffer = new byte[BUF_SIZE];

                    while (!deflater.finished()) {
                        int compressedSize = deflater.deflate(buffer);
                        outputStream.write(buffer, 0, compressedSize);
                    }

                    return outputStream.toByteArray();
                } catch (Exception e) {
                    if (SSLLogger.isOn()
                            && SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                        SSLLogger.warning("Exception during certificate "
                                + "compression: ", e);
                    }
                    return null;
                }
            });

    static Map.Entry<Integer, Function<byte[], byte[]>> selectDeflater(
            int[] compressionAlgorithmIds) {

        for (var entry : DEFLATORS.entrySet()) {
            for (int id : compressionAlgorithmIds) {
                if (id == entry.getKey()) {
                    return new AbstractMap.SimpleImmutableEntry<>(entry);
                }
            }
        }

        return null;
    }

    private static final Map<Integer, Function<byte[], byte[]>> INFLATORS =
            Map.of(ZLIB.id, (input) -> {
                try (Inflater inflater = new Inflater();
                        ByteArrayOutputStream outputStream =
                                new ByteArrayOutputStream(input.length)) {

                    inflater.setInput(input);
                    byte[] buffer = new byte[BUF_SIZE];

                    while (!inflater.finished()) {
                        int decompressedSize = inflater.inflate(buffer);

                        if (decompressedSize == 0) {
                            if (inflater.needsDictionary()) {
                                if (SSLLogger.isOn()
                                        && SSLLogger.isOn(
                                        SSLLogger.Opt.HANDSHAKE)) {
                                    SSLLogger.warning("Compressed input "
                                            + "requires a dictionary");
                                }

                                return null;
                            }

                            if (inflater.needsInput()) {
                                if (SSLLogger.isOn()
                                        && SSLLogger.isOn(
                                        SSLLogger.Opt.HANDSHAKE)) {
                                    SSLLogger.warning(
                                            "Incomplete compressed input");
                                }

                                return null;
                            }

                            // Else just break the loop.
                            break;
                        }

                        outputStream.write(buffer, 0, decompressedSize);

                        // Bound the memory usage.
                        if (outputStream.size()
                                > SSLConfiguration.maxHandshakeMessageSize) {
                            if (SSLLogger.isOn()
                                    && SSLLogger.isOn(
                                    SSLLogger.Opt.HANDSHAKE)) {
                                SSLLogger.warning("The size of the "
                                        + "uncompressed certificate message "
                                        + "exceeds maximum allowed size of "
                                        + SSLConfiguration.maxHandshakeMessageSize
                                        + " bytes; compressed size: "
                                        + input.length);
                            }

                            return null;
                        }
                    }

                    return outputStream.toByteArray();
                } catch (Exception e) {
                    if (SSLLogger.isOn()
                            && SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                        SSLLogger.warning(
                                "Exception during certificate decompression: ",
                                e);
                    }
                    return null;
                }
            });

    static Map<Integer, Function<byte[], byte[]>> getInflaters() {
        return INFLATORS;
    }
}
