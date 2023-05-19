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

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;

/*
  @test
  @bug 4462285
  @summary tests that DropTarget.addNotify doesn't throw NPE if peer hierarchy
           is incomplete
  @key headful
  @run main DropTargetAddNotifyNPETest
*/

public class DropTargetAddNotifyNPETest {

    volatile Component component1;
    volatile Component component2;
    volatile Frame frame;
    volatile DropTargetListener dtListener;
    volatile DropTarget dropTarget1;
    volatile DropTarget dropTarget2;

    public static void main(String[] args) throws Exception {
        DropTargetAddNotifyNPETest test = new DropTargetAddNotifyNPETest();
        EventQueue.invokeAndWait(() -> {
            test.init();
            if (test.frame != null) {
                test.frame.dispose();
            }
        });
    }

    public void init() {
        component1 = new LWComponent();
        component2 = new LWComponent();
        frame = new Frame("DropTargetAddNotifyNPETest");
        dtListener = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                dtde.rejectDrop();
            }
        };
        dropTarget1 = new DropTarget(component1, dtListener);
        dropTarget2 = new DropTarget(component2, dtListener);

        frame.add(component2);
        component1.addNotify();
        component2.addNotify();
    }
}

class LWComponent extends Component {}
