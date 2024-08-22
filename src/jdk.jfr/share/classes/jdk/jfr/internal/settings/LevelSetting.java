/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jdk.jfr.Label;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Name;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.Type;

@MetadataDefinition
@Label("Level")
@Name(Type.SETTINGS_PREFIX + "Level")
public final class LevelSetting extends JDKSettingControl {
    private final PlatformEventType eventType;
    private final List<String> levels;
    private String value;

    public LevelSetting(PlatformEventType eventType, String[] levels) {
        this.eventType = Objects.requireNonNull(eventType);
        this.levels = Arrays.asList(Objects.requireNonNull(levels));
        this.value = levels[0];
    }

    @Override
    public String combine(Set<String> values) {
        int maxIndex = 0; // index 0 contains the default value
        for (String value : values) {
            maxIndex = Math.max(maxIndex, levels.indexOf(value));
        }
        return levels.get(maxIndex);
    }

    @Override
    public void setValue(String value) {
        int index = levels.indexOf(value);
        if (index != -1) {
            this.eventType.setLevel(index);
            this.value = value;
        }
    }

    @Override
    public String getValue() {
        return value;
    }
}
