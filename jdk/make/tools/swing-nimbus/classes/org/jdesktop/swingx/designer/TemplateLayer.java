/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package org.jdesktop.swingx.designer;

import org.jdesktop.swingx.designer.effects.Effect;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;

/**
 * TemplateLayer
 *
 * @author Created by Jasper Potts (Jul 2, 2007)
 */
public class TemplateLayer extends Layer {

    private String fileName;
    private transient SoftReference<BufferedImage> imgRef = null;

    public TemplateLayer() {
        type = LayerType.template;
    }

    public TemplateLayer(String fileName, BufferedImage templateImage) {
        super("Template");
        this.fileName = fileName;
        type = LayerType.template;
        if (templateImage != null) {
            imgRef = new SoftReference<BufferedImage>(templateImage);
        }
    }

    // =================================================================================================================
    // Methods

    public String getName() {
        return super.getName();
    }

    /**
     * template layers are always locked
     *
     * @return <code>true</code>
     */
    public boolean isLocked() {
        return true;
    }

    public void add(SimpleShape shape) {
        throw new IllegalStateException("Template layers can't contain shapes");
    }

    public void addEffect(Effect effect) {
        throw new IllegalStateException("Template layers can't contain effects");
    }

    public void addLayer(int i, Layer layer) {
        throw new IllegalStateException("Template layers can't contain sub layers");
    }

    public void addLayer(Layer layer) {
        throw new IllegalStateException("Template layers can't contain sub layers");
    }

    public void paint(Graphics2D g2, double pixelSize) {
        if (isVisible()) {
            BufferedImage img = getTemplateImage();
            if (img != null) g2.drawImage(img, 0, 0, null);
        }
    }


    public Image getBuffer(GraphicsConfiguration graphicsConfiguration) {
        return getTemplateImage();
    }

    public BufferedImage getTemplateImage() {
        BufferedImage img = null;
        if (imgRef == null || (img = imgRef.get()) == null) {

            // can not access canvas
            final File templateImgFile = new File(getCanvas().getTemplatesDir(), fileName);
            System.out.println("templateImgFile = " + templateImgFile.getAbsolutePath());
            System.out.println("templateImgFile.exists = " + templateImgFile.exists());
            try {
                img = ImageIO.read(templateImgFile);
                imgRef = new SoftReference<BufferedImage>(img);
            } catch (IOException e) {
                e.printStackTrace();
                // create error image
                img = new BufferedImage(getCanvas().getSize().width, getCanvas().getSize().height,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = img.createGraphics();
                g2.setColor(Color.RED);
                g2.fillRect(0, 0, img.getWidth(), img.getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(g2.getFont().deriveFont(8f));
                FontMetrics fontMetrics = g2.getFontMetrics();
                Rectangle2D stringBounds = fontMetrics.getStringBounds("Missing Image", g2);
                int offsetX = (int) ((img.getWidth() - stringBounds.getWidth()) / 2d);
                int offsetY = (int) (((img.getHeight() - stringBounds.getHeight()) / 2d) - stringBounds.getY());
                g2.drawString("Missing Image", offsetX, offsetY);
                g2.dispose();
                imgRef = new SoftReference<BufferedImage>(img);
            }
        }
        return img;
    }
}
