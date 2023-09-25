/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.ChangedEvent;
import com.sun.hotspot.igv.data.ChangedEventProvider;
import com.sun.hotspot.igv.data.ChangedListener;
import com.sun.hotspot.igv.graph.Diagram;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author Thomas Wuerthinger
 */
public class FilterChain implements ChangedEventProvider<FilterChain> {

    private final List<Filter> filters;
    private final transient ChangedEvent<FilterChain> changedEvent;
    private final String name;

    private final ChangedListener<Filter> changedListener = new ChangedListener<Filter>() {
        @Override
        public void changed(Filter source) {
            changedEvent.fire();
        }
    };

    public FilterChain(String name) {
        this.name = name;
        filters = new ArrayList<>();
        changedEvent = new ChangedEvent<>(this);
    }

    public FilterChain() {
        this("");
    }

    public void sortBy(List<String> order) {
        filters.sort(Comparator.comparingInt(f -> order.indexOf(f.getName())));
    }

    @Override
    public ChangedEvent<FilterChain> getChangedEvent() {
        return changedEvent;
    }

    public void applyInOrder(Diagram diagram, FilterChain filterOrder) {
        for (Filter filter : filterOrder.getFilters()) {
            if (filters.contains(filter)) {
                filter.apply(diagram);
            }
        }
    }

    public void addFilter(Filter filter) {
        assert filter != null;
        filters.add(filter);
        filter.getChangedEvent().addListener(changedListener);
        changedEvent.fire();
    }

    public boolean containsFilter(Filter filter) {
        return filters.contains(filter);
    }

    public void clearFilters() {
        for (Filter filter : filters) {
            filter.getChangedEvent().removeListener(changedListener);
        }
        filters.clear();
        changedEvent.fire();
    }

    public void removeFilter(Filter filter) {
        assert filters.contains(filter);
        filters.remove(filter);
        filter.getChangedEvent().removeListener(changedListener);
        changedEvent.fire();
    }

    public void moveFilterUp(Filter filter) {
        assert filters.contains(filter);
        int index = filters.indexOf(filter);
        if (index != 0) {
            filters.remove(index);
            filters.add(index - 1, filter);
        }
        changedEvent.fire();
    }

    public void moveFilterDown(Filter filter) {
        assert filters.contains(filter);
        int index = filters.indexOf(filter);
        if (index != filters.size() - 1) {
            filters.remove(index);
            filters.add(index + 1, filter);
        }
        changedEvent.fire();
    }

    public void addFilters(List<Filter> filtersToAdd) {
        for (Filter filter : filtersToAdd) {
            addFilter(filter);
        }
    }

    public List<Filter> getFilters() {
        return Collections.unmodifiableList(filters);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
