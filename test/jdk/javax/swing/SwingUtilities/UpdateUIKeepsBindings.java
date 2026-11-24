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
 * @bug 8394820
 * @key headful
 * @summary Verifies application key bindings are not cleared on L&F change
 * @run main UpdateUIKeepsBindings
 */

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class UpdateUIKeepsBindings {
    private static final KeyStroke F5 = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
    private static final int[] CONDITIONS = {
            JComponent.WHEN_FOCUSED,
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
            JComponent.WHEN_IN_FOCUSED_WINDOW};
    private static final String[] CONDITION_NAMES = {
            "WHEN_FOCUSED",
            "WHEN_ANCESTOR_OF_FOCUSED_COMPONENT",
            "WHEN_IN_FOCUSED_WINDOW"};

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        SwingUtilities.invokeAndWait(UpdateUIKeepsBindings::test);
    }

    private static void test() {
        Action action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {}
        };
        JComponent[] components = {
                new JTextField(), new JTable(), new JButton(),
                new JComboBox<String>(), new JList<String>()};
        JPanel panel = new JPanel();
        for (JComponent component : components) {
            panel.add(component);
            component.getActionMap().put("myAction", action);
            for (int condition : CONDITIONS) {
                component.getInputMap(condition).put(F5, "myAction");
            }
        }

        // The same look and feel, as an 
        // application changing its theme updates the UI
        SwingUtilities.updateComponentTreeUI(panel);

        int lost = 0;
        for (JComponent component : components) {
            String name = component.getClass().getSimpleName();
            if (component.getActionMap().get("myAction") == null) {
                System.out.println(name + ": ActionMap binding lost");
                lost++;
            }
            for (int i = 0; i < CONDITIONS.length; i++) {
                if (component.getInputMap(CONDITIONS[i]).get(F5) == null) {
                    System.out.println(name + ": InputMap " +
                                       CONDITION_NAMES[i] + " binding lost");
                    lost++;
                }
            }
        }
        if (lost > 0) {
            throw new RuntimeException(lost +
                         " application key bindings lost by updateComponentTreeUI()");
        }
        System.out.println("Passed: all application key bindings kept");
    }
}

