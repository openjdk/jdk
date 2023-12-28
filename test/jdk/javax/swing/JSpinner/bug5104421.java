/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.SpinnerDateModel;

/*
 * @test
 * @bug 5104421
 * @summary SpinnerDateModel.setValue(Object) throws exception with incorrect message
 * @run main bug5104421
 */

public class bug5104421 {
    public static void main(String[] args) {
        SpinnerDateModel model = new SpinnerDateModel();
        try {
            model.setValue(Integer.valueOf(42));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().toLowerCase().indexOf("null value") != -1) {
                throw new RuntimeException("SpinnerDateModel.setValue(Object) throws " +
                        "exception with incorrect message");
            }
        }
        System.out.println("Test Passed!");
    }
}
