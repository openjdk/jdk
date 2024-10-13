/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4886538
 * @summary JFormattedTextField not returning correct value (class)
 * @run main bug4886538
 */

import javax.swing.JFormattedTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultFormatterFactory;

public class bug4886538 {

    public static void main(String[] args) throws Exception {
        // test default display formatter
        TestFormattedTextField field = new TestFormattedTextField(0.0);
        field.setFormatter(((DefaultFormatterFactory) field.
                getFormatterFactory()).getDisplayFormatter());
        field.setText("10");
        field.commitEdit();

        Object dblValue = field.getValue();
        if (!(dblValue instanceof Double)) {
            throw new RuntimeException("The JFormattedTextField's value " +
                    "should be instanceof Double");
        }

        // test default editor formatter
        field = new TestFormattedTextField(0.0);
        field.setFormatter(((DefaultFormatterFactory) field.
                getFormatterFactory()).getEditFormatter());
        field.setText("10");
        field.commitEdit();

        dblValue = field.getValue();
        if (!(dblValue instanceof Double)) {
            throw new RuntimeException("The JFormattedTextField's value " +
                    "should be instanceof Double");
        }

    }

    static class TestFormattedTextField extends JFormattedTextField {
        public TestFormattedTextField(Object value) {
            super(value);
        }
        public void setFormatter(JFormattedTextField.AbstractFormatter formatter) {
            super.setFormatter(formatter);
        }
    }

}
