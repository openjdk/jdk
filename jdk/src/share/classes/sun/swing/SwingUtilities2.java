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

package sun.swing;

import java.security.*;
import java.lang.reflect.*;
import java.lang.ref.SoftReference;
import java.awt.*;
import static java.awt.RenderingHints.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.print.PrinterGraphics;
import java.text.Bidi;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultCaret;
import javax.swing.table.TableCellRenderer;
import sun.swing.PrintColorUIResource;
import sun.swing.ImageIconUIResource;
import sun.print.ProxyPrintGraphics;
import sun.awt.*;
import sun.security.action.GetPropertyAction;
import sun.security.util.SecurityConstants;
import java.io.*;
import java.util.*;
import sun.font.FontDesignMetrics;
import sun.font.FontManager;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * A collection of utility methods for Swing.
 * <p>
 * <b>WARNING:</b> While this class is public, it should not be treated as
 * public API and its API may change in incompatable ways between dot dot
 * releases and even patch releases. You should not rely on this class even
 * existing.
 *
 */
public class SwingUtilities2 {
    /**
     * The <code>AppContext</code> key for our one <code>LAFState</code>
     * instance.
     */
    public static final Object LAF_STATE_KEY =
            new StringBuffer("LookAndFeel State");

    // Most of applications use 10 or less fonts simultaneously
    private static final int STRONG_BEARING_CACHE_SIZE = 10;
    // Strong cache for the left and right side bearings
    // for STRONG_BEARING_CACHE_SIZE most recently used fonts.
    private static BearingCacheEntry[] strongBearingCache =
            new BearingCacheEntry[STRONG_BEARING_CACHE_SIZE];
    // Next index to insert an entry into the strong bearing cache
    private static int strongBearingCacheNextIndex = 0;
    // Soft cache for the left and right side bearings
    private static Set<SoftReference<BearingCacheEntry>> softBearingCache =
            new HashSet<SoftReference<BearingCacheEntry>>();

    public static final FontRenderContext DEFAULT_FRC =
        new FontRenderContext(null, false, false);

    /**
     * A JComponent client property is used to determine text aa settings.
     * To avoid having this property persist between look and feels changes
     * the value of the property is set to null in JComponent.setUI
     */
    public static final Object AA_TEXT_PROPERTY_KEY =
                          new StringBuffer("AATextInfoPropertyKey");

    /**
     * Used to tell a text component, being used as an editor for table
     * or tree, how many clicks it took to start editing.
     */
    private static final StringBuilder SKIP_CLICK_COUNT =
        new StringBuilder("skipClickCount");

    /* Presently this class assumes default fractional metrics.
     * This may need to change to emulate future platform L&Fs.
     */
    public static class AATextInfo {

        private static AATextInfo getAATextInfoFromMap(Map hints) {

            Object aaHint   = hints.get(KEY_TEXT_ANTIALIASING);
            Object contHint = hints.get(KEY_TEXT_LCD_CONTRAST);

            if (aaHint == null ||
                aaHint == VALUE_TEXT_ANTIALIAS_OFF ||
                aaHint == VALUE_TEXT_ANTIALIAS_DEFAULT) {
                return null;
            } else {
                return new AATextInfo(aaHint, (Integer)contHint);
            }
        }

        public static AATextInfo getAATextInfo(boolean lafCondition) {
            SunToolkit.setAAFontSettingsCondition(lafCondition);
            Toolkit tk = Toolkit.getDefaultToolkit();
            Object map = tk.getDesktopProperty(SunToolkit.DESKTOPFONTHINTS);
            if (map instanceof Map) {
                return getAATextInfoFromMap((Map)map);
            } else {
                return null;
            }
        }

        Object aaHint;
        Integer lcdContrastHint;
        FontRenderContext frc;

        /* These are rarely constructed objects, and only when a complete
         * UI is being updated, so the cost of the tests here is minimal
         * and saves tests elsewhere.
         * We test that the values are ones we support/expect.
         */
        public AATextInfo(Object aaHint, Integer lcdContrastHint) {
            if (aaHint == null) {
                throw new InternalError("null not allowed here");
            }
            if (aaHint == VALUE_TEXT_ANTIALIAS_OFF ||
                aaHint == VALUE_TEXT_ANTIALIAS_DEFAULT) {
                throw new InternalError("AA must be on");
            }
            this.aaHint = aaHint;
            this.lcdContrastHint = lcdContrastHint;
            this.frc = new FontRenderContext(null, aaHint,
                                             VALUE_FRACTIONALMETRICS_DEFAULT);
        }
    }

    /**
     * Key used in client properties used to indicate that the
     * <code>ComponentUI</code> of the JComponent instance should be returned.
     */
    public static final Object COMPONENT_UI_PROPERTY_KEY =
                            new StringBuffer("ComponentUIPropertyKey");

    /** Client Property key for the text maximal offsets for BasicMenuItemUI */
    public static final StringUIClientPropertyKey BASICMENUITEMUI_MAX_TEXT_OFFSET =
        new StringUIClientPropertyKey ("maxTextOffset");

    // security stuff
    private static Field inputEvent_CanAccessSystemClipboard_Field = null;
    private static final String UntrustedClipboardAccess =
        "UNTRUSTED_CLIPBOARD_ACCESS_KEY";

    //all access to  charsBuffer is to be synchronized on charsBufferLock
    private static final int CHAR_BUFFER_SIZE = 100;
    private static final Object charsBufferLock = new Object();
    private static char[] charsBuffer = new char[CHAR_BUFFER_SIZE];

    /**
     * checks whether TextLayout is required to handle characters.
     *
     * @param text characters to be tested
     * @param start start
     * @param limit limit
     * @return <tt>true</tt>  if TextLayout is required
     *         <tt>false</tt> if TextLayout is not required
     */
    public static final boolean isComplexLayout(char[] text, int start, int limit) {
        return FontManager.isComplexText(text, start, limit);
    }

    //
    // WARNING WARNING WARNING WARNING WARNING WARNING
    // Many of the following methods are invoked from older API.
    // As this older API was not passed a Component, a null Component may
    // now be passsed in.  For example, SwingUtilities.computeStringWidth
    // is implemented to call SwingUtilities2.stringWidth, the
    // SwingUtilities variant does not take a JComponent, as such
    // SwingUtilities2.stringWidth can be passed a null Component.
    // In other words, if you add new functionality to these methods you
    // need to gracefully handle null.
    //

    /**
     * Returns whether or not text should be drawn antialiased.
     *
     * @param c JComponent to test.
     * @return Whether or not text should be drawn antialiased for the
     *         specified component.
     */
    public static AATextInfo drawTextAntialiased(JComponent c) {
        if (c != null) {
            /* a non-null property implies some form of AA requested */
            return (AATextInfo)c.getClientProperty(AA_TEXT_PROPERTY_KEY);
        }
        // No component, assume aa is off
        return null;
    }

    /**
     * Returns the left side bearing of the first character of string. The
     * left side bearing is calculated from the passed in FontMetrics.
     *
     * @param c JComponent that will display the string
     * @param fm FontMetrics used to measure the String width
     * @param string String to get the left side bearing for.
     */
    public static int getLeftSideBearing(JComponent c, FontMetrics fm,
                                         String string) {
        if ((string == null) || (string.length() == 0)) {
            return 0;
        }
        return getLeftSideBearing(c, fm, string.charAt(0));
    }

