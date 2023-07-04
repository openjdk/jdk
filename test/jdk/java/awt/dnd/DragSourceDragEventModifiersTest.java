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
  @bug 4924527
  @summary tests DragSourceDragEvent.getGestureModifiers[Ex]() \
  for valid and invalid modifiers
  @key headful
*/

import java.awt.Button;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceContext;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.InputEvent;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;

public class DragSourceDragEventModifiersTest  {
    boolean failed;

    static class DummyImage extends Image {
        public DummyImage() {}
        public int getWidth(ImageObserver observer) {return 0;}
        public int getHeight(ImageObserver observer){return 0;}
        public ImageProducer getSource() {return null;}
        public Graphics getGraphics() {return null;}
        public void flush() {}

        public Object getProperty(String name, ImageObserver observer) {
            return null;
        }
    }

    static class DummyDGRecognizer extends DragGestureRecognizer {
        private final DragSource dragSource;
        private final Component component;

        public DummyDGRecognizer(DragSource ds,Component c) {
            super(ds,c);
            component = c;
            dragSource = ds;
        }

        public void addDragGestureListener(DragGestureListener dgl) {}
        public void appendEvent(InputEvent awtie) {}
        public void fireDragGestureRecognized(int dragAction, Point p) {}
        public Component getComponent() {return component;}
        public DragSource getDragSource() {return dragSource;}
        public int getSourceActions() {return DnDConstants.ACTION_COPY_OR_MOVE;}
        public InputEvent getTriggerEvent() {return null;}
        public void registerListeners() {}
        public void removeDragGestureListener(DragGestureListener dgl) {}
        public void resetRecognizer() {}
        public void setComponent(Component c) {}
        public void setSourceActions(int actions) {}
        public void unregisterListeners() {}
    }


    DragSource ds = new DragSource();

    int[] actions = {
        DnDConstants.ACTION_NONE,
        DnDConstants.ACTION_COPY,
        DnDConstants.ACTION_MOVE,
        DnDConstants.ACTION_COPY_OR_MOVE,
        DnDConstants.ACTION_LINK,
        DnDConstants.ACTION_REFERENCE
    };

    Cursor[] cursors = {
        DragSource.DefaultCopyDrop,
        DragSource.DefaultMoveDrop,
        DragSource.DefaultLinkDrop,
        DragSource.DefaultCopyNoDrop,
        DragSource.DefaultMoveNoDrop,
        DragSource.DefaultLinkNoDrop
    };

    DummyImage image = new DummyImage();

    Point point = new Point(0,0);

    Transferable transferable = new Transferable() {
        public DataFlavor[] getTransferDataFlavors() {return null;}
        public boolean isDataFlavorSupported(DataFlavor flavor) {return false;}
        public Object getTransferData(DataFlavor flavor) {return null;}
    };

    DragSourceListener dsl = new DragSourceListener() {
        public void dragEnter(DragSourceDragEvent dsde) {}
        public void dragOver(DragSourceDragEvent dsde) {}
        public void dropActionChanged(DragSourceDragEvent dsde) {}
        public void dragExit(DragSourceEvent dsde) {}
        public void dragDropEnd(DragSourceDropEvent dsde) {}
    };
    /*
    int modifiers[] = {
        InputEvent.ALT_GRAPH_MASK,
        InputEvent.ALT_MASK,
        InputEvent.BUTTON1_MASK,
        InputEvent.BUTTON2_MASK,
        InputEvent.BUTTON3_MASK,
        InputEvent.CTRL_MASK,
        InputEvent.META_MASK,
        InputEvent.SHIFT_MASK
    };

    int exModifiers[] = {
        InputEvent.SHIFT_DOWN_MASK,
        InputEvent.ALT_DOWN_MASK,
        InputEvent.BUTTON1_DOWN_MASK,
        InputEvent.BUTTON2_DOWN_MASK,
        InputEvent.BUTTON3_DOWN_MASK,
        InputEvent.CTRL_DOWN_MASK,
        InputEvent.META_DOWN_MASK,
        InputEvent.ALT_GRAPH_DOWN_MASK,
    };
    */
    DragGestureEvent getDragGestureEvent() {
         java.util.Vector vector = new java.util.Vector();
         vector.add(new java.lang.Integer(0));
         return new DragGestureEvent(new DummyDGRecognizer(ds, new Button()),
                                     actions[1],
                                     new java.awt.Point(0,0),
                                     vector);
    }
    DragGestureEvent dge = getDragGestureEvent();

