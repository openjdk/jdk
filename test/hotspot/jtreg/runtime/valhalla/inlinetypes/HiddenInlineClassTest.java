/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test a hidden inline class.
 * @library /test/lib
 * @enablePreview
 * @run main runtime.valhalla.inlinetypes.HiddenInlineClassTest
 */

package runtime.valhalla.inlinetypes;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HiddenInlineClassTest {
    static final Path CLASSES_DIR = Paths.get(System.getProperty("test.classes", "."));

    static byte[] readClassFile(String classFileName) throws Exception {
       Path path = CLASSES_DIR.resolve(classFileName.replace('.', File.separatorChar) + ".class");
       return Files.readAllBytes(path);
    }

    public static void main(String[] args) throws Throwable {
        Lookup lookup = MethodHandles.lookup();
        byte[] bytes = readClassFile("runtime.valhalla.inlinetypes.HiddenPoint");

        // Define a hidden class that is an inline type.
        Class<?> c = lookup.defineHiddenClass(bytes, true).lookupClass();
        Object hp = c.newInstance();
        String s = (String)c.getMethod("getValue").invoke(hp);
        if (!s.equals("x: 0, y: 0")) {
            throw new RuntimeException(
                "wrong value returned by method getValue() in inline hidden class: " + s);
        }
    }

}

value class HiddenPoint {
    int x;
    int y;

    HiddenPoint() {
        this.x = 0;
        this.y = 0;
    }

    public String getValue() {
        return "x: " + x + ", y: " + y;
    }
}

