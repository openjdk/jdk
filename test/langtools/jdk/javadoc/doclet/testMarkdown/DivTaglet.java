/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownInlineTagTree;

import jdk.javadoc.doclet.Taglet;

/**
 * An inline taglet that generates output in a {@code <div>} element.
 */
public class DivTaglet implements Taglet {
    @Override
    public String getName() {
        return "div";
    }

    @Override
    public Set<Location> getAllowedLocations() {
        return Set.of(Location.values());
    }

    @Override
    public boolean isInlineTag() {
        return true;
    }

    @Override
    public String toString(List<? extends DocTree> trees, Element e) {
        var children = ((UnknownInlineTagTree) trees.get(0)).getContent();
        return "<div>"
                + children.stream().map(DocTree::toString).collect(Collectors.joining())
                + "</div>";
    }
}
