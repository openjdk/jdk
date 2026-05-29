/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper driver to move a class file to the hidden/ directory.
 * Replaces AppendToBootstrapClassPathSetUp.sh and AppendToClassPathSetUp.sh.
 * Usage: MoveClassToHidden <className>
 */
public class MoveClassToHidden {
    public static void main(String[] args) throws Exception {
        String className = args[0];
        String testClasses = System.getProperty("test.classes");
        Path src = Path.of(testClasses, className + ".class");
        Path hiddenDir = Path.of("hidden");
        Files.createDirectories(hiddenDir);
        Files.move(src, hiddenDir.resolve(className + ".class"));
        System.out.println("Moved " + className + ".class to hidden/");
    }
}
