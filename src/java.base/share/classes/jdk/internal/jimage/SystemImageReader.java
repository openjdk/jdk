/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

/**
 * Static holder class for singleton {@link ImageReader} instance.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public class SystemImageReader {
    private static final ImageReader SYSTEM_IMAGE_READER;

    static {
        String javaHome = System.getProperty("java.home");
        FileSystem fs;
        if (SystemImageReader.class.getClassLoader() == null) {
            try {
                fs = (FileSystem) Class.forName("sun.nio.fs.DefaultFileSystemProvider")
                        .getMethod("theFileSystem")
                        .invoke(null);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        } else {
            fs = FileSystems.getDefault();
        }
        try {
            SYSTEM_IMAGE_READER = ImageReader.open(fs.getPath(javaHome, "lib", "modules"), PreviewMode.FOR_RUNTIME);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Returns the singleton {@code ImageReader} to read the image file in this
     * run-time image. The returned instance must not be closed.
     *
     * @throws UncheckedIOException if an I/O error occurs
     */
    public static ImageReader get() {
        return SYSTEM_IMAGE_READER;
    }

    /**
     * Returns the "raw" API for accessing underlying jimage resource entries.
     *
     * <p>This is only meaningful for use by code dealing directly with jimage
     * files, and cannot be used to reliably lookup resources used at runtime.
     */
    public static ResourceEntries getResourceEntries() {
        return get().getResourceEntries();
    }

    private SystemImageReader() {}
}
