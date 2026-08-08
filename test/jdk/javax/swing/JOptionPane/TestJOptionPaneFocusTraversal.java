/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6741930
 * @key headful
 * @summary Verify if JOptionPane honours Focus Traversal Policy
 * @run main TestJOptionPaneFocusTraversal
 */

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.GridLayout;
import java.awt.Robot;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class TestJOptionPaneFocusTraversal {

    static JFrame frame;
    static JButton b1;
    static volatile boolean passed;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("JOptionPane FocusTraversal");
                b1 = new JButton( new StaticOptionPane());
                frame.getContentPane().setLayout(new FlowLayout());
                frame.add(b1);

                KeyboardFocusManager fm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                fm.addPropertyChangeListener( new DiagChangeListener() );

                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible( true );
            });
            robot.delay(1000);
            robot.waitForIdle();
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch(Exception e) {}
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
            }).start();
            SwingUtilities.invokeAndWait(() -> b1.doClick());
            if (!passed) {
                throw new RuntimeException("JOptionPane doesn't honour FocusTraversalPolicy");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static class DiagChangeListener implements java.beans.PropertyChangeListener {
        private JDialog diag;

        public void propertyChange( java.beans.PropertyChangeEvent e ) {
            String prop = e.getPropertyName();

            if ("currentFocusCycleRoot".equals(prop)) {
                if (e.getNewValue() instanceof JDialog) {
                    diag = (JDialog) e.getNewValue();
                    System.out.println("------------------------------" );
                    System.out.println(prop +" changed to JDialog");
                    printFocusPolicy();
                }
            } else if ("focusOwner".equals(prop) && diag != null) {
                if ( e.getNewValue() instanceof JButton ) {
                    System.out.println( prop +" changed to JButton but:" );
                    printFocusPolicy();
                }
            } else if ("permanentFocusOwner".equals(prop) && diag != null) {
                if (e.getNewValue() instanceof JButton) {
                    System.out.println( prop +" changed to JButton but:" );
                    printFocusPolicy();
                    diag = null;
                }
            }
        }

        private void printFocusPolicy() {
            FocusTraversalPolicy ftp = diag.getFocusTraversalPolicy();
            System.out.println("getFirstComponent: " +
                                getDescription(ftp.getFirstComponent(diag)));
            System.out.println("getInitialComponent: " +
                                getDescription(ftp.getInitialComponent(diag)));
            System.out.println("getDefaultComponent: " +
                                getDescription(ftp.getDefaultComponent(diag)));
            System.out.println();
        }

        private String getDescription( Component comp ) {
            if (comp != null) {
                return comp.getClass() +", "+ comp.getName();
            } return null;
        }
    }


    private static class StaticOptionPane extends AbstractAction {
        public StaticOptionPane() {
            super("StaticOptionPane");
        }

        public void actionPerformed( ActionEvent AE ) {
            JPanel panel = new JPanel( new GridLayout(0,2) );

            panel.add( new JLabel("Text 1:"));
            JTextField text1 = new JTextField("Focus should be here");
            text1.addFocusListener( new MyFocusListener() );
            text1.setName( "TEXT ONE" );
            panel.add( text1 );

            panel.add( new JLabel("Text 2:"));
            JTextField text2 = new JTextField("according to the");
            panel.add( text2 );

            panel.add( new JLabel("Text 3:"));
            JTextField text3 = new JTextField("FocusTraversalPolicy");
            panel.add( text3 );

            System.out.println(" StaticOptionPane");
            JOptionPane.showConfirmDialog(frame, panel, "Test !",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE );
        }
    }

    private static class MyFocusListener implements FocusListener {
        public void focusGained(FocusEvent FE) {
            System.out.println("focusGained: "+ FE.getComponent().getName() );
            passed = true;
        }

        public void focusLost(FocusEvent FE) { }
    }
}
