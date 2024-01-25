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
  @bug 4726853
  @key headful
  @summary Checkbox is changing it's state after removing from CheckboxGroup
*/

import java.awt.Checkbox;
import java.awt.CheckboxGroup;

public class SetCheckboxGroupNull {

    public static void main(String[] args) {
        boolean passed = true;

        // 1 step
        {
            CheckboxGroup g = new CheckboxGroup();
            Checkbox cb1 = new Checkbox("Label", true, g);
            System.out.println("1. (should be true) "+cb1.getState());
            passed = passed && (cb1.getState() == true);
            cb1.setCheckboxGroup(null);
            System.out.println("2. (should be true) "+cb1.getState());
            passed = passed && (cb1.getState() == true);
        }

        // 2 step
        {
            CheckboxGroup g = new CheckboxGroup();
            Checkbox cb1 = new Checkbox("CB1", true, g);
            System.out.println("3. (should be true) " + cb1.getState());
            passed = passed && (cb1.getState() == true);
            g.setSelectedCheckbox(null);
            System.out.println("4. (should be false) " + cb1.getState());
            passed = passed && (cb1.getState() == false);
        }

        if (!passed) {
            throw new RuntimeException("SetCheckboxGroupNull FAILED");
        }
        System.out.println("SetCheckboxGroupNull PASSED");
    }
}
