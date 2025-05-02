/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jdk.jfr.EventType;
import jdk.jfr.Experimental;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.util.Utils;

/**
 * Type referenced in a FROM-clause.
 * <p>
 * If the query has a WHEN clause, the available events for the event type
 * is restricted by a list of filter conditions.
 */
final class FilteredType {
    public record Filter (Field field, String value) {

        @Override
        public int hashCode() {
            return field.name.hashCode() + value.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof Filter that) {
                return this.field.name.equals(that.field.name) && Objects.equals(this.value, that.value);
            }
            return false;
        }
    }

    private final List<Filter> filters = new ArrayList<>();
    private final EventType eventType;
    private final String simpleName;

    public FilteredType(EventType type) {
        this.eventType = type;
        this.simpleName = Utils.makeSimpleName(type);
    }

    public boolean isExperimental() {
        return eventType.getAnnotation(Experimental.class) != null;
    }

    public String getName() {
        return eventType.getName();
    }

    public String getLabel() {
        return eventType.getLabel();
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void addFilter(Filter filter) {
        filters.add(filter);
    }

    public List<Filter> getFilters() {
        return filters;
    }

    public ValueDescriptor getField(String name) {
        return eventType.getField(name);
    }

    public List<ValueDescriptor> getFields() {
        return eventType.getFields();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(eventType.getId()) + filters.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof FilteredType that) {
            return that.eventType.getId() == this.eventType.getId()
                && that.filters.equals(this.filters);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName());
        sb.append(" ");
        for (Filter condition : filters) {
            sb.append(condition.field());
            sb.append(" = ");
            sb.append(condition.value());
            sb.append(" ");
        }
        return sb.toString();
    }
}
