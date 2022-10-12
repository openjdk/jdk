/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.hotspot.igv.graph.Block;
import com.sun.hotspot.igv.graph.BlockSelector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveBlockFilter extends AbstractFilter {

    private final List<RemoveBlockRule> rules;
    private final String name;

    public RemoveBlockFilter(String name) {
        this.name = name;
        rules = new ArrayList<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void apply(Diagram diagram) {
        for (RemoveBlockRule r : rules) {
            List<Block> selected = r.getBlockSelector().selected(diagram);
            Set<Block> toRemove = new HashSet<>(selected);
            diagram.removeAllBlocks(toRemove);
        }
    }

    public void addRule(RemoveBlockRule rule) {
        rules.add(rule);
    }

    public static class RemoveBlockRule {

        private final BlockSelector selector;

        public RemoveBlockRule(BlockSelector selector) {
            this.selector = selector;
        }

        public BlockSelector getBlockSelector() {
            return selector;
        }
    }
}
