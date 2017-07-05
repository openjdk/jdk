/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */
package com.sun.hotspot.igv.view.widgets;

import com.sun.hotspot.igv.graph.InputSlot;
import com.sun.hotspot.igv.view.DiagramScene;
import java.awt.Point;
import java.util.List;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Thomas Wuerthinger
 */
public class InputSlotWidget extends SlotWidget {

    private InputSlot inputSlot;

    public InputSlotWidget(InputSlot slot, DiagramScene scene, Widget parent, FigureWidget fw) {
        super(slot, scene, parent, fw);
        inputSlot = slot;
        init();
        getFigureWidget().getLeftWidget().addChild(this);
    }

    public InputSlot getInputSlot() {
        return inputSlot;
    }

    protected Point calculateRelativeLocation() {
        if (getFigureWidget().getBounds() == null) {
            return new Point(0, 0);
        }

        double x = 0;
        List<InputSlot> slots = inputSlot.getFigure().getInputSlots();
        assert slots.contains(inputSlot);
        return new Point((int) x, (int) (calculateRelativeY(slots.size(), slots.indexOf(inputSlot))));
    }
}
