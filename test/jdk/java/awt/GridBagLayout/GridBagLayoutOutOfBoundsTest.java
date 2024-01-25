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

/*
  @test
  @bug 5055696
  @summary REGRESSION: GridBagLayout throws ArrayIndexOutOfBoundsExceptions
  @key headful
  @run main GridBagLayoutOutOfBoundsTest
*/
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Panel;

public class GridBagLayoutOutOfBoundsTest {
    final static int L=2;
    static Frame frame;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            try {
                frame = new Frame("GridBagLayoutOutOfBoundsTestFrame");
                frame.validate();
                GridBagLayout layout = new GridBagLayout();
                frame.setLayout(layout);
                GridBagConstraints gridBagConstraints;

                Button[] mb = new Button[L];
                for (int i = 0; i<L; i++){
                    mb[i] = new Button(""+i);
                }
                for (int i = 0; i<mb.length; i++){
                    gridBagConstraints = new GridBagConstraints();
                    gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
                    gridBagConstraints.gridheight = GridBagConstraints.REMAINDER;
                    frame.add(mb[i], gridBagConstraints);
                }
                frame.setVisible(true);
            } finally {
                if (frame != null) {
                    frame.dispose();
                }
            }
        });
    }
}
