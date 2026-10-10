/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug     8393551
 * @summary Do not write invalid QTable
 */

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.plugins.jpeg.JPEGQTable;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JpegQTableMetadataTest {
    public static void main(String[] args) throws Exception {
        BufferedImage img = new BufferedImage(8, 1, BufferedImage.TYPE_3BYTE_BGR);
        ImageWriter w = ImageIO.getImageWritersByFormatName("JPEG").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(img);
        IIOMetadata meta = w.getDefaultImageMetadata(type, p);
        String fmt = meta.getNativeMetadataFormatName();
        Node root = meta.getAsTree(fmt);

        Node markerSeq = null;
        NodeList kids = root.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i).getNodeName().equals("markerSequence")) {
                markerSeq = kids.item(i);
            }
        }
        if (markerSeq == null) throw new RuntimeException("no markerSequence");

        // Replace existing dqt tables
        for (int i = 0; i < markerSeq.getChildNodes().getLength(); i++) {
            int id = 0;
            Node c = markerSeq.getChildNodes().item(i);
            if (c.getNodeName().equals("dqt")) {
                markerSeq.removeChild(c);
                int[] qTableData = new int[64];
                qTableData[1] = -1;
                qTableData[2] = 256;
                JPEGQTable qTable = new JPEGQTable(qTableData);
                IIOMetadataNode dqt = new IIOMetadataNode("dqt");
                IIOMetadataNode qt = new IIOMetadataNode("dqtable");
                qt.setAttribute("elementPrecision", "0");
                qt.setAttribute("qtableId", String.valueOf(id));
                qt.setUserObject(qTable);
                dqt.appendChild(qt);
                markerSeq.appendChild(dqt);
                id++;
            }
        }
        meta.setFromTree(fmt, root);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        w.setOutput(ios);
        w.write(null, new IIOImage(img, null, meta), p);
        ios.flush();

        ImageReader r = ImageIO.getImageReadersByFormatName("JPEG").next();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ImageInputStream iis = ImageIO.createImageInputStream(bais);
        r.setInput(iis);
        meta = r.getImageMetadata(0); // exception here if not validated during write.

        // The rest is just to view the corrected data.
        root = meta.getAsTree(fmt);
        kids = root.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i).getNodeName().equals("markerSequence")) {
                markerSeq = kids.item(i);
                for (int j = 0; j < markerSeq.getChildNodes().getLength(); j++) {
                    Node c = markerSeq.getChildNodes().item(j);
                    if (c.getNodeName().equals("dqt")) {
                        for (int k = 0; k < c.getChildNodes().getLength(); k++) {
                            IIOMetadataNode dqtNode = (IIOMetadataNode)(c.getChildNodes().item(k));
                            JPEGQTable qTable = (JPEGQTable)dqtNode.getUserObject();
                            System.out.println(qTable);
                        }
                    }
                }
            }
        }
    }
}
