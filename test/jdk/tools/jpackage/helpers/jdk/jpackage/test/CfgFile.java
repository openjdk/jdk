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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public final class CfgFile {
    public String getValue(String sectionName, String key) {
        var section = getSection(sectionName);
        TKit.assertTrue(section != null, String.format(
                "Check section [%s] is found in [%s] cfg file", sectionName, id));

        String value = section.getValue(key);
        TKit.assertNotNull(value, String.format(
                "Check key [%s] is found in [%s] section of [%s] cfg file", key,
                sectionName, id));

        return value;
    }

    public String getValueUnchecked(String sectionName, String key) {
        var section = getSection(sectionName);
        if (section != null) {
            return section.getValue(key);
        } else {
            return null;
        }
    }

    public void addValue(String sectionName, String key, String value) {
        var section = getSection(sectionName);
        if (section == null) {
            section = new Section(sectionName, new ArrayList<>());
            data.add(section);
        }
        section.data.add(Map.entry(key, value));
    }

    public CfgFile() {
        this(new ArrayList<>(), "*");
    }

    public static CfgFile combine(CfgFile base, CfgFile mods) {
        var cfgFile = new CfgFile(new ArrayList<>(), "*");
        for (var src : List.of(base, mods)) {
            for (var section : src.data) {
                for (var kvp : section.data) {
                    cfgFile.addValue(section.name, kvp.getKey(), kvp.getValue());
                }
            }
        }
        return cfgFile;
    }

    private CfgFile(List<Section> data, String id) {
        this.data = data;
        this.id = id;
    }

    public void save(Path path) {
        var lines = data.stream().flatMap(section -> {
            return Stream.concat(
                    Stream.of(String.format("[%s]", section.name)),
                    section.data.stream().map(kvp -> {
                        return String.format("%s=%s", kvp.getKey(), kvp.getValue());
                    }));
        });
        TKit.createTextFile(path, lines);
    }

    private Section getSection(String name) {
        Objects.requireNonNull(name);
        for (var section : data.reversed()) {
            if (name.equals(section.name)) {
                return section;
            }
        }
        return null;
    }

    public static CfgFile load(Path path) throws IOException {
        TKit.trace(String.format("Read [%s] jpackage cfg file", path));

        final Pattern sectionBeginRegex = Pattern.compile( "\\s*\\[([^]]*)\\]\\s*");
        final Pattern keyRegex = Pattern.compile( "\\s*([^=]*)=(.*)" );

        List<Section> sections = new ArrayList<>();

        String currentSectionName = null;
        List<Map.Entry<String, String>> currentSection = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            Matcher matcher = sectionBeginRegex.matcher(line);
            if (matcher.find()) {
                if (currentSectionName != null) {
                    sections.add(new Section(currentSectionName,
                            Collections.unmodifiableList(new ArrayList<>(
                                    currentSection))));
                }
                currentSectionName = matcher.group(1);
                currentSection.clear();
                continue;
            }

            matcher = keyRegex.matcher(line);
            if (matcher.find()) {
                currentSection.add(Map.entry(matcher.group(1), matcher.group(2)));
            }
        }

        if (!currentSection.isEmpty()) {
            sections.add(new Section(
                    Optional.ofNullable(currentSectionName).orElse(""),
                    Collections.unmodifiableList(currentSection)));
        }

        return new CfgFile(sections, path.toString());
    }

    private static record Section(String name, List<Map.Entry<String, String>> data) {
        String getValue(String key) {
            Objects.requireNonNull(key);
            for (var kvp : data.reversed()) {
                if (key.equals(kvp.getKey())) {
                    return kvp.getValue();
                }
            }
            return null;
        }
    }

    private final List<Section> data;
    private final String id;
}
