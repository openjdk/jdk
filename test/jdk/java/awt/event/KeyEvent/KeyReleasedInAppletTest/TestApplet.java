/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.*;
import java.awt.*;
import java.awt.Color;
import java.awt.Label;
import java.awt.TextArea;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.String;
import java.lang.System;


public class TestApplet extends JApplet {

    public void init() {
        final TextArea log = new TextArea("Events:\n");
        log.setEditable(false);
        log.setSize(400, 200);
        this.add(log);
        log.addKeyListener(
                new KeyAdapter() {
                    @Override public void keyTyped(KeyEvent e) {
                        log.append("Key typed: char = " + e.getKeyChar() + "\n");
                    }

                    @Override public void keyPressed(KeyEvent e) {
                        log.append("Key pressed: char = " + e.getKeyChar() + " code = " + e.getKeyCode() + "\n");
                    }

                    @Override public void keyReleased(KeyEvent e) {
                        log.append("Key released: char = " + e.getKeyChar() + " code = " + e.getKeyCode() + "\n");
                    }
                });
    }

    public void start() {
    }

    public void stop() {
    }

    public void destroy() {
    }

}
