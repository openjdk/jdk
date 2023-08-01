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
  @summary we should create Component-, Container- and HierarchyEvents if
  appropriate AWTEventListener added on Toolkit
  @key headful
*/

import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;
import java.lang.reflect.InvocationTargetException;

public class ToolkitListenerTest implements AWTEventListener
{
    public static Frame frame;
    static boolean containerEventReceived = false;
    static boolean componentEventReceived = false;
    static boolean hierarchyEventReceived = false;
    static boolean hierarchyBoundsEventReceived = false;

    public static void main(String[] args) throws Exception {
        ToolkitListenerTest test = new ToolkitListenerTest();
        test.start();
    }
    public void start() throws Exception {
        Toolkit.getDefaultToolkit().
            addAWTEventListener(this,
                AWTEvent.COMPONENT_EVENT_MASK |
                    AWTEvent.CONTAINER_EVENT_MASK |
                    AWTEvent.HIERARCHY_EVENT_MASK |
                    AWTEvent.HIERARCHY_BOUNDS_EVENT_MASK);
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("ToolkitListenerTest");
            frame.setSize(200, 200);
            frame.add(new Button());
            frame.setBounds(100, 100, 100, 100);
        });
        try {
            Toolkit.getDefaultToolkit().getSystemEventQueue().
                invokeAndWait(new Runnable() {
                    public void run() {}
                });

            EventQueue.invokeAndWait(() -> {
                if (!componentEventReceived) {
                    throw new RuntimeException("Test Failed: ComponentEvent " +
                        "was not dispatched");
                }
                if (!containerEventReceived) {
                    throw new RuntimeException("Test Failed: ContainerEvent " +
                        "was not dispatched");
                }
                if (!hierarchyEventReceived) {
                    throw new RuntimeException("Test Failed: " +
                        "HierarchyEvent(HIERARCHY_CHANGED) was not dispatched");
                }
                if (!hierarchyBoundsEventReceived) {
                    throw new RuntimeException("Test Failed: " +
                        "HierarchyEvent(ANCESTOR_MOVED or ANCESTOR_RESIZED) " +
                        "was not dispatched");
                }
            });
        } catch (InterruptedException ie) {
            throw new RuntimeException("Test Failed: InterruptedException " +
                "accured.");
        } catch (InvocationTargetException ite) {
            throw new RuntimeException("Test Failed: " +
                "InvocationTargetException accured.");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public void eventDispatched(AWTEvent e) {
        System.err.println(e);
        if (e instanceof ContainerEvent) {
            containerEventReceived = true;
        } else if (e instanceof ComponentEvent) {
            componentEventReceived = true;
        } else if (e instanceof HierarchyEvent) {
            switch (e.getID()) {
                case HierarchyEvent.HIERARCHY_CHANGED:
                    hierarchyEventReceived = true;
                    break;
                case HierarchyEvent.ANCESTOR_MOVED:
                case HierarchyEvent.ANCESTOR_RESIZED:
                    hierarchyBoundsEventReceived = true;
                    break;
            }
        }
    }
}