    /**
     * Returns the left side bearing of the specified character. The
     * left side bearing is calculated from the passed in FontMetrics.
     *
     * @param c JComponent that will display the string
     * @param fm FontMetrics used to measure the String width
     * @param firstChar Character to get the left side bearing for.
     */
    public static int getLeftSideBearing(JComponent c, FontMetrics fm,
                                         char firstChar) {
        return getBearing(c, fm, firstChar, true);
    }

    /**
     * Returns the right side bearing of the last character of string. The
     * right side bearing is calculated from the passed in FontMetrics.
     *
     * @param c JComponent that will display the string
     * @param fm FontMetrics used to measure the String width
     * @param string String to get the right side bearing for.
     */
    public static int getRightSideBearing(JComponent c, FontMetrics fm,
                                          String string) {
        if ((string == null) || (string.length() == 0)) {
            return 0;
        }
        return getRightSideBearing(c, fm, string.charAt(string.length() - 1));
    }

    /**
     * Returns the right side bearing of the specified character. The
     * right side bearing is calculated from the passed in FontMetrics.
     *
     * @param c JComponent that will display the string
     * @param fm FontMetrics used to measure the String width
     * @param lastChar Character to get the right side bearing for.
     */
    public static int getRightSideBearing(JComponent c, FontMetrics fm,
                                         char lastChar) {
        return getBearing(c, fm, lastChar, false);
    }

    /* Calculates the left and right side bearing for a character.
     * Strongly caches bearings for STRONG_BEARING_CACHE_SIZE
     * most recently used Fonts and softly caches as many as GC allows.
     */
    private static int getBearing(JComponent comp, FontMetrics fm, char c,
                                  boolean isLeftBearing) {
        if (fm == null) {
            if (comp == null) {
                return 0;
            } else {
                fm = comp.getFontMetrics(comp.getFont());
            }
        }
        synchronized (SwingUtilities2.class) {
            BearingCacheEntry entry = null;
            BearingCacheEntry searchKey = new BearingCacheEntry(fm);
            // See if we already have an entry in the strong cache
            for (BearingCacheEntry cacheEntry : strongBearingCache) {
                if (searchKey.equals(cacheEntry)) {
                    entry = cacheEntry;
                    break;
                }
            }
            // See if we already have an entry in the soft cache
            if (entry == null) {
                Iterator<SoftReference<BearingCacheEntry>> iter =
                        softBearingCache.iterator();
                while (iter.hasNext()) {
                    BearingCacheEntry cacheEntry = iter.next().get();
                    if (cacheEntry == null) {
                        // Remove discarded soft reference from the cache
                        iter.remove();
                        continue;
                    }
                    if (searchKey.equals(cacheEntry)) {
                        entry = cacheEntry;
                        putEntryInStrongCache(entry);
                        break;
                    }
                }
            }
            if (entry == null) {
                // No entry, add it
                entry = searchKey;
                cacheEntry(entry);
            }
            return (isLeftBearing)
                    ? entry.getLeftSideBearing(c)
                    : entry.getRightSideBearing(c);
        }
    }

    private synchronized static void cacheEntry(BearingCacheEntry entry) {
        // Move the oldest entry from the strong cache into the soft cache
        BearingCacheEntry oldestEntry =
                strongBearingCache[strongBearingCacheNextIndex];
        if (oldestEntry != null) {
            softBearingCache.add(new SoftReference<BearingCacheEntry>(oldestEntry));
        }
        // Put entry in the strong cache
        putEntryInStrongCache(entry);
    }

    private synchronized static void putEntryInStrongCache(BearingCacheEntry entry) {
        strongBearingCache[strongBearingCacheNextIndex] = entry;
        strongBearingCacheNextIndex = (strongBearingCacheNextIndex + 1)
                % STRONG_BEARING_CACHE_SIZE;
    }

    /**
     * Returns the FontMetrics for the current Font of the passed
     * in Graphics.  This method is used when a Graphics
     * is available, typically when painting.  If a Graphics is not
     * available the JComponent method of the same name should be used.
     * <p>
     * Callers should pass in a non-null JComponent, the exception
     * to this is if a JComponent is not readily available at the time of
     * painting.
     * <p>
     * This does not necessarily return the FontMetrics from the
     * Graphics.
     *
     * @param c JComponent requesting FontMetrics, may be null
     * @param g Graphics Graphics
     */
    public static FontMetrics getFontMetrics(JComponent c, Graphics g) {
        return getFontMetrics(c, g, g.getFont());
    }


    /**
     * Returns the FontMetrics for the specified Font.
     * This method is used when a Graphics is available, typically when
     * painting.  If a Graphics is not available the JComponent method of
     * the same name should be used.
     * <p>
     * Callers should pass in a non-null JComonent, the exception
     * to this is if a JComponent is not readily available at the time of
     * painting.
     * <p>
     * This does not necessarily return the FontMetrics from the
     * Graphics.
     *
     * @param c JComponent requesting FontMetrics, may be null
     * @param c Graphics Graphics
     * @param font Font to get FontMetrics for
     */
    public static FontMetrics getFontMetrics(JComponent c, Graphics g,
                                             Font font) {
        if (c != null) {
            // Note: We assume that we're using the FontMetrics
            // from the widget to layout out text, otherwise we can get
            // mismatches when printing.
            return c.getFontMetrics(font);
        }
        return Toolkit.getDefaultToolkit().getFontMetrics(font);
    }


    /**
     * Returns the width of the passed in String.
     * If the passed String is <code>null</code>, returns zero.
     *
     * @param c JComponent that will display the string, may be null
     * @param fm FontMetrics used to measure the String width
     * @param string String to get the width of
     */
    public static int stringWidth(JComponent c, FontMetrics fm, String string){
        if (string == null || string.equals("")) {
            return 0;
        }
        return fm.stringWidth(string);
    }


    /**
     * Clips the passed in String to the space provided.
     *
     * @param c JComponent that will display the string, may be null
     * @param fm FontMetrics used to measure the String width
     * @param string String to display
     * @param availTextWidth Amount of space that the string can be drawn in
     * @return Clipped string that can fit in the provided space.
     */
    public static String clipStringIfNecessary(JComponent c, FontMetrics fm,
                                               String string,
                                               int availTextWidth) {
        if ((string == null) || (string.equals("")))  {
            return "";
        }
        int textWidth = SwingUtilities2.stringWidth(c, fm, string);
        if (textWidth > availTextWidth) {
            return SwingUtilities2.clipString(c, fm, string, availTextWidth);
        }
        return string;
    }


