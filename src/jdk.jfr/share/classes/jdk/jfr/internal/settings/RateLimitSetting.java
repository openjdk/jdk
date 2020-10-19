/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Name;
import jdk.jfr.Timespan;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.Utils;

@MetadataDefinition
@Label("Event Emmission Rate Limit")
@Description("Event emissions rate (events per second) limit")
@Name(Type.SETTINGS_PREFIX + "RateLimit")
public final class RateLimitSetting extends JDKSettingControl {
    private final static Pattern NUMBER_PTN = Pattern.compile("([0-9]+)");
    private final static long typeId = Type.getTypeId(RateLimitSetting.class);

    private String value = "0";
    private final PlatformEventType eventType;

    public RateLimitSetting(PlatformEventType eventType) {
       this.eventType = Objects.requireNonNull(eventType);
    }

    @Override
    public String combine(Set<String> values) {
        long min = Long.MAX_VALUE;
        String text = "0";
        for (String value : values) {
            long l = parseValueSafe(value);
            if (l < min) {
                text = value;
                min = l;
            }
        }
        return text;
    }

    @Override
    public void setValue(String value) {
        long l =  parseValueSafe(value);
        this.value = value;
        eventType.setRateLimit(l);
    }

    @Override
    public String getValue() {
        return value;
    }

    public static boolean isType(long typeId) {
        return RateLimitSetting.typeId == typeId;
    }

    public static long parseValueSafe(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            Matcher matcher = NUMBER_PTN.matcher(value);
            if (matcher.find()) {
                return Long.valueOf(matcher.group(1));
            } else {
                return 0L;
            }
        } catch (NumberFormatException nfe) {
            return 0L;
        }
    }
}
