/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4924516
  @summary Verifies that SHOWING_CHANGED event is propagated to \
           HierarchyListeners then toolkit enabled
  @key headful
*/


import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class ShowingChangedEventTest
        implements AWTEventListener, HierarchyListener{
    private boolean eventRegisteredOnButton = false;

    private final JFrame frame = new JFrame("ShowingChangedEventTest");
    private final JPanel panel = new JPanel();
    private final JButton button = new JButton();


    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            ShowingChangedEventTest showingChangedEventTest
                    = new ShowingChangedEventTest();

            try {
                showingChangedEventTest.start();
            } finally {
                showingChangedEventTest.frame.dispose();
            }
        });
    }

    public void start ()  {
        frame.getContentPane().add(panel);
        panel.add(button);

        frame.pack();
        frame.setVisible(true);

        Toolkit.getDefaultToolkit()
                .addAWTEventListener(this, AWTEvent.HIERARCHY_EVENT_MASK);

        button.addHierarchyListener(this);
        panel.setVisible(false);

        if (!eventRegisteredOnButton){
            throw new RuntimeException("Event wasn't registered on Button.");
        }
    }

    @Override
    public void eventDispatched(AWTEvent awtevt) {
        if (awtevt instanceof HierarchyEvent) {
            HierarchyEvent hevt = (HierarchyEvent) awtevt;
            if (hevt != null && (hevt.getChangeFlags()
                    & HierarchyEvent.SHOWING_CHANGED) != 0) {
                System.out.println("Hierarchy event was received on Toolkit. "
                        + "SHOWING_CHANGED for "
                        + hevt.getChanged().getClass().getName());
            }
        }
    }

    @Override
    public void hierarchyChanged(HierarchyEvent e) {
        if ((HierarchyEvent.SHOWING_CHANGED & e.getChangeFlags()) != 0) {
            System.out.println("Hierarchy event was received on Button. "
                    + "SHOWING_CHANGED for "
                    + e.getChanged().getClass().getName());
        }
        eventRegisteredOnButton = true;
    }
}
