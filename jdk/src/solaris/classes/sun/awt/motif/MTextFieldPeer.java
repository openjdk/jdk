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
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.event.TextEvent;
import java.awt.im.InputMethodRequests;


public class MTextFieldPeer extends MComponentPeer implements TextFieldPeer {
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

        TextField txt = (TextField)target;

        setText(txt.getText());
        if (txt.echoCharIsSet()) {
            setEchoChar(txt.getEchoChar());
        }

        start = txt.getSelectionStart();
        end = txt.getSelectionEnd();

        if (end > start) {
            select(start, end);
        } else {
            setCaretPosition(start);
        }

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

    public MTextFieldPeer(TextField target) {
        super(target);
    }

    public void setEditable(boolean editable) {
        pSetEditable(editable);

        /* 4136955 - Calling setBackground() here works around an Xt
         * bug by forcing Xt to flush an internal widget cache
         */
        setBackground(target.getBackground());
    }

    public native void pSetEditable(boolean editable);
    public native void select(int selStart, int selEnd);
    public native int getSelectionStart();
    public native int getSelectionEnd();
    public native void setText(String l);
    public native void insertReplaceText(String l);
    public native void preDispose();
    public native String getText();
    public native void setEchoChar(char c);
    public native void setFont(Font f);
    public native void setCaretPosition(int pos);
    public native int getCaretPosition();

    // CDE/Motif defaults: margin=5, shadow=2, highlight=1 -- times 2.
    // Should have asked the widgets for correct values (see MTextAreaPeer).
    private static final int padding = 16;

    public Dimension getMinimumSize() {
        FontMetrics fm = getFontMetrics(target.getFont());
        return new Dimension(fm.stringWidth(((TextField)target).getText())+20,
                             fm.getMaxDescent() + fm.getMaxAscent() + padding);
    }

    public Dimension getPreferredSize(int cols) {
        return getMinimumSize(cols);
    }

    public Dimension getMinimumSize(int cols) {
        FontMetrics fm = getFontMetrics(target.getFont());
        return new Dimension(fm.charWidth('0') * cols + 20,
                             fm.getMaxDescent() + fm.getMaxAscent() + padding);
    }

    public boolean isFocusable() {
        return true;
    }

    // NOTE: This method is called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void action(final long when, final int modifiers) {
        MToolkit.executeOnEventHandlerThread(target, new Runnable() {
            public void run() {
                postEvent(new ActionEvent(target, ActionEvent.ACTION_PERFORMED,
                                          ((TextField)target).getText(), when,
                                          modifiers));
            }
        });
    }

    protected void disposeImpl() {
        preDispose();
        super.disposeImpl();
    }

    /*
     * Post a new TextEvent when the value of a text component changes.
     */
    public void valueChanged() {
        postEvent(new TextEvent(target, TextEvent.TEXT_VALUE_CHANGED));
    }

    // Called from native widget when paste key is pressed and we
    // already own the selection (prevents Motif from hanging while
    // waiting for the selection)
    //
    // NOTE: This method is called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void pasteFromClipboard() {
        Clipboard clipboard = target.getToolkit().getSystemClipboard();

        Transferable content = clipboard.getContents(this);
        if (content != null) {
            try {
                String data = (String)(content.getTransferData(DataFlavor.stringFlavor));
                insertReplaceText(data);

            } catch (Exception e) {
            }
        }
    }

    /*
     * Print the native component by rendering the Motif look ourselves.
     * ToDo(aim): needs to query native motif for more accurate size and
     * color information, left text offset, and selected text.
     */
    public final static int BORDER = 2;
    public final static int MARGIN = 4;

    public void print(Graphics g) {
        TextField txt = (TextField)target;
        Dimension d = txt.size();
        int w = d.width - (2 * BORDER);
        int h = d.height - (2 * BORDER);
        Color bg = txt.getBackground();
        Color fg = txt.getForeground();
        Color highlight = bg.brighter();
        String text = txt.getText();
        int moved = 0;
        int selStart = 0;
        int selEnd = 0;

        g.setFont(txt.getFont());
        g.setColor(txt.isEditable() ? highlight : bg);
        g.fillRect(BORDER, BORDER, w, h);

        g.setColor(bg);
        //g.drawRect(0, 0, d.width-1, d.height-1);
        draw3DRect(g, bg, 1, 1, d.width-3, d.height-3, false);

        if (text != null) {
            g.clipRect(BORDER, MARGIN, w, d.height - (2 * MARGIN));
            FontMetrics fm = g.getFontMetrics();

            w = d.width - BORDER;
            h = d.height - (2 * MARGIN);
            int xs = pos2x(selStart) - moved;
            int xe = pos2x(selEnd) - moved;

            if ((xs < MARGIN) && (xe > w)) {
                g.setColor(highlight);
                g.fillRect(BORDER, MARGIN, w - BORDER, h);
            } else {
                g.setColor(bg);
                //g.fillRect(BORDER, MARGIN, w - BORDER, h);

                if ((xs >= MARGIN) && (xs <= w)) {
                    g.setColor(highlight); // selected text

                    if (xe > w) {
                        g.fillRect(xs, MARGIN, w - xs, h);
                    } else if (xs == xe) {
                      //g.fillRect(xs, MARGIN, 1, h);
                    } else {
                        g.fillRect(xs, MARGIN, xe - xs, h);
                    }
                } else if ((xe >= MARGIN) && (xe <= w)) {
                    g.setColor(highlight);
                    g.fillRect(BORDER, MARGIN, xe - BORDER, h);
                }
            }
           g.setColor(fg);
           int x = MARGIN - moved;
           char echoChar = txt.getEchoChar();
           if (echoChar == 0) {
               g.drawString(text, x, BORDER + MARGIN + fm.getMaxAscent());
           } else {
               char data[] = new char[text.length()];
               for (int i = 0 ; i < data.length ; i++) {
                   data[i] = echoChar;
               }
               g.drawChars(data, 0, data.length, x,
                           BORDER + MARGIN + fm.getMaxAscent());

           }
        }

        target.print(g);
    }

    int pos2x(int pos) {
        TextField txt = (TextField)target;
        FontMetrics fm = getFontMetrics(txt.getFont());
        int x = MARGIN, widths[] = fm.getWidths();
        String text = txt.getText();
        char echoChar = txt.getEchoChar();
        if (echoChar == 0) {
            for (int i = 0 ; i < pos ; i++) {
                x += widths[text.charAt(i)];
            }
        } else {
            x += widths[echoChar] * pos;
        }
        return x;
    }

    /**
     * DEPRECATED
     */
    public void setEchoCharacter(char c) {
        setEchoChar(c);
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
    public Dimension minimumSize(int cols) {
        return getMinimumSize(cols);
    }

    /**
     * DEPRECATED
     */
    public Dimension preferredSize(int cols) {
        return getPreferredSize(cols);
    }
    void pShow(){
      super.pShow();
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

     public InputMethodRequests getInputMethodRequests() {
            return null;
      }



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
        return;  // Need to write implemetation of this.
    }
*/

}
