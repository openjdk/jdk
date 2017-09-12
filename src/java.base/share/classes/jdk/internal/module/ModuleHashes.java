/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The result of hashing the contents of a number of module artifacts.
 */

public final class ModuleHashes {

    /**
     * A supplier of a message digest.
     */
    public static interface HashSupplier {
        byte[] generate(String algorithm);
    }

    private final String algorithm;
    private final Map<String, byte[]> nameToHash;

    /**
     * Creates a {@code ModuleHashes}.
     *
     * @param algorithm   the algorithm used to create the hashes
     * @param nameToHash  the map of module name to hash value
     */
    public ModuleHashes(String algorithm, Map<String, byte[]> nameToHash) {
        this.algorithm = algorithm;
        this.nameToHash = Collections.unmodifiableMap(nameToHash);
    }

    /**
     * Returns the algorithm used to hash the modules ("SHA-256" for example).
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
     * Returns the hash for the given module name, {@code null}
     * if there is no hash recorded for the module.
     */
    public byte[] hashFor(String mn) {
        return nameToHash.get(mn);
    }

    /**
     * Returns unmodifiable map of module name to hash
     */
    public Map<String, byte[]> hashes() {
        return nameToHash;
    }

    /**
     * Computes the hash for the given file with the given message digest
     * algorithm.
     *
     * @throws UncheckedIOException if an I/O error occurs
     * @throws RuntimeException if the algorithm is not available
     */
    public static byte[] computeHash(Path file, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);

            // Ideally we would just mmap the file but this consumes too much
            // memory when jlink is running concurrently on very large jmods
            try (FileChannel fc = FileChannel.open(file)) {
                ByteBuffer bb = ByteBuffer.allocate(32*1024);
                while (fc.read(bb) > 0) {
                    bb.flip();
                    md.update(bb);
                    assert bb.remaining() == 0;
                    bb.clear();
                }
            }

            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Computes the hash for every entry in the given map, returning a
     * {@code ModuleHashes} to encapsulate the result. The map key is
     * the entry name, typically the module name. The map value is the file
     * path to the entry (module artifact).
     *
     * @return ModuleHashes that encapsulates the hashes
     */
    public static ModuleHashes generate(Map<String, Path> map, String algorithm) {
        Map<String, byte[]> nameToHash = new HashMap<>();
        for (Map.Entry<String, Path> entry: map.entrySet()) {
            String name = entry.getKey();
            Path path = entry.getValue();
            nameToHash.put(name, computeHash(path, algorithm));
        }
        return new ModuleHashes(algorithm, nameToHash);
    }

    /**
     * This is used by jdk.internal.module.SystemModules class
     * generated at link time.
     */
    public static class Builder {
        final String algorithm;
        final Map<String, byte[]> nameToHash;

        Builder(String algorithm, int initialCapacity) {
            this.nameToHash = new HashMap<>(initialCapacity);
            this.algorithm =  Objects.requireNonNull(algorithm);
        }

        /**
         * Sets the module hash for the given module name
         */
        public Builder hashForModule(String mn, byte[] hash) {
            nameToHash.put(mn, hash);
            return this;
        }

        /**
         * Builds a {@code ModuleHashes}.
         */
        public ModuleHashes build() {
            if (!nameToHash.isEmpty()) {
                return new ModuleHashes(algorithm, nameToHash);
            } else {
                return null;
            }
        }
    }
}
