/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

import static javax.tools.JavaFileObject.Kind.*;

/**
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
*/
public abstract class BaseFileObject implements JavaFileObject {
    protected BaseFileObject(JavacFileManager fileManager) {
        this.fileManager = fileManager;
    }

    public JavaFileObject.Kind getKind() {
        String n = getName();
        if (n.endsWith(CLASS.extension))
            return CLASS;
        else if (n.endsWith(SOURCE.extension))
            return SOURCE;
        else if (n.endsWith(HTML.extension))
            return HTML;
        else
            return OTHER;
    }

    @Override
    public String toString() {
        return getPath();
    }

    /** @deprecated see bug 6410637 */
    @Deprecated
    public String getPath() {
        return getName();
    }

    /** @deprecated see bug 6410637 */
    @Deprecated
    abstract public String getName();

    public NestingKind getNestingKind() { return null; }

    public Modifier getAccessLevel()  { return null; }

    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new InputStreamReader(openInputStream(), getDecoder(ignoreEncodingErrors));
    }

    protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
        throw new UnsupportedOperationException();
    }

    protected abstract String inferBinaryName(Iterable<? extends File> path);

    protected static String removeExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        return (lastDot == -1 ? fileName : fileName.substring(0, lastDot));
    }

    /** The file manager that created this JavaFileObject. */
    protected final JavacFileManager fileManager;

}
