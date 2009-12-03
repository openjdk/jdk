/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.CharsetDecoder;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.FileObject;
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

    /** Return a short name for the object, such as for use in raw diagnostics
     */
    public abstract String getShortName();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getName() + "]";
    }

    public NestingKind getNestingKind() { return null; }

    public Modifier getAccessLevel()  { return null; }

    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new InputStreamReader(openInputStream(), getDecoder(ignoreEncodingErrors));
    }

    protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
        throw new UnsupportedOperationException();
    }

    protected abstract String inferBinaryName(Iterable<? extends File> path);

    protected static JavaFileObject.Kind getKind(String filename) {
        if (filename.endsWith(CLASS.extension))
            return CLASS;
        else if (filename.endsWith(SOURCE.extension))
            return SOURCE;
        else if (filename.endsWith(HTML.extension))
            return HTML;
        else
            return OTHER;
    }

    protected static String removeExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        return (lastDot == -1 ? fileName : fileName.substring(0, lastDot));
    }

    protected static URI createJarUri(File jarFile, String entryName) {
        URI jarURI = jarFile.toURI().normalize();
        String separator = entryName.startsWith("/") ? "!" : "!/";
        try {
            // The jar URI convention appears to be not to re-encode the jarURI
            return new URI("jar:" + jarURI + separator + entryName);
        } catch (URISyntaxException e) {
            throw new CannotCreateUriError(jarURI + separator + entryName, e);
        }
    }

    /** Used when URLSyntaxException is thrown unexpectedly during
     *  implementations of (Base)FileObject.toURI(). */
    protected static class CannotCreateUriError extends Error {
        private static final long serialVersionUID = 9101708840997613546L;
        public CannotCreateUriError(String value, Throwable cause) {
            super(value, cause);
        }
    }

    /** Return the last component of a presumed hierarchical URI.
     *  From the scheme specific part of the URI, it returns the substring
     *  after the last "/" if any, or everything if no "/" is found.
     */
    public static String getSimpleName(FileObject fo) {
        URI uri = fo.toUri();
        String s = uri.getSchemeSpecificPart();
        return s.substring(s.lastIndexOf("/") + 1); // safe when / not found

    }

    // force subtypes to define equals
    @Override
    public abstract boolean equals(Object other);

    // force subtypes to define hashCode
    @Override
    public abstract int hashCode();

    /** The file manager that created this JavaFileObject. */
    protected final JavacFileManager fileManager;
}
