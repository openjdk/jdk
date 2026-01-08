/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.SecretKey;

/**
 * An interface for <code>SecretKey</code>s that support using its slice as a new
 * <code>SecretKey</code>.
 * <p>
 * This is mainly used by PKCS #11 implementations that support the
 * EXTRACT_KEY_FROM_KEY mechanism even if the key itself is sensitive
 * and non-extractable.
 */
public interface SliceableSecretKey {

    /**
     * Returns a slice as a new <code>SecretKey</code>.
     *
     * @param alg the new algorithm name
     * @param from the byte offset of the new key in the full key
     * @param to the to offset (exclusive) of the new key in the full key
     * @return the new key
     * @throws ArrayIndexOutOfBoundsException for improper <code>from</code>
     *      and <code>to</code> values
     * @throws UnsupportedOperationException if slicing is not supported
     */
    SecretKey slice(String alg, int from, int to);
}
