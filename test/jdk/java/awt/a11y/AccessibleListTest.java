/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
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
 * @bug 8271846
 * @summary Test implementation of AccessibleList interface
 * @author Artem.Semenov@jetbrains.com
 * @run main AccessibleListTest
 * @requires (os.family == "windows" | os.family == "mac")
 */

import javax.swing.JList;
import  javax.swing.ListSelectionModel;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleList;
import javax.accessibility.AccessibleTable;
import java.util.Map;

public class AccessibleListTest {
    private static boolean testResult = false;
    private static String exceptionString = null;
    private static final String[] NAMES = {"One", "Two", "Three", "Four", "Five"};
    private static final boolean OK = true;
    private static final boolean FAIL = false;
    private static int failCount = 0;
    private static final Map<Integer, Integer> listSelectionModeMap = Map.of(
            AccessibleList.SINGLE_SELECTION, ListSelectionModel.SINGLE_SELECTION,
            AccessibleList.SINGLE_INTERVAL_SELECTION, ListSelectionModel.SINGLE_INTERVAL_SELECTION,
            AccessibleList.MULTIPLE_INTERVAL_SELECTION, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    );
    private static final Map<Integer, Integer> accessibleListModeMap = Map.of(
            ListSelectionModel.SINGLE_SELECTION, AccessibleList.SINGLE_SELECTION,
            ListSelectionModel.SINGLE_INTERVAL_SELECTION, AccessibleList.SINGLE_INTERVAL_SELECTION,
            ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, AccessibleList.MULTIPLE_INTERVAL_SELECTION
    );

    public static void main(String[] args) throws Exception {
        AccessibleListTest.runTest();

        if (!testResult) {
            throw new RuntimeException(AccessibleListTest.exceptionString);
        }
    }

    private static void countersControl(final boolean isOk, final String methodName) {
        if (isOk) {
            System.out.println(methodName + ": ok");
        } else {
            failCount += 1;
            System.out.println(methodName + ": fail");
        }
    }

    public static void runTest() {
        JList<String> list = new JList<>(NAMES);

        AccessibleContext ac = list.getAccessibleContext();
        if (ac == null) {
            countersControl(FAIL, "accessibleConntext");
            return;
        }
        countersControl(OK, "acccessibleContext");

        AccessibleTable at = ac.getAccessibleTable();
        if ((at == null) && !(at instanceof AccessibleList)) {
            countersControl(FAIL, "AccessibleList");
            return;
        }
        countersControl(OK, "AccessibleList");

        AccessibleList al = ((AccessibleList) at);

        int size = al.getSize();
        if (size == 5) {
            countersControl(OK, "getSize()");
        } else {
            countersControl(FAIL, "getSize()");
        }

        int listSelectionMode = list.getSelectionMode();
        int accessibleListMode = listSelectionModeMap.get(al.getSelectionMode());
        if (listSelectionMode == accessibleListMode) {
            countersControl(OK, "getSelectionMode()");
        } else {
            countersControl(FAIL, "getSelectionMode()");
        }

        al.setSelectionMode(AccessibleList.MULTIPLE_INTERVAL_SELECTION);

        listSelectionMode = list.getSelectionMode();
        accessibleListMode = listSelectionModeMap.get(al.getSelectionMode());
        if (listSelectionMode == accessibleListMode) {
            countersControl(OK, "setSelectionMode()");
        } else {
            countersControl(FAIL, "setSelectionMode()");
        }

        al.setSelectionInterval(0, 2);

        if (list.getSelectionModel().isSelectedIndex(0) &&
                list.getSelectionModel().isSelectedIndex(1) &&
                list.getSelectionModel().isSelectedIndex(2)) {
            countersControl(OK, "setSelectionInterval()");
        } else {
            countersControl(FAIL, "setSelectionInterval()");
        }

        if (al.isSelectedIndex(1)) {
            countersControl(OK, "isSelectedIndex()");
        } else {
            countersControl(FAIL, "isSelectedIndex()");
        }

        if (al.getSelectedItemsCount() == list.getSelectionModel().getSelectedItemsCount()) {
            countersControl(OK, "getSelectedItemsCount()");
        } else {
            countersControl(FAIL, "getSelectedItemsCount()");
        }

        if (!al.isSelectionEmpty()) {
            countersControl(OK, "isSelectionEmpty()");
        } else {
            countersControl(FAIL, "isSelectionEmpty()");
        }

        if (al.getMaxSelectionIndex() == 2) {
            countersControl(OK, "getMaxSelectionIndex()");
        } else {
            countersControl(FAIL, "getMaxSelectionIndex()");
        }

        if (al.getMinSelectionIndex() == 0) {
            countersControl(OK, "getMinSelectionIndex()");
        } else {
            countersControl(FAIL, "getMinSelectionIndex()");
        }

        int[] listSelectedIndices = list.getSelectionModel().getSelectedIndices();
        int[] accessibleListSelectedIndices = al.getSelectedIndices();

        if (listSelectedIndices.length != accessibleListSelectedIndices.length) {
            countersControl(FAIL, "getSelectedIndices()");
        } else {
            int length = listSelectedIndices.length, i = 0;
            boolean ok = true;
            while (ok && (i < length)) {
                if (listSelectedIndices[i] != accessibleListSelectedIndices[i]) {
                    ok = false;
                } else {
                    i += 1;
                }
            }
            if (ok) {
                countersControl(OK, "getSelectedIndices()");
            } else {
                countersControl(FAIL, "getSelectedIndices()");
            }
        }

        al.addSelectionInterval(3, 4);
        if (al.getSelectedItemsCount() == 5) {
            countersControl(OK, "addSelectionInterval()");
        } else {
            countersControl(FAIL, "addSelectionInterval()");
        }

        al.removeSelectionInterval(2, 3);
        if (al.getSelectedItemsCount() == 3) {
            countersControl(OK, "removeSelectionInterval()");
        } else {
            countersControl(FAIL, "removeSelectionInterval()");
        }

        al.setSelectionInterval(0, 4);
        al.removeIndexInterval(2, 3);
        if (al.getSelectedItemsCount() == 3) {
            countersControl(OK, "removeIndexInterval()");
        } else {
            countersControl(FAIL, "removeIndexInterval()");
        }

        al.removeSelectionInterval(0, 4);
        al.setSelectionInterval(0, 0);
        al.addSelectionInterval(4, 4);
        al.insertIndexInterval(0, 2, true);
        al.insertIndexInterval(4, 2, false);
        if (al.getSelectedItemsCount() == 4) {
            countersControl(OK, "insertIndexInterval()");
        } else {
            countersControl(FAIL, "insertIndexInterval()");
        }

        if (failCount == 0) {
            AccessibleListTest.testResult = true;
            System.out.println("All methods: Ok");
        } else {
            AccessibleListTest.exceptionString = "Test failed. " + String.valueOf(failCount) + " methods is no ok";
        }
    }
}
