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

package com.sun.io;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.util.zip.CheckedOutputStream;

/**
 * Package-private utility class
 */
final class IOStreams {

    /**
     * Returns true if this class satisfies two conditions:
     * <pre>
     * - the reference to {@code byte[]} is not kept within the class
     * - the argument of {@link OutputStream#write(byte[])}} and {@link OutputStream#write(byte[], int, int)}} is not modified within the methods
     * - the {@code byte[]} is not read outside of the given bounds
     * </pre>
     *
     * @return true if this class is trusted
     * @see java.io.ByteArrayInputStream#transferTo(OutputStream)
     * @see java.io.BufferedInputStream#implTransferTo(OutputStream)
     */
    public static boolean isTrusted(OutputStream os) {
        var clazz = os.getClass();
        return clazz == ByteArrayOutputStream.class
                || clazz == FileOutputStream.class
                || clazz == PipedOutputStream.class
                || clazz == CheckedOutputStream.class;
    }
}
