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
 * @summary JList preserves application bindings during style changes and serialization
 * @run main JListKeepsBindings nimbus
 * @run main JListKeepsBindings serialization
 */

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ListUI;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

public class JListKeepsBindings {
    private static final String ACTION_KEY = "applicationAction";
    private static final KeyStroke F5 =
            KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                switch (args[0]) {
                    case "nimbus" -> testNimbus();
                    case "serialization" -> testSerialization();
                    default -> throw new IllegalArgumentException(args[0]);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void testNimbus() throws Exception {
        UIManager.setLookAndFeel(new NimbusLookAndFeel());
        JList<String> list = new JList<>(new String[] {"one"});
        Bindings bindings = new Bindings(list);
        ListUI ui = list.getUI();
        for (int height : new int[] {25, 30}) {
            UIDefaults overrides = new UIDefaults();
            overrides.put("List.cellHeight", height);
            list.putClientProperty("Nimbus.Overrides", overrides);
            if (list.getUI() != ui || list.getFixedCellHeight() != height) {
                throw new RuntimeException("Style refresh was not exercised");
            }
            bindings.check("Nimbus.Overrides height=" + height);
        }
    }

    private static void testSerialization() throws Exception {
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        JList<String> list = new JList<>(new String[] {"one"});
        Bindings bindings = new Bindings(list);
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        Object oldMap = defaults.remove("List.focusInputMap");
        try {
            if (UIManager.get("List.focusInputMap") != null) {
                throw new RuntimeException("Focus input map is still present");
            }
            write(list);
            // Check the original object: serialization reinstalls its UI.
            bindings.check("after serialization");
        } finally {
            if (oldMap != null) {
                defaults.put("List.focusInputMap", oldMap);
            }
        }
    }

    private static void write(JList<?> list) throws IOException {
        try (ObjectOutputStream out =
                     new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(list);
        }
    }

    private static final class TestAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent event) { }
    }

    private static final class Bindings {
        private final JList<?> list;
        private final InputMap input;
        private final ActionMap actions;
        private final Action action = new TestAction();

        Bindings(JList<?> list) {
            this.list = list;
            input = list.getInputMap(JComponent.WHEN_FOCUSED);
            actions = list.getActionMap();
            input.put(F5, ACTION_KEY);
            actions.put(ACTION_KEY, action);
            check("before update");
        }

        void check(String phase) {
            if (list.getInputMap(JComponent.WHEN_FOCUSED) != input
                    || !ACTION_KEY.equals(input.get(F5))) {
                throw new RuntimeException(phase + ": input binding lost");
            }
            if (list.getActionMap() != actions
                    || actions.get(ACTION_KEY) != action) {
                throw new RuntimeException(phase + ": action binding lost");
            }
        }
    }
}
