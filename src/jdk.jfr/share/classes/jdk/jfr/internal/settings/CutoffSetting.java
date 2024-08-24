/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jfr.internal.util.ValueParser.MISSING;

import java.util.Objects;
import java.util.Set;

import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Name;
import jdk.jfr.Timespan;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.util.ValueParser;

@MetadataDefinition
@Label("Cutoff")
@Description("Limit running time of event")
@Name(Type.SETTINGS_PREFIX + "Cutoff")
@Timespan
public final class CutoffSetting extends JDKSettingControl {
    public static final String DEFAULT_VALUE = ValueParser.INFINITY;
    private String value = DEFAULT_VALUE;
    private final PlatformEventType eventType;

    public CutoffSetting(PlatformEventType eventType) {
       this.eventType = Objects.requireNonNull(eventType);
    }

    @Override
    public String combine(Set<String> values) {
        long max = 0;
        String text = null;
        for (String value : values) {
            long nanos = ValueParser.parseTimespanWithInfinity(value, MISSING);
            if (nanos != MISSING && nanos > max) {
                text = value;
                max = nanos;
            }
        }
        return Objects.requireNonNullElse(text, DEFAULT_VALUE);
    }

    @Override
    public void setValue(String value) {
        long nanos = ValueParser.parseTimespanWithInfinity(value, MISSING);
        if (nanos != MISSING) {
            eventType.setCutoff(nanos);
            this.value = value;
        }
    }

    @Override
    public String getValue() {
        return value;
    }

    public static long parseValueSafe(String value) {
        if (value == null) {
            return 0L;
        }
        return ValueParser.parseTimespanWithInfinity(value, 0L);
    }
}
