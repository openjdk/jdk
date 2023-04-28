/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4257143
  @summary RFE: Cannot set some AWT properties until peer has been created
  @key headful
*/

import java.awt.EventQueue;
import java.awt.TextArea;
import java.awt.TextField;

public class PeerlessSetCaret {
    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            TextField tf = new TextField("Hello, World!");
            TextArea ta = new TextArea("Hello, World!");

            // without the fix these will throw IllegalComponentStateException
            tf.setCaretPosition(1);
            ta.setCaretPosition(1);
        });
    }
}
