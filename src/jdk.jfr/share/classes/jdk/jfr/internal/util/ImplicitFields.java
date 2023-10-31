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
package jdk.jfr.internal.util;

import java.util.ArrayList;
import java.util.List;

import jdk.jfr.internal.RemoveFields;
/**
 * Class that describes fields that was not directly named
 * in the event definition.
 */
public final class ImplicitFields {
    public static final String START_TIME = "startTime";
    public static final String DURATION = "duration";
    public static final String EVENT_THREAD = "eventThread";
    public static final String STACK_TRACE = "stackTrace";

    private final List<String> fields = new ArrayList<>(4);

    public ImplicitFields(Class<?> eventClass) {
        fields.add(START_TIME); // for completeness, not really needed
        fields.add(DURATION);
        fields.add(STACK_TRACE);
        fields.add(EVENT_THREAD);
        for (Class<?> c = eventClass; jdk.internal.event.Event.class != c; c = c.getSuperclass()) {
            RemoveFields rf = c.getAnnotation(RemoveFields.class);
            if (rf != null) {
                for (String value : rf.value()) {
                    fields.remove(value);
                }
            }
        }
    }
    public void removeFields(String... fieldNames) {
        for (String fieldName : fieldNames) {
            fields.remove(fieldName);
        }
    }

    public boolean hasDuration() {
        return fields.contains(DURATION);
    }

    public boolean hasEventThread() {
        return fields.contains(EVENT_THREAD);
    }

    public boolean hasStackTrace() {
        return fields.contains(STACK_TRACE);
    }
}
