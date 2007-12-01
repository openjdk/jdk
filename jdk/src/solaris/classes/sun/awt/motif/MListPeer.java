/*
 * Copyright 1995-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

class MListPeer extends MComponentPeer implements ListPeer {
    native void create(MComponentPeer parent);

    void initialize() {
        List li = (List)target;

        /* add any items that were already inserted in the target. */
        int  nitems = li.countItems();
        for (int i = 0; i < nitems; i++) {
            addItem(li.getItem(i), -1);
        }

        /* set whether this list should allow multiple selections. */
        setMultipleSelections(li.allowsMultipleSelections());

        /* make the visible position visible. */
        int index = li.getVisibleIndex();
        if (index >= 0) {
            makeVisible(index);
        }

        /* select the item if necessary. */
        int sel[] = li.getSelectedIndexes();
        for (int i = 0 ; i < sel.length ; i++) {
            select(sel[i]);
        }

        /* BugID 4060345 to avoid showing scrollbar in empty List */
        if (nitems == 0) {
            addItem(" ", 0);
            delItems(0, 0);
        }
        super.pSetScrollbarBackground(getParent_NoClientCode(li).getBackground());

        if (!target.isBackgroundSet()) {
            target.setBackground(SystemColor.text);
        }
        if (!target.isForegroundSet()) {
            target.setForeground(SystemColor.textText);
        }

        super.initialize();
    }

    MListPeer(List target) {
        super(target);
    }

    /* New method name for 1.1 */
    public void add(String item, int index) {
        addItem(item, index);
    }

    /* New method name for 1.1 */
    public void removeAll() {
        clear();
    }

    /* New method name for 1.1 */
    public void setMultipleMode (boolean b) {
        setMultipleSelections(b);
    }

    /* New method name for 1.1 */
    public Dimension getPreferredSize(int rows) {
        return preferredSize(rows);
    }

    /* New method name for 1.1 */
    public Dimension getMinimumSize(int rows) {
        return minimumSize(rows);
    }

    public void setForeground(Color c) {
        pSetInnerForeground(c);
    }

    public native void setBackground(Color c);
    public native void setMultipleSelections(boolean v);
    public native boolean isSelected(int index);
    public native void addItem(String item, int index);
    public native void delItems(int start, int end);
    public native void select(int index);
    public native void deselect(int index);
    public native void makeVisible(int index);

    public void clear() {
        List l = (List)target;
        int count = l.countItems();
        if (count > 0) {
            delItems(0, count-1);
        }
    }

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

    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void action(int index, final long when, final int modifiers) {
        final List list = (List)target;
        final int selectIndex = index;

        MToolkit.executeOnEventHandlerThread(list, new Runnable() {
            public void run() {
                list.select(selectIndex);
                postEvent(new ActionEvent(target, ActionEvent.ACTION_PERFORMED,
                                          list.getItem(selectIndex), when,
                                          modifiers));
            }
        });
    } // action()

    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleListChanged(int index) {
        final MListPeer listPeer = this;
        final List list = (List)target;
        final int listIndex = index;

        MToolkit.executeOnEventHandlerThread(list, new Runnable() {
            public void run() {
                int selected[] = listPeer.getSelectedIndexes();
                boolean isSelected = false;

                for (int i=0; i < selected.length; i++) {
                    if (listIndex == selected[i]) {
                        isSelected = true;
                        break;
                    }
                }
                postEvent(new ItemEvent(list, ItemEvent.ITEM_STATE_CHANGED,
                                Integer.valueOf(listIndex),
                                isSelected? ItemEvent.SELECTED : ItemEvent.DESELECTED));

            }
        });
    } // handleListChanged()

    public Dimension minimumSize() {
        return minimumSize(4);
    }

    public Dimension preferredSize(int v) {
        return minimumSize(v);
    }

    public Dimension minimumSize(int v) {
        FontMetrics fm = getFontMetrics(((List)target).getFont());
        return new Dimension(SCROLLBAR + 2*MARGIN +
                             fm.stringWidth("0123456789abcde"),
                             ((fm.getHeight()+2*SPACE) * v) +
                             2*MARGIN);
    }

    public boolean isFocusable() {
        return true;
    }

    /*
     * Print the native component by rendering the Motif look ourselves.
     * ToDo(aim): needs to query native motif for more accurate size and
     * color information, selected items, and item offset.
     */
    final static int    MARGIN = 2;
    final static int    SPACE = 1;
    final static int    SCROLLBAR = 16;
    int fontHeight;
    int fontAscent;
    int fontLeading;
    int vval;
    int hval;
    int vmax;
    int hmax;

    public void print(Graphics g) {
        List l = (List)target;
        Dimension d = l.size();
        Color bg = l.getBackground();
        Color fg = l.getForeground();
        int numItems = l.getItemCount();
        FontMetrics fm = getFontMetrics(l.getFont());
        int w, h;
        int vvis, hvis, vmin, hmin;
        int max = 0;

        for (int i = 0; i < numItems; i++) {
            int len = fm.stringWidth(l.getItem(i));
            max = Math.max(max, len);
        }

        fontHeight = fm.getHeight();
        fontAscent = fm.getAscent();
        fontLeading = fm.getLeading();

        hmin = vmin = 0;

        vvis = itemsInWindow(true);
        vmax = Math.max(numItems - vvis, 0);
        h = d.height - SCROLLBAR;

        if (vmax != 0) {
            w = d.width - SCROLLBAR;
            hvis = w - ((2 * SPACE) + (2 * MARGIN));
            hmax = Math.max(max - hvis, 0);
        } else {
            w = d.width;
            hvis = w - ((2 * SPACE) + (2 * MARGIN));
            hmax = Math.max(max - hvis, 0);
        }
        if (hmax == 0) {
            h = d.height;
            vvis = itemsInWindow(false);
            vmax = Math.max(numItems - vvis, 0);
        }
        if (vmax == 0) {
            w = d.width;
            hvis = w - ((2 * SPACE) + (2 * MARGIN));
            hmax = Math.max(max - hvis, 0);
        }

        hval = 0;
        vval = 0;
        /*
System.out.println("print List: "+d.width+"x"+d.height+" numItems="+numItems+
"max="+max+" vsb=("+vmin+".."+vmax+","+vval+","+vvis+
") hsb=("+hmin+".."+hmax+","+hval+","+hvis+")");
*/

        g.setColor(bg);
        g.fillRect(0, 0, w, h);

        if (hmax != 0) {
            int sbw = d.width - ((vmax == 0) ? 0 : SCROLLBAR);
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
        if (vmax != 0) {
            int sbh = d.height - ((hmax == 0) ? 0 : SCROLLBAR);
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

        draw3DRect(g, bg, 0, 0, w - 1, h - 1, false);

        if (numItems > 0) {
            int n = itemsInWindow(hmax != 0);
            int e = Math.min(numItems - 1, (vval + n) - 1);
            paintItems(g, bg, fg, vval, e);
        }

        target.print(g);
    }

    int itemsInWindow(boolean scrollbarVisible) {
        Dimension d = target.size();
        int h;
        if (scrollbarVisible) {
            h = d.height - ((2 * MARGIN) + SCROLLBAR);
        } else {
            h = d.height - 2*MARGIN;
        }
        int i = fontHeight - fontLeading;
        return h / (i + (2 * SPACE));
    }

    void paintItem(Graphics g, Color bg, Color fg, int index, boolean isSelected) {
        List l = (List)target;
        Dimension d = l.size();
        int numItems = l.getItemCount();
        Color shadow = bg.darker();

        if ((index < vval) || (index >= (vval + itemsInWindow(hmax != 0)))) {
            return;
        }
        int w = d.width - ((2 * MARGIN) + ((vmax != 0)? SCROLLBAR : 0));
        int h = (fontHeight - fontLeading);
        int htotal = h + (2 * SPACE);
        int index2y = MARGIN + (index * htotal) + SPACE;
        int y = index2y - (vval * htotal);
        int x = MARGIN + SPACE;
        Graphics ng = g.create();
        try {
            if (index > numItems - 1) {
                ng.setColor(bg);
                ng.fillRect(x - 2, y - 2, w, h + 4);
                return;
            }
            if (isSelected) {
                ng.setColor(shadow);
                ng.fillRect(x - 1, y - 1, w - 2, h + 2);
            } else {
                ng.setColor(bg);
                ng.fillRect(x - 1, y - 1, w - 2, h + 2);
            }
            ng.setColor(bg);

            ng.drawRect(x - 2, y - 2, w - 1, h + 3);
            ng.setColor(fg);
            String str = (String)l.getItem(index);
            ng.clipRect(x, y, w - (2 * SPACE), h);
            ng.drawString(str, x - hval, y + fontAscent);
        } finally {
            ng.dispose();
        }
    }

    void paintItems(Graphics g, Color bg, Color fg, int s, int e) {
        for (int i = s ; i <= e ; i++) {
          paintItem(g, bg, fg, i, false);
        }
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

    native void nativeHandleMouseWheel(int scrollType,
                                       int scrollAmount,
                                       int wheelRotation);
}
