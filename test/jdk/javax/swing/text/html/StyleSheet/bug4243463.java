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

/* @test
   @bug 4243463
   @summary Tests that StyleSheet has following methods:
            public void addStyleSheet(StyleSheet ss);
            public void removeStyleSheet(StyleSheet ss);
            public Enumeration getStyleSheets()
   @run main bug4243463
*/

import javax.swing.text.html.StyleSheet;

public class bug4243463 {

    public static void main(String[] argv) throws Exception {
        StyleSheet main = new StyleSheet();
        StyleSheet ss = new StyleSheet();
        ss.addRule("p {color:red;}");

        main.addStyleSheet(ss);
        StyleSheet[] sheets = main.getStyleSheets();
        if (sheets.length != 1 || sheets[0] != ss) {
            throw new RuntimeException("getStyleSheets failed");
        }

        main.removeStyleSheet(ss);
        sheets = main.getStyleSheets();
        if (sheets != null) {
            throw new RuntimeException("StyleSheet is not removed");
        }
    }
}
