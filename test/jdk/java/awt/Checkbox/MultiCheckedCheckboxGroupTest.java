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
  @bug 4136496
  @key headful
  @summary Checkbox.setCheckboxGroup(CheckboxGroup) works wrong on some Checkbox states
*/

import java.awt.Checkbox;
import java.awt.CheckboxGroup;

public class MultiCheckedCheckboxGroupTest {

    public static void main(String[] args) throws Exception {

        CheckboxGroup gr = new CheckboxGroup();
        Checkbox chb1 = new Checkbox("Box 1", true, gr);
        Checkbox chb2 = new Checkbox("Box 2", true, null);

        chb2.setCheckboxGroup(gr);

        System.out.println("chb1="+chb1);
        System.out.println("chb2="+chb2);
        System.out.println("gr.getSelectedCheckbox="+gr.getSelectedCheckbox());

        if(chb1.getState()
          && !chb2.getState()
          && chb1.getCheckboxGroup() == gr
          && chb2.getCheckboxGroup() == gr
          && gr.getSelectedCheckbox() == chb1) {
            System.out.println("PASSED");
        } else {
            System.out.println("FAILED");
            throw new RuntimeException("Test FAILED");
        }
    }
}
