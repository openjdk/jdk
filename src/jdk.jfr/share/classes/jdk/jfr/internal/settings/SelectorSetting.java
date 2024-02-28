/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Datadog, Inc. All rights reserved.
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

import jdk.jfr.Label;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Name;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.Type;

import java.util.Objects;
import java.util.Set;

@MetadataDefinition
@Label("Selector")
@Name(Type.SETTINGS_PREFIX + "Selector")
public final class SelectorSetting extends JDKSettingControl {

    private final PlatformEventType eventType;
    private SelectorValue value;

    public SelectorSetting(PlatformEventType eventType) {
        this.eventType = Objects.requireNonNull(eventType);
    }

    @Override
    public void setValue(String value) {
        this.value = SelectorValue.of(value);
        eventType.setSelector(this.value.ordinal());
    }

    @Override
    public String combine(Set<String> values) {
        SelectorValue combined = SelectorValue.NONE;
        return values.stream().map(SelectorValue::of).reduce(combined, (a, b) -> {
            if (a == SelectorValue.NONE) {
                return b;
            } else {
                // simplified check - only "all" and "if-context" values are supported for now
                return a == SelectorValue.ALL || b == SelectorValue.ALL ? SelectorValue.ALL : SelectorValue.CONTEXT;
            }
        }).key;
    }

    @Override
    public String getValue() {
        return value.key;
    }

    public boolean isSelected() {
        return switch (value) {
            case ALL -> true;
            case CONTEXT -> JVM.hasContext();
            case NONE -> false;
        };
    }
}