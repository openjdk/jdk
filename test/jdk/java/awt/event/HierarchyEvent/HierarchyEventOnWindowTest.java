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
  @bug 4460376
  @summary HierarchyEvents on Frame should be dispatched correctly
           when on its child Window this event type enabled
  @key headful
  @run main HierarchyEventOnWindowTest
*/

import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.HierarchyBoundsAdapter;
import java.lang.reflect.InvocationTargetException;

public class HierarchyEventOnWindowTest {
    static Frame frame;

    public static void main(String args[]) throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("HierarchyEventOnWindowTest");
            CustomWindow window = new CustomWindow(frame);
            window.enableEvents();
            frame.add(new Button(""));
            window.disableEvents();
            window.addHierarchyListener(e -> {});
            window.addHierarchyBoundsListener(new HierarchyBoundsAdapter(){});
            frame.add(new Button(""));
        });

        if (frame != null) {
            EventQueue.invokeAndWait(() -> frame.dispose());
        }
    }
}

class CustomWindow extends Window {
    public CustomWindow(Frame frame) {
        super(frame);
    }
    public void enableEvents() {
        enableEvents(AWTEvent.HIERARCHY_EVENT_MASK |
                     AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
    }
    public void disableEvents() {
        disableEvents(AWTEvent.HIERARCHY_EVENT_MASK |
                      AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
    }
}
