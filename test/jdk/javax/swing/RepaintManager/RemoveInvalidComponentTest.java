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

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 8361493
 * @key headful
 * @summary Verifies that RepaintManager's removeInvalidComponent
 *          remove wrong component
 * @run main/othervm RemoveInvalidComponentTest
 */
public final class RemoveInvalidComponentTest {
    private static volatile boolean isValidateCalled;

    private static final class EqualLabel extends JLabel {

        public EqualLabel(String text) {
            super(text);
        }

        @Override
        public void validate() {
            System.out.println("validate called");
            isValidateCalled = true;
            super.validate();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualLabel el
                   && el.getText().equals(getText());
        }

        @Override
        public boolean isValidateRoot() {
            return true;
        }
    }

    private static void runTest() {
        final RepaintManager repaintManager = new RepaintManager();

        JLabel label1 = new EqualLabel("label");
        JLabel label2 = new EqualLabel("label");

        if (!label1.equals(label2)) {
            throw new RuntimeException("label1.equals(label2) returned false");
        }

        JFrame frame = new JFrame("RemoveInvalidComponentTest");

        frame.add(label1);
        frame.add(label2);

        frame.setVisible(true);

        repaintManager.addInvalidComponent(label1);
        repaintManager.removeInvalidComponent(label2);
        repaintManager.validateInvalidComponents();

        frame.dispose();
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(RemoveInvalidComponentTest::runTest);

        if (!isValidateCalled) {
            throw new RuntimeException("Label1 was removed from repaint manager");
        }
    }
}
