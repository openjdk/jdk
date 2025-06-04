/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4330102
  @summary Tests that Color object is serializable
  @run main ColorSerializationTest
*/

import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.IndexColorModel;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;

public class ColorSerializationTest {

    public static void main(String[] args) {
        java.awt.Color cobj = new java.awt.Color(255, 255, 255);
        try {
            cobj.createContext(
                    new IndexColorModel(
                            8, 1,
                            new byte[]{0}, new byte[]{0}, new byte[]{0}),
                    new Rectangle(1, 1, 2, 3),
                    new Rectangle(3, 3),
                    new AffineTransform(),
                    new RenderingHints(null));
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            ObjectOutputStream objos = new ObjectOutputStream(ostream);
            objos.writeObject(cobj);
            objos.close();
            System.out.println("Test PASSED");
        } catch (java.io.IOException e) {
            System.out.println("Test FAILED");
            throw new RuntimeException("Test FAILED: Color is not serializable: " + e.getMessage());
        }

    }
}
