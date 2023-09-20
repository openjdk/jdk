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

/*
 * @test
 * @bug 4475478
 * @summary Tests that there is no NullPointerException
            thrown when we try to set Frame's icon
            which has null data
 * @key headful
 * @run main SetIconImageExceptionTest
*/
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Toolkit;

public class SetIconImageExceptionTest {
    static Frame f;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            try {
                // Test with non-existent image to test with null data
                //  not throwing NPE
                Image icon = Toolkit.getDefaultToolkit().getImage("notexistent.gif");
                f = new Frame("Frame with icon");
                f.setIconImage(icon);
                f.setVisible(true);
            } finally {
                if (f != null) {
                    f.dispose();
                }
            }
        });
    }

 }// class SetIconImageExceptionTest

