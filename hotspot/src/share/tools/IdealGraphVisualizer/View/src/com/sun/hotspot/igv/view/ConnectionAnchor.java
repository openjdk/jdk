/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */
package com.sun.hotspot.igv.view;

import com.sun.hotspot.igv.view.widgets.SlotWidget;
import java.awt.Point;
import java.awt.Rectangle;
import org.netbeans.api.visual.anchor.Anchor;
import org.netbeans.api.visual.anchor.Anchor.Entry;
import org.netbeans.api.visual.anchor.Anchor.Result;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Thomas Wuerthinger
 */
public class ConnectionAnchor extends Anchor {

    public enum HorizontalAlignment {

        Left,
        Center,
        Right
    }
    private HorizontalAlignment alignment;

    public ConnectionAnchor(Widget widget) {
        this(HorizontalAlignment.Center, widget);
    }

    public ConnectionAnchor(HorizontalAlignment alignment, Widget widget) {
        super(widget);
        this.alignment = alignment;
    }

    public Result compute(Entry entry) {
        return new Result(getRelatedSceneLocation(), Anchor.DIRECTION_ANY);
    }

    @Override
    public Point getRelatedSceneLocation() {
        Point p = null;
        Widget w = getRelatedWidget();
        if (w != null) {
            if (w instanceof SlotWidget) {
                p = ((SlotWidget) w).getAnchorPosition();
            } else {
                Rectangle r = w.convertLocalToScene(w.getBounds());
                int y = r.y + r.height / 2;
                int x = r.x;
                if (alignment == HorizontalAlignment.Center) {
                    x = r.x + r.width / 2;
                } else if (alignment == HorizontalAlignment.Right) {
                    x = r.x + r.width;
                }

                p = new Point(x, y);
            }
        }

        return p;
    }
}
