/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.jpackage.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public final class CfgFile {
    public String getValue(String section, String key) {
        Objects.requireNonNull(section);
        Objects.requireNonNull(key);

        Map<String, String> entries = data.get(section);
        TKit.assertTrue(entries != null, String.format(
                "Check section [%s] is found in [%s] cfg file", section, id));

        String value = entries.get(key);
        TKit.assertNotNull(value, String.format(
                "Check key [%s] is found in [%s] section of [%s] cfg file", key,
                section, id));

        return value;
    }

    public String getValueUnchecked(String section, String key) {
        Objects.requireNonNull(section);
        Objects.requireNonNull(key);

        return Optional.ofNullable(data.get(section)).map(v -> v.get(key)).orElse(
                null);
    }

    public void setValue(String section, String key, String value) {
        Objects.requireNonNull(section);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        if (!data.containsKey(section)) {
            data.put(section, new LinkedHashMap<>());
        }

        data.get(section).put(key, value);
    }

    public CfgFile() {
        this(new LinkedHashMap<>(), "*");
    }
    
    public static CfgFile combine(CfgFile base, CfgFile mods) {
        var cfgFile = new CfgFile(new LinkedHashMap<>(), "*");
        for (var src : List.of(base, mods)) {
            for (var section : src.data.entrySet()) {
                for (var kvp : section.getValue().entrySet()) {
                    cfgFile.setValue(section.getKey(), kvp.getKey(), kvp.getValue());
                }
            }
        }
        return cfgFile;
    }

    private CfgFile(Map<String, Map<String, String>> data, String id) {
        this.data = data;
        this.id = id;
    }

    public void save(Path path) {
        var lines = data.entrySet().stream().flatMap(section -> {
            return Stream.concat(
                    Stream.of(String.format("[%s]", section.getKey())),
                    section.getValue().entrySet().stream().map(kvp -> {
                        return String.format("%s=%s", kvp.getKey(), kvp.getValue());
                    }));
        });
        TKit.createTextFile(path, lines);
    }

    public static CfgFile load(Path path) throws IOException {
        TKit.trace(String.format("Read [%s] jpackage cfg file", path));

        final Pattern sectionBeginRegex = Pattern.compile( "\\s*\\[([^]]*)\\]\\s*");
        final Pattern keyRegex = Pattern.compile( "\\s*([^=]*)=(.*)" );

        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        String currentSectionName = null;
        Map<String, String> currentSection = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            Matcher matcher = sectionBeginRegex.matcher(line);
            if (matcher.find()) {
                if (currentSectionName != null) {
                    result.put(currentSectionName, new LinkedHashMap<>(currentSection));
                }
                currentSectionName = matcher.group(1);
                currentSection.clear();
                continue;
            }

            matcher = keyRegex.matcher(line);
            if (matcher.find()) {
                currentSection.put(matcher.group(1), matcher.group(2));
                continue;
            }
        }

        if (!currentSection.isEmpty()) {
            result.put(Optional.ofNullable(currentSectionName).orElse(""), currentSection);
        }

        return new CfgFile(result, path.toString());
    }

    private final Map<String, Map<String, String>> data;
    private final String id;
}
