/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6726690
 * @summary Verifies SwingUtilities.replaceUI*Map() methods remove
 *          previously installed maps
 * @run main UIMapTest
 */

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class UIMapTest {

    public static void main(String[] args) {

        StringBuilder str = new StringBuilder();

        // Create the test button
        JButton button = new JButton("Test");

        // Create an input map that maps ENTER to the button
        ComponentInputMap map = new ComponentInputMap(button);
        map.put(KeyStroke.getKeyStroke("pressed ENTER"), "pressed");
        map.put(KeyStroke.getKeyStroke("released ENTER"), "released");

        // Add the map
        SwingUtilities.replaceUIInputMap(button, JComponent.WHEN_IN_FOCUSED_WINDOW, map);

        // Attempt to remove the map
        SwingUtilities.replaceUIInputMap(button, JComponent.WHEN_IN_FOCUSED_WINDOW, null);

        if (button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).
            get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) != null) {
            str.append("\nSwingUtilities.replaceUIInputMap " +
                       "didn't remove previously installed input map");
        }

        // Get the InputMap for the button when it has focus
        InputMap inputMap = button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Map the VK_ENTER key stroke to a specific action name
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "doEnterAction");
        Action enterAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { }
        };
        button.getActionMap().put("doEnterAction",  enterAction);
        SwingUtilities.replaceUIActionMap(button, null);
        if (button.getActionMap().size() != 0) {
            str.append("\nSwingUtilities.replaceUIActionMap " +
                       "didn't remove previously installed action map");
        }
        if (str.length() != 0) {
            throw new RuntimeException(str.toString());
        }
    }
}
