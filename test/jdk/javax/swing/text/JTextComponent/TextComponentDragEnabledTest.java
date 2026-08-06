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
 * @bug 8388884
 * @key headful
 * @summary Checks dragEnabled defaults and explicit settings across installed L&Fs
 * @run main TextComponentDragEnabledTest
 */

import java.util.List;
import java.util.function.Supplier;

import javax.swing.JEditorPane;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.text.JTextComponent;

public class TextComponentDragEnabledTest {

    private static final String AQUA_LAF = "com.apple.laf.AquaLookAndFeel";

    private static final List<Supplier<JTextComponent>> TEXT_COMPONENTS = List.of(
            JTextField::new,
            JTextArea::new,
            JTextPane::new,
            JEditorPane::new,
            JPasswordField::new
    );

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                runTest();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void runTest() throws Exception {
        testDefaultsForAllLookAndFeels();
        testExplicitSettingsForAllLookAndFeels();
    }

    private static void testDefaultsForAllLookAndFeels()
            throws Exception {
        for (UIManager.LookAndFeelInfo laf :
                UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing L&F " + laf.getClassName());
            UIManager.setLookAndFeel(laf.getClassName());

            for (Supplier<JTextComponent> supplier : TEXT_COMPONENTS) {
                JTextComponent component = supplier.get();
                boolean expected = AQUA_LAF.equals(laf.getClassName());
                checkDragEnabled(component, expected,
                        "new component under " + laf.getClassName());
            }
        }
    }

    private static void testExplicitSettingsForAllLookAndFeels()
            throws Exception {
        for (boolean expected : new boolean[] { false, true }) {
            for (UIManager.LookAndFeelInfo laf :
                UIManager.getInstalledLookAndFeels()) {
                UIManager.setLookAndFeel(new MetalLookAndFeel());

                for (Supplier<JTextComponent> supplier : TEXT_COMPONENTS) {
                    JTextComponent component = supplier.get();
                    component.setDragEnabled(expected);

                    UIManager.setLookAndFeel(laf.getClassName());
                    component.updateUI();

                    checkDragEnabled(component, expected,
                        "after switching to " + laf.getClassName());
                }
            }
        }
    }

    private static void checkDragEnabled(JTextComponent component,
                                          boolean expected,
                                          String msg) {
        boolean actual = component.getDragEnabled();
        if (actual != expected) {
            throw new RuntimeException(component.getClass().getName()
                    + ": " + msg
                    + "; expected dragEnabled=" + expected
                    + ", actual=" + actual);
        }
    }
}
