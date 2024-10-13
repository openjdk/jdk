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
 * @bug 4753342
 * @key headful
 * @summary Makes sure add/remove/setLayout redirect to the contentpane
 */

import java.awt.Container;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JWindow;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

public class RootPaneChecking {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
                MyJFrame frame = new MyJFrame();
                frame.setTitle("RootPaneChecking");
                checkRootPaneCheckingEnabled(frame);
                frame.setRootPaneCheckingEnabled(false);
                checkRootPaneCheckingDisabled(frame);

                MyJWindow window = new MyJWindow();
                checkRootPaneCheckingEnabled(window);
                window.setRootPaneCheckingEnabled(false);
                checkRootPaneCheckingDisabled(window);

                MyJDialog dialog = new MyJDialog();
                checkRootPaneCheckingEnabled(dialog);
                dialog.setRootPaneCheckingEnabled(false);
                checkRootPaneCheckingDisabled(dialog);

                MyJInternalFrame iframe = new MyJInternalFrame();
                checkRootPaneCheckingEnabled(iframe);
                iframe.setRootPaneCheckingEnabled(false);
                checkRootPaneCheckingDisabled(iframe);
        });
    }

    private static void checkRootPaneCheckingEnabled(RootPaneContainer rpc) {
        Container parent = (Container) rpc;
        Container cp = rpc.getContentPane();
        // Test add
        JButton button = new JButton("RootPaneChecking");
        parent.add(button);
        if (button.getParent() != cp) {
            throw new RuntimeException("Add parent mismatch, want: " +
                    cp + " got " + button.getParent());
        }

        // Test remove
        parent.remove(button);
        if (button.getParent() != null) {
            throw new RuntimeException("Remove mismatch, want null got " +
                    button.getParent());
        }

        // Test setLayout
        LayoutManager manager = new GridLayout();
        parent.setLayout(manager);
        if (manager != cp.getLayout()) {
            throw new RuntimeException("LayoutManager mismatch, want: " +
                    manager + " got " + cp.getLayout());
        }
    }

    private static void checkRootPaneCheckingDisabled(RootPaneContainer rpc) {
        Container parent = (Container) rpc;
        Container cp = rpc.getContentPane();

        // Test add
        JButton button = new JButton("RootPaneChecking");
        parent.add(button);
        if (button.getParent() != parent) {
            throw new RuntimeException("Add parent mismatch, want: " +
                    parent + " got " + button.getParent());
        }

        // Test setLayout
        LayoutManager manager = new GridLayout();
        parent.setLayout(manager);
        if (manager != parent.getLayout()) {
            throw new RuntimeException("LayoutManager mismatch, want: " +
                    manager + " got " + cp.getLayout());
        }
    }

    static class MyJFrame extends JFrame {
        public void setRootPaneCheckingEnabled(boolean x) {
            super.setRootPaneCheckingEnabled(x);
        }
    }

    static class MyJWindow extends JWindow {
        public void setRootPaneCheckingEnabled(boolean x) {
            super.setRootPaneCheckingEnabled(x);
        }
    }

    static class MyJDialog extends JDialog {
        public void setRootPaneCheckingEnabled(boolean x) {
            super.setRootPaneCheckingEnabled(x);
        }
    }

    static class MyJInternalFrame extends JInternalFrame {
        public void setRootPaneCheckingEnabled(boolean x) {
            super.setRootPaneCheckingEnabled(x);
        }
    }
}
