/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ColorLiveRangeFilter extends AbstractFilter {

    private List<ColorRule> colorRules;
    private String name;

    public ColorLiveRangeFilter(String name) {
        this.name = name;
        colorRules = new ArrayList<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void apply(Diagram diagram) {
        for (ColorRule rule : colorRules) {
            if (rule.getSelector() != null) {
                List<LiveRangeSegment> segments = rule.getSelector().selected(diagram);
                for (LiveRangeSegment s : segments) {
                    applyRule(rule, s);
                    if (rule.getColor() != null) {
                        s.setColor(rule.getColor());
                    }
                }
            } else {
                for (LiveRangeSegment s : diagram.getLiveRangeSegments()) {
                    applyRule(rule, s);
                }
            }
        }
    }

    private void applyRule(ColorRule rule, LiveRangeSegment s) {
        if (rule.getColor() != null) {
            s.setColor(rule.getColor());
        }
    }

    public void addRule(ColorRule r) {
        colorRules.add(r);
    }

    public static class ColorRule {

        private Color color;
        private LiveRangeSelector selector;

        public ColorRule(LiveRangeSelector selector, Color c) {
            this.selector = selector;
            this.color = c;
        }

        public ColorRule(Color c) {
            this(null, c);
        }

        public Color getColor() {
            return color;
        }

        public LiveRangeSelector getSelector() {
            return selector;
        }
    }
}
