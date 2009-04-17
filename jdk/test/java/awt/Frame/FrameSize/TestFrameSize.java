/*
 * Copyright 2009 Red Hat, Inc.  All Rights Reserved.
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
  @test
  @bug 6721088
  @summary X11 Window sizes should be what we set them to
  @author Omair Majid <omajid@redhat.com>: area=awt.toplevel
  @run main TestFrameSize
 */

/**
 * TestFrameSize.java
 *
 * Summary: test that X11 Awt windows are drawn with correct sizes
 *
 * Test fails if size of window is wrong
 */

import java.awt.Dimension;
import java.awt.Frame;

public class TestFrameSize {

        static Dimension desiredDimensions = new Dimension(200, 200);
        static int ERROR_MARGIN = 15;
        static Frame mainWindow;

        public static void drawGui() {
                mainWindow = new Frame("");
                mainWindow.setPreferredSize(desiredDimensions);
                mainWindow.pack();

                Dimension actualDimensions = mainWindow.getSize();
                System.out.println("Desired dimensions: " + desiredDimensions.toString());
                System.out.println("Actual dimensions:  " + actualDimensions.toString());
                if (Math.abs(actualDimensions.height - desiredDimensions.height) > ERROR_MARGIN) {
                        throw new RuntimeException("Incorrect widow size");
                }
        }

        public static void main(String[] args) {
                try {
                        drawGui();
                } finally {
                        if (mainWindow != null) {
                                mainWindow.dispose();
                        }
                }
        }
}
