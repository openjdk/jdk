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
  @test
  @bug 4704042
  @summary Unit tests for Insets.set()
  @run main SetInsetsTest
*/
import java.awt.Insets;
import java.awt.EventQueue;

public class SetInsetsTest {
    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            Insets insets = new Insets(0,0,0,0);
            insets.set(100,100,100,100);
            if (insets.top != 100 ||
                insets.bottom != 100 ||
                insets.left != 100 ||
                insets.right != 100) {
                throw new RuntimeException("Test Failed!  Insets=" + insets);
            }
        });
    }
}// class SetInsetsTest
