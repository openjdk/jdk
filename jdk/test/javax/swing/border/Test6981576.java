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
 * @bug 6981576
 * @summary Tests that default border for the titled border is not null
 * @author Sergey Malenkov
 */

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.TitledBorder;

public class Test6981576 extends TitledBorder implements Runnable, Thread.UncaughtExceptionHandler {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Test6981576());
    }

    private int index;
    private LookAndFeelInfo[] infos;
    private JFrame frame;

    private Test6981576() {
        super("");
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        getBorder().paintBorder(c, g, x, y, width, height);
    }

    public void run() {
        if (this.infos == null) {
            this.infos = UIManager.getInstalledLookAndFeels();
            Thread.currentThread().setUncaughtExceptionHandler(this);
            JPanel panel = new JPanel();
            panel.setBorder(this);
            this.frame = new JFrame(getClass().getSimpleName());
            this.frame.add(panel);
            this.frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            this.frame.setVisible(true);
        }
        if (this.index == this.infos.length) {
            this.frame.dispose();
        }
        else {
            LookAndFeelInfo info = this.infos[this.index % this.infos.length];
            try {
                UIManager.setLookAndFeel(info.getClassName());
            }
            catch (Exception exception) {
                System.err.println("could not change look and feel");
            }
            SwingUtilities.updateComponentTreeUI(this.frame);
            this.frame.pack();
            this.frame.setLocationRelativeTo(null);
            this.index++;
            SwingUtilities.invokeLater(this);
        }
    }

    public void uncaughtException(Thread thread, Throwable throwable) {
        System.exit(1);
    }
}
