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
package jdk.jpackage.internal.model;

import java.nio.file.Path;
import java.util.Optional;

/**
 * File association.
 */
public interface FileAssociation {

    /**
     * Returns file association description if available or an empty {@link Optional} instance.
     * @return file association description
     */
    Optional<String> description();

    /**
     * Returns file association icon if available or an empty {@link Optional} instance.
     * @see {@link #hasIcon()}
     * @return file association icon
     */
    Optional<Path> icon();

    /**
     * Returns <code>true</code> if the file association has an icon.
     * @see {@link #icon()}
     * @return <code>true</code> if the file association has an icon
     */
    default boolean hasIcon() {
        return icon().isPresent();
    }

    /**
     * Returns file association MIME type. E.g.: <code>application/foo</code>, <code>text/plain<code>.
     * @return file association MIME type
     */
    String mimeType();

    /**
     * Returns file association extension. E.g.: <code>.txt</code>
     * @return file association extension
     */
    String extension();

    /**
     * Default implementation of {@link FileAssociation} interface
     */
    record Stub(Optional<String> description, Optional<Path> icon, String mimeType, String extension) implements FileAssociation {
    }
}
