/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record WixComponentKey(String[] attrs) {

    WixComponentKey {
        Objects.requireNonNull(attrs);
        if (attrs.length != MODEL.length) {
            throw new IllegalArgumentException();
        }

        attrs = Arrays.copyOf(attrs, attrs.length);
    }

    WixComponentKey() {
        this(MODEL);
    }

    enum Attribute {
        UPGRADE_CODE,
        PATH,
        ROLE,
        REGKEY,
    }

    WixComponentKey set(Attribute attr, String value) {
        attrs[attr.ordinal()] = value;
        return this;
    }

    String get(Attribute attr) {
        return attrs[attr.ordinal()];
    }

    WixComponentKey upgradeCode(UUID v) {
        return set(Attribute.UPGRADE_CODE, Objects.requireNonNull(v).toString());
    }

    WixComponentKey path(Path v) {
        return set(Attribute.PATH, Objects.requireNonNull(v).toString());
    }

    WixComponentKey regkey(String v) {
        return set(Attribute.REGKEY, Objects.requireNonNull(v));
    }

    WixComponentKey role(String v) {
        return set(Attribute.ROLE, Objects.requireNonNull(v).toString());
    }

    UUID toGuid() {
        var upgradeCode = get(Attribute.UPGRADE_CODE);

        if (upgradeCode == null || get(Attribute.PATH) == null) {
            throw new IllegalStateException();
        }

        String effectiveUpgradeCode;

        try {
            effectiveUpgradeCode = UUID.fromString(upgradeCode).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(ex);
        }

        var key = Stream.of(Attribute.values()).flatMap(attr -> {
            return Optional.ofNullable(get(attr)).map(attrValue -> {
                switch (attr) {
                    // Windows paths and registry key/value names are not case sensitive.
                    case PATH, REGKEY -> attrValue = attrValue.toLowerCase(Locale.ROOT);
                    case UPGRADE_CODE -> attrValue = effectiveUpgradeCode;
                    default -> {}
                }
                return String.format("%s=%s", attr.name(), attrValue);
            }).stream();
        }).collect(Collectors.joining(";"));

        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private final static String[] MODEL = new String[Attribute.values().length];
}
