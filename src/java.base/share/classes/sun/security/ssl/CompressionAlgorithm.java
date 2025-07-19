/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Enum for (D)TLS certificate compression algorithms.
 */
enum CompressionAlgorithm {
    ZLIB                (1,  "zlib"),
    BROTLI              (2,  "brotli"),
    ZSTD                (3,  "zstd");

    final int id;
    final String name;

    CompressionAlgorithm(int id, String name) {
        this.id = id;
        this.name = name;
    }

    static CompressionAlgorithm nameOf(String name) {
        for (CompressionAlgorithm cca :
                CompressionAlgorithm.values()) {
            if (cca.name.equals(name)) {
                return cca;
            }
        }

        return null;
    }

    static String nameOf(int id) {
        for (CompressionAlgorithm cca :
                CompressionAlgorithm.values()) {
            if (cca.id == id) {
                return cca.name;
            }
        }

        return "<UNKNOWN CONTENT TYPE: " + id + ">";
    }

    // Return the size of a SignatureScheme structure in TLS record
    static int sizeInRecord() {
        return 2;
    }

    // Get local supported algorithm collection.
    static Map<Integer, Function<byte[], byte[]>> findInflaters(
            SSLConfiguration config) {
        if (config.certInflaters == null || config.certInflaters.isEmpty()) {
            if (SSLLogger.isOn &&
                    SSLLogger.isOn("ssl,handshake,verbose")) {
                SSLLogger.finest(
                    "No supported certificate compression algorithms");
            }
            return Map.of();
        }

        Map<Integer, Function<byte[], byte[]>> inflaters =
                new LinkedHashMap<>(config.certInflaters.size());
        for (Map.Entry<String, Function<byte[], byte[]>> entry :
                config.certInflaters.entrySet()) {
            CompressionAlgorithm ca =
                    CompressionAlgorithm.nameOf(entry.getKey());
            if (ca == null) {
                if (SSLLogger.isOn &&
                        SSLLogger.isOn("ssl,handshake,verbose")) {
                    SSLLogger.finest(
                        "Ignore unsupported certificate " +
                        "compression algorithm: " + entry.getKey());
                }
                continue;
            }

            inflaters.putIfAbsent(ca.id, entry.getValue());
        }

        return inflaters;
    }

    static Map.Entry<Integer, Function<byte[], byte[]>> selectDeflater(
            SSLConfiguration config,
            int[] compressionAlgorithmIds) {
        if (config.certDeflaters == null) {
            return null;
        }

        for (Map.Entry<String, Function<byte[], byte[]>> entry :
                config.certDeflaters.entrySet()) {
            CompressionAlgorithm ca =
                    CompressionAlgorithm.nameOf(entry.getKey());
            if (ca != null) {
                for (int id :  compressionAlgorithmIds) {
                    if (ca.id == id) {
                        return new AbstractMap.SimpleImmutableEntry<>(
                                id, entry.getValue());
                    }
                }
            }
        }

        return null;
    }
}
