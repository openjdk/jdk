/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6199676
 * @summary Tests preview panel after L&F changing
 * @author Sergey Malenkov
 */

import java.awt.Component;
import java.awt.Container;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public class Test6199676 implements Runnable {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Test6199676());
    }

    private static void exit(String error) {
        if (error != null) {
            System.err.println(error);
            System.exit(1);
        }
        else {
            System.exit(0);
        }
    }

    private static Component getPreview(Container container) {
        String name = "ColorChooser.previewPanelHolder";
        for (Component component : container.getComponents()) {
            if (!name.equals(component.getName())) {
                component = (component instanceof Container)
                        ? getPreview((Container) component)
                        : null;
            }
            if (component instanceof Container) {
                container = (Container) component;
                return 1 == container.getComponentCount()
                        ? container.getComponent(0)
                        : null;
            }
        }
        return null;
    }

    private static boolean isShowing(Component component) {
        return (component != null) && component.isShowing();
    }

    private int index;
    private boolean updated;
    private JColorChooser chooser;

    public synchronized void run() {
        if (this.chooser == null) {
            this.chooser = new JColorChooser();

            JFrame frame = new JFrame(getClass().getName());
            frame.add(this.chooser);
            frame.setVisible(true);
        }
        else if (this.updated) {
            if (isShowing(this.chooser.getPreviewPanel())) {
                exit("custom preview panel is showing");
            }
            exit(null);
        }
        else {
            Component component = this.chooser.getPreviewPanel();
            if (component == null) {
                component = getPreview(this.chooser);
            }
            if (!isShowing(component)) {
                exit("default preview panel is not showing");
            }
            this.updated = true;
            this.chooser.setPreviewPanel(new JPanel());
        }
        LookAndFeelInfo[] infos = UIManager.getInstalledLookAndFeels();
        LookAndFeelInfo info = infos[++this.index % infos.length];
        try {
            UIManager.setLookAndFeel(info.getClassName());
        }
        catch (Exception exception) {
            exit("could not change L&F");
        }
        SwingUtilities.updateComponentTreeUI(this.chooser);
        SwingUtilities.invokeLater(this);
    }
}
