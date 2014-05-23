/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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


/* JFrameCreateTime:
 *
 * Example swing application that just creates a JFrame object.
 *
 */

/* Early in 1.5 it was reported that doing a step into the first JFrame
 *   was very slow (VisualMust debugger people reported this).
 */

import java.awt.GraphicsEnvironment;
import javax.swing.*;

public class JFrameCreateTime {
    public static void main(String[] args) {
        JFrame f;
        long start, end;
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("JFrameCreateTime test was skipped due to headless mode");
        } else {
            start = System.currentTimeMillis();
            f = new JFrame("JFrame");
            end = System.currentTimeMillis();

            System.out.println("JFrame first creation took " + (end - start) + " ms");

            start = System.currentTimeMillis();
            f = new JFrame("JFrame");
            end = System.currentTimeMillis();

            System.out.println("JFrame second creation took " + (end - start) + " ms");
            System.exit(0);
        }
    }
}
