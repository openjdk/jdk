/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.dnd.DragSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/*
  @test
  @bug 4407057
  @summary tests that deserialized DragSource has a non-null flavor map
  @key headful
  @run main DragSourceSerializationTest
*/

public class DragSourceSerializationTest {

    public static void main(String[] args) throws Exception {
        try {
            final DragSource dragSource = DragSource.getDefaultDragSource();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream    oos  = new ObjectOutputStream(baos);
            oos.writeObject(dragSource);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);

            final DragSource copy = (DragSource)ois.readObject();
            if (copy.getFlavorMap() == null) {
                throw new RuntimeException("getFlavorMap() returns null");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
