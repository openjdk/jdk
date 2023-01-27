/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
   @bug 6817009
   @summary Verifies if AAction.SELECTED_KEY not toggled when using key binding
   @run main TestSelectedKey
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.awt.Robot;

import javax.swing.SwingUtilities;
import javax.swing.JLabel;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.KeyStroke;
import javax.swing.JToolBar;
import javax.swing.JToggleButton;
import javax.swing.JPopupMenu;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.ActionMap;

public class TestSelectedKey {
    private static Robot robot;
    private static JFrame frame;
    private static boolean toggled = false;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> createGUI());
            robot.waitForIdle();
            robot.delay(1000);
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_Z);
            robot.keyRelease(KeyEvent.VK_Z);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.waitForIdle();
            robot.delay(1000);
            if (!toggled) {
                throw new RuntimeException("JToggleButton not toggled via accelerator key");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createGUI() {
        final JLabel label = new JLabel("Toggle me actions 0");
        final JLabel selected = new JLabel("Toggle me selected: false");
        AbstractAction action = new AbstractAction("Toggle me") {
            private int count = 0;
            @Override
            public void actionPerformed(ActionEvent e) {
                label.setText("Toggle me actions " + (++count));
            }
        };
        action.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if(Action.SELECTED_KEY.equals(evt.getPropertyName())) {
                    selected.setText("Toggle me selected: " + evt.getNewValue());
                    toggled = (boolean)evt.getNewValue();
                }
            }
        });
        action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK));
        // need to set manually or won't be updated by Swing
        action.putValue(Action.SELECTED_KEY, Boolean.FALSE);

        Box labels = new Box(BoxLayout.PAGE_AXIS);
        labels.add(label);
        labels.add(selected);

        JToolBar toolBar = new JToolBar();
        toolBar.add(new JToggleButton(action));

        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.add(new JCheckBoxMenuItem(action));
        labels.setComponentPopupMenu(popupMenu);

        frame = new JFrame("Test Action.SELECTED_KEY");
        frame.getContentPane().add(toolBar, BorderLayout.PAGE_START);
        frame.getContentPane().add(labels, BorderLayout.CENTER);

        InputMap inputMap = frame.getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = frame.getRootPane().getActionMap();
        inputMap.put((KeyStroke) action.getValue(Action.ACCELERATOR_KEY), "toggleAction");
        actionMap.put("toggleAction", action);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(400, 200));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
