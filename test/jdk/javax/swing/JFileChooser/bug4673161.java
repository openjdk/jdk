/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4673161
 * @requires (os.family == "windows")
 * @summary Tests if JFileChooser preferred size depends on selected files
 * @run main bug4673161
 */

import java.awt.Dimension;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.UIManager;

public class bug4673161 {

  public static void main(String[] args) throws Exception {
    JFileChooser fc = new JFileChooser();
    Dimension d = fc.getPreferredSize();
    JFileChooser fc2 = new JFileChooser();
    File[] files = new File[50];
    for (int i = 0; i < 50; i++) {
      files[i] = new File("file" + i);
    }
    fc2.setSelectedFiles(files);
    Dimension d2 = fc2.getPreferredSize();
    if (!d.equals(d2)) {
      throw new RuntimeException("Test failed: JFileChooser preferred " +
              "size depends on selected files");
    }

    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

    JFileChooser fc3 = new JFileChooser();
    d = fc3.getPreferredSize();
    fc2 = new JFileChooser();
    files = new File[50];
    for (int i = 0; i < 50; i++) {
      files[i] = new File("file" + i);
    }
    fc2.setSelectedFiles(files);
    d2 = fc2.getPreferredSize();
    if (!d.equals(d2)) {
      throw new RuntimeException("Test failed: JFileChooser preferred " +
              "size depends on selected files");
    }
  }
}