    /**
     * Clips the passed in String to the space provided.  NOTE: this assumes
     * the string does not fit in the available space.
     *
     * @param c JComponent that will display the string, may be null
     * @param fm FontMetrics used to measure the String width
     * @param string String to display
     * @param availTextWidth Amount of space that the string can be drawn in
     * @return Clipped string that can fit in the provided space.
     */
    public static String clipString(JComponent c, FontMetrics fm,
                                    String string, int availTextWidth) {
        // c may be null here.
        String clipString = "...";
        int stringLength = string.length();
        availTextWidth -= SwingUtilities2.stringWidth(c, fm, clipString);
        if (availTextWidth <= 0) {
            //can not fit any characters
            return clipString;
        }

        boolean needsTextLayout;

        synchronized (charsBufferLock) {
            if (charsBuffer == null || charsBuffer.length < stringLength) {
                charsBuffer  = string.toCharArray();
            } else {
                string.getChars(0, stringLength, charsBuffer, 0);
            }
            needsTextLayout =
                isComplexLayout(charsBuffer, 0, stringLength);
            if (!needsTextLayout) {
                int width = 0;
                for (int nChars = 0; nChars < stringLength; nChars++) {
                    width += fm.charWidth(charsBuffer[nChars]);
                    if (width > availTextWidth) {
                        string = string.substring(0, nChars);
                        break;
                    }
                }
            }
        }
        if (needsTextLayout) {
            FontRenderContext frc = getFontRenderContext(c, fm);
            AttributedString aString = new AttributedString(string);
            LineBreakMeasurer measurer =
                new LineBreakMeasurer(aString.getIterator(), frc);
            int nChars = measurer.nextOffset(availTextWidth);
            string = string.substring(0, nChars);

        }
        return string + clipString;
    }


    /**
     * Draws the string at the specified location.
     *
     * @param c JComponent that will display the string, may be null
     * @param g Graphics to draw the text to
     * @param text String to display
     * @param x X coordinate to draw the text at
     * @param y Y coordinate to draw the text at
     */
    public static void drawString(JComponent c, Graphics g, String text,
                                  int x, int y) {
        // c may be null

        // All non-editable widgets that draw strings call into this
        // methods.  By non-editable that means widgets like JLabel, JButton
        // but NOT JTextComponents.
        if ( text == null || text.length() <= 0 ) { //no need to paint empty strings
            return;
        }
        if (isPrinting(g)) {
            Graphics2D g2d = getGraphics2D(g);
            if (g2d != null) {
                /* The printed text must scale linearly with the UI.
                 * Calculate the width on screen, obtain a TextLayout with
                 * advances for the printer graphics FRC, and then justify
                 * it to fit in the screen width. This distributes the spacing
                 * more evenly than directly laying out to the screen advances.
                 */
                float screenWidth = (float)
                   g2d.getFont().getStringBounds(text, DEFAULT_FRC).getWidth();
                TextLayout layout = new TextLayout(text, g2d.getFont(),
                                                   g2d.getFontRenderContext());

                layout = layout.getJustifiedLayout(screenWidth);
                /* Use alternate print color if specified */
                Color col = g2d.getColor();
                if (col instanceof PrintColorUIResource) {
                    g2d.setColor(((PrintColorUIResource)col).getPrintColor());
                }

                layout.draw(g2d, x, y);

                g2d.setColor(col);

                return;
            }
        }

        // If we get here we're not printing
        AATextInfo info = drawTextAntialiased(c);
        if (info != null && (g instanceof Graphics2D)) {
            Graphics2D g2 = (Graphics2D)g;

            Object oldContrast = null;
            Object oldAAValue = g2.getRenderingHint(KEY_TEXT_ANTIALIASING);
            if (info.aaHint != oldAAValue) {
                g2.setRenderingHint(KEY_TEXT_ANTIALIASING, info.aaHint);
            } else {
                oldAAValue = null;
            }
            if (info.lcdContrastHint != null) {
                oldContrast = g2.getRenderingHint(KEY_TEXT_LCD_CONTRAST);
                if (info.lcdContrastHint.equals(oldContrast)) {
                    oldContrast = null;
                } else {
                    g2.setRenderingHint(KEY_TEXT_LCD_CONTRAST,
                                        info.lcdContrastHint);
                }
            }

            g.drawString(text, x, y);

            if (oldAAValue != null) {
                g2.setRenderingHint(KEY_TEXT_ANTIALIASING, oldAAValue);
            }
            if (oldContrast != null) {
                g2.setRenderingHint(KEY_TEXT_LCD_CONTRAST, oldContrast);
            }
        }
        else {
            g.drawString(text, x, y);
        }
    }


    /**
     * Draws the string at the specified location underlining the specified
     * character.
     *
     * @param c JComponent that will display the string, may be null
     * @param g Graphics to draw the text to
     * @param text String to display
     * @param underlinedIndex Index of a character in the string to underline
     * @param x X coordinate to draw the text at
     * @param y Y coordinate to draw the text at
     */
    public static void drawStringUnderlineCharAt(JComponent c,Graphics g,
                           String text, int underlinedIndex, int x,int y) {
        if (text == null || text.length() <= 0) {
            return;
        }
        SwingUtilities2.drawString(c, g, text, x, y);
        int textLength = text.length();
        if (underlinedIndex >= 0 && underlinedIndex < textLength ) {
            int underlineRectY = y;
            int underlineRectHeight = 1;
            int underlineRectX = 0;
            int underlineRectWidth = 0;
            boolean isPrinting = isPrinting(g);
            boolean needsTextLayout = isPrinting;
            if (!needsTextLayout) {
                synchronized (charsBufferLock) {
                    if (charsBuffer == null || charsBuffer.length < textLength) {
                        charsBuffer = text.toCharArray();
                    } else {
                        text.getChars(0, textLength, charsBuffer, 0);
                    }
                    needsTextLayout =
                        isComplexLayout(charsBuffer, 0, textLength);
                }
            }
            if (!needsTextLayout) {
                FontMetrics fm = g.getFontMetrics();
                underlineRectX = x +
                    SwingUtilities2.stringWidth(c,fm,
                                        text.substring(0,underlinedIndex));
                underlineRectWidth = fm.charWidth(text.
                                                  charAt(underlinedIndex));
            } else {
                Graphics2D g2d = getGraphics2D(g);
                if (g2d != null) {
                    TextLayout layout =
                        new TextLayout(text, g2d.getFont(),
                                       g2d.getFontRenderContext());
                    if (isPrinting) {
                        float screenWidth = (float)g2d.getFont().
                            getStringBounds(text, DEFAULT_FRC).getWidth();
                        layout = layout.getJustifiedLayout(screenWidth);
                    }
                    TextHitInfo leading =
                        TextHitInfo.leading(underlinedIndex);
                    TextHitInfo trailing =
                        TextHitInfo.trailing(underlinedIndex);
                    Shape shape =
                        layout.getVisualHighlightShape(leading, trailing);
                    Rectangle rect = shape.getBounds();
                    underlineRectX = x + rect.x;
                    underlineRectWidth = rect.width;
                }
            }
            g.fillRect(underlineRectX, underlineRectY + 1,
                       underlineRectWidth, underlineRectHeight);
        }
    }


    /**
     * A variation of locationToIndex() which only returns an index if the
     * Point is within the actual bounds of a list item (not just in the cell)
     * and if the JList has the "List.isFileList" client property set.
     * Otherwise, this method returns -1.
     * This is used to make WindowsL&F JFileChooser act like native dialogs.
     */
    public static int loc2IndexFileList(JList list, Point point) {
        int index = list.locationToIndex(point);
        if (index != -1) {
            Object bySize = list.getClientProperty("List.isFileList");
            if (bySize instanceof Boolean && ((Boolean)bySize).booleanValue() &&
                !pointIsInActualBounds(list, index, point)) {
                index = -1;
            }
        }
        return index;
    }


