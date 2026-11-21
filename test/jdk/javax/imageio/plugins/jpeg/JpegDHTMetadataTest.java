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
 * @summary Verify no crash with invalid DHT
 */

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGHuffmanTable;
import javax.imageio.stream.ImageOutputStream;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JpegDHTMetadataTest {
    public static void main(String[] args) throws Exception {
      test(0); // dc
      test(1); // ac
    }

    static void test(int acORdc) throws Exception {

        String name = (acORdc == 1) ? "AC" : "DC";
        String attr = (acORdc == 1) ? "1" : "0";

        BufferedImage img = new BufferedImage(8, 1, BufferedImage.TYPE_BYTE_GRAY);
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

        // Remove default DHT metadata and install a partial DHT: DC-only or AC-only.
        for (int i = markerSeq.getChildNodes().getLength() - 1; i >= 0; i--) {
            Node c = markerSeq.getChildNodes().item(i);
            if (c.getNodeName().equals("dht")) {
                markerSeq.removeChild(c);
            }
        }

        IIOMetadataNode dht = new IIOMetadataNode("dht");
        IIOMetadataNode ht = new IIOMetadataNode("dhtable");
        ht.setAttribute("class", attr);
        ht.setAttribute("htableId", "0");
        short[] lengths = new short[16];
        lengths[0] = 1;
        short[] values = new short[] { 0 };
        ht.setUserObject(new JPEGHuffmanTable(lengths, values));
        dht.appendChild(ht);
        markerSeq.appendChild(dht);

        // This accepts the partial DHT metadata without throwing an exception.
        meta.setFromTree(fmt, root);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        w.setOutput(ios);
        System.out.println("write metadata-only partial DHT class = " + name);
        w.write(null, new IIOImage(img, null, meta), p);
        ios.flush();
        System.out.println("ok bytes=" + baos.size());
    }
}
