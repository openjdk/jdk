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
package jdk.internal.jimage;

import java.io.InputStream;
import java.util.stream.Stream;

/**
 * Accesses the underlying resource entries in a jimage file.
 *
 * <p>This API is designed only for use by the jlink classes, which read the raw
 * jimage files. Use the {@link ImageReader} API to read jimage contents at
 * runtime to correctly account for preview mode.
 *
 * <p>This API ignores the {@code previewMode} of the {@link ImageReader} from
 * which it is obtained, and returns an unmapped view of entries (e.g. allowing
 * for direct access of resources in the {@code META-INF/preview/...} namespace).
 *
 * <p>It disallows access to resource directories (i.e. {@code "/modules/..."})
 * or packages entries (i.e. {@code "/packages/..."}).
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public interface ResourceEntries {
    /**
     * Returns the jimage names for all resources in the given module, in
     * random order. Entry names will always be prefixed by the given module
     * name (e.g. {@code "/<module-name>/..."}).
     */
    Stream<String> getEntryNames(String module);

    /**
     * Returns the (uncompressed) size of a resource given its jimage name.
     *
     * @throws java.util.NoSuchElementException if the resource does not exist.
     */
    long getSize(String name);

    /**
     * Returns a copy of a resource's content given its jimage name.
     *
     * @throws java.util.NoSuchElementException if the resource does not exist.
     */
    byte[] getBytes(String name);
}
