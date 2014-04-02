/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.apple.laf;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.*;
import javax.swing.plaf.*;

import apple.laf.JRSUIConstants.Size;
import apple.laf.*;

import com.apple.laf.AquaUtilControlSize.*;
import com.apple.laf.AquaUtils.RecyclableSingleton;

public class AquaIcon {
    interface InvertableIcon extends Icon {
        public Icon getInvertedIcon();
    }

    static UIResource getIconFor(final JRSUIControlSpec spec, final int width, final int height) {
        return new CachableJRSUIIcon(width, height) {
            public void initIconPainter(final AquaPainter<JRSUIState> painter) {
                spec.initIconPainter(painter);
            }
        };
    }

    // converts an object that is an icon into an image so we can hand it off to AppKit
    public static Image getImageForIcon(final Icon i) {
        if (i instanceof ImageIcon) return ((ImageIcon)i).getImage();

        final int w = i.getIconWidth();
        final int h = i.getIconHeight();

        if (w <= 0 || h <= 0) return null;

        // This could be any kind of icon, so we need to make a buffer for it, draw it and then pass the new image off to appkit.
        final BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final Graphics g = image.getGraphics();
        i.paintIcon(null, g, 0, 0);
        g.dispose();
        return image;
    }

    public interface JRSUIControlSpec {
        public void initIconPainter(final AquaPainter<? extends JRSUIState> painter);
    }

    static abstract class JRSUIIcon implements Icon, UIResource {
        protected final AquaPainter<JRSUIState> painter = AquaPainter.create(JRSUIState.getInstance());

