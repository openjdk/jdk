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

import static jdk.jpackage.internal.util.PathUtils.resolveNullablePath;

import java.nio.file.Path;
import jdk.jpackage.internal.util.CompositeProxy;

/**
 * Java runtime app image layout.
 * <p>
 * Use {@link #DEFAULT} field to get the default runtime app image layout or
 * {@link #create(Path)} method to create custom runtime app image layout.
 */
public interface RuntimeLayout extends AppImageLayout {

    @Override
    default RuntimeLayout resolveAt(Path root) {
        return create(new AppImageLayout.Stub(resolveNullablePath(root, rootDirectory()),
                resolveNullablePath(root, runtimeDirectory())));
    }

    /**
     * Creates Java runtime app image layout.
     * <p>
     * {@link #runtimeDirectory()} method
     * called on the created object will return the value of the
     * <code>runtimeDirectory<code> parameter. {@link #rootDirectory()} method
     * called on the created object will return <code>Path.of("")<code> value.
     *
     * @param runtimeDirectory Java runtime directory
     * @return Java runtime app image layout
     */
    static RuntimeLayout create(Path runtimeDirectory) {
        return create(new AppImageLayout.Stub(Path.of(""), runtimeDirectory));
    }

    private static RuntimeLayout create(AppImageLayout layout) {
        return CompositeProxy.create(RuntimeLayout.class, layout);
    }

    /**
     * Singleton.
     * <p>
     * {@link #runtimeDirectory()} of the singleton returns empty string (""), i.e.
     * the runtime directory is the same as the directory at which the layout is
     * resolved.
     */
    static final RuntimeLayout DEFAULT = create(Path.of(""));
}
