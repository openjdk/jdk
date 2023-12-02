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

package com.sun.tools.javac.launcher;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;

/**
 * An in-memory file manager.
 *
 * <p>Class files (of kind {@link JavaFileObject.Kind#CLASS CLASS}) written to
 * {@link StandardLocation#CLASS_OUTPUT} will be written to an in-memory cache.
 * All other file manager operations will be delegated to a specified file manager.
 *
 * <p><strong>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</strong></p>
 */
final class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final Map<String, byte[]> map;

    MemoryFileManager(Map<String, byte[]> map, JavaFileManager delegate) {
        super(delegate);
        this.map = map;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className,
                                               JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        if (location == StandardLocation.CLASS_OUTPUT && kind == JavaFileObject.Kind.CLASS) {
            return createInMemoryClassFile(className);
        } else {
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }
    }

    private JavaFileObject createInMemoryClassFile(String className) {
        URI uri = URI.create("memory:///" + className.replace('.', '/') + ".class");
        return new SimpleJavaFileObject(uri, JavaFileObject.Kind.CLASS) {
            @Override
            public OutputStream openOutputStream() {
                return new ByteArrayOutputStream() {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        map.put(className, toByteArray());
                    }
                };
            }
        };
    }

    @Override
    public boolean contains(Location location, FileObject fo) throws IOException {
        return fo instanceof ProgramFileObject || super.contains(location, fo);
    }
}
