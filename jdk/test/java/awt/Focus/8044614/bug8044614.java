/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8044614
 * @summary Tests focus transfer between applets in different browser windows
 * @author Dmitry Markov
 * @library ../../regtesthelpers
 * @build Sysout
 * @run applet/manual=yesno bug8044614.html
 */

import javax.swing.JApplet;

import test.java.awt.regtesthelpers.Sysout;

public class bug8044614 extends JApplet {
    public void init() {
        String[] instructions = {
            "(1) Go to the test directory test/java/awt/Focus/8044614",
            "(2) Compile source file: javac TestApplet.java",
            "(3) Open the \"main.html\" file in the browser",
            "(4) Click the \"Start First Applet\" link to open the first applet window",
            "(5) Wait for the applet to start (press \"Run\" to any security alerts that appears)",
            "(6) Enter \"Hello\" to the text field",
            "(7) Click the \"Start Second Applet)\" link to open the second applet window",
            "(8) Wait for the applet to start (press \"Run\" to any security alerts that appears)",
            "(9) Enter \"World\" to the text field",
            "(10) Go back to the first applet and make sure you can enter some text to the text field"
        };

        Sysout.createDialogWithInstructions(instructions);
    }
}

