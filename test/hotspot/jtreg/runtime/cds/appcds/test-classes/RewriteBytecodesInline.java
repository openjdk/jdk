/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.File;
import java.lang.invoke.MethodHandles;
import jdk.test.whitebox.WhiteBox;

public class RewriteBytecodesInline {
  public static void main(String args[]) throws Throwable {
    String from = "___xxx___";
    String to   = "___yyy___";
    File clsFile = new File(args[0]);
    Class fieldClass = Util.defineModifiedClass(MethodHandles.lookup(), clsFile, from, to);

    WithInlinedField wif = new WithInlinedField();

    if (wif.p.getClass() != fieldClass) {
      throw new RuntimeException("Mismatched field class");
    }

    // Even if the Point class is not loaded from the CDS archive, make sure the WithInlinedField class
    // can still be loaded successfully, and properly get the rewritten version of Point.
    // The archived version of WithInlinedField must not be loaded, because it references the archived
    // version of Point, but a different version of Point has been loaded.

    if (!wif.p.msg().equals(to)) {
      throw new RuntimeException("Wrong output, expected: " + to + ", but got: " + wif.p.msg());
    }

    WhiteBox wb = WhiteBox.getWhiteBox();
    if (wb.isSharedClass(fieldClass)) {
      throw new RuntimeException("wb.isSharedClass(superClass) should be false");
    }
    if (wb.isSharedClass(wif.p.getClass())) {
      throw new RuntimeException("wb.isSharedClass(child.getClass()) should be false");
    }
  }
}
