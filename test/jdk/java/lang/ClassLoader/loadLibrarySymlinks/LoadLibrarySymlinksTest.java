/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8348828
 */

import java.nio.file.Files;
import java.nio.file.Path;

public class LoadLibrarySymlinksTest {
    public static void main(String... args) throws Exception {
      var libBase = System.mapLibraryName("SymlinksTest");
      var lib = Path.of(System.getProperty("test.nativepath")).resolve(libBase).toRealPath();
      var scratch = Path.of(".").toAbsolutePath();

      // Symlink and target have extenion.
      Files.copy(lib, scratch.resolve(System.mapLibraryName("goodname")));
      var test1 = scratch.resolve(System.mapLibraryName("test1"));
      Files.createSymbolicLink(test1, Path.of(System.mapLibraryName("goodname")));
      System.load(test1.toString());

      // Symlink has extension but target does not.
      Files.copy(lib, scratch.resolve("barename"));
      var test2 = scratch.resolve(System.mapLibraryName("test2"));
      Files.createSymbolicLink(test2, Path.of("barename"));
      System.load(test2.toString());

      // Neither symlink nor target have extension.
      Files.copy(lib, scratch.resolve("barename2"));
      var test3 = scratch.resolve("test3");
      Files.createSymbolicLink(test3, Path.of("barename2"));
      System.load(test3.toString());
    }
}
