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
package jdk.jfr.internal.settings;

import java.util.List;

import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Name;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.tracing.Modification;
import jdk.jfr.internal.tracing.Filter;
import jdk.jfr.internal.tracing.PlatformTracer;

@MetadataDefinition
@Label("Filter")
@Description("Methods to be filtered")
@Name(Type.SETTINGS_PREFIX + "Filter")
public final class MethodSetting extends FilterSetting {
    private final Modification modification;
    private volatile static boolean initialized;

    public MethodSetting(PlatformEventType eventType, Modification modification, String defaultValue) {
        super(eventType, defaultValue);
        this.modification = modification;
    }

    @Override
    public boolean isValid(String text) {
        return Filter.isValid(text);
    }

    @Override
    protected void apply(PlatformEventType eventType, List<String> filters) {
        ensureInitialized();
        PlatformTracer.setFilters(modification, filters);
    }

    // Expected to be called when holding external lock, so no extra
    // synchronization is required here.
    private static void ensureInitialized() {
        if (!initialized) {
            PlatformTracer.initialize();
            initialized = true;
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
