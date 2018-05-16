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

import static jdk.jfr.internal.EventInstrumentation.FIELD_DURATION;

import java.io.IOException;
import java.util.List;

import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * Parses an event and returns a {@link RecordedEvent}.
 *
 */
final class EventParser extends Parser {
    private final Parser[] parsers;
    private final EventType eventType;
    private final TimeConverter timeConverter;
    private final boolean hasDuration;
    private final List<ValueDescriptor> valueDescriptors;

    EventParser(TimeConverter timeConverter, EventType type, Parser[] parsers) {
        this.timeConverter = timeConverter;
        this.parsers = parsers;
        this.eventType = type;
        this.hasDuration = type.getField(FIELD_DURATION) != null;
        this.valueDescriptors = type.getFields();
    }

    @Override
    public Object parse(RecordingInput input) throws IOException {
        Object[] values = new Object[parsers.length];
        for (int i = 0; i < parsers.length; i++) {
            values[i] = parsers[i].parse(input);
        }
        Long startTicks = (Long) values[0];
        long startTime = timeConverter.convertTimestamp(startTicks);
        if (hasDuration) {
            long durationTicks = (Long) values[1];
            long endTime = timeConverter.convertTimestamp(startTicks + durationTicks);
            return new RecordedEvent(eventType, valueDescriptors, values, startTime, endTime, timeConverter);
        } else {
            return new RecordedEvent(eventType, valueDescriptors, values, startTime, startTime, timeConverter);
        }
    }
}