    DragSourceContext dsc = new DragSourceContext(dge,
                                                  cursors[0],
                                                  image,
                                                  point,
                                                  transferable,
                                                  dsl);

    public static void main(String[] args) {
        new DragSourceDragEventModifiersTest().start();
    }

    public void start() {
        try {
            // valid modifiers:

            check(InputEvent.BUTTON1_MASK, InputEvent.BUTTON1_MASK,
                    InputEvent.BUTTON1_DOWN_MASK);

            check(InputEvent.BUTTON1_MASK | InputEvent.SHIFT_MASK,
                    InputEvent.BUTTON1_MASK | InputEvent.SHIFT_MASK,
                    InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

            check(InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON1_MASK,
                    InputEvent.BUTTON1_DOWN_MASK);

            check(InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                    InputEvent.BUTTON1_MASK | InputEvent.SHIFT_MASK,
                    InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

            // invalid modifiers:

            int invalidMods = 0;
            check(invalidMods, invalidMods, invalidMods);

            invalidMods = InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_MASK;
            check(invalidMods, invalidMods, invalidMods);

            invalidMods = (InputEvent.ALT_GRAPH_DOWN_MASK << 1);
            check(invalidMods, invalidMods, invalidMods);

            invalidMods = InputEvent.BUTTON1_DOWN_MASK
                    | (InputEvent.ALT_GRAPH_DOWN_MASK << 1);
            check(invalidMods, invalidMods, invalidMods);

            invalidMods = InputEvent.BUTTON1_MASK
                    | (InputEvent.ALT_GRAPH_DOWN_MASK << 1);
            check(invalidMods, invalidMods, invalidMods);

            invalidMods = InputEvent.BUTTON1_DOWN_MASK
                    | InputEvent.SHIFT_MASK
                    | (InputEvent.ALT_GRAPH_DOWN_MASK << 1);
            check(invalidMods, invalidMods, invalidMods);

        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (failed) {
            throw new RuntimeException("wrong behavior of " +
                    "DragSourceDragEvent.getModifiers[Ex]()," +
                    " see error messages above");
        }

        System.err.println("test passed!");
    }

    void check(int mods, int expectedMods, int expectedExMods) {
        System.err.println("testing DragSourceDragEvent " +
                "created with 1st constructor");
        System.err.println("modifiers passed to the constructor: "
                + Integer.toBinaryString(mods));
        verify(create1(mods), expectedMods, expectedExMods);

        System.err.println("testing DragSourceDragEvent " +
                "created with 2nd constructor");
        System.err.println("modifiers passed to the constructor: "
                + Integer.toBinaryString(mods));
        verify(create2(mods), expectedMods, expectedExMods);
    }

    void verify(DragSourceDragEvent dsde, int expectedMods, int expectedExMods) {
        if (dsde.getGestureModifiers() != expectedMods) {
            failed = true;
            System.err.print("ERROR: ");
        }
        System.err.println("getGestureModifiers() returned: "
                + Integer.toBinaryString(dsde.getGestureModifiers()) +
                " ; expected: " + Integer.toBinaryString(expectedMods));

        if (dsde.getGestureModifiersEx() != expectedExMods) {
            failed = true;
            System.err.print("ERROR: ");
        }
        System.err.println("getGestureModifiersEx() returned: "
                + Integer.toBinaryString(dsde.getGestureModifiersEx()) +
                " ; expected: " + Integer.toBinaryString(expectedExMods));

        System.err.println();
    }

    DragSourceDragEvent create1(int mods) {
        return new DragSourceDragEvent(dsc, actions[0], actions[0], mods);
    }

    DragSourceDragEvent create2(int mods) {
        return new DragSourceDragEvent(dsc, actions[0], actions[0], mods, 0, 0);
    }
}
