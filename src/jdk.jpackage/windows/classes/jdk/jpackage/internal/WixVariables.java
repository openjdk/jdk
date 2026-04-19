/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.jpackage.internal.WixToolset.WixToolsetType;

final class WixVariables {

    WixVariables() {
        this.values = new HashMap<>();
    }

    private WixVariables(Map<String, String> values) {
        this.values = values;
        this.isImmutable = true;
    }

    WixVariables define(String variableName) {
        return put(variableName, "yes");
    }

    WixVariables put(String variableName, String variableValue) {
        Objects.requireNonNull(variableName);
        Objects.requireNonNull(variableValue);
        validateMutable();
        values.compute(variableName, (k, v) -> {
            if (!allowOverrides && v != null) {
                throw overridingDisabled();
            }
            return variableValue;
        });
        return this;
    }

    WixVariables putAll(Map<String, String> values) {
        Objects.requireNonNull(values);
        validateMutable();
        if (!allowOverrides && !Collections.disjoint(this.values.keySet(), values.keySet())) {
            throw overridingDisabled();
        } else {
            values.entrySet().forEach(e -> {
                put(e.getKey(), e.getValue());
            });
        }
        return this;
    }

    WixVariables putAll(WixVariables other) {
        return putAll(other.values);
    }

    WixVariables allowOverrides(boolean v) {
        validateMutable();
        allowOverrides = v;
        return this;
    }

    WixVariables createdImmutableCopy() {
        if (isImmutable) {
            return this;
        } else {
            return new WixVariables(Map.copyOf(values));
        }
    }

    List<String> toWixCommandLine(WixToolsetType wixType) {
        var orderedWixVars = values.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey));
        return (switch (Objects.requireNonNull(wixType)) {
            case Wix3 -> {
                yield orderedWixVars.map(wixVar -> {
                    return String.format("-d%s=%s", wixVar.getKey(), wixVar.getValue());
                });
            }
            case Wix4 -> {
                yield orderedWixVars.flatMap(wixVar -> {
                    return Stream.of("-d", String.format("%s=%s", wixVar.getKey(), wixVar.getValue()));
                });
            }
        }).toList();
    }

    private void validateMutable() {
        if (isImmutable) {
            throw new IllegalStateException("WiX variables container is immutable");
        }
    }

    private static IllegalStateException overridingDisabled() {
        return new IllegalStateException("Overriding variables is unsupported");
    }

    private final Map<String, String> values;
    private boolean allowOverrides;
    private boolean isImmutable;

    static final WixVariables EMPTY = new WixVariables().createdImmutableCopy();
}
