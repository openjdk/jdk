/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.TextArea;

/*
 * @test
 * @bug 4120876
 * @key headful
 * @summary Ensure that getText can handle strings of various lengths,
 *          in particular strings longer than 255 characters
 */

public class Length {

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            TextArea ta = new TextArea();
            StringBuffer sb = new StringBuffer("x");

            for (int i = 0; i < 14; i++) {
                String s = sb.toString();
                check(ta, s.substring(1));
                check(ta, s);
                check(ta, s + "y");
                sb.append(s);
            }
        });
    }

    static void check(TextArea ta, String s) {
        ta.setText(s);
        String s2 = ta.getText();
        System.err.println(s.length() + " " + s2.length());
        if (s.length() != s2.length()) {
            throw new RuntimeException("Expected " + s.length() +
                                       "chars, got " + s2.length());
        }
    }
}
