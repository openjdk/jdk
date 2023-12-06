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

import javax.swing.text.html.HTMLEditorKit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/*
 * @test
 * @bug 4213373
 * @summary  Serialization bug on HTMLEditorKit.
 */

public class bug4213373 {

    public static void main(String[] args) throws Exception {
        HTMLEditorKit ekr = null;
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            HTMLEditorKit ekw = new HTMLEditorKit();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(ekw);
            byte[] buf = baos.toByteArray();

            ByteArrayInputStream bais = new ByteArrayInputStream(buf);
            ois = new ObjectInputStream(bais);
            ekr = (HTMLEditorKit) ois.readObject();
        } finally {
           if (oos != null) {
               oos.close();
           }
           if (ois != null) {
                ois.close();
            }
        }
    }
}
