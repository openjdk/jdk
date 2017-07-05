/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jaotc.collect;

import java.nio.file.Path;
import java.util.function.BiConsumer;

public interface ClassSource {
    static boolean pathIsClassFile(Path entry) {
        String fileName = entry.getFileName().toString();
        return fileName.endsWith(".class") && !fileName.endsWith("module-info.class");
    }

    static String makeClassName(Path path) {
        String fileName = path.toString();

        if (!fileName.endsWith(".class")) {
            throw new IllegalArgumentException("File doesn't end with .class: '" + fileName + "'");
        }

        int start = 0;
        if (fileName.startsWith("/")) {
            start = 1;
        }

        String className = fileName.substring(start, fileName.length() - ".class".length());
        className = className.replace('/', '.');
        return className;
    }

    void eachClass(BiConsumer<String, ClassLoader> consumer);
}
