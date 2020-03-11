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

import jdk.javadoc.internal.doclets.formats.html.SearchIndexItem.Category;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * A container for organizing {@linkplain SearchIndexItem search items}
 * by {@linkplain Category category}.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public final class SearchIndexItems {

    private final Map<Category, Set<SearchIndexItem>> items = new HashMap<>();
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

    private Set<SearchIndexItem> newSetForCategory(Category category) {
        final Comparator<SearchIndexItem> cmp;
        if (category == Category.TYPES) {
            cmp = utils.makeTypeSearchIndexComparator();
        } else {
            cmp = utils.makeGenericSearchIndexComparator();
        }
        return new TreeSet<>(cmp);
    }

    /**
     * Checks if there are items of any of the specified categories
     * in this container.
     *
     * <p> Iff there exists an item {@code i} for which there is a category
     * {@code c} from the specified categories such that
     * {@code i.getCategory().equals(c)}, then {@code true} is returned.
     *
     * @param firstCategory
     *         the first category
     * @param otherCategories
     *         other categories (optional)
     *
     * @return {@code true} if there are items of any of the specified categories,
     *         {@code false} otherwise
     *
     * @throws NullPointerException
     *         if there are {@code null} categories
     */
    public boolean containsAnyOfCategories(Category firstCategory,
                                           Category... otherCategories)
    {
        return itemsOfCategories(firstCategory, otherCategories)
                .findAny()
                .isPresent();
    }

    /**
     * Returns a stream of items of any of the specified categories
     * from this container.
     *
     * <p> The returned stream consists of all items {@code i} for which there
     * is a category {@code c} from the specified categories such that
     * {@code i.getCategory().equals(c)}. The stream may be empty.
     *
     * @param firstCategory
     *         the first category
     * @param otherCategories
     *         other categories (optional)
     *
     * @return a stream of items of the specified categories
     *
     * @throws NullPointerException
     *         if there are {@code null} categories
     */
    public Stream<SearchIndexItem> itemsOfCategories(Category firstCategory,
                                                     Category... otherCategories)
    {
        return concatenatedStreamOf(firstCategory, otherCategories)
                .distinct()
                .flatMap(this::itemsOf);
    }

    private Stream<SearchIndexItem> itemsOf(Category cat) {
        Objects.requireNonNull(cat);
        return items.getOrDefault(cat, Set.of()).stream();
    }

    /**
     * Returns a concatenated stream of elements.
     *
     * <p> The elements of the returned stream are encountered in the following order:
     * {@code first, remaining[0], remaining[1], ..., remaining[remaining.length - 1]}.
     *
     * @param first
     *         the first element
     * @param remaining
     *         the remaining elements, if any
     * @param <T>
     *         the type of elements
     *
     * @return the stream of elements
     *
     * @throws NullPointerException
     *         if {@code remaining} is {@code null}
     */
    private static <T> Stream<T> concatenatedStreamOf(T first, T[] remaining) {
        return Stream.concat(Stream.of(first), Stream.of(remaining));
    }
}
