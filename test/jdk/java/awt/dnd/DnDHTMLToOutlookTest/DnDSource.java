/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/*
 *  AWT Button is a DragSource and also a transferable object
 */

class DnDSource extends Button implements Transferable,
                                          DragGestureListener,
                                          DragSourceListener {
    private DataFlavor m_df;
    private transient int m_dropAction;
    private ByteArrayInputStream m_data = null;

    static byte[] getContent(InputStream is) {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        try {
            int read;
            while (-1 != (read = is.read())){
                tmp.write(read);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
        return tmp.toByteArray();
    }

    DnDSource(String label) {
        super(label);
        setBackground(Color.yellow);
        setForeground(Color.blue);
        setSize(200, 120);

        try {
            m_df = new DataFlavor("text/html; Class=" + InputStream.class.getName() + "; charset=UTF-8");
        } catch(Exception e) {
            e.printStackTrace();
        }

        DragSource dragSource = new DragSource();
        dragSource.createDefaultDragGestureRecognizer(
                this,
                DnDConstants.ACTION_COPY_OR_MOVE,
                this
        );
        dragSource.addDragSourceListener(this);

        String dir = System.getProperty("test.src");
        String sep = System.getProperty("file.separator");
        if (dir == null) {
            dir = ".";
        }

        try {
            m_data = new ByteArrayInputStream(Files.readAllBytes(
                    Paths.get(dir, "DnDSource.html")));
            m_data.mark(m_data.available());
            addActionListener(
                new ActionListener(){
                    public void actionPerformed(ActionEvent ae){
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                               .setContents( DnDSource.this, null);
                    }
                }
            );
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * a Drag gesture has been recognized
     */

    public void dragGestureRecognized(DragGestureEvent dge) {
        System.err.println("starting Drag");
        try {
            dge.startDrag(null, this, this);
        } catch (InvalidDnDOperationException e) {
            e.printStackTrace();
        }
    }

    /**
     * as the hotspot enters a platform dependent drop site
     */

    public void dragEnter(DragSourceDragEvent dsde) {
        System.err.println("[Source] dragEnter");
    }

    /**
     * as the hotspot moves over a platform dependent drop site
     */

    public void dragOver(DragSourceDragEvent dsde) {
        System.err.println("[Source] dragOver");
        m_dropAction = dsde.getDropAction();
        System.out.println("m_dropAction = " + m_dropAction);
    }

    /**
     * as the operation changes
     */

    public void dragGestureChanged(DragSourceDragEvent dsde) {
        System.err.println("[Source] dragGestureChanged");
        m_dropAction = dsde.getDropAction();
        System.out.println("m_dropAction = " + m_dropAction);
    }

    /**
     * as the hotspot exits a platform dependent drop site
     */

    public void dragExit(DragSourceEvent dsde) {
        System.err.println("[Source] dragExit");
    }

    /**
     * as the operation completes
     */

    public void dragDropEnd(DragSourceDropEvent dsde) {
        System.err.println("[Source] dragDropEnd");
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
        System.err.println("[Source] dropActionChanged");
        m_dropAction = dsde.getDropAction();
        System.out.println("m_dropAction = " + m_dropAction);
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {m_df};
    }

    public boolean isDataFlavorSupported(DataFlavor sdf) {
        System.err.println("[Source] isDataFlavorSupported" + m_df.equals(sdf));
        return m_df.equals(sdf);
    }

    public Object getTransferData(DataFlavor tdf) throws UnsupportedFlavorException {
        if (!m_df.equals(tdf)) {
            throw new UnsupportedFlavorException(tdf);
        }
        System.err.println("[Source] Ok");
        m_data.reset();
        return m_data;
    }
}
