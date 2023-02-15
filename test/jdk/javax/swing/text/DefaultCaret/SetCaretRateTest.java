/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;

/*
 * @test
 * @bug 8301989
 * @summary Test checks that there is no exception happens
 *          when setting blink rate on a javax.swing.DefaultCaret that is not
 *          associated with any text component
 * @run main SetCaretRateTest
 */
public class SetCaretRateTest {
    public static void main(String[] args) {
        Caret caret = new DefaultCaret();
        caret.setBlinkRate(0);
        caret.setBlinkRate(100);
        caret.setBlinkRate(0);
        caret = new DefaultCaret();
        caret.setBlinkRate(100);
        caret.setBlinkRate(0);
        caret.setBlinkRate(100);
    }
}
