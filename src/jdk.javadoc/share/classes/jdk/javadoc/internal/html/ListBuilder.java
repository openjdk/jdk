/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * A utility class for building nested HTML lists. This class is implemented as a
 * stack of nested list/item pairs where list items are added to the inner-most
 * list and nested lists are added to the current list item of the inner-most list.
 * Lists can be added to and removed from the stack using the {@link #pushNestedList}
 * and {@link #popNestedList} methods.
 */
public class ListBuilder extends Content {

    private final HtmlTree root;
    private record Pair(HtmlTree list, HtmlTree item) {}
    private final Deque<Pair> stack = new ArrayDeque<>();
    private HtmlTree currentList;
    private HtmlTree currentItem;

    /**
     * Creates a new list builder.
     *
     * @param list the root list element
     */
    public ListBuilder(HtmlTree list) {
        Objects.requireNonNull(list, "Root list element is required");
        root = currentList = list;
    }

    /**
     * Adds a new list item with the given content to the current list. The last added list item
     * will also be used as container when pushing a new nested list.
     *
     * @param listItemContent the content for the new list item.
     * @return this list builder
     */
    public ListBuilder add(Content listItemContent) {
        currentItem = HtmlTree.LI(listItemContent);
        currentList.addUnchecked(currentItem);
        return this;
    }

    /**
     * Adds a new nested list to the current list item and makes it the recipient for new list items.
     * Note that the nested list is added even if empty; use {@code addNested} to avoid adding empty
     * lists.
     *
     * @param list the nested list
     * @return this list builder
     */
    public ListBuilder pushNestedList(HtmlTree list) {
        Objects.requireNonNull(currentItem, "List item required for nested list");
        stack.push(new Pair(currentList, currentItem));
        currentList = list;
        currentItem = null;
        return this;
    }

    /**
     * Pops the current list from the stack and returns to adding list items to the parent list.
     *
     * @return this list builder
     * @throws NoSuchElementException if the stack is empty
     */
    public ListBuilder popNestedList() {
        Pair pair = stack.pop();
        // First restore currentItem and add nested list to it before restoring currentList to outer list.
        currentItem = pair.item;
        currentItem.add(currentList);
        currentList = pair.list;
        return this;
    }

    @Override
    public boolean write(Writer writer, String newline, boolean atNewline) throws IOException {
        return root.write(writer, newline, atNewline);
    }

    @Override
    public boolean isEmpty() {
        return root.isEmpty();
    }
}
