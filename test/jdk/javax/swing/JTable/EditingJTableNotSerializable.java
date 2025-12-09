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

/*
 * @test
 * @bug 6441373
 * @summary Verifies Editing JTable is Serializable
 * @run main EditingJTableNotSerializable
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.SwingUtilities;

public class EditingJTableNotSerializable {

    private static JTable serialize(JTable jt) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(jt);
        byte[] bdata = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(bdata);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (JTable)ois.readObject();
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Object[][] data = new Object[][]{ new Object[]{ 1,2,3,4,5}};
                Object[] names = new Object[]{ 1,2,3,4,5};
                JTable jt = new JTable(data, names);
                jt.editCellAt(0,3);
                System.out.println("Serializing editing JTable");
                JTable newjt = serialize(jt);
                if (newjt.isEditing()) {
                    throw new RuntimeException("Editing table is serializable");
                }

                TableCellEditor tce = jt.getCellEditor();
                tce.stopCellEditing();
                System.out.println("Serializing non-editing JTable");
                serialize(jt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
