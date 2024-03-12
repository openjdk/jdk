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
  @bug 4114268
  @key headful
  @summary Checkbox.setCheckboxGroup(null) alters selection for CB's previous CBGroup
*/

import java.awt.Checkbox;
import java.awt.CheckboxGroup;

public class NullCheckboxGroupTest {


    public static void main(String[] args) {
        CheckboxGroup cbg = new CheckboxGroup();
        Checkbox chbox1 = new Checkbox("First", cbg, true);
        Checkbox chbox2 = new Checkbox("Second", cbg, false);

        chbox2.setCheckboxGroup(null);

        System.out.println("chbox1="+chbox1);
        System.out.println("chbox2="+chbox2);
        System.out.println("cbg="+cbg);

        if (cbg.getSelectedCheckbox() != chbox1) {
            System.out.println("FAILED");
            throw new RuntimeException("Test FAILED");
        } else {
            System.out.println("PASSED");
        }
    }
 }
