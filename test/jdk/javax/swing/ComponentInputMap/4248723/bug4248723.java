/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4248723
 * @summary Tests that ComponentInputMap doesn't throw NPE when deserializing
 * @run main bug4248723
 */

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.swing.ComponentInputMap;
import javax.swing.JButton;
import javax.swing.KeyStroke;

public class bug4248723 {
    public static Object serializeAndDeserialize(Object toWrite)
            throws ClassNotFoundException, IOException {
        ByteArrayOutputStream ostream = new ByteArrayOutputStream();
        ObjectOutputStream p = new ObjectOutputStream(ostream);
        p.writeObject(toWrite);
        p.flush();
        byte[] data = ostream.toByteArray();
        ostream.close();

        ByteArrayInputStream istream = new ByteArrayInputStream(data);
        ObjectInputStream q = new ObjectInputStream(istream);
        Object retValue = q.readObject();
        istream.close();
        return retValue;
    }

    public static void main(String[] argv) {
        ComponentInputMap cim = new ComponentInputMap(new JButton());
        cim.put(KeyStroke.getKeyStroke(
                KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), "A");
        try {
            cim = (ComponentInputMap)serializeAndDeserialize(cim);
        } catch (ClassNotFoundException|IOException ignore) {
            // Should not cause test to fail so silently ignore these
        }
    }
}
