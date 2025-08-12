/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.javadoc.internal.html;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A utility class for building nested HTML lists. The class maintains a
 * stack of nested list trees which it adapts to obtain the desired nesting
 * level of added items.
 */
public class ListBuilder extends Content {

    private final HtmlTag listTag;
    private final HtmlStyle listStyle;
    private final HtmlTree root;

    private record Pair(HtmlTree list, HtmlTree item) {}
    private final Deque<Pair> stack = new ArrayDeque<>();

    private HtmlTree currentList;
    private HtmlTree currentItem;

    final static int MAX_LEVEL = 5;

    /**
     * Creates a new list builder.
     *
     * @param listTag the tag to use for the list elements
     * @param listStyle the style to use for list elements
     */
    public ListBuilder(HtmlTag listTag, HtmlStyle listStyle) {
        Objects.requireNonNull(listTag, "List tag is required");
        this.listTag = listTag;
        this.listStyle = listStyle;
        root = currentList = createList().put(HtmlAttr.TABINDEX, "-1");
    }

    /**
     * Adds a new list item with the given content to the current list at the given nesting level,
     * which must be greater than or equal to 0 and less than or equal to {@code MAX_LEVEL}.
     * If {@code level} is greater than the current nesting level new sublists are added
     * until the level is reached. If {@code level} is less than the current level, sublists
     * are closed to reach the level.
     *
     * @param listItemContent content for the new list item.
     * @param level nesting level for the added item
     */
    public void addItem(Content listItemContent, int level) {
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException(String.valueOf(level));
        }
        while (level > stack.size()) {
            pushNestedList();
        }
        while (level < stack.size()) {
            popNestedList();
        }
        currentItem = HtmlTree.LI(listItemContent);
        currentList.addUnchecked(currentItem);
    }

    /**
     * Adds a new nested nested list to which new items will be added.
     */
    private void pushNestedList() {
        if (currentItem == null) {
            currentItem = HtmlTree.LI();
        }
        stack.push(new Pair(currentList, currentItem));
        currentList = createList();
        currentItem = null;
    }

    /**
     * Closes the current nested list and makes its parent the current list.
     *
     * @throws NoSuchElementException if the stack is empty
     */
    private void popNestedList() {
        Pair pair = stack.pop();
        // First restore currentItem and add nested list to it before restoring currentList to outer list.
        currentItem = pair.item;
        currentItem.add(currentList);
        currentList = pair.list;
    }

    @Override
    public boolean write(Writer writer, String newline, boolean atNewline) throws IOException {
        while (!stack.isEmpty()) {
            popNestedList();
        }
        return root.write(writer, newline, atNewline);
    }

    @Override
    public boolean isEmpty() {
        return root.isEmpty();
    }

    private HtmlTree createList() {
        var list = HtmlTree.of(listTag);
        if (listStyle != null) {
            list.setStyle(listStyle);
        }
        return list;
    }
}
