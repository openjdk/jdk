/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @bug 4413434
 * @summary Verify that class loaded outside of application class loader is
 *          correctly resolved during deserialization when read in by custom
 *          readObject() method of a bootstrap class (in this case,
 *          java.awt.Button).
 */

import java.awt.Button;
import java.awt.event.MouseAdapter;
import java.io.*;

public class Foo implements Runnable {

    static class Adapter extends MouseAdapter implements Serializable {}

    public void run() {
        try {
            Button button = new Button();
            button.addMouseListener(new Adapter());

            // iterate to trigger java.lang.reflect code generation
            for (int i = 0; i < 100; i++) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(bout);
                oout.writeObject(button);
                oout.close();
                ObjectInputStream oin = new ObjectInputStream(
                    new ByteArrayInputStream(bout.toByteArray()));
                oin.readObject();
            }
        } catch (Exception ex) {
            throw new Error(
                "Error occured while (de)serializing Button: " + ex);
        }
    }
}
