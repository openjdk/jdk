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

/* @test
   @bug 4143867 4237390 4383709
   @summary Tests set/getAction(.) and some constructors with Action argument
   @key headful
   @run main bug4143867
*/

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.DefaultButtonModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JToggleButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;

public class bug4143867 {
    static final int TEST_MNEMONIC = KeyEvent.VK_1;
    static JFrame fr;

    public static void main(String[] argv) throws Exception {
        bug4143867 b = new bug4143867();
        SwingUtilities.invokeAndWait(() -> {
            try {
                b.doInitAndTest();
            } finally {
                if (fr != null) {
                    fr.dispose();
                }
            }
        });
    }

    public void doInitAndTest() {
        fr = new JFrame("bug4143867");
        JMenuBar mb = new JMenuBar();
        JMenu m = mb.add(new JMenu("Menu1"));
        fr.setJMenuBar(mb);
        JMenuItem it1 = m.add(new JMenuItem("Item1"));
        fr.getContentPane().setLayout(new FlowLayout());
        JButton bt1 = new JButton("Button1");
        fr.getContentPane().add(bt1);

        final AbstractAction al = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
              System.out.println("Pressed...");
            }
        };
        al.putValue(Action.NAME, "Action");
        al.putValue(Action.MNEMONIC_KEY, new Integer(TEST_MNEMONIC));
        m.add(al);
        m.getItem(0).setAction(al);
        bt1.setAction(al);
        JButton bt2 = new JButton(al);
        fr.getContentPane().add(bt2);
        if (it1.getAction() != al || m.getItem(1).getAction() != al ||
            bt1.getAction() != al || bt2.getAction() != al) {
            throw new RuntimeException("Action was not set correctly.");
        }

        if (bt1.getMnemonic() != TEST_MNEMONIC) {
            throw new RuntimeException("Failed 4383709: JButton doesn't get mnemonic from Action");
        }

        class TestProtectedOfAbstractButton extends AbstractButton {
            public void test() {
                PropertyChangeListener pcl = createActionPropertyChangeListener(null);
                setModel(new DefaultButtonModel());
                configurePropertiesFromAction(al);
            }
        }
        TestProtectedOfAbstractButton tpAB = new TestProtectedOfAbstractButton();
        tpAB.test();

        //  Constructors presence test
        JRadioButton ct1         = new JRadioButton(al);
        JCheckBox ct2            = new JCheckBox(al);
        JRadioButton ct3         = new JRadioButton(al);
        JToggleButton ct4        = new JToggleButton(al);
        JMenuItem ct5            = new JMenuItem(al);
        JMenu ct6                = new JMenu(al);
        JCheckBoxMenuItem ct7    = new JCheckBoxMenuItem(al);
        JRadioButtonMenuItem ct8 = new JRadioButtonMenuItem(al);
        if (ct1.getAction() != al) {
            throw new RuntimeException("Constructor error in JRadioButton...");
        }
        if (ct2.getAction() != al) {
            throw new RuntimeException("Constructor error in JCheckBox...");
        }
        if (ct3.getAction() != al) {
            throw new RuntimeException("Constructor error in JRadioButton...");
        }
        if (ct4.getAction() != al) {
            throw new RuntimeException("Constructor error in JToggleButton...");
        }
        if (ct5.getAction() != al) {
            throw new RuntimeException("Constructor error in JMenuItem...");
        }
        if (ct6.getAction() != al) {
            throw new RuntimeException("Constructor error in JMenu...");
        }
        if (ct7.getAction() != al) {
            throw new RuntimeException("Constructor error in JCheckBoxMenuItem...");
        }
        if (ct8.getAction() != al) {
            throw new RuntimeException("Constructor error in JRadioButtonMenuItem...");
        }
    }
}
