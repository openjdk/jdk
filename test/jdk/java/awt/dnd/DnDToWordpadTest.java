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

/*
 * @test
 * @bug 6362095
 * @summary Tests basic DnD functionality to a wordpad
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DnDToWordpadTest
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Panel;
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public class DnDToWordpadTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                The test window contains a yellow button. Click on the button
                to copy image into the clipboard or drag the image.
                Paste or drop the image over Wordpad (when the mouse
                enters the Wordpad during the drag, the application
                should change the cursor to indicate that a copy operation is
                about to happen; release the mouse button).
                An image of a red rectangle should appear inside the document.
                You should be able to repeat this operation multiple times.
                Please, click "Pass" if above conditions are true,
                otherwise click "Fail".
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(DnDToWordpadTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("DnD To WordPad Test");
        Panel mainPanel;
        Component dragSource;

        mainPanel = new Panel();
        mainPanel.setLayout(null);

        mainPanel.setBackground(Color.black);
        try {
            dragSource = new DnDSource("Drag ME!");
            mainPanel.add(dragSource);
            f.add(mainPanel);
        } catch (IOException e) {
            e.printStackTrace();
        }

        f.setSize(200, 200);
        return f;
    }
}

class DnDSource extends Button implements Transferable,
        DragGestureListener,
        DragSourceListener {
    private DataFlavor m_df;
    private transient int m_dropAction;
    private Image m_img;

    DnDSource(String label) throws IOException {
        super(label);

        setBackground(Color.yellow);
        setForeground(Color.blue);
        setSize(200, 120);

        m_df = DataFlavor.imageFlavor;

        DragSource dragSource = new DragSource();
        dragSource.createDefaultDragGestureRecognizer(
                this,
                DnDConstants.ACTION_COPY_OR_MOVE,
                this
        );
        dragSource.addDragSourceListener(this);

        // Create test gif image to drag
        Path p = Path.of(System.getProperty("test.classes", "."));
        BufferedImage bImg = new BufferedImage(79, 109, TYPE_INT_ARGB);
        Graphics2D cg = bImg.createGraphics();
        cg.setColor(Color.RED);
        cg.fillRect(0, 0, 79, 109);
        ImageIO.write(bImg, "png", new File(p + java.io.File.separator +
                "DnDSource_Red.gif"));

        m_img = Toolkit.getDefaultToolkit()
                .getImage(System.getProperty("test.classes", ".")
                + java.io.File.separator + "DnDSource_Red.gif");

        addActionListener(
                ae -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        (Transferable) DnDSource.this,
                        null
                )
        );
    }

    public void paint(Graphics g) {
        g.drawImage(m_img, 10, 10, null);
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
        return new DataFlavor[]{m_df};
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
        return m_img;
    }
}
