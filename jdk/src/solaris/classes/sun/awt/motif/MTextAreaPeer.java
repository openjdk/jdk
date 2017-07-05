/*
 * Copyright 1995-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.motif;

import java.awt.*;
import java.awt.peer.*;
import java.awt.event.TextEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.datatransfer.*;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import java.util.Vector;
import java.awt.im.InputMethodRequests;


public class MTextAreaPeer extends MComponentPeer implements TextAreaPeer {
    native void pCreate(MComponentPeer parent);

    private boolean firstChangeSkipped;

    /**
     * Initialize JNI field and method IDs
     */
    private static native void initIDs();

    static {
        initIDs();
    }

    void create(MComponentPeer parent) {
        firstChangeSkipped = false;
        pCreate(parent);
    }

    void initialize() {
        int start, end;

        TextArea txt = (TextArea)target;
        String  text;

        if ((text = txt.getText()) != null) {
            setText(text);
        }

        start = txt.getSelectionStart();
        end = txt.getSelectionEnd();

        if (end > start) {
            select(start, end);
        } else {
            setCaretPosition(start);
        }

        super.pSetScrollbarBackground(getParent_NoClientCode(target).getBackground());

        if (!target.isBackgroundSet()) {
            // This is a way to set the background color of the TextArea
            // without calling setBackground - go through native C code
            setTargetBackground(SystemColor.text);
        }
        if (!target.isForegroundSet()) {
            target.setForeground(SystemColor.textText);
        }

        setEditable(txt.isEditable());

//      oldSelectionStart = -1; // accessibility support
//      oldSelectionEnd = -1;   // accessibility support

        super.initialize();
    }

    public MTextAreaPeer(TextArea target) {
        super(target);
    }

    public void setEditable(boolean editable) {
        pSetEditable(editable);

        /* 4136955 - Calling setBackground() here works around an Xt
         * bug by forcing Xt to flush an internal widget cache
         */
        setBackground(target.getBackground());
    }
    public void setBackground(Color c) {
        setTextBackground(c);
    }
    public void setForeground(Color c) {
        pSetInnerForeground(c);
    }

    native int getExtraWidth();
    native int getExtraHeight();
    public native void setTextBackground(Color c);
    public native void pSetEditable(boolean e);
    public native void select(int selStart, int selEnd);
    public native int getSelectionStart();
    public native int getSelectionEnd();
    public native void setText(String txt);
    public native String getText();
    public native void insert(String txt, int pos);
    public native void replaceRange(String txt, int start, int end);
    public native void setFont(Font f);
    public native void setCaretPosition(int pos);
    public native int getCaretPosition();
    public native void pSetCursor(Cursor c);
    native void pShow2();
    native void pMakeCursorVisible();


    public Dimension getMinimumSize() {
        return getMinimumSize(10, 60);
    }
    public Dimension getPreferredSize(int rows, int cols) {
        return getMinimumSize(rows, cols);
    }
    public Dimension getMinimumSize(int rows, int cols) {
        FontMetrics fm = getFontMetrics(target.getFont());

        /* Calculate proper size for text area plus scrollbars.
         *   - Motif allows NO leading in its text areas ...
         *   - extra width and height counts everything outside the
         *     usable text space.
         * (bug 4103248, 4120310):
         *   - Motif uses maxAscent + maxDescent, not ascent + descent.
         */
        int colWidth = fm.charWidth('0');
        int rowHeight = fm.getMaxAscent() + fm.getMaxDescent();
        return new Dimension(cols * colWidth + getExtraWidth(),
                             rows * rowHeight + getExtraHeight());
    }
    public boolean isFocusable() {
        return true;
    }

    // Called from native widget when paste key is pressed and we
    // already own the selection (prevents Motif from hanging while
    // waiting for the selection)
    //
    public void pasteFromClipboard() {
        Clipboard clipboard = target.getToolkit().getSystemClipboard();

        Transferable content = clipboard.getContents(this);
        if (content != null) {
            try {
                String data = (String)(content.getTransferData(DataFlavor.stringFlavor));
                // fix for 4401853: to clear TextArea selection if null is pasted
                data = (data == null ? "" : data);
                replaceRange(data, getSelectionStart(), getSelectionEnd());

            } catch (Exception e) {
            }
        }
    }

    /*
     * Print the native component by rendering the Motif look ourselves.
     * ToDo(aim): needs to query native motif for more accurate size and
     * color information, the top/left text offsets, and selected text.
     */
    static final int MARGIN = 2;
    static final int BORDER = 1;
    static final int SCROLLBAR = 16;
    int fontHeight;
    int fontAscent;
    int fontLeading;
    int topLine = 0;
    int numLines = 0;
    int textLength = 0;
    Vector lines;
    int selStart = 0;
    int selEnd = 0;
    int movedRight = 0;

    // the following vars are assigned in print() method
    transient boolean hscrollbar;
    transient boolean vscrollbar;

    public void print(Graphics g) {
        TextArea area = (TextArea)target;
        Dimension d = area.size();
        Color bg = area.getBackground();
        Color fg = area.getForeground();
        FontMetrics fm = getFontMetrics(area.getFont());
        int vmin, vmax, vval, vvis;
        int hmin, hmax, hval, hvis;
        int max = 0;

        /*
          Doesn't work right yet.
        selStart = area.getSelectionStart();
        selEnd = area.getSelectionEnd();
        */

        // Figure out number of lines and max line length
        String text = area.getText();
        textLength = text.length();
        BufferedReader is = new BufferedReader(new StringReader(text));
        String line;
        int pos = 0;
        lines = new Vector();
        int sv = ((TextArea)target).getScrollbarVisibility();
        vscrollbar = (sv == TextArea.SCROLLBARS_BOTH ||
                sv == TextArea.SCROLLBARS_VERTICAL_ONLY);
        hscrollbar = (sv == TextArea.SCROLLBARS_BOTH ||
                sv == TextArea.SCROLLBARS_HORIZONTAL_ONLY);
        boolean wrap = !hscrollbar;
        int w = d.width - (vscrollbar ? SCROLLBAR : 0);
        int h = d.height - (hscrollbar ? SCROLLBAR : 0);

        try {
            numLines = 0;
            while((line = is.readLine()) != null) {
                int len = fm.stringWidth(line);
                if (len > w && wrap) {
                   // need to do line wrapping
                   int start = 0;
                   int end = 0;
                   int string_length = line.length();
                   while (true) {
                       int line_width = 0;
                       end = start + 1; // at least one character per line
                       while (end < string_length) {
                               char c = line.charAt(end);
                               int cw = fm.charWidth(c);
                               if (line_width + cw + 10 > w) // +10?
                                       break;
                               line_width += cw;
                               end++;
                       }
                       // form a line from start to end (not including end)
                       String substr = line.substring(start, end);
                       // System.out.println("wrap line: " + substr);
                       TextLine tline = new TextLine();
                       tline.text = substr;
                       tline.pos = pos + start;
                       lines.addElement(tline);
                       start = end;
                       max = Math.max(max, len);
                       numLines ++;
                       if (end == string_length) {
                           // we have processed the whole string
                           pos += line.length() + 1; // +1 for the ending \n ?
                           break;
                       }
                   }
                } else {
                TextLine tline = new TextLine();
                tline.text = line;
                tline.pos = pos;
                lines.addElement(tline);
                pos += line.length() + 1;

                max = Math.max(max, len);
                numLines++;
                }
            }
            is.close();

        } catch (IOException e) {
        }


        fontHeight = fm.getHeight();
        fontAscent = fm.getAscent();
        fontLeading = fm.getLeading();

        hmin = vmin = 0;

        vvis = linesInWindow(true);
        vmax = Math.max(numLines - vvis, 0);
        vval = 0;

        hvis = w - (2 * MARGIN);
        hmax = Math.max(max - hvis, 0);
        hval = 0;

        g.setColor(bg);
        g.fillRect(BORDER, BORDER, w, h);
        if (vscrollbar)
        {
            int sbh = d.height - (hscrollbar ? SCROLLBAR : 0);
            g.fillRect(d.width - SCROLLBAR - 3, 1, SCROLLBAR - 3, sbh - 1);
            Graphics ng = g.create();
            try {
                ng.translate(d.width - (SCROLLBAR - 2), 0);
                drawScrollbar(ng, bg, SCROLLBAR - 2, sbh,
                              vmin, vmax, vval, vvis, false);
            } finally {
                ng.dispose();
            }
        }
        if (hscrollbar)
        {
            int sbw = d.width - (vscrollbar ? SCROLLBAR : 0);
            g.fillRect(1, d.height - SCROLLBAR - 3, sbw - 1, SCROLLBAR - 3);
            Graphics ng = g.create();
            try {
                ng.translate(0, d.height - (SCROLLBAR - 2));
                drawScrollbar(ng, bg, SCROLLBAR - 2, sbw,
                              hmin, hmax, hval, hvis, true);
            } finally {
                ng.dispose();
            }
        }

        draw3DRect(g, bg, 0, 0, w - 1, h - 1, false);

        if (text != null) {
            int l = linesInWindow(true);
            h = d.height - ((2 * MARGIN) + SCROLLBAR);
            int e = Math.min(numLines - 1, (topLine + l) - 1);
            paintLines(g, bg, fg, topLine, e);
        }


        target.print(g);
    }

    int linesInWindow(boolean horizScrollbar) {
        Dimension d = target.size();
        int htotal = d.height - ((2 * MARGIN) + (horizScrollbar? SCROLLBAR : 0));
        return htotal / fontHeight;
    }

    void paintLines(Graphics g, Color bg, Color fg, int s, int e) {
        Dimension d = target.size();
        int w = d.width - ((2 * BORDER) + (vscrollbar ? SCROLLBAR : 0));
        int h = d.height - ((2 * BORDER) + (hscrollbar ? SCROLLBAR : 0));
        int lm = linesInWindow(true) + topLine;
        s = Math.max(topLine, s);
        e = Math.min(e, lm - 1);
        Graphics ng = g.create();
        try {
            ng.clipRect(BORDER + MARGIN, MARGIN + BORDER, w - (2*MARGIN),
                        h - (2*MARGIN));
            ng.setFont(target.getFont());
            for (int i = s ; i <= e; i++) {
                paintLine(ng, bg, fg, i);
            }
        } finally {
            ng.dispose();
        }
    }

    void paintLine(Graphics g, Color bg, Color fg, int lnr) {
        Dimension d = target.size();
        int l = linesInWindow(true);

        if((lnr < topLine) || (lnr >= l + topLine)) {
            return;
        }
        int w = d.width - ((2 * BORDER) + (hscrollbar ? SCROLLBAR : 0));
        int y = MARGIN + fontLeading + ((lnr - topLine) * fontHeight);
        String text = ((TextLine)lines.elementAt(lnr)).text;
        int len = text.length();

        if (lnr > numLines - 1) {
            g.setColor(bg);
            g.fillRect(BORDER, y - fontLeading, w, fontHeight);
            return;
        }
        int s = 0;
        int e = (lnr < numLines - 1) ? len : textLength;
        int xs = pos2x(selStart) - movedRight;
        int xe = pos2x(selEnd) - movedRight;

        Color highlight = bg.brighter();
        if ((selStart < s) && (selEnd > e)) {
            g.setColor(highlight);
            g.fillRect(BORDER, y - fontLeading, w, fontHeight);
        } else {
            g.setColor(bg);
            g.fillRect(BORDER, y - fontLeading, w, fontHeight);

            if ((selStart >= s) && (selStart <= e)) {
                g.setColor(highlight);

                if (selEnd > e) {
                    g.fillRect(xs, y - fontLeading, (w + BORDER) - xs, fontHeight);
                } else if (selStart == selEnd) {
                  //g.fillRect(xs, y - fontLeading, 1, fontHeight);
                } else {
                    g.fillRect(xs, y - fontLeading, xe - xs, fontHeight);
                }
            } else if ((selEnd >= s) && (selEnd <= e)) {
                g.setColor(highlight);
                g.fillRect(BORDER, y - fontLeading, xe - BORDER, fontHeight);
            }
        }
        g.setColor(fg);
        g.drawString(text, MARGIN - movedRight, y + fontAscent);
    }

    int pos2x(int pos) {
        FontMetrics fm = getFontMetrics(target.getFont());
        int widths[] = fm.getWidths();
        TextLine tl1 = (TextLine)lines.elementAt(0);
        TextLine tl2;
        int l = 0;
        for (int i = 0; i < lines.size() - 1; i++) {
            tl2 = (TextLine)lines.elementAt(i+1);
            if (pos >= tl1.pos && pos < tl2.pos) {
                l = i;
                break;
            }
            tl1 = tl2;
        }
        int x = MARGIN;
        for (int i = 0 ; i < (pos - tl1.pos - 1) ; i++) {
            x += widths[tl1.text.charAt(i)];
        }
        return x;
    }

    /**
     * DEPRECATED
     */
    public void insertText(String txt, int pos) {
        insert(txt, pos);
    }

    /**
     * DEPRECATED
     */
    public void replaceText(String txt, int start, int end) {
        replaceRange(txt, start, end);
    }

    /**
     * DEPRECATED
     */
    public Dimension minimumSize() {
        return getMinimumSize();
    }

    /**
     * DEPRECATED
     */
    public Dimension preferredSize(int rows, int cols) {
        return getPreferredSize(rows, cols);
    }

    /**
     * DEPRECATED
     */
    public Dimension minimumSize(int rows, int cols) {
        return getMinimumSize(rows, cols);
    }

    /*
     * Post a new TextEvent when the value of a text component changes.
     */
    public void valueChanged() {
        postEvent(new TextEvent(target, TextEvent.TEXT_VALUE_CHANGED));
    }

    void pShow(){
      pShow2();
      notifyTextComponentChange(true);
    }

    void pHide(){
      notifyTextComponentChange(false);
      super.pHide();
    }

    void pDispose(){
      notifyTextComponentChange(false);
      super.pDispose();
    }

    public boolean handlesWheelScrolling() {return true;}

    public void handleEvent(AWTEvent e) {
        if (e.getID() == MouseEvent.MOUSE_WHEEL) {
            MouseWheelEvent mwe = (MouseWheelEvent)e;
            nativeHandleMouseWheel(mwe.getScrollType(),
                                   mwe.getScrollAmount(),
                                   mwe.getWheelRotation());
        }
        else {
            super.handleEvent(e);
        }
    }

     public InputMethodRequests getInputMethodRequests() {
            return null;
      }



    native void nativeHandleMouseWheel(int scrollType,
                                       int scrollAmount,
                                       int wheelRotation);

    //
    // Accessibility support
    //


    // stub functions: to be fully implemented in a future release
    public int getIndexAtPoint(int x, int y) { return -1; }
    public Rectangle getCharacterBounds(int i) { return null; }
    public long filterEvents(long mask) { return 0; }

/*  To be fully implemented in a future release

    int oldSelectionStart;
    int oldSelectionEnd;

    public native int getIndexAtPoint(int x, int y);
    public native Rectangle getCharacterBounds(int i);
    public native long filterEvents(long mask);

    /**
     * Handle a change in the text selection endpoints
     * (Note: could be simply a change in the caret location)
     *
    public void selectionValuesChanged(int start, int end) {
        return;  // Need to write implementation of this.
    }
*/
}


class TextLine {
    String text;
    int pos;
}
