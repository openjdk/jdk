/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
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
package sun.awt.windows;

import java.awt.*;
import java.awt.peer.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;

final class WListPeer extends WComponentPeer implements ListPeer {

    @Override
    public boolean isFocusable() {
        return true;
    }

    // ListPeer implementation

    @Override
    public int[] getSelectedIndexes() {
        List l = (List)target;
        int len = l.countItems();
        int sel[] = new int[len];
        int nsel = 0;
        for (int i = 0 ; i < len ; i++) {
            if (isSelected(i)) {
                sel[nsel++] = i;
            }
        }
        int selected[] = new int[nsel];
        System.arraycopy(sel, 0, selected, 0, nsel);
        return selected;
    }

    /* New method name for 1.1 */
    @Override
    public void add(String item, int index) {
        addItem(item, index);
    }

    /* New method name for 1.1 */
    @Override
    public void removeAll() {
        clear();
    }

    /* New method name for 1.1 */
    @Override
    public void setMultipleMode (boolean b) {
        setMultipleSelections(b);
    }

    /* New method name for 1.1 */
    @Override
    public Dimension getPreferredSize(int rows) {
        return preferredSize(rows);
    }

    /* New method name for 1.1 */
    @Override
    public Dimension getMinimumSize(int rows) {
        return minimumSize(rows);
    }

    private FontMetrics   fm;
    public void addItem(String item, int index) {
        addItems(new String[] {item}, index, fm.stringWidth(item));
    }
    native void addItems(String[] items, int index, int width);

    @Override
    public native void delItems(int start, int end);
    public void clear() {
        List l = (List)target;
        delItems(0, l.countItems());
    }
    @Override
    public native void select(int index);
    @Override
    public native void deselect(int index);
    @Override
    public native void makeVisible(int index);
    public native void setMultipleSelections(boolean v);
    public native int  getMaxWidth();

    public Dimension preferredSize(int v) {
        if ( fm == null ) {
            List li = (List)target;
            fm = getFontMetrics( li.getFont() );
        }
        Dimension d = minimumSize(v);
        d.width = Math.max(d.width, getMaxWidth() + 20);
        return d;
    }
    public Dimension minimumSize(int v) {
        return new Dimension(20 + fm.stringWidth("0123456789abcde"),
                             (fm.getHeight() * v) + 4); // include borders
    }

    // Toolkit & peer internals

    WListPeer(List target) {
        super(target);
    }

    @Override
    native void create(WComponentPeer parent);

    @Override
    void initialize() {
        List li = (List)target;

        fm = getFontMetrics( li.getFont() );

        // Fixed 6336384: setFont should be done before addItems
        Font  f = li.getFont();
        if (f != null) {
            setFont(f);
        }

        // add any items that were already inserted in the target.
        int  nitems = li.countItems();
        if (nitems > 0) {
            String[] items = new String[nitems];
            int maxWidth = 0;
            int width = 0;
            for (int i = 0; i < nitems; i++) {
                items[i] = li.getItem(i);
                width = fm.stringWidth(items[i]);
                if (width > maxWidth) {
                    maxWidth = width;
                }
            }
            addItems(items, 0, maxWidth);
        }

        // set whether this list should allow multiple selections.
        setMultipleSelections(li.allowsMultipleSelections());

        // select the item if necessary.
        int sel[] = li.getSelectedIndexes();
        for (int i = 0 ; i < sel.length ; i++) {
            select(sel[i]);
        }

        // make the visible position visible.
        // fix for 4676536 by kdm@sparc.spb.su
        // we should call makeVisible() after we call select()
        // because of a bug in Windows which is manifested by
        // incorrect scrolling of the selected item if the list
        // height is less than an item height of the list.
        int index = li.getVisibleIndex();
        if (index < 0 && sel.length > 0) {
            index = sel[0];
        }
        if (index >= 0) {
            makeVisible(index);
        }

        super.initialize();
    }

    @Override
    public boolean shouldClearRectBeforePaint() {
        return false;
    }

    private native void updateMaxItemWidth();

    /*public*/ native boolean isSelected(int index);

    // update the fontmetrics when the font changes
    @Override
    synchronized void _setFont(Font f)
    {
        super._setFont( f );
            fm = getFontMetrics( ((List)target).getFont() );
        updateMaxItemWidth();
    }

    // native callbacks

    void handleAction(final int index, final long when, final int modifiers) {
        final List l = (List)target;
        WToolkit.executeOnEventHandlerThread(l, new Runnable() {
            @Override
            public void run() {
                l.select(index);
                postEvent(new ActionEvent(target, ActionEvent.ACTION_PERFORMED,
                                          l.getItem(index), when, modifiers));
            }
        });
    }

    void handleListChanged(final int index) {
        final List l = (List)target;
        WToolkit.executeOnEventHandlerThread(l, new Runnable() {
            @Override
            public void run() {
                postEvent(new ItemEvent(l, ItemEvent.ITEM_STATE_CHANGED,
                                Integer.valueOf(index),
                                isSelected(index)? ItemEvent.SELECTED :
                                                   ItemEvent.DESELECTED));

            }
        });
    }
}
