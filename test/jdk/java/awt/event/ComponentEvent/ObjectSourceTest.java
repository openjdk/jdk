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
  @test
  @bug 4420658
  @summary No ClassCastException should be thrown when getComponent()
           is called on an event with a non-Component source.
           The result should be null.
  @key headful
  @run main ObjectSourceTest
*/

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ComponentEvent;


public class ObjectSourceTest {
    static Frame frame;

    public static void main(String[] args) {
        frame = new Frame("ObjectSourceTest");

        ComponentEvent ce = new ComponentEvent(frame, ComponentEvent.COMPONENT_SHOWN);
        Object obj = new Object();
        ce.setSource(obj);

        Component comp = ce.getComponent();
        if (comp != null) {
            throw new RuntimeException("ObjectSourceTest failed. comp != null");
        }

        if (frame != null) {
            frame.dispose();
        }
    }
 }