        public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
            painter.paint(g, c, x, y, getIconWidth(), getIconHeight());
        }
    }

    static abstract class DynamicallySizingJRSUIIcon extends JRSUIIcon {
        protected final SizeDescriptor sizeDescriptor;
        protected SizeVariant sizeVariant;

        public DynamicallySizingJRSUIIcon(final SizeDescriptor sizeDescriptor) {
            this.sizeDescriptor = sizeDescriptor;
            this.sizeVariant = sizeDescriptor.regular;
            initJRSUIState();
        }

        public abstract void initJRSUIState();

        public int getIconHeight() {
            return sizeVariant == null ? 0 : sizeVariant.h;
        }

        public int getIconWidth() {
            return sizeVariant == null ? 0 : sizeVariant.w;
        }

        public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
            final Size size = c instanceof JComponent ? AquaUtilControlSize.getUserSizeFrom((JComponent)c) : Size.REGULAR;
            sizeVariant = sizeDescriptor.get(size);
            painter.state.set(size);
            super.paintIcon(c, g, x, y);
        }
    }

    static abstract class CachingScalingIcon implements Icon, UIResource {
        int width;
        int height;
        Image image;

        public CachingScalingIcon(final int width, final int height) {
            this.width = width;
            this.height = height;
        }

        void setSize(final int width, final int height) {
            this.width = width;
            this.height = height;
            this.image = null;
        }

        Image getImage() {
            if (image != null) return image;

            if (!GraphicsEnvironment.isHeadless()) {
                image = getOptimizedImage();
            }

            return image;
        }

        private Image getOptimizedImage() {
            final Image img = createImage();
            // TODO: no RuntimeOptions for now
            //if (RuntimeOptions.getRenderer(null) != RuntimeOptions.Sun) return img;
            return getProgressiveOptimizedImage(img, getIconWidth(), getIconHeight());
        }

        static Image getProgressiveOptimizedImage(final Image img, final int w, final int h) {
            if (img == null) return null;

            final int halfImgW = img.getWidth(null) / 2;
            final int halfImgH = img.getHeight(null) / 2;
            if (w * 2 > halfImgW && h * 2 > halfImgH) return img;

            final BufferedImage halfImage = new BufferedImage(halfImgW, halfImgH, BufferedImage.TYPE_INT_ARGB);
            final Graphics g = halfImage.getGraphics();
            ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, halfImgW, halfImgH, null);
            g.dispose();

            return getProgressiveOptimizedImage(halfImage, w, h);
        }

        abstract Image createImage();

        public boolean hasIconRef() {
            return getImage() != null;
        }

        public void paintIcon(final Component c, Graphics g, final int x, final int y) {
            g = g.create();

            if (g instanceof Graphics2D) {
                // improves icon rendering quality in Quartz
                ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            }

            final Image myImage = getImage();
            if (myImage != null) {
                g.drawImage(myImage, x, y, getIconWidth(), getIconHeight(), null);
            }

            g.dispose();
        }

        public int getIconWidth() {
            return width;
        }

        public int getIconHeight() {
            return height;
        }

    }

    static abstract class CachableJRSUIIcon extends CachingScalingIcon implements UIResource {
        public CachableJRSUIIcon(final int width, final int height) {
            super(width, height);
        }

        Image createImage() {
            final AquaPainter<JRSUIState> painter = AquaPainter.create(JRSUIState.getInstance());
            initIconPainter(painter);

            final BufferedImage img = new BufferedImage(getIconWidth(), getIconHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
            final Graphics g = img.getGraphics();
            g.setClip(new Rectangle(0, 0, getIconWidth(), getIconHeight()));
            painter.paint(g, null, 0, 0, getIconWidth(), getIconHeight());
            g.dispose();
            return img;
        }

        public abstract void initIconPainter(final AquaPainter<JRSUIState> painter);
    }

    static class FileIcon extends CachingScalingIcon {
        final File file;

        public FileIcon(final File file, final int width, final int height) {
            super(width, height);
            this.file = file;
        }

        public FileIcon(final File file) {
            this(file, 16, 16);
        }

        Image createImage() {
            return AquaUtils.getCImageCreator().createImageOfFile(file.getAbsolutePath(), getIconWidth(), getIconHeight());
        }
    }

    static class SystemIconSingleton extends RecyclableSingleton<SystemIcon> {
        final String selector;

        public SystemIconSingleton(String selector) {
            this.selector = selector;
        }

        @Override
        protected SystemIcon getInstance() {
            return new SystemIcon(selector);
        }
    }

    static class SystemIconUIResourceSingleton extends RecyclableSingleton<IconUIResource> {
        final String selector;

        public SystemIconUIResourceSingleton(String selector) {
            this.selector = selector;
        }

        @Override
        protected IconUIResource getInstance() {
            return new IconUIResource(new SystemIcon(selector));
        }
    }

    static class SystemIcon extends CachingScalingIcon {
        private static final SystemIconUIResourceSingleton folderIcon = new SystemIconUIResourceSingleton("fldr");
        static IconUIResource getFolderIconUIResource() { return folderIcon.get(); }

        private static final SystemIconUIResourceSingleton openFolderIcon = new SystemIconUIResourceSingleton("ofld");
        static IconUIResource getOpenFolderIconUIResource() { return openFolderIcon.get(); }

        private static final SystemIconUIResourceSingleton desktopIcon = new SystemIconUIResourceSingleton("desk");
        static IconUIResource getDesktopIconUIResource() { return desktopIcon.get(); }

        private static final SystemIconUIResourceSingleton computerIcon = new SystemIconUIResourceSingleton("FNDR");
        static IconUIResource getComputerIconUIResource() { return computerIcon.get(); }

        private static final SystemIconUIResourceSingleton documentIcon = new SystemIconUIResourceSingleton("docu");
        static IconUIResource getDocumentIconUIResource() { return documentIcon.get(); }

        private static final SystemIconUIResourceSingleton hardDriveIcon = new SystemIconUIResourceSingleton("hdsk");
        static IconUIResource getHardDriveIconUIResource() { return hardDriveIcon.get(); }

        private static final SystemIconUIResourceSingleton floppyIcon = new SystemIconUIResourceSingleton("flpy");
        static IconUIResource getFloppyIconUIResource() { return floppyIcon.get(); }

        //private static final SystemIconUIResourceSingleton noteIcon = new SystemIconUIResourceSingleton("note");
        //static IconUIResource getNoteIconUIResource() { return noteIcon.get(); }

        private static final SystemIconSingleton caut = new SystemIconSingleton("caut");
        static SystemIcon getCautionIcon() { return caut.get(); }

        private static final SystemIconSingleton stop = new SystemIconSingleton("stop");
        static SystemIcon getStopIcon() { return stop.get(); }

        final String selector;

        public SystemIcon(final String iconSelector, final int width, final int height) {
            super(width, height);
            selector = iconSelector;
        }

        public SystemIcon(final String iconSelector) {
            this(iconSelector, 16, 16);
        }

        Image createImage() {
            return AquaUtils.getCImageCreator().createSystemImageFromSelector(
                    selector, getIconWidth(), getIconHeight());
        }
    }
}
