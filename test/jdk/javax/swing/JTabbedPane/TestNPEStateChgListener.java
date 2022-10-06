/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 6286501 8294046
 * @summary  Verifies if NPE is thrown from stateChanged listener of JTabbedPane
  * @run main TestNPEStateChgListener
 */
import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.SwingUtilities;

public class TestNPEStateChgListener {

    private static JFrame frame;

    public static void main(String[] args) throws  Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {

                frame = new JFrame("JTabbedPane Testing");
                JTabbedPane tbp = new JTabbedPane();

                tbp.addTab("Tab 1 ", new JLabel("I am JLabel 1"));
                tbp.addTab("Tab 2 ", new JLabel("I am JLabel 2"));
                tbp.addTab("Tab 3 ", new JLabel("I am JLabel 3 "));
                tbp.addTab("Tab 4 ", new JLabel("I am JLabel 4"));

                frame.setLayout(new BorderLayout());
                frame.add(tbp, BorderLayout.CENTER);
                tbp.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        tbp.updateUI();
                    }
                });

                frame.setSize(600, 200);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
                tbp.setSelectedIndex(1);
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
