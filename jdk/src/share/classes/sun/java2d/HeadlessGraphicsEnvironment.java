/*
 * Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d;

import java.awt.GraphicsEnvironment;
import java.awt.GraphicsDevice;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.image.BufferedImage;
import java.awt.Font;
import java.text.AttributedCharacterIterator;
import java.awt.print.PrinterJob;
import java.util.Map;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;
import java.util.StringTokenizer;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.io.IOException;
import java.io.FilenameFilter;
import java.io.File;
import java.util.NoSuchElementException;
import sun.awt.FontConfiguration;
import java.util.TreeMap;
import java.util.Set;
import java.awt.font.TextAttribute;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.Properties;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * Headless decorator implementation of a SunGraphicsEnvironment
 */

public class HeadlessGraphicsEnvironment extends GraphicsEnvironment
    implements FontSupport {

    private GraphicsEnvironment ge;
    private FontSupport fontSupport;

    public HeadlessGraphicsEnvironment(GraphicsEnvironment ge) {
        this.ge = ge;
        if (ge instanceof FontSupport) {
            fontSupport = (FontSupport)ge;
        }
    }

    public GraphicsDevice[] getScreenDevices()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public GraphicsDevice getDefaultScreenDevice()
        throws HeadlessException {
        throw new HeadlessException();
    }

    public Point getCenterPoint() throws HeadlessException {
        throw new HeadlessException();
    }

    public Rectangle getMaximumWindowBounds() throws HeadlessException {
        throw new HeadlessException();
    }

    public Graphics2D createGraphics(BufferedImage img) {
        return ge.createGraphics(img); }

    public Font[] getAllFonts() { return ge.getAllFonts(); }

    public String[] getAvailableFontFamilyNames() {
        return ge.getAvailableFontFamilyNames(); }

    public String[] getAvailableFontFamilyNames(Locale l) {
        return ge.getAvailableFontFamilyNames(l); }

    public FontConfiguration getFontConfiguration() {
        if (fontSupport != null) {
            return fontSupport.getFontConfiguration();
        }
        return null;
    }

    /* Used by FontManager : internal API */
    public GraphicsEnvironment getSunGraphicsEnvironment() {
        return ge;
    }
}
