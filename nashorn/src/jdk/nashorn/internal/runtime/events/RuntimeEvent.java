/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.events;

import java.util.logging.Level;
import jdk.nashorn.internal.objects.NativeDebug;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Class for representing a runtime event, giving less global dependencies than logger.
 * Every {@link NativeDebug} object keeps a queue of RuntimeEvents that can be explored
 * through the debug API.
 *
 * @param <T> class of the value this event wraps
 */
public class RuntimeEvent<T> {
    /** Queue size for the runtime event buffer */
    public static final int RUNTIME_EVENT_QUEUE_SIZE = Options.getIntProperty("nashorn.runtime.event.queue.size", 1024);

    private final Level level;
    private final T value;

    /**
     * Constructor
     *
     * @param level  log level for runtime event to create
     * @param object object to wrap
     */
    public RuntimeEvent(final Level level, final T object) {
        this.level = level;
        this.value = object;
    }

    /**
     * Return the value wrapped in this runtime event
     * @return value
     */
    public final T getValue() {
        return value;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append('[').
            append(level).
            append("] ").
            append(value == null ? "null" : getValueClass().getSimpleName()).
            append(" value=").
            append(value);

        return sb.toString();
    }

    /**
     * Descriptor for this runtime event, must be overridden and
     * implemented, e.g. "RewriteException"
     * @return event name
     */
    public final Class<?> getValueClass() {
        return value.getClass();
    }
}
