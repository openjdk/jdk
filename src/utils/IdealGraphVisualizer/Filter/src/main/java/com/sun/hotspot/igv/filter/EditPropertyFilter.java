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

import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.Selector;
import java.util.function.UnaryOperator;
import java.util.List;

public class EditPropertyFilter extends AbstractFilter {

    private String name;
    private Selector selector;
    private final String inputPropertyName;
    private final String outputPropertyName;
    private final UnaryOperator<String> editFunction;

    public EditPropertyFilter(String name, Selector selector,
                              String inputPropertyName, String outputPropertyName,
                              UnaryOperator<String> editFunction) {
        this.name = name;
        this.selector = selector;
        this.inputPropertyName = inputPropertyName;
        this.outputPropertyName = outputPropertyName;
        this.editFunction = editFunction;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void apply(Diagram diagram) {
        List<Figure> list = selector.selected(diagram);
        for (Figure f : list) {
            String inputVal = f.getProperties().get(inputPropertyName);
            String outputVal = editFunction.apply(inputVal);
            f.getProperties().setProperty(outputPropertyName, outputVal);
        }
    }
}
