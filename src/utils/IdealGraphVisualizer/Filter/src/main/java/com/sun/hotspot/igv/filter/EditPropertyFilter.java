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
import java.util.function.Function;
import java.util.List;

public class EditPropertyFilter extends AbstractFilter {

    private String name;
    private Selector selector;
    private final String[] inputPropertyNames;
    private final String outputPropertyName;
    private final Function<String[], String> editFunction;

    public EditPropertyFilter(String name, Selector selector,
                              String[] inputPropertyNames, String outputPropertyName,
                              Function<String[], String> editFunction) {
        this.name = name;
        this.selector = selector;
        this.inputPropertyNames = inputPropertyNames;
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
        String[] inputVals = new String[inputPropertyNames.length];
        for (Figure f : list) {
            for (int i = 0; i < inputPropertyNames.length; i++) {
                inputVals[i] = f.getProperties().get(inputPropertyNames[i]);
            }
            String outputVal = editFunction.apply(inputVals);
            f.getProperties().setProperty(outputPropertyName, outputVal);
        }
    }
}
