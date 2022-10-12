/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6187113
 * @summary  Verifies if
 *  DefaultListSelectionModel.removeIndexInterval(0, Integer.MAX_VALUE) fails
 * @run main TestDefListModelException
 */
import javax.swing.DefaultListSelectionModel;

public class TestDefListModelException {
    public static void main(String[] args) throws Exception {
        test1();
        test2();
    }

    private static void test1() {
        boolean passed = false;
        DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
        selectionModel.setSelectionInterval(0, 1);
        try {
            selectionModel.removeIndexInterval(0, Integer.MAX_VALUE);
        } catch (IndexOutOfBoundsException e) {
            passed = true;
        }
        if (!passed) {
            throw new RuntimeException("IOOBE not thrown");
        }
    }

    private static void test2() {
        boolean passed = false;
        DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
        try {
            selectionModel.setSelectionInterval(Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
        } catch (IndexOutOfBoundsException e) {
            passed = true;
        }
        if (!passed) {
            throw new RuntimeException("IOOBE not thrown");
        }
        selectionModel.removeIndexInterval(0, 1);
    }
}


