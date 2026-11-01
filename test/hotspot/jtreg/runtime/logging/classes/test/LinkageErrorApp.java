/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.io.InputStream;

public class LinkageErrorApp {

    static class SingleDefinition { }
    static class DuplicateDefinition { }

    static class DuplicateLoader extends ClassLoader {
        DuplicateLoader() {
            super(null);
        }

        Class<?> define(byte[] bytes, String classname) {
            return defineClass(classname, bytes, 0, bytes.length);
        }
    }

    private static byte[] readClassBytes(Class<?> clazz) throws IOException {
        String resource = clazz.getName().replace('.', '/') + ".class";
        ClassLoader loader = clazz.getClassLoader();
        if (loader != null) {
            InputStream in = loader.getResourceAsStream(resource);
            if (in == null) {
                throw new RuntimeException("Could not find " + clazz.getName());
            }
            return in.readAllBytes();
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        byte[] duplicateDefBytes = readClassBytes(DuplicateDefinition.class);
        DuplicateLoader loader = new DuplicateLoader();

        SingleDefinition s = new SingleDefinition();
        loader.define(duplicateDefBytes, "LinkageErrorApp$DuplicateDefinition");
        // This will throw a LinkageError
        loader.define(duplicateDefBytes, "LinkageErrorApp$DuplicateDefinition");
    }
}
