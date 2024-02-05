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

/* @test
 * @bug 4662505
 * @summary IllegalArgumentException with empty JTree and key event
 * @run main bug4662505
 */

import java.awt.event.KeyEvent;
import java.util.Date;

import javax.swing.JTree;
import javax.swing.SwingUtilities;

public class bug4662505 {
    static DummyTree tree;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            tree = new DummyTree();

            try {
                tree.doTest();
            } catch (Exception e) {
                throw new RuntimeException("Empty JTree shouldn't handle " +
                        "first letter navigation", e);
            }
        });
    }

    static class DummyTree extends JTree {
        public DummyTree() {
            super(new Object[]{});
        }

        public void doTest() {
            KeyEvent key = new KeyEvent(tree, KeyEvent.KEY_TYPED,
                    new Date().getTime(), 0, KeyEvent.VK_UNDEFINED, 'a');
            processKeyEvent(key);
        }
    }
}
