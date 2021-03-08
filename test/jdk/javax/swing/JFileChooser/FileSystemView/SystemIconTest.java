/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8182043
 * @requires os.family == "windows"
 * @summary Access to Windows Large Icons
 * sun.awt.shell.ShellFolder
 * @run main SystemIconTest
 */
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;
import java.io.File;

public class SystemIconTest {
    static final FileSystemView fsv = FileSystemView.getFileSystemView();

    public static void main(String[] args) {
        testSystemIcon();
        System.out.println("ok");
    }

    static void testSystemIcon() {
        String windir = System.getenv("windir");
        testSystemIcon(new File(windir));
        testSystemIcon(new File(windir + "/explorer.exe"));
        return;
    }

    static void testSystemIcon(File file) {
        int[] sizes = new int[] {16, 32, 48, 64, 128};
        for (int size : sizes) {
            ImageIcon icon = (ImageIcon) fsv.getSystemIcon(file, size);

            //Enable below to see the icon
            //JLabel label = new JLabel(icon);
            //JOptionPane.showMessageDialog(null, label);

            if (icon == null) {
                throw new RuntimeException("icon is null!!!");
            }

            if (icon.getIconWidth() != size) {
                throw new RuntimeException("Wrong icon size " +
                        icon.getIconWidth() + " when requested " + size);
            }
        }
    }
}
