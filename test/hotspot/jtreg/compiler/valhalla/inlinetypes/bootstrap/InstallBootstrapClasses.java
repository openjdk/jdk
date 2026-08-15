/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import jdk.test.lib.Utils;

// Copy classes into a separate folder to put them on the bootclasspath
public class InstallBootstrapClasses {

    private static void copyClass(String name) throws IOException {
        Path source = Path.of(Utils.TEST_CLASSES).resolve("compiler/valhalla/inlinetypes/bootstrap/" + name);
        Path dest = Path.of("boot");
        Path target = dest.resolve(name);
        Files.createDirectories(dest);
        System.out.println("TEST_CLASSES: " + Utils.TEST_CLASSES);
        System.out.println("source: " + source);
        System.out.println("target: " + target);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void main(String[] args) throws IOException {
        copyClass(ValueOnBootclasspath.class.getSimpleName() + ".class");
        copyClass(MyClass.class.getSimpleName() + ".class");
    }
}
