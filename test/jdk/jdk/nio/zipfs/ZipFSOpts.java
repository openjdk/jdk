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

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Utility for declaring ZIP file system options and creating the environment map.
 */
enum ZipFSOpts {
    /** Sets the {@code "create"} key to {@code true}. */
    CREATE("create", true),
    /** Sets the {@code "enablePosixFileAttributes"} key to {@code true}. */
    POSIX("enablePosixFileAttributes", true),
    /** Sets the {@code "accessMode"} key to {@code "readWrite"}. */
    READ_WRITE("accessMode", "readWrite"),
    /** Sets the {@code "accessMode"} key to {@code "readOnly"}. */
    READ_ONLY("accessMode", "readOnly"),
    /** Sets the {@code "noCompression"} key to {@code true}. */
    UNCOMPRESSED("noCompression", true);

    final String key;
    final Object value;

    ZipFSOpts(String key, Object value) {
        this.key = requireNonNull(key);
        this.value = requireNonNull(value);
    }

    /**
     * Adds this option to a mutable environment map.
     *
     * @throws IllegalArgumentException if an option with the same key way already in the map.
     */
    Map<String, Object> addTo(Map<String, Object> map) {
        if (map.put(key, value) != null) {
            throw new IllegalArgumentException("Cannot supply options with the same key: " + key);
        }
        return map;
    }

    /**
     * Sets this option to a mutable environment map, overwriting any existing option with the
     * same key.
     */
    Map<String, Object> setIn(Map<String, Object> map) {
        map.put(key, value);
        return map;
    }

    /**
     * Returns a mutable environment map of the given options.
     *
     * @throws IllegalArgumentException if two or more options share the same key.
     */
    static Map<String, Object> toEnvMap(ZipFSOpts... opts) {
        HashMap<String, Object> map = new HashMap<>();
        for (ZipFSOpts opt : opts) {
            opt.addTo(map);
        }
        return map;
    }
}