    /**
     * Returns true if the given point is within the actual bounds of the
     * JList item at index (not just inside the cell).
     */
    private static boolean pointIsInActualBounds(JList list, int index,
                                                Point point) {
        ListCellRenderer renderer = list.getCellRenderer();
        ListModel dataModel = list.getModel();
        Object value = dataModel.getElementAt(index);
        Component item = renderer.getListCellRendererComponent(list,
                          value, index, false, false);
        Dimension itemSize = item.getPreferredSize();
        Rectangle cellBounds = list.getCellBounds(index, index);
        if (!item.getComponentOrientation().isLeftToRight()) {
            cellBounds.x += (cellBounds.width - itemSize.width);
        }
        cellBounds.width = itemSize.width;

        return cellBounds.contains(point);
    }


    /**
     * Returns true if the given point is outside the preferredSize of the
     * item at the given row of the table.  (Column must be 0).
     * Does not check the "Table.isFileList" property. That should be checked
     * before calling this method.
     * This is used to make WindowsL&F JFileChooser act like native dialogs.
     */
    public static boolean pointOutsidePrefSize(JTable table, int row, int column, Point p) {
        if (table.convertColumnIndexToModel(column) != 0 || row == -1) {
            return true;
        }
        TableCellRenderer tcr = table.getCellRenderer(row, column);
        Object value = table.getValueAt(row, column);
        Component cell = tcr.getTableCellRendererComponent(table, value, false,
                false, row, column);
        Dimension itemSize = cell.getPreferredSize();
        Rectangle cellBounds = table.getCellRect(row, column, false);
        cellBounds.width = itemSize.width;
        cellBounds.height = itemSize.height;

        // See if coords are inside
        // ASSUME: mouse x,y will never be < cell's x,y
        assert (p.x >= cellBounds.x && p.y >= cellBounds.y);
        return p.x > cellBounds.x + cellBounds.width ||
                p.y > cellBounds.y + cellBounds.height;
    }

    /**
     * Set the lead and anchor without affecting selection.
     */
    public static void setLeadAnchorWithoutSelection(ListSelectionModel model,
                                                     int lead, int anchor) {
        if (anchor == -1) {
            anchor = lead;
        }
        if (lead == -1) {
            model.setAnchorSelectionIndex(-1);
            model.setLeadSelectionIndex(-1);
        } else {
            if (model.isSelectedIndex(lead)) {
                model.addSelectionInterval(lead, lead);
            } else {
                model.removeSelectionInterval(lead, lead);
            }
            model.setAnchorSelectionIndex(anchor);
        }
    }

    /**
     * Ignore mouse events if the component is null, not enabled, the event
     * is not associated with the left mouse button, or the event has been
     * consumed.
     */
    public static boolean shouldIgnore(MouseEvent me, JComponent c) {
        return c == null || !c.isEnabled()
                         || !SwingUtilities.isLeftMouseButton(me)
                         || me.isConsumed();
    }

    /**
     * Request focus on the given component if it doesn't already have it
     * and <code>isRequestFocusEnabled()</code> returns true.
     */
    public static void adjustFocus(JComponent c) {
        if (!c.hasFocus() && c.isRequestFocusEnabled()) {
            c.requestFocus();
        }
    }

    /**
     * The following draw functions have the same semantic as the
     * Graphics methods with the same names.
     *
     * this is used for printing
     */
    public static int drawChars(JComponent c, Graphics g,
                                 char[] data,
                                 int offset,
                                 int length,
                                 int x,
                                 int y) {
        if ( length <= 0 ) { //no need to paint empty strings
            return x;
        }
        int nextX = x + getFontMetrics(c, g).charsWidth(data, offset, length);
        if (isPrinting(g)) {
            Graphics2D g2d = getGraphics2D(g);
            if (g2d != null) {
                FontRenderContext deviceFontRenderContext = g2d.
                    getFontRenderContext();
                FontRenderContext frc = getFontRenderContext(c);
                if (frc != null &&
                    !isFontRenderContextPrintCompatible
                    (deviceFontRenderContext, frc)) {
                    TextLayout layout =
                        new TextLayout(new String(data,offset,length),
                                       g2d.getFont(),
                                       deviceFontRenderContext);
                    float screenWidth = (float)g2d.getFont().
                        getStringBounds(data, offset, offset + length, frc).
                        getWidth();
                    layout = layout.getJustifiedLayout(screenWidth);

                    /* Use alternate print color if specified */
                    Color col = g2d.getColor();
                    if (col instanceof PrintColorUIResource) {
                        g2d.setColor(((PrintColorUIResource)col).getPrintColor());
                    }

                    layout.draw(g2d,x,y);

                    g2d.setColor(col);

                    return nextX;
                }
            }
        }
        // Assume we're not printing if we get here, or that we are invoked
        // via Swing text printing which is laid out for the printer.
        AATextInfo info = drawTextAntialiased(c);
        if (info != null && (g instanceof Graphics2D)) {
            Graphics2D g2 = (Graphics2D)g;

            Object oldContrast = null;
            Object oldAAValue = g2.getRenderingHint(KEY_TEXT_ANTIALIASING);
            if (info.aaHint != null && info.aaHint != oldAAValue) {
                g2.setRenderingHint(KEY_TEXT_ANTIALIASING, info.aaHint);
            } else {
                oldAAValue = null;
            }
            if (info.lcdContrastHint != null) {
                oldContrast = g2.getRenderingHint(KEY_TEXT_LCD_CONTRAST);
                if (info.lcdContrastHint.equals(oldContrast)) {
                    oldContrast = null;
                } else {
                    g2.setRenderingHint(KEY_TEXT_LCD_CONTRAST,
                                        info.lcdContrastHint);
                }
            }

            g.drawChars(data, offset, length, x, y);

            if (oldAAValue != null) {
                g2.setRenderingHint(KEY_TEXT_ANTIALIASING, oldAAValue);
            }
            if (oldContrast != null) {
                g2.setRenderingHint(KEY_TEXT_LCD_CONTRAST, oldContrast);
            }
        }
        else {
            g.drawChars(data, offset, length, x, y);
        }
        return nextX;
    }

    /*
     * see documentation for drawChars
     * returns the advance
     */
    public static float drawString(JComponent c, Graphics g,
                                   AttributedCharacterIterator iterator,
                                   int x,
                                   int y) {

        float retVal;
        boolean isPrinting = isPrinting(g);
        Color col = g.getColor();

        if (isPrinting) {
            /* Use alternate print color if specified */
            if (col instanceof PrintColorUIResource) {
                g.setColor(((PrintColorUIResource)col).getPrintColor());
            }
        }

        Graphics2D g2d = getGraphics2D(g);
        if (g2d == null) {
            g.drawString(iterator,x,y); //for the cases where advance
                                        //matters it should not happen
            retVal = x;

        } else {
            FontRenderContext frc;
            if (isPrinting) {
                frc = getFontRenderContext(c);
                if (frc.isAntiAliased() || frc.usesFractionalMetrics()) {
                    frc = new FontRenderContext(frc.getTransform(), false, false);
                }
            } else if ((frc = getFRCProperty(c)) != null) {
                /* frc = frc; ! */
            } else {
                frc = g2d.getFontRenderContext();
            }
            TextLayout layout = new TextLayout(iterator, frc);
            if (isPrinting) {
                FontRenderContext deviceFRC = g2d.getFontRenderContext();
                if (!isFontRenderContextPrintCompatible(frc, deviceFRC)) {
                    float screenWidth = layout.getAdvance();
                    layout = new TextLayout(iterator, deviceFRC);
                    layout = layout.getJustifiedLayout(screenWidth);
                }
            }
            layout.draw(g2d, x, y);
            retVal = layout.getAdvance();
        }

        if (isPrinting) {
            g.setColor(col);
        }

        return retVal;
    }

