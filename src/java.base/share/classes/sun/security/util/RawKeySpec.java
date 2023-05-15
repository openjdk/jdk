/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.security.spec.KeySpec;

/**
 * This is a KeySpec that is used to specify a key by its byte array implementation.
 * It is intended to be used in testing algorithms where the algorithm specification
 * describes the key in this form.
 */
public class RawKeySpec implements KeySpec {
    private final byte[] keyArr;
    /**
     * The sole constructor.
     * @param key contains the key as a byte array
     */
    public RawKeySpec(byte[] key) {
        keyArr = key.clone();
    }

    /**
     * Getter function.
     * @return a copy of the key bits
     */
    public byte[] getKeyArr() {
        return keyArr.clone();
    }
}
