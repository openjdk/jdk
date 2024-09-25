/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Datadog, Inc. All rights reserved.
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

import static jdk.jfr.internal.util.TimespanUnit.SECONDS;
import static jdk.jfr.internal.util.TimespanUnit.MILLISECONDS;

import java.util.Objects;
import java.util.Set;

import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Name;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.Throttle;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.util.Rate;
import jdk.jfr.internal.util.TimespanUnit;
import jdk.jfr.internal.util.Utils;

@MetadataDefinition
@Label("Throttle")
@Description("Throttles the emission rate for an event")
@Name(Type.SETTINGS_PREFIX + "Throttle")
public final class ThrottleSetting extends JDKSettingControl {
    public static final String DEFAULT_VALUE = Throttle.DEFAULT;
    private final PlatformEventType eventType;
    private String value = DEFAULT_VALUE;

    public ThrottleSetting(PlatformEventType eventType) {
       this.eventType = Objects.requireNonNull(eventType);
    }

    @Override
    public String combine(Set<String> values) {
        Rate max = null;
        String text = null;
        for (String value : values) {
            Rate rate = Rate.of(value);
            if (rate != null) {
                if (max == null || rate.isHigher(max)) {
                    text = value;
                    max = rate;
                }
            }
        }
        // "off" is default
        return Objects.requireNonNullElse(text, DEFAULT_VALUE);
    }

    @Override
    public void setValue(String value) {
        if ("off".equals(value)) {
            eventType.setThrottle(-2, 1000);
            this.value = value;
            return;
        }

        Rate rate = Rate.of(value);
        if (rate != null) {
            long millis = 1000;
            long samples = rate.amount();
            TimespanUnit unit = rate.unit();
            // if unit is more than 1 s, set millis
            if (unit.nanos > SECONDS.nanos) {
                millis = unit.nanos / MILLISECONDS.nanos;
            }
            // if unit is less than 1 s, scale samples
            if (unit.nanos < SECONDS.nanos) {
                long perSecond = SECONDS.nanos / unit.nanos;
                samples *= Utils.multiplyOverflow(samples, perSecond, Long.MAX_VALUE);
            }
            eventType.setThrottle(samples, millis);
            this.value = value;
        }
    }

    @Override
    public String getValue() {
        return value;
    }
}

