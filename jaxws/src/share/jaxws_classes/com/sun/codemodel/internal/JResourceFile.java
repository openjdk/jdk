/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Represents a resource file in the application-specific file format.
 */
public abstract class JResourceFile {

    private final String name;

    protected JResourceFile( String name ) {
        this.name = name;
    }

    /**
     * Gets the name of this property file
     */
    public String name() {
        return name;
    }

    /**
     * Returns true if this file should be generated into the directory
     * that the resource files go into.
     *
     * <p>
     * Returns false if this file should be generated into the directory
     * where other source files go.
     */
    protected boolean isResource() {
        return true;
    }

    /**
     * called by JPackage to produce the file image.
     */
    protected abstract void build( OutputStream os ) throws IOException;
}
