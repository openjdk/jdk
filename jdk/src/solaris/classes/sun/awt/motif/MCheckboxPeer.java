/*
 * Copyright 1995-2000 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.event.*;

public class MCheckboxPeer extends MComponentPeer implements CheckboxPeer {
    private boolean inUpCall = false;
    private boolean inInit=false;

    native void create(MComponentPeer parent);
    native void pSetState(boolean state);
    native boolean pGetState();

    public native void setLabel(String label);
    public native void setCheckboxGroup(CheckboxGroup g);


    void initialize() {
        Checkbox t = (Checkbox)target;
        inInit=true;

        setState(t.getState());
        setCheckboxGroup(t.getCheckboxGroup());
        super.initialize();
        inInit=false;
    }

    public MCheckboxPeer(Checkbox target) {
        super(target);
    }

    public boolean isFocusable() {
        return true;
    }

    public void setState(boolean state) {
        if (inInit) {
                pSetState(state);
        } else if (!inUpCall && (state != pGetState())) {
                pSetState(state);
                // 4135725 : do not notify on programatic changes
                // notifyStateChanged(state);
        }
    }
    private native int getIndicatorSize();
    private native int getSpacing();

    public Dimension getMinimumSize() {
        String lbl = ((Checkbox)target).getLabel();
        if (lbl == null) {
            lbl = "";
        }
        FontMetrics fm = getFontMetrics(target.getFont());
        /*
         * Spacing (number of pixels between check mark and label text) is
         * currently set to 0, but in case it ever changes we have to add
         * it. 8 is a heuristic number. Indicator size depends on font
         * height, so we don't need to include it in checkbox's height
         * calculation.
         */
        int wdth = fm.stringWidth(lbl) + getIndicatorSize() + getSpacing() + 8;
        int hght = Math.max(fm.getHeight() + 8, 15);
        return new Dimension(wdth, hght);
    }


    void notifyStateChanged(boolean state) {
        Checkbox cb = (Checkbox) target;
        ItemEvent e = new ItemEvent(cb,
                          ItemEvent.ITEM_STATE_CHANGED,
                          cb.getLabel(),
                          state ? ItemEvent.SELECTED : ItemEvent.DESELECTED);
        postEvent(e);
    }


    // NOTE: This method is called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    void action(boolean state) {
        final Checkbox cb = (Checkbox)target;
        final boolean newState = state;
        MToolkit.executeOnEventHandlerThread(cb, new Runnable() {
            public void run() {
                CheckboxGroup cbg = cb.getCheckboxGroup();
                /* Bugid 4039594. If this is the current Checkbox in
                 * a CheckboxGroup, then return to prevent deselection.
                 * Otherwise, it's logical state will be turned off,
                 * but it will appear on.
                 */
                if ((cbg != null) && (cbg.getSelectedCheckbox() == cb) &&
                    cb.getState()) {
                  inUpCall = false;
                  cb.setState(true);
                  return;
                }
                // All clear - set the new state
                cb.setState(newState);
                notifyStateChanged(newState);
            } // run()
        });
    } // action()



    static final int SIZE = 19;
    static final int BORDER = 4;
    static final int SIZ = SIZE - BORDER*2 - 1;

    /*
     * Print the native component by rendering the Motif look ourselves.
     * ToDo(aim): needs to query native motif for more accurate size and
     * color information; need to render check mark.
     */
    public void print(Graphics g) {
        Checkbox cb = (Checkbox)target;
        Dimension d = cb.size();
        Color bg = cb.getBackground();
        Color fg = cb.getForeground();
        Color shadow = bg.darker();
        int x = BORDER;
        int y = ((d.height - SIZE) / 2) + BORDER;

        g.setColor(cb.getState()? shadow : bg);

        if (cb.getCheckboxGroup() != null) {
            g.fillOval(x, y, SIZ, SIZ);
            draw3DOval(g, bg, x, y, SIZ, SIZ, !(cb.getState()));
            if (cb.getState()) {
                g.setColor(fg);
                g.fillOval(x + 3, y + 3, SIZ - 6, SIZ - 6);
            }
        } else {
            g.fillRect(x, y, SIZ, SIZ);
            draw3DRect(g, bg, x, y, SIZ, SIZ, !(cb.getState()));
            if (cb.getState()) {
                g.setColor(fg);
                g.drawLine(x+1, y+1, x+SIZ-1, y+SIZ-1);
                g.drawLine(x+1, y+SIZ-1, x+SIZ-1, y+1);
            }
        }
        g.setColor(fg);
        String lbl = cb.getLabel();
        if (lbl != null) {
            // REMIND: align
            g.setFont(cb.getFont());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(lbl, SIZE,
                         (d.height + fm.getMaxAscent() - fm.getMaxDescent()) / 2);
        }

        target.print(g);
    }

    /**
     * DEPRECATED
     */
    public Dimension minimumSize() {
            return getMinimumSize();
    }

}