    /*
     * Checks if two given FontRenderContexts are compatible for printing.
     * We can't just use equals as we want to exclude from the comparison :
     * + whether AA is set as irrelevant for printing and shouldn't affect
     * printed metrics anyway
     * + any translation component in the transform of either FRC, as it
     * does not affect metrics.
     * Compatible means no special handling needed for text painting
     */
    private static boolean
        isFontRenderContextPrintCompatible(FontRenderContext frc1,
                                           FontRenderContext frc2) {

        if (frc1 == frc2) {
            return true;
        }

        if (frc1 == null || frc2 == null) { // not supposed to happen
            return false;
        }

        if (frc1.getFractionalMetricsHint() !=
            frc2.getFractionalMetricsHint()) {
            return false;
        }

        /* If both are identity, return true */
        if (!frc1.isTransformed() && !frc2.isTransformed()) {
            return true;
        }

        /* That's the end of the cheap tests, need to get and compare
         * the transform matrices. We don't care about the translation
         * components, so return true if they are otherwise identical.
         */
        double[] mat1 = new double[4];
        double[] mat2 = new double[4];
        frc1.getTransform().getMatrix(mat1);
        frc2.getTransform().getMatrix(mat2);
        return
            mat1[0] == mat2[0] &&
            mat1[1] == mat2[1] &&
            mat1[2] == mat2[2] &&
            mat1[3] == mat2[3];
    }

    /*
     * Tries it best to get Graphics2D out of the given Graphics
     * returns null if can not derive it.
     */
    public static Graphics2D getGraphics2D(Graphics g) {
        if (g instanceof Graphics2D) {
            return (Graphics2D) g;
        } else if (g instanceof ProxyPrintGraphics) {
            return (Graphics2D)(((ProxyPrintGraphics)g).getGraphics());
        } else {
            return null;
        }
    }

    /*
     * Returns FontRenderContext associated with Component.
     * FontRenderContext from Component.getFontMetrics is associated
     * with the component.
     *
     * Uses Component.getFontMetrics to get the FontRenderContext from.
     * see JComponent.getFontMetrics and TextLayoutStrategy.java
     */
    public static FontRenderContext getFontRenderContext(Component c) {
        assert c != null;
        if (c == null) {
            return DEFAULT_FRC;
        } else {
            return c.getFontMetrics(c.getFont()).getFontRenderContext();
        }
    }

    /**
     * A convenience method to get FontRenderContext.
     * Returns the FontRenderContext for the passed in FontMetrics or
     * for the passed in Component if FontMetrics is null
     */
    private static FontRenderContext getFontRenderContext(Component c, FontMetrics fm) {
        assert fm != null || c!= null;
        return (fm != null) ? fm.getFontRenderContext()
            : getFontRenderContext(c);
    }

    /*
     * This method is to be used only for JComponent.getFontMetrics.
     * In all other places to get FontMetrics we need to use
     * JComponent.getFontMetrics.
     *
     */
    public static FontMetrics getFontMetrics(JComponent c, Font font) {
        FontRenderContext  frc = getFRCProperty(c);
        if (frc == null) {
            frc = DEFAULT_FRC;
        }
        return FontDesignMetrics.getMetrics(font, frc);
    }


    /* Get any FontRenderContext associated with a JComponent
     * - may return null
     */
    private static FontRenderContext getFRCProperty(JComponent c) {
        if (c != null) {
            AATextInfo info =
                (AATextInfo)c.getClientProperty(AA_TEXT_PROPERTY_KEY);
            if (info != null) {
                return info.frc;
            }
        }
        return null;
    }

    /*
     * returns true if the Graphics is print Graphics
     * false otherwise
     */
    static boolean isPrinting(Graphics g) {
        return (g instanceof PrinterGraphics || g instanceof PrintGraphics);
    }

    /**
     * Determines whether the SelectedTextColor should be used for painting text
     * foreground for the specified highlight.
     *
     * Returns true only if the highlight painter for the specified highlight
     * is the swing painter (whether inner class of javax.swing.text.DefaultHighlighter
     * or com.sun.java.swing.plaf.windows.WindowsTextUI) and its background color
     * is null or equals to the selection color of the text component.
     *
     * This is a hack for fixing both bugs 4761990 and 5003294
     */
    public static boolean useSelectedTextColor(Highlighter.Highlight h, JTextComponent c) {
        Highlighter.HighlightPainter painter = h.getPainter();
        String painterClass = painter.getClass().getName();
        if (painterClass.indexOf("javax.swing.text.DefaultHighlighter") != 0 &&
                painterClass.indexOf("com.sun.java.swing.plaf.windows.WindowsTextUI") != 0) {
            return false;
        }
        try {
            DefaultHighlighter.DefaultHighlightPainter defPainter =
                    (DefaultHighlighter.DefaultHighlightPainter) painter;
            if (defPainter.getColor() != null &&
                    !defPainter.getColor().equals(c.getSelectionColor())) {
                return false;
            }
        } catch (ClassCastException e) {
            return false;
        }
        return true;
    }

    /**
     * BearingCacheEntry is used to cache left and right character bearings
     * for a particular <code>Font</code> and <code>FontRenderContext</code>.
     */
    private static class BearingCacheEntry {
        private FontMetrics fontMetrics;
        private Font font;
        private FontRenderContext frc;
        private Map<Character, Short> cache;
        // Used for the creation of a GlyphVector
        private static final char[] oneChar = new char[1];

        public BearingCacheEntry(FontMetrics fontMetrics) {
            this.fontMetrics = fontMetrics;
            this.font = fontMetrics.getFont();
            this.frc = fontMetrics.getFontRenderContext();
            this.cache = new HashMap<Character, Short>();
            assert (font != null && frc != null);
        }

        public int getLeftSideBearing(char aChar) {
            Short bearing = cache.get(aChar);
            if (bearing == null) {
                bearing = calcBearing(aChar);
                cache.put(aChar, bearing);
            }
            return ((0xFF00 & bearing) >>> 8) - 127;
        }

        public int getRightSideBearing(char aChar) {
            Short bearing = cache.get(aChar);
            if (bearing == null) {
                bearing = calcBearing(aChar);
                cache.put(aChar, bearing);
            }
            return (0xFF & bearing) - 127;
        }

