/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6557865
 * @summary -source 5 -target 5 should not put ACC_SYNTHETIC on package-info
 * @author Wei Tao
 * @compile T6557865.java
 * @compile -source 5 -target 5 T6232928/package-info.java
 * @run main T6557865
 */

import java.io.*;
import java.lang.reflect.Modifier;

public class T6557865 {
  public static void main(String... args) throws Exception {
    Class pkginfo_cls = Class.forName("T6232928.package-info");
    int mod = pkginfo_cls.getModifiers();
    if ((mod & 0x1000) != 0) {
      throw new AssertionError("Test failed: interface package-info shouldn't be synthetic in -target 5.");
    }
  }
}
