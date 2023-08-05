/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JMenuBar;

import static javax.swing.SwingUtilities.invokeAndWait;

/*
 * @test
 * @bug 4208018
 * @key headful
 * @summary Tests if calling JFrame.dispose() when menubar is set, cause Exception.
 */

public class bug4208018 {
    private static JFrame jFrame;

    public static void main(String[] args) throws Exception {
        try {
            invokeAndWait(() -> {
                jFrame = new JFrame("bug4208018 - Test dispose");
                JMenuBar menubar = new JMenuBar();
                jFrame.setJMenuBar(menubar);
                jFrame.dispose();
            });
        } catch (Exception e) {
            throw new RuntimeException("Test failed!" +
                    " Calling dispose on JFrame caused exception", e);
        }
    }
}