        /* Calculates left and right side bearings for a character.
         * Makes an assumption that bearing is a value between -127 and +127.
         * Stores LSB and RSB as single two-byte number (short):
         * LSB is the high byte, RSB is the low byte.
         */
        private short calcBearing(char aChar) {
            oneChar[0] = aChar;
            GlyphVector gv = font.createGlyphVector(frc, oneChar);
            Rectangle pixelBounds = gv.getGlyphPixelBounds(0, frc, 0f, 0f);

            // Get bearings
            int lsb = pixelBounds.x;
            int rsb = pixelBounds.width - fontMetrics.charWidth(aChar);

            /* HRGB/HBGR LCD glyph images will always have a pixel
             * on the left and a pixel on the right
             * used in colour fringe reduction.
             * Text rendering positions this correctly but here
             * we are using the glyph image to adjust that position
             * so must account for it.
             */
            if (lsb < 0) {
                 Object aaHint = frc.getAntiAliasingHint();
                 if (aaHint == VALUE_TEXT_ANTIALIAS_LCD_HRGB ||
                     aaHint == VALUE_TEXT_ANTIALIAS_LCD_HBGR) {
                     lsb++;
                 }
            }
            if (rsb > 0) {
                 Object aaHint = frc.getAntiAliasingHint();
                 if (aaHint == VALUE_TEXT_ANTIALIAS_LCD_HRGB ||
                     aaHint == VALUE_TEXT_ANTIALIAS_LCD_HBGR) {
                     rsb--;
                 }
            }

            // Make sure that LSB and RSB are valid (see 6472972)
            if (lsb < -127 || lsb > 127) {
                lsb = 0;
            }
            if (rsb < -127 || rsb > 127) {
                rsb = 0;
            }

            int bearing = ((lsb + 127) << 8) + (rsb + 127);
            return (short)bearing;
        }

        public boolean equals(Object entry) {
            if (entry == this) {
                return true;
            }
            if (!(entry instanceof BearingCacheEntry)) {
                return false;
            }
            BearingCacheEntry oEntry = (BearingCacheEntry)entry;
            return (font.equals(oEntry.font) &&
                    frc.equals(oEntry.frc));
        }

        public int hashCode() {
            int result = 17;
            if (font != null) {
                result = 37 * result + font.hashCode();
            }
            if (frc != null) {
                result = 37 * result + frc.hashCode();
            }
            return result;
        }
    }


    /*
     * here goes the fix for 4856343 [Problem with applet interaction
     * with system selection clipboard]
     *
     * NOTE. In case isTrustedContext() no checking
     * are to be performed
     */


    /**
     * checks the security permissions for accessing system clipboard
     *
     * for untrusted context (see isTrustedContext) checks the
     * permissions for the current event being handled
     *
     */
    public static boolean canAccessSystemClipboard() {
        boolean canAccess = false;
        if (!GraphicsEnvironment.isHeadless()) {
            SecurityManager sm = System.getSecurityManager();
            if (sm == null) {
                canAccess = true;
            } else {
                try {
                    sm.checkSystemClipboardAccess();
                    canAccess = true;
                } catch (SecurityException e) {
                }
                if (canAccess && ! isTrustedContext()) {
                    canAccess = canCurrentEventAccessSystemClipboard(true);
                }
            }
        }
        return canAccess;
    }

    /**
     * Returns true if EventQueue.getCurrentEvent() has the permissions to
     * access the system clipboard
     */
    public static boolean canCurrentEventAccessSystemClipboard() {
        return  isTrustedContext()
            || canCurrentEventAccessSystemClipboard(false);
    }

    /**
     * Returns true if the given event has permissions to access the
     * system clipboard
     *
     * @param e AWTEvent to check
     */
    public static boolean canEventAccessSystemClipboard(AWTEvent e) {
        return isTrustedContext()
            || canEventAccessSystemClipboard(e, false);
    }

    /**
     * returns canAccessSystemClipboard field from InputEvent
     *
     * @param ie InputEvent to get the field from
     */
    private static synchronized boolean inputEvent_canAccessSystemClipboard(InputEvent ie) {
        if (inputEvent_CanAccessSystemClipboard_Field == null) {
            inputEvent_CanAccessSystemClipboard_Field =
                AccessController.doPrivileged(
                    new java.security.PrivilegedAction<Field>() {
                        public Field run() {
                            try {
                                Field field = InputEvent.class.
                                    getDeclaredField("canAccessSystemClipboard");
                                field.setAccessible(true);
                                return field;
                            } catch (SecurityException e) {
                            } catch (NoSuchFieldException e) {
                            }
                            return null;
                        }
                    });
        }
        if (inputEvent_CanAccessSystemClipboard_Field == null) {
            return false;
        }
        boolean ret = false;
        try {
            ret = inputEvent_CanAccessSystemClipboard_Field.
                getBoolean(ie);
        } catch(IllegalAccessException e) {
        }
        return ret;
    }

    /**
     * Returns true if the given event is corrent gesture for
     * accessing clipboard
     *
     * @param ie InputEvent to check
     */

    private static boolean isAccessClipboardGesture(InputEvent ie) {
        boolean allowedGesture = false;
        if (ie instanceof KeyEvent) { //we can validate only keyboard gestures
            KeyEvent ke = (KeyEvent)ie;
            int keyCode = ke.getKeyCode();
            int keyModifiers = ke.getModifiers();
            switch(keyCode) {
            case KeyEvent.VK_C:
            case KeyEvent.VK_V:
            case KeyEvent.VK_X:
                allowedGesture = (keyModifiers == InputEvent.CTRL_MASK);
                break;
            case KeyEvent.VK_INSERT:
                allowedGesture = (keyModifiers == InputEvent.CTRL_MASK ||
                                  keyModifiers == InputEvent.SHIFT_MASK);
                break;
            case KeyEvent.VK_COPY:
            case KeyEvent.VK_PASTE:
            case KeyEvent.VK_CUT:
                allowedGesture = true;
                break;
            case KeyEvent.VK_DELETE:
                allowedGesture = ( keyModifiers == InputEvent.SHIFT_MASK);
                break;
            }
        }
        return allowedGesture;
    }

