/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.module;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Supporting class for computing, encoding and decoding hashes (message
 * digests).
 */

public class Hasher {
    private Hasher() { }

    /**
     * A supplier of an encoded message digest.
     */
    public static interface HashSupplier {
        String generate(String algorithm);
    }

    /**
     * Encapsulates the result of hashing the contents of a number of module
     * artifacts.
     */
    public static class DependencyHashes {
        private final String algorithm;
        private final Map<String, String> nameToHash;

        public DependencyHashes(String algorithm, Map<String, String> nameToHash) {
            this.algorithm = algorithm;
            this.nameToHash = nameToHash;
        }

        /**
         * Returns the algorithm used to hash the dependences ("SHA-256" or
         * "MD5" for example).
         */
        public String algorithm() {
            return algorithm;
        }

        /**
         * Returns the set of module names for which hashes are recorded.
         */
        public Set<String> names() {
            return nameToHash.keySet();
        }

        /**
         * Retruns the hash string for the given module name, {@code null}
         * if there is no hash recorded for the module.
         */
        public String hashFor(String dn) {
            return nameToHash.get(dn);
        }
    }


    /**
     * Computes the hash for the given file with the given message digest
     * algorithm. Returns the results a base64-encoded String.
     *
     * @throws UncheckedIOException if an I/O error occurs
     * @throws RuntimeException if the algorithm is not available
     */
    public static String generate(Path file, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);

            // Ideally we would just mmap the file but this consumes too much
            // memory when jlink is running concurrently on very large jmods
            try (FileChannel fc = FileChannel.open(file)) {
                ByteBuffer bb = ByteBuffer.allocate(32*1024);
                int nread;
                while ((nread = fc.read(bb)) > 0) {
                    bb.flip();
                    md.update(bb);
                    assert bb.remaining() == 0;
                    bb.clear();
                }
            }

            byte[] bytes = md.digest();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Computes the hash for every entry in the given map, returning a
     * {@code DependencyHashes} to encapsulate the result. The map key is
     * the entry name, typically the module name. The map value is the file
     * path to the entry (module artifact).
     *
     * @return DependencyHashes encapsulate the hashes
     */
    public static DependencyHashes generate(Map<String, Path> map, String algorithm) {
        Map<String, String> nameToHash = new HashMap<>();
        for (Map.Entry<String, Path> entry: map.entrySet()) {
            String name = entry.getKey();
            Path path = entry.getValue();
            nameToHash.put(name, generate(path, algorithm));
        }
        return new DependencyHashes(algorithm, nameToHash);
    }
}
