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
import java.awt.event.ItemEvent;

class MChoicePeer extends MComponentPeer implements ChoicePeer {
    boolean inUpCall=false;

    native void create(MComponentPeer parent);
    native void pReshape(int x, int y, int width, int height);
    native void pSelect(int index, boolean init);
    native void appendItems(String[] items);

    void initialize() {
        Choice opt = (Choice)target;
        int itemCount = opt.countItems();
        String[] items = new String[itemCount];
        for (int i=0; i < itemCount; i++) {
            items[i] = opt.getItem(i);
        }
        if (itemCount > 0) {
            appendItems(items);
            pSelect(opt.getSelectedIndex(), true);
        }
        super.initialize();
    }

    public MChoicePeer(Choice target) {
        super(target);
    }

    public boolean isFocusable() {
        return true;
    }

    public Dimension getMinimumSize() {
        FontMetrics fm = getFontMetrics(target.getFont());
        Choice c = (Choice)target;
        int w = 0;
        for (int i = c.countItems() ; i-- > 0 ;) {
            w = Math.max(fm.stringWidth(c.getItem(i)), w);
        }
        return new Dimension(32 + w, Math.max(fm.getHeight() + 8, 15) + 5);
    }

    public native void setFont(Font f);

    public void add(String item, int index) {
        addItem(item, index);
        // Adding an item can change the size of the Choice, so do
        // a reshape, based on the font size.
        Rectangle r = target.getBounds();
        reshape(r.x, r.y, 0, 0);
    }

    public native void remove(int index);

    public native void removeAll();

    /**
     * DEPRECATED, but for now, called by add(String, int).
     */
    public native void addItem(String item, int index);

    // public native void remove(int index);

    public native void setBackground(Color c);

    public native void setForeground(Color c);

    public void select(int index) {
        if (!inUpCall) {
            pSelect(index, false);
        }
    }

    void notifySelection(String item) {
        Choice c = (Choice)target;
        ItemEvent e = new ItemEvent(c, ItemEvent.ITEM_STATE_CHANGED,
                                item, ItemEvent.SELECTED);
        postEvent(e);
    }


    // NOTE: This method is called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    void action(final int index) {
        final Choice c = (Choice)target;
        inUpCall = false;  /* Used to prevent native selection. */
        MToolkit.executeOnEventHandlerThread(c, new Runnable() {
            public void run() {
                String item;
                synchronized(c) {
                    if (index >= c.getItemCount()) {
                        /* Nothing to do when the list is too short */
                        return;
                    }
                    inUpCall = true;       /* Prevent native selection. */
                    c.select(index);       /* set value in target */
                    item = c.getItem(index);
                    inUpCall = false;
                }
                notifySelection(item);
            }
        });
    }

    /*
     * Print the native component by rendering the Motif look ourselves.
     * ToDo(aim): needs to query native motif for more accurate size and
     * color information.
     */
    public void print(Graphics g) {
        Choice ch = (Choice)target;
        Dimension d = ch.size();
        Color bg = ch.getBackground();
        Color fg = ch.getForeground();

        g.setColor(bg);
        g.fillRect(2, 2, d.width-1, d.height-1);
        draw3DRect(g, bg, 1, 1, d.width-2, d.height-2, true);
        draw3DRect(g, bg, d.width - 18, (d.height / 2) - 3, 10, 6, true);

        g.setColor(fg);
        g.setFont(ch.getFont());
        FontMetrics fm = g.getFontMetrics();
        String lbl = ch.getSelectedItem();
        if (lbl == null){
            lbl = "";
        }
        if (lbl != ""){
            g.drawString(lbl, 5, (d.height + fm.getMaxAscent()-fm.getMaxDescent())/2);
        }

        target.print(g);
    }

    /**
     * DEPRECATED
     */
    public Dimension minimumSize() {
            return getMinimumSize();
    }

    protected void disposeImpl() {
        freeNativeData();
        super.disposeImpl();
    }

    private native void freeNativeData();
}
