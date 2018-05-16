/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.consumer;

import java.util.List;

import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.Type;

/**
 * A recorded Java thread group.
 *
 * @since 9
 */
public final class RecordedThreadGroup extends RecordedObject {

    static ObjectFactory<RecordedThreadGroup> createFactory(Type type, TimeConverter timeConverter) {
        return new ObjectFactory<RecordedThreadGroup>(type) {
            @Override
            RecordedThreadGroup createTyped(List<ValueDescriptor> desc, long id, Object[] object) {
                return new RecordedThreadGroup(desc, object, timeConverter);
            }
        };
    }

    private RecordedThreadGroup(List<ValueDescriptor> descriptors, Object[] objects, TimeConverter timeConverter) {
        super(descriptors, objects, timeConverter);
    }

    /**
     * Returns the name of the thread group, or {@code null} if doesn't exist.
     *
     * @return the thread group name, or {@code null} if doesn't exist
     */
    public String getName() {
        return getTyped("name", String.class, null);
    }

    /**
     * Returns the parent thread group, or {@code null} if it doesn't exist.
     *
     * @return parent thread group, or {@code null} if it doesn't exist.
     */
    public RecordedThreadGroup getParent() {
        return getTyped("parent", RecordedThreadGroup.class, null);
    }
}
