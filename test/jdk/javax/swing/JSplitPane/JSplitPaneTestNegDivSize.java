/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 4797982
 * @summary Verifies if negative size of JSplitPane divider can be set.
 * @run main JSplitPaneTestNegDivSize
 */
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class JSplitPaneTestNegDivSize {

   private static volatile int divSize;

   public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {	   
            JFrame frame = new JFrame();
            frame.setSize(new Dimension(400,200));
            frame.setVisible(true);
            JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true,
                     new JTextArea("I am top text area!"),
		     new JTextArea("I am bottom text area!"));
            frame.getContentPane().add(sp, BorderLayout.CENTER);
            sp.setDividerSize(-50);
	    divSize = sp.getDividerSize();
            System.out.println(divSize);
            frame.dispose();
        });
        if (divSize < 0) {
            throw new RuntimeException("Negative divider size");
        }
    }
}