    /**
     * Returns true if e has the permissions to
     * access the system clipboard and if it is allowed gesture (if
     * checkGesture is true)
     *
     * @param e AWTEvent to check
     * @param checkGesture boolean
     */
    private static boolean canEventAccessSystemClipboard(AWTEvent e,
                                                        boolean checkGesture) {
        if (EventQueue.isDispatchThread()) {
            /*
             * Checking event permissions makes sense only for event
             * dispathing thread
             */
            if (e instanceof InputEvent
                && (! checkGesture || isAccessClipboardGesture((InputEvent)e))) {
                return inputEvent_canAccessSystemClipboard((InputEvent)e);
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    /**
     * Returns true if EventQueue.getCurrentEvent() has the permissions to
     * access the system clipboard and if it is allowed gesture (if
     * checkGesture true)
     *
     * @param checkGesture boolean
     */
    private static boolean canCurrentEventAccessSystemClipboard(boolean
                                                               checkGesture) {
        AWTEvent event = EventQueue.getCurrentEvent();
        return canEventAccessSystemClipboard(event, checkGesture);
    }

    /**
     * see RFE 5012841 [Per AppContect security permissions] for the
     * details
     *
     */
    private static boolean isTrustedContext() {
        return (System.getSecurityManager() == null)
            || (AppContext.getAppContext().
                get(UntrustedClipboardAccess) == null);
    }

    public static String displayPropertiesToCSS(Font font, Color fg) {
        StringBuffer rule = new StringBuffer("body {");
        if (font != null) {
            rule.append(" font-family: ");
            rule.append(font.getFamily());
            rule.append(" ; ");
            rule.append(" font-size: ");
            rule.append(font.getSize());
            rule.append("pt ;");
            if (font.isBold()) {
                rule.append(" font-weight: 700 ; ");
            }
            if (font.isItalic()) {
                rule.append(" font-style: italic ; ");
            }
        }
        if (fg != null) {
            rule.append(" color: #");
            if (fg.getRed() < 16) {
                rule.append('0');
            }
            rule.append(Integer.toHexString(fg.getRed()));
            if (fg.getGreen() < 16) {
                rule.append('0');
            }
            rule.append(Integer.toHexString(fg.getGreen()));
            if (fg.getBlue() < 16) {
                rule.append('0');
            }
            rule.append(Integer.toHexString(fg.getBlue()));
            rule.append(" ; ");
        }
        rule.append(" }");
        return rule.toString();
    }

    /**
     * Utility method that creates a <code>UIDefaults.LazyValue</code> that
     * creates an <code>ImageIcon</code> <code>UIResource</code> for the
     * specified image file name. The image is loaded using
     * <code>getResourceAsStream</code>, starting with a call to that method
     * on the base class parameter. If it cannot be found, searching will
     * continue through the base class' inheritance hierarchy, up to and
     * including <code>rootClass</code>.
     *
     * @param baseClass the first class to use in searching for the resource
     * @param rootClass an ancestor of <code>baseClass</code> to finish the
     *                  search at
     * @param imageFile the name of the file to be found
     * @return a lazy value that creates the <code>ImageIcon</code>
     *         <code>UIResource</code> for the image,
     *         or null if it cannot be found
     */
    public static Object makeIcon(final Class<?> baseClass,
                                  final Class<?> rootClass,
                                  final String imageFile) {

        return new UIDefaults.LazyValue() {
            public Object createValue(UIDefaults table) {
                /* Copy resource into a byte array.  This is
                 * necessary because several browsers consider
                 * Class.getResource a security risk because it
                 * can be used to load additional classes.
                 * Class.getResourceAsStream just returns raw
                 * bytes, which we can convert to an image.
                 */
                byte[] buffer =
                    java.security.AccessController.doPrivileged(
                        new java.security.PrivilegedAction<byte[]>() {
                    public byte[] run() {
                        try {
                            InputStream resource = null;
                            Class<?> srchClass = baseClass;

                            while (srchClass != null) {
                                resource = srchClass.getResourceAsStream(imageFile);

                                if (resource != null || srchClass == rootClass) {
                                    break;
                                }

                                srchClass = srchClass.getSuperclass();
                            }

                            if (resource == null) {
                                return null;
                            }

                            BufferedInputStream in =
                                new BufferedInputStream(resource);
                            ByteArrayOutputStream out =
                                new ByteArrayOutputStream(1024);
                            byte[] buffer = new byte[1024];
                            int n;
                            while ((n = in.read(buffer)) > 0) {
                                out.write(buffer, 0, n);
                            }
                            in.close();
                            out.flush();
                            return out.toByteArray();
                        } catch (IOException ioe) {
                            System.err.println(ioe.toString());
                        }
                        return null;
                    }
                });

                if (buffer == null) {
                    return null;
                }
                if (buffer.length == 0) {
                    System.err.println("warning: " + imageFile +
                                       " is zero-length");
                    return null;
                }

                return new ImageIconUIResource(buffer);
            }
        };
    }

    /* Used to help decide if AA text rendering should be used, so
     * this local display test should be additionally qualified
     * against whether we have XRender support on both ends of the wire,
     * as with that support remote performance may be good enough to turn
     * on by default. An additional complication there is XRender does not
     * appear capable of performing gamma correction needed for LCD text.
     */
    public static boolean isLocalDisplay() {
        try {
            // On Windows just return true. Permission to read os.name
            // is granted to all code but wrapped in try to be safe.
            if (OSInfo.getOSType() == OSInfo.OSType.WINDOWS) {
                return true;
            }
            // Else probably Solaris or Linux in which case may be remote X11
            Class<?> x11Class = Class.forName("sun.awt.X11GraphicsEnvironment");
            Method isDisplayLocalMethod = x11Class.getMethod(
                      "isDisplayLocal", new Class[0]);
            return (Boolean)isDisplayLocalMethod.invoke(null, (Object[])null);
        } catch (Throwable t) {
        }
        // If we get here we're most likely being run on some other O/S
        // or we didn't properly detect Windows.
        return true;
    }

    /**
     * Returns an integer from the defaults table. If <code>key</code> does
     * not map to a valid <code>Integer</code>, or can not be convered from
     * a <code>String</code> to an integer, the value 0 is returned.
     *
     * @param key  an <code>Object</code> specifying the int.
     * @return the int
     */
    public static int getUIDefaultsInt(Object key) {
        return getUIDefaultsInt(key, 0);
    }

    /**
     * Returns an integer from the defaults table that is appropriate
     * for the given locale. If <code>key</code> does not map to a valid
     * <code>Integer</code>, or can not be convered from a <code>String</code>
     * to an integer, the value 0 is returned.
     *
     * @param key  an <code>Object</code> specifying the int. Returned value
     *             is 0 if <code>key</code> is not available,
     * @param l the <code>Locale</code> for which the int is desired
     * @return the int
     */
    public static int getUIDefaultsInt(Object key, Locale l) {
        return getUIDefaultsInt(key, l, 0);
    }

    /**
     * Returns an integer from the defaults table. If <code>key</code> does
     * not map to a valid <code>Integer</code>, or can not be convered from
     * a <code>String</code> to an integer, <code>default</code> is
     * returned.
     *
     * @param key  an <code>Object</code> specifying the int. Returned value
     *             is 0 if <code>key</code> is not available,
     * @param defaultValue Returned value if <code>key</code> is not available,
     *                     or is not an Integer
     * @return the int
     */
    public static int getUIDefaultsInt(Object key, int defaultValue) {
        return getUIDefaultsInt(key, null, defaultValue);
    }

    /**
     * Returns an integer from the defaults table that is appropriate
     * for the given locale. If <code>key</code> does not map to a valid
     * <code>Integer</code>, or can not be convered from a <code>String</code>
     * to an integer, <code>default</code> is returned.
     *
     * @param key  an <code>Object</code> specifying the int. Returned value
     *             is 0 if <code>key</code> is not available,
     * @param l the <code>Locale</code> for which the int is desired
     * @param defaultValue Returned value if <code>key</code> is not available,
     *                     or is not an Integer
     * @return the int
     */
    public static int getUIDefaultsInt(Object key, Locale l, int defaultValue) {
        Object value = UIManager.get(key, l);

        if (value instanceof Integer) {
            return ((Integer)value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String)value);
            } catch (NumberFormatException nfe) {}
        }
        return defaultValue;
    }

    // At this point we need this method here. But we assume that there
    // will be a common method for this purpose in the future releases.
    public static Component compositeRequestFocus(Component component) {
        if (component instanceof Container) {
            Container container = (Container)component;
            if (container.isFocusCycleRoot()) {
                FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
                Component comp = policy.getDefaultComponent(container);
                if (comp!=null) {
                    comp.requestFocus();
                    return comp;
                }
            }
            Container rootAncestor = container.getFocusCycleRootAncestor();
            if (rootAncestor!=null) {
                FocusTraversalPolicy policy = rootAncestor.getFocusTraversalPolicy();
                Component comp = policy.getComponentAfter(rootAncestor, container);

                if (comp!=null && SwingUtilities.isDescendingFrom(comp, container)) {
                    comp.requestFocus();
                    return comp;
                }
            }
        }
        if (component.isFocusable()) {
            component.requestFocus();
            return component;
        }
        return null;
    }

    /**
     * Change focus to the visible component in {@code JTabbedPane}.
     * This is not a general-purpose method and is here only to permit
     * sharing code.
     */
    public static boolean tabbedPaneChangeFocusTo(Component comp) {
        if (comp != null) {
            if (comp.isFocusTraversable()) {
                SwingUtilities2.compositeRequestFocus(comp);
                return true;
            } else if (comp instanceof JComponent
                       && ((JComponent)comp).requestDefaultFocus()) {

                 return true;
            }
        }

        return false;
    }

    /**
     * Submits a value-returning task for execution on the EDT and
     * returns a Future representing the pending results of the task.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws NullPointerException if the task is null
     */
    public static <V> Future<V> submit(Callable<V> task) {
        if (task == null) {
            throw new NullPointerException();
        }
        FutureTask<V> future = new FutureTask<V>(task);
        execute(future);
        return future;
    }

    /**
     * Submits a Runnable task for execution on the EDT and returns a
     * Future representing that task.
     *
     * @param task the task to submit
     * @param result the result to return upon successful completion
     * @return a Future representing pending completion of the task,
     *         and whose <tt>get()</tt> method will return the given
     *         result value upon completion
     * @throws NullPointerException if the task is null
     */
    public static <V> Future<V> submit(Runnable task, V result) {
        if (task == null) {
            throw new NullPointerException();
        }
        FutureTask<V> future = new FutureTask<V>(task, result);
        execute(future);
        return future;
    }

    /**
     * Sends a Runnable to the EDT for the execution.
     */
    private static void execute(Runnable command) {
        SwingUtilities.invokeLater(command);
    }

    /**
     * Sets the {@code SKIP_CLICK_COUNT} client property on the component
     * if it is an instance of {@code JTextComponent} with a
     * {@code DefaultCaret}. This property, used for text components acting
     * as editors in a table or tree, tells {@code DefaultCaret} how many
     * clicks to skip before starting selection.
     */
    public static void setSkipClickCount(Component comp, int count) {
        if (comp instanceof JTextComponent
                && ((JTextComponent) comp).getCaret() instanceof DefaultCaret) {

            ((JTextComponent) comp).putClientProperty(SKIP_CLICK_COUNT, count);
        }
    }

    /**
     * Return the MouseEvent's click count, possibly reduced by the value of
     * the component's {@code SKIP_CLICK_COUNT} client property. Clears
     * the {@code SKIP_CLICK_COUNT} property if the mouse event's click count
     * is 1. In order for clearing of the property to work correctly, there
     * must be a mousePressed implementation on the caller with this
     * call as the first line.
     */
    public static int getAdjustedClickCount(JTextComponent comp, MouseEvent e) {
        int cc = e.getClickCount();

        if (cc == 1) {
            comp.putClientProperty(SKIP_CLICK_COUNT, null);
        } else {
            Integer sub = (Integer) comp.getClientProperty(SKIP_CLICK_COUNT);
            if (sub != null) {
                return cc - sub;
            }
        }

        return cc;
    }

    /**
     * Used by the {@code liesIn} method to return which section
     * the point lies in.
     *
     * @see #liesIn
     */
    public enum Section {

        /** The leading section */
        LEADING,

        /** The middle section */
        MIDDLE,

        /** The trailing section */
        TRAILING
    }

    /**
     * This method divides a rectangle into two or three sections along
     * the specified axis and determines which section the given point
     * lies in on that axis; used by drag and drop when calculating drop
     * locations.
     * <p>
     * For two sections, the rectangle is divided equally and the method
     * returns whether the point lies in {@code Section.LEADING} or
     * {@code Section.TRAILING}. For horizontal divisions, the calculation
     * respects component orientation.
     * <p>
     * For three sections, if the rectangle is greater than or equal to
     * 30 pixels in length along the axis, the calculation gives 10 pixels
     * to each of the leading and trailing sections and the remainder to the
     * middle. For smaller sizes, the rectangle is divided equally into three
     * sections.
     * <p>
     * Note: This method assumes that the point is within the bounds of
     * the given rectangle on the specified axis. However, in cases where
     * it isn't, the results still have meaning: {@code Section.MIDDLE}
     * remains the same, {@code Section.LEADING} indicates that the point
     * is in or somewhere before the leading section, and
     * {@code Section.TRAILING} indicates that the point is in or somewhere
     * after the trailing section.
     *
     * @param rect the rectangle
     * @param p the point the check
     * @param horizontal {@code true} to use the horizontal axis,
     *        or {@code false} for the vertical axis
     * @param ltr {@code true} for left to right orientation,
     *        or {@code false} for right to left orientation;
     *        only used for horizontal calculations
     * @param three {@code true} for three sections,
     *        or {@code false} for two
     *
     * @return the {@code Section} where the point lies
     *
     * @throws NullPointerException if {@code rect} or {@code p} are
     *         {@code null}
     */
    private static Section liesIn(Rectangle rect, Point p, boolean horizontal,
                                  boolean ltr, boolean three) {

        /* beginning of the rectangle on the axis */
        int p0;

        /* point on the axis we're interested in */
        int pComp;

        /* length of the rectangle on the axis */
        int length;

        /* value of ltr if horizontal, else true */
        boolean forward;

        if (horizontal) {
            p0 = rect.x;
            pComp = p.x;
            length = rect.width;
            forward = ltr;
        } else {
            p0 = rect.y;
            pComp = p.y;
            length = rect.height;
            forward = true;
        }

        if (three) {
            int boundary = (length >= 30) ? 10 : length / 3;

            if (pComp < p0 + boundary) {
               return forward ? Section.LEADING : Section.TRAILING;
           } else if (pComp >= p0 + length - boundary) {
               return forward ? Section.TRAILING : Section.LEADING;
           }

           return Section.MIDDLE;
        } else {
            int middle = p0 + length / 2;
            if (forward) {
                return pComp >= middle ? Section.TRAILING : Section.LEADING;
            } else {
                return pComp < middle ? Section.TRAILING : Section.LEADING;
            }
        }
    }

    /**
     * This method divides a rectangle into two or three sections along
     * the horizontal axis and determines which section the given point
     * lies in; used by drag and drop when calculating drop locations.
     * <p>
     * See the documentation for {@link #liesIn} for more information
     * on how the section is calculated.
     *
     * @param rect the rectangle
     * @param p the point the check
     * @param ltr {@code true} for left to right orientation,
     *        or {@code false} for right to left orientation
     * @param three {@code true} for three sections,
     *        or {@code false} for two
     *
     * @return the {@code Section} where the point lies
     *
     * @throws NullPointerException if {@code rect} or {@code p} are
     *         {@code null}
     */
    public static Section liesInHorizontal(Rectangle rect, Point p,
                                           boolean ltr, boolean three) {
        return liesIn(rect, p, true, ltr, three);
    }

    /**
     * This method divides a rectangle into two or three sections along
     * the vertical axis and determines which section the given point
     * lies in; used by drag and drop when calculating drop locations.
     * <p>
     * See the documentation for {@link #liesIn} for more information
     * on how the section is calculated.
     *
     * @param rect the rectangle
     * @param p the point the check
     * @param three {@code true} for three sections,
     *        or {@code false} for two
     *
     * @return the {@code Section} where the point lies
     *
     * @throws NullPointerException if {@code rect} or {@code p} are
     *         {@code null}
     */
    public static Section liesInVertical(Rectangle rect, Point p,
                                         boolean three) {
        return liesIn(rect, p, false, false, three);
    }
}
