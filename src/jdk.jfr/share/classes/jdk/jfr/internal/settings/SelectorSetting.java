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

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@MetadataDefinition
@Label("Selector")
@Name(Type.SETTINGS_PREFIX + "Selector")
public final class SelectorSetting extends JDKSettingControl {

    private final PlatformEventType eventType;
    private EnumSet<SelectorValue> value;

    public SelectorSetting(PlatformEventType eventType) {
        this.eventType = Objects.requireNonNull(eventType);
    }

    @Override
    public void setValue(String value) {
        this.value = SelectorValue.of(value);
        int mask = this.value.stream().map(SelectorValue::getValue).reduce((byte)0, (l, r) -> (byte) (l | r));
        eventType.setSelector(mask);
    }

    @Override
    public String combine(Set<String> values) {
        Set<SelectorValue> combined = values.stream().map(SelectorValue::of).flatMap(Collection::stream).collect(Collectors.toSet());
        if (!combined.isEmpty()) {
            combined.remove(SelectorValue.ALL);
        }
        return asString(combined);
    }

    @Override
    public String getValue() {
        return asString(value);
    }

    private static String asString(Set<SelectorValue> value) {
        return value.stream().map(s -> s.key).collect(Collectors.joining(","));
    }

    public boolean isSelected() {
        boolean selected = false;
        if (value.contains(SelectorValue.ALL)) {
            selected = true;
        } else if (value.contains(SelectorValue.CONTEXT)) {
            selected = JVM.hasContext();
        } else if (value.contains(SelectorValue.TRIGGERED)) {
            // TODO
        }
        return selected;
    }
}