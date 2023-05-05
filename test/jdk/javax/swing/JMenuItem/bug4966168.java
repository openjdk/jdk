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

/*  @test
    @bug 4966168
    @summary JInternalFrame not serializable in Motif & GTK L&F
    @run main bug4966168
*/

import javax.swing.AbstractAction;
import javax.swing.JButton;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.awt.event.ActionEvent;

public class bug4966168 {

    public static class MyAction extends AbstractAction implements Serializable {
        public void actionPerformed(ActionEvent e) {}
    }

    public static void main(String args[]) throws Exception {
        bug4966168 test = new bug4966168();
        test.init();
    }

    public void init() {

        JButton button = new JButton(new MyAction());

        ObjectOutputStream out = null;

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            out = new ObjectOutputStream(byteStream);
        } catch (IOException e) {}

        if (out != null) {
            System.out.println("Testing...");
            try {
                out.writeObject(button);
            } catch (Exception e) {
                System.out.println(e);
                throw new Error("Serialization exception. Test failed.");
            }
        }
    }
}
