/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @key headful
  @bug 4187912
  @summary Test that some incorrectly written DnD code cannot hang the app
  @run main NotReallySerializableTest
*/

import java.awt.Button;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceContext;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;


public class NotReallySerializableTest {
    public static void main(String[] args) throws IOException {
        Toolkit tk = Toolkit.getDefaultToolkit();

        DragGestureRecognizer dgr = tk.createDragGestureRecognizer
                (java.awt.dnd.MouseDragGestureRecognizer.class,
                        DragSource.getDefaultDragSource(), new Button(),
                        DnDConstants.ACTION_LINK, new TrickDragGestureListener());
        DragGestureEvent dge = new DragGestureEvent
                (dgr, DnDConstants.ACTION_LINK, new Point(0, 0),
                        new TrickList());
        DragSourceContext dsc = new DragSourceContext(dge,
                Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR),
                null, null, new TrickTransferable(),
                new TrickDragSourceListener());
        DropTarget dt = new DropTarget(new Button(),
                new TrickDropTargetListener());

        ObjectOutputStream stream = new ObjectOutputStream
                (new OutputStream() {
                    public void write(int b) {}
                });

        stream.writeObject(dgr);
        stream.writeObject(dge);
        stream.writeObject(dsc);
        stream.writeObject(dt);

        System.out.println("test passed");
    }
}

class TrickList extends ArrayList implements Serializable {
    Object trick = new Object();

    TrickList() {
        add(trick);
    }
}

class TrickDragGestureListener implements DragGestureListener, Serializable {
    Object trick = new Object();

    public void dragGestureRecognized(DragGestureEvent dge) {}
}

class TrickTransferable extends StringSelection implements Serializable {
    Object trick = new Object();

    TrickTransferable() {
        super("");
    }
}

class TrickDragSourceListener extends DragSourceAdapter
    implements Serializable
{
    Object trick = new Object();
}

class TrickDropTargetListener extends DropTargetAdapter
    implements Serializable
{
    Object trick = new Object();

    public void drop(DropTargetDropEvent dtde) {}
}
