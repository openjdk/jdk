/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Interface to add custom actions composed of shell commands to installers.
 */
abstract class ShellCustomAction {

    List<String> requiredPackages() {
        return Collections.emptyList();
    }

    final Map<String, String> create() throws IOException {
        Map<String, String> result = new HashMap<>();
        replacementStringIds().forEach(key -> {
            result.put(key, "");
        });
        result.putAll(createImpl());
        return result;
    }

    protected List<String> replacementStringIds() {
        return Collections.emptyList();
    }

    protected abstract Map<String, String> createImpl() throws IOException;

    static ShellCustomAction nop(List<String> replacementStringIds) {
        return new ShellCustomAction() {
            @Override
            protected List<String> replacementStringIds() {
                return replacementStringIds;
            }

            @Override
            protected Map<String, String> createImpl() throws IOException {
                return Map.of();
            }
        };
    }

    static void mergeReplacementData(Map<String, String> target,
            Map<String, String> newValues) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(newValues);

        for (var kvp : newValues.entrySet()) {
            String newValue = kvp.getValue();
            String existingValue = target.putIfAbsent(kvp.getKey(), newValue);
            if (existingValue != null) {
                if (existingValue.isEmpty()) {
                    target.replace(kvp.getKey(), newValue);
                } else if (!newValue.isEmpty() && !newValue.
                        equals(existingValue)) {
                    throw new IllegalArgumentException(String.format(
                            "Key [%s] value mismatch", kvp.getKey()));
                }
            }
        }
    }

    protected static String stringifyShellCommands(String... commands) {
        return stringifyShellCommands(Arrays.asList(commands));
    }

    protected static String stringifyShellCommands(List<String> commands) {
        return String.join("\n", commands.stream().filter(
                s -> s != null && !s.isEmpty()).toList());
    }

    protected static String stringifyTextFile(String resourceName) throws IOException {
        try ( InputStream is = OverridableResource.readDefault(resourceName);
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isr)) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
