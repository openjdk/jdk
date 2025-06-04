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

import javax.swing.JFormattedTextField;
import javax.swing.text.NumberFormatter;
import java.text.DecimalFormat;

/*
 * @test
 * @bug 4763466
 * @summary JFormattedTextField and the - sign
 */

public class bug4763466 {

    public static void main(String[] args) throws Exception {
        DecimalFormat decimalFormat = new DecimalFormat("##0.00");
        NumberFormatter textFormatter = new NumberFormatter(decimalFormat);
        textFormatter.setAllowsInvalid(false);
        textFormatter.setValueClass(Double.class);

        JFormattedTextField ftf = new JFormattedTextField(textFormatter);
        ftf.setCaretPosition(0);
        ftf.setValue((double) -1);

        if (ftf.getCaretPosition() == 0) {
            throw new RuntimeException("Test Failed. Caret position shouldn't be 0" +
                    " as the sign is literal");
        }
    }
}
