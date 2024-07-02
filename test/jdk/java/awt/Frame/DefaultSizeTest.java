/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;

/*
 * @test 4033151
 * @summary Test that frame default size is minimum possible size
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DefaultSizeTest
 */

public class DefaultSizeTest {

    private static final String INSTRUCTIONS = """
            An empty frame is created.
            It should be located to the right of this window
            and should be the minimum size allowed by the window manager.
            For any WM, the frame should be very small.
            If the frame is not large, click Pass or Fail otherwise.
            """;


    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("DefaultSizeTest Instructions Frame")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(10)
                .columns(45)
                .testUI(() -> new Frame("DefaultSize"))
                .screenCapture()
                .build()
                .awaitAndCheck();
    }
}
