/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jfr.internal.PlatformEventType;

abstract class BooleanSetting extends JDKSettingControl {
    private final PlatformEventType eventType;
    private final String defaultValue;
    private String value;

    public BooleanSetting(PlatformEventType eventType, String defaultValue) {
        this.eventType = Objects.requireNonNull(eventType);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        if (parse(defaultValue) == null) {
            throw new InternalError("Only 'true' or 'false' is allowed with class BooleanSetting");
        }
    }

    protected abstract void apply(PlatformEventType eventType, boolean value);

    @Override
    public String combine(Set<String> values) {
        if (values.contains("true")) {
            return "true";
        }
        if (values.contains("false")) {
            return "false";
        }
        return defaultValue;
    }

    @Override
    public void setValue(String value) {
        Boolean b = parse(value);
        if (b != null) {
            apply(eventType, b.booleanValue());
            this.value = value;
        }
    }

    @Override
    public String getValue() {
        return value;
    }

    private static Boolean parse(String value) {
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
