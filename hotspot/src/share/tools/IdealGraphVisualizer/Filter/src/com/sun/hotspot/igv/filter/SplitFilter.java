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
package com.sun.hotspot.igv.filter;

import com.sun.hotspot.igv.graph.Connection;
import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.InputSlot;
import com.sun.hotspot.igv.graph.OutputSlot;
import com.sun.hotspot.igv.graph.Selector;
import java.util.List;

/**
 *
 * @author Thomas Wuerthinger
 */
public class SplitFilter extends AbstractFilter {

    private String name;
    private Selector selector;

    public SplitFilter(String name, Selector selector) {
        this.name = name;
        this.selector = selector;
    }

    public String getName() {
        return name;
    }

    public void apply(Diagram d) {
        List<Figure> list = selector.selected(d);

        for (Figure f : list) {
            for (OutputSlot os : f.getOutputSlots()) {
                for (Connection c : os.getConnections()) {
                    InputSlot is = c.getInputSlot();
                    is.setName(f.getProperties().get("dump_spec"));
                    String s = f.getProperties().get("short_name");
                    if (s != null) {
                        is.setShortName(s);
                    }
                }
            }

            d.removeFigure(f);
        }
    }
}
