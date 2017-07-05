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
package jdk.internal.jrtfs;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Formatter;

/**
 * Base class for file attributes supported by jrt file systems.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public abstract class AbstractJrtFileAttributes implements BasicFileAttributes {

    // jrt fs specific attributes
    /**
     * Compressed resource file. If not available or not applicable, 0L is
     * returned.
     *
     * @return the compressed resource size for compressed resources.
     */
    public abstract long compressedSize();

    /**
     * "file" extension of a file resource.
     *
     * @return extension string for the file resource
     */
    public abstract String extension();

    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder(1024);
        try (Formatter fm = new Formatter(sb)) {
            if (creationTime() != null) {
                fm.format("    creationTime    : %tc%n", creationTime().toMillis());
            } else {
                fm.format("    creationTime    : null%n");
            }

            if (lastAccessTime() != null) {
                fm.format("    lastAccessTime  : %tc%n", lastAccessTime().toMillis());
            } else {
                fm.format("    lastAccessTime  : null%n");
            }
            fm.format("    lastModifiedTime: %tc%n", lastModifiedTime().toMillis());
            fm.format("    isRegularFile   : %b%n", isRegularFile());
            fm.format("    isDirectory     : %b%n", isDirectory());
            fm.format("    isSymbolicLink  : %b%n", isSymbolicLink());
            fm.format("    isOther         : %b%n", isOther());
            fm.format("    fileKey         : %s%n", fileKey());
            fm.format("    size            : %d%n", size());
            fm.format("    compressedSize  : %d%n", compressedSize());
            fm.format("    extension       : %s%n", extension());
        }
        return sb.toString();
    }
}
