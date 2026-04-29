/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.Component;
import java.awt.EventQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/**
 * @test
 * @bug 6441373
 * @summary Checks that editing/non-editing JTable is serializable
 * @run main/timeout=260/othervm -Xmx32m JTableSerialization
 */
public final class JTableSerialization {

    private static JTable table;
    private static final int ROW = 1;
    private static final int COLUMN = 1;
    private static final String SOME_TEST_LABEL = "Some TEST label";
    private static final String TEST_EDIT_VALUE = "Test EDIT value";

    public static void main(String[] argv) throws Exception {
        for (UIManager.LookAndFeelInfo laf : getInstalledLookAndFeels()) {
            AtomicBoolean go = new AtomicBoolean(false);
            EventQueue.invokeAndWait(() -> go.set(tryLookAndFeel(laf)));
            if (!go.get()) {
                continue;
            }
            for (boolean editing : new boolean[]{true, false}) {
                EventQueue.invokeAndWait(JTableSerialization::init);
                long endtime = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (System.nanoTime() < endtime) {
                    // need to jump to/from EDT to flush all pending events
                    EventQueue.invokeAndWait(() -> test(editing));
                }
                EventQueue.invokeAndWait(JTableSerialization::validate);
            }
        }
    }

    private static void init() {
        JLabel label = new JLabel(SOME_TEST_LABEL);
        table = new JTable(2, 2);
        table.add(label);
        table.setValueAt(TEST_EDIT_VALUE, ROW, COLUMN);
        checkNonEditingState(table);
    }

    private static void test(boolean editing) {
        if (editing) {
            table.editCellAt(ROW, COLUMN);
            checkEditingState(table);
        }
        table = copyTable(table);
        checkNonEditingState(table);
    }

    private static void validate() {
        Object value = table.getValueAt(ROW, COLUMN);
        if (!value.equals(TEST_EDIT_VALUE)) {
            throw new RuntimeException("Wrong value: " + value);
        }
        for (Component component : table.getComponents()) {
            if (component instanceof JLabel) {
                if (((JLabel) component).getText().equals(SOME_TEST_LABEL)) {
                    return;
                }
            }
        }
        throw new RuntimeException("JLabel is not found");
    }


    private static void checkNonEditingState(JTable jt) {
        if (jt.isEditing()) {
            throw new RuntimeException("Should not be editing");
        }
        if (jt.getEditorComponent() != null) {
            throw new RuntimeException("Editor should be null");
        }
        int row = jt.getEditingRow();
        if (row != -1) {
            throw new RuntimeException("Expected row -1 but was: " + row);
        }
        int column = jt.getEditingColumn();
        if (column != -1) {
            throw new RuntimeException("Expected column -1 but was: " + column);
        }
    }

    private static void checkEditingState(JTable jt) {
        if (!jt.isEditing()) {
            throw new RuntimeException("Should be editing");
        }
        if (jt.getEditorComponent() == null) {
            throw new RuntimeException("Editor should not be null");
        }
        if (jt.getEditingRow() != ROW) {
            throw new RuntimeException("Row should be: " + ROW);
        }
        if (jt.getEditingColumn() != COLUMN) {
            throw new RuntimeException("Column should be: " + COLUMN);
        }
    }

    private static JTable copyTable(JTable jt) {
        try {
            byte[] bdata;
            try (var baos = new ByteArrayOutputStream();
                 var oos = new ObjectOutputStream(baos))
            {
                oos.writeObject(jt);
                bdata = baos.toByteArray();
            }
            try (var bais = new ByteArrayInputStream(bdata);
                 var ois = new ObjectInputStream(bais))
            {
                return (JTable) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean tryLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
            System.out.println("LookAndFeel: " + laf.getClassName());
            return true;
        } catch (UnsupportedLookAndFeelException ignored) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
