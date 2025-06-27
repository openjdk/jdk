/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.HeadlessException;

/*
 * @test 8358526
 * @summary Verify behaviour of no-args HeadlessException and getMessage
 * @run main/othervm -Djava.awt.headless=true HeadlessExceptionTest
 * @run main/othervm HeadlessExceptionTest
 */

public class HeadlessExceptionTest {

    public static void main (String[] args) {
        String nullmsg = new HeadlessException().getMessage();
        String emptymsg = new HeadlessException("").getMessage();
        System.out.println("nullmsg=" + nullmsg);
        System.out.println("emptymsg=" + emptymsg);
        if (nullmsg != null) {
            if ("".equals(nullmsg)) {
                throw new RuntimeException("empty message instead of null");
            }
            if (!nullmsg.equals(emptymsg)) {
                throw new RuntimeException("non-null messages differ");
            }
        }
    }
}
