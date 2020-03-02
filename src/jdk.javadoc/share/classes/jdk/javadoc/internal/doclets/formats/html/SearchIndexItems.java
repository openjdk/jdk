/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A container for organizing {@linkplain SearchIndexItem search items}
 * by {@linkplain SearchIndexItem.Category category}.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public final class SearchIndexItems {

    private final Map<SearchIndexItem.Category, Set<SearchIndexItem>> items = new HashMap<>();
    private final Utils utils;

    public SearchIndexItems(Utils utils) {
        this.utils = Objects.requireNonNull(utils);
    }

    /**
     * Adds the specified item to this container.
     *
     * @param item
     *         the item to add
     */
    public void add(SearchIndexItem item) {
        Objects.requireNonNull(item);
        items.computeIfAbsent(item.getCategory(), this::newSetForCategory)
                .add(item);
    }

    private Set<SearchIndexItem> newSetForCategory(SearchIndexItem.Category category) {
        final Comparator<SearchIndexItem> cmp;
        if (category == SearchIndexItem.Category.TYPES) {
            cmp = utils.makeTypeSearchIndexComparator();
        } else {
            cmp = utils.makeGenericSearchIndexComparator();
        }
        return new TreeSet<>(cmp);
    }

    /**
     * Retrieves the items of the specified category from this container.
     *
     * <p> The returned collection is either empty, if there are no items
     * of the specified category, or contains only items {@code i} such that
     * {@code i.getCategory().equals(cat)}. In any case, the returned collection
     * is unmodifiable.
     *
     * @param cat
     *         the category of the items to retrieve
     *
     * @return a collection of items of the specified category
     */
    public Collection<SearchIndexItem> get(SearchIndexItem.Category cat) {
        Objects.requireNonNull(cat);
        Set<SearchIndexItem> col = items.getOrDefault(cat, Set.of());
        return Collections.unmodifiableCollection(col);
    }
}
