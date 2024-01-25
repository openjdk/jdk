/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Toolkit;

/**
 * @test
 * @bug 8273581
 * @summary verify the "sun.font.fontmanager" system property is not set
 * @run main/othervm -Djava.awt.headless=true CheckFontManagerSystemProperty
 */

public class CheckFontManagerSystemProperty {

    public static void main(String[] args) {
        // force AWT library loading
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        if (toolkit == null) {
            throw new RuntimeException("Toolkit not found!");
        }
        String tkProp = System.getProperty("sun.font.fontmanager");
        if (tkProp != null) {
            throw new RuntimeException("tkProp = " + tkProp);
        }
    }
}
