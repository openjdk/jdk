/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.graph.*;
import java.util.ArrayList;
import java.util.List;

public class RemoveEmptySlotsFilter extends AbstractFilter {

    private String name;
    private Selector selector;

    public RemoveEmptySlotsFilter(String name, Selector selector) {
        this.name = name;
        this.selector = selector;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void apply(Diagram diagram) {
        List<Figure> list = selector.selected(diagram);
        for (Figure f : list) {
            List<InputSlot> empty = new ArrayList<>();
            for (InputSlot is : f.getInputSlots()) {
                if (is.getConnections().isEmpty()) {
                    empty.add(is);
                }
            }
            for (InputSlot is : empty) {
                f.removeSlot(is);
            }
        }
    }
}
