/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4546535
  @summary java.awt.Container.remove(int) throws unexpected NPE
*/

import java.awt.Canvas;
import java.awt.Panel;

public class RemoveByIndexExceptionTest {

    public static void main(String[] args) throws Exception {
        Panel p = new Panel();
        p.add(new Canvas());
        p.remove(0);

        int[] bad = {-1, 0, 1};
        for (int i = 0; i < bad.length; i++) {
            try {
                System.out.println("Removing " + bad[i]);
                p.remove(bad[i]);
                System.out.println("No exception");
            } catch (ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
                System.out.println("This is correct exception - " + e);
            } catch (NullPointerException e) {
                e.printStackTrace();
                throw new RuntimeException("Test Failed: NPE was thrown.");
            }
        }
        System.out.println("Test Passed.");
    }
}
