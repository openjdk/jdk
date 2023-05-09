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

/* @test
   @bug 4186951
   @summary Bulletproofing for AbstractAction.ArrayTable Serialization.
   @run main bug4186951
*/

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

public class bug4186951 {
    public static void main(String[] args) throws Exception {
        AbstractAction ma = new MyAction();
        MyClassSer mcs = new MyClassSer();
        ma.putValue("serializable",mcs);
        MyClassNonSer mcn = new MyClassNonSer();
        ma.putValue("non-serializable",mcn);
        FileOutputStream fos = new FileOutputStream("file.test");
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(ma);
        FileInputStream fis = new FileInputStream("file.test");
        ObjectInputStream ois = new ObjectInputStream(fis);
        ma = (MyAction)ois.readObject();
        File fil = new File("file.test");
        if (fil!=null) {
            fil.delete();
        }
        if (!((MyClassSer)ma.getValue("serializable")).equals(mcs)) {
            throw new RuntimeException("Serialisable class " +
                                        " wasn't serialized...");
        }
        if ((MyClassNonSer)ma.getValue("non-serializable") != null) {
            throw new RuntimeException("Serialisation occurs for " +
                                        " non-serialisable class...");
        }
    }

    static class MyAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {}
    }

    static class MyClassSer implements Serializable {
        String str = "default_string";
        public boolean equals(MyClassSer s) {
          return str.equals(s.str);
        }
    }

    static class MyClassNonSer {
        String str = "default_string";
    }

}
