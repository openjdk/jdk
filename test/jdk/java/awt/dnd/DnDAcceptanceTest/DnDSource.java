/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Color;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.MouseDragGestureRecognizer;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

class DnDSource extends Button implements Transferable,
                                          DragGestureListener,
                                          DragSourceListener {
    private DataFlavor df;
    private transient int dropAction;

    DnDSource(String label) {
        super(label);
        Toolkit.getDefaultToolkit().createDragGestureRecognizer(MouseDragGestureRecognizer.class,
                                                                DragSource.getDefaultDragSource(),
                                                                this, DnDConstants.ACTION_COPY, this);
        setBackground(Color.yellow);
        setForeground(Color.blue);
        df = new DataFlavor(DnDSource.class, "DnDSource");
    }

    public void dragGestureRecognized(DragGestureEvent dge) {
        System.err.println("starting Drag");
        try {
            dge.startDrag(null, this, this);
        } catch (InvalidDnDOperationException e) {
            e.printStackTrace();
        }
    }

    public void dragEnter(DragSourceDragEvent dsde) {
        System.err.println("[Source] dragEnter");
        dsde.getDragSourceContext().setCursor(DragSource.DefaultCopyDrop);
    }

    public void dragOver(DragSourceDragEvent dsde) {
        System.err.println("[Source] dragOver");
        dropAction = dsde.getDropAction();
        System.out.println("dropAction = " + dropAction);
    }

    public void dragGestureChanged(DragSourceDragEvent dsde) {
        System.err.println("[Source] dragGestureChanged");
        dropAction = dsde.getDropAction();
        System.out.println("dropAction = " + dropAction);
    }

    public void dragExit(DragSourceEvent dsde) {
        System.err.println("[Source] dragExit");
        dsde.getDragSourceContext().setCursor(null);
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
        System.err.println("[Source] dragDropEnd");
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
        System.err.println("[Source] dropActionChanged");
        dropAction = dsde.getDropAction();
        System.out.println("dropAction = " + dropAction);
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {df};
    }

    public boolean isDataFlavorSupported(DataFlavor sdf) {
        return df.equals(sdf);
    }

    public Object getTransferData(DataFlavor tdf) throws UnsupportedFlavorException, IOException {

        Object copy = null;

        if (!df.equals(tdf)) {
            throw new UnsupportedFlavorException(tdf);
        }
        Container parent = getParent();
        switch (dropAction) {
            case DnDConstants.ACTION_COPY:
                try {
                    copy = this.clone();
                } catch (CloneNotSupportedException e) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos  = new ObjectOutputStream(baos);

                    oos.writeObject(this);
                    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                    ObjectInputStream ois = new ObjectInputStream(bais);
                    try {
                        copy = ois.readObject();
                    } catch (ClassNotFoundException cnfe) {
                        // do nothing
                    }
                }

                parent.add(this);
                return copy;

            case DnDConstants.ACTION_MOVE:
                synchronized(this) {
                    if (parent != null) parent.remove(this);
                }
                return this;

            case DnDConstants.ACTION_LINK:
                return this;

            default:
                //throw new IOException("bad operation");
                return this; // workaround for: 4135456  getDropAction() always return 0
        }
    }
}
