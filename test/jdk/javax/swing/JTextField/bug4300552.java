/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JTextField;
import java.awt.ComponentOrientation;
import java.awt.font.TextAttribute;

/*
 * @test
 * @bug 4300552
 * @summary A JTextComponent's RUN_DIRECTION document property was not being
 *          initialized.
 */

public class bug4300552 {
    private static JTextField textField;

    public static void main(String[] args) throws Exception {
        textField = new JTextField("\u0633\u0644\u0627\u0645 Peace");
        testCompOrientation();

        textField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        testCompOrientation();

        textField.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        testCompOrientation();
    }

    private static void testCompOrientation() {
        Object runDir = textField.getDocument().getProperty(TextAttribute.RUN_DIRECTION);
        if (runDir == null) {
            throw new RuntimeException("Document's run direction property should be set");
        }

        Boolean runDirFlag = (Boolean) runDir;
        ComponentOrientation o = textField.getComponentOrientation();
        if ((TextAttribute.RUN_DIRECTION_LTR == runDirFlag) != o.isLeftToRight()) {
            throw new RuntimeException("Document's run direction property("
                    + (TextAttribute.RUN_DIRECTION_LTR == runDirFlag ? "LTR" : "RTL")
                    + ") doesn't match component orientation ("
                    + (o.isLeftToRight() ? "LTR" : "RTL") + ")");
        }
    }
}
