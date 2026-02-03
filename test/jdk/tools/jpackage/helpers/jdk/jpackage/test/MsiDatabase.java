/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


final class MsiDatabase {

    static MsiDatabase load(Path msiFile, Path idtFileOutputDir, Set<Table> tableNames) {
        try {
            Files.createDirectories(idtFileOutputDir);

            var orderedTableNames = tableNames.stream().sorted().toList();

            Executor.of("cscript.exe", "//Nologo")
                    .addArgument(TKit.TEST_SRC_ROOT.resolve("resources/msi-export.js"))
                    .addArgument(msiFile)
                    .addArgument(idtFileOutputDir)
                    .addArguments(orderedTableNames.stream().map(Table::tableName).toList())
                    .dumpOutput()
                    .execute(0);

            var tables = orderedTableNames.stream().map(tableName -> {
                return Map.entry(tableName, idtFileOutputDir.resolve(tableName + ".idt"));
            }).filter(e -> {
                return Files.exists(e.getValue());
            }).collect(Collectors.toMap(Map.Entry::getKey, e -> {
                return MsiTable.loadFromTextArchiveFile(e.getValue());
            }));

            return new MsiDatabase(tables);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }


    enum Table {
        COMPONENT("Component"),
        DIRECTORY("Directory"),
        FILE("File"),
        PROPERTY("Property"),
        SHORTCUT("Shortcut"),
        ;

        Table(String name) {
            this.tableName = Objects.requireNonNull(name);
        }

        String tableName() {
            return tableName;
        }

        private final String tableName;

        static final Set<Table> FIND_PROPERTY_REQUIRED_TABLES = Set.of(PROPERTY);
        static final Set<Table> LIST_SHORTCUTS_REQUIRED_TABLES = Set.of(COMPONENT, DIRECTORY, FILE, SHORTCUT);
    }


    private MsiDatabase(Map<Table, MsiTable> tables) {
        this.tables = Map.copyOf(tables);
    }

    Set<Table> tableNames() {
        return tables.keySet();
    }

    MsiDatabase append(MsiDatabase other) {
        Map<Table, MsiTable> newTables = new HashMap<>(tables);
        newTables.putAll(other.tables);
        return new MsiDatabase(newTables);
    }

    Optional<String> findProperty(String propertyName) {
        Objects.requireNonNull(propertyName);
        return tables.get(Table.PROPERTY).findRow("Property", propertyName).map(row -> {
            return row.apply("Value");
        });
    }

    Collection<Shortcut> listShortcuts() {
        var shortcuts = tables.get(Table.SHORTCUT);
        if (shortcuts == null) {
            return List.of();
        }
        return IntStream.range(0, shortcuts.rowCount()).mapToObj(i -> {
            var row = shortcuts.row(i);
            var shortcutPath = directoryPath(row.apply("Directory_")).resolve(fileNameFromFieldValue(row.apply("Name")));
            var workDir = directoryPath(row.apply("WkDir"));
            var shortcutTarget = Path.of(expandFormattedString(row.apply("Target")));
            return new Shortcut(shortcutPath, shortcutTarget, workDir);
        }).toList();
    }

    record Shortcut(Path path, Path target, Path workDir) {

        Shortcut {
            Objects.requireNonNull(path);
            Objects.requireNonNull(target);
            Objects.requireNonNull(workDir);
        }

        void assertEquals(Shortcut expected) {
            TKit.assertEquals(expected.path, path, "Check the shortcut path");
            TKit.assertEquals(expected.target, target, "Check the shortcut target");
            TKit.assertEquals(expected.workDir, workDir, "Check the shortcut work directory");
        }
    }

    private Path directoryPath(String directoryId) {
        var table = tables.get(Table.DIRECTORY);
        Path result = null;
        for (var row = table.findRow("Directory", directoryId);
                row.isPresent();
                directoryId = row.get().apply("Directory_Parent"), row = table.findRow("Directory", directoryId)) {

            Path pathComponent;
            if (DIRECTORY_PROPERTIES.contains(directoryId)) {
                pathComponent = Path.of(directoryId);
                directoryId = null;
            } else {
                pathComponent = fileNameFromFieldValue(row.get().apply("DefaultDir"));
            }

            if (result != null) {
                result = pathComponent.resolve(result);
            } else {
                result = pathComponent;
            }

            if (directoryId == null) {
                break;
            }
        }

        return Objects.requireNonNull(result);
    }

    private String expandFormattedString(String str) {
        return expandFormattedString(str, token -> {
            if (token.charAt(0) == '#') {
                var filekey = token.substring(1);
                var fileRow = tables.get(Table.FILE).findRow("File", filekey).orElseThrow();

                var component = fileRow.apply("Component_");
                var componentRow = tables.get(Table.COMPONENT).findRow("Component", component).orElseThrow();

                var fileName = fileNameFromFieldValue(fileRow.apply("FileName"));
                var filePath = directoryPath(componentRow.apply("Directory_"));

                return filePath.resolve(fileName).toString();
            } else {
                throw new UnsupportedOperationException(String.format(
                        "Unrecognized token [%s] in formatted string [%s]", token, str));
            }
        });
    }

    private static Path fileNameFromFieldValue(String fieldValue) {
        var pipeIdx = fieldValue.indexOf('|');
        if (pipeIdx < 0) {
            return Path.of(fieldValue);
        } else {
            return Path.of(fieldValue.substring(pipeIdx + 1));
        }
    }

    private static String expandFormattedString(String str, Function<String, String> callback) {
        // Naive implementation of https://learn.microsoft.com/en-us/windows/win32/msi/formatted
        //  - No recursive property expansion.
        //  - No curly brakes ({}) handling.

        Objects.requireNonNull(str);
        Objects.requireNonNull(callback);
        var sb = new StringBuilder();
        var m = FORMATTED_STRING_TOKEN.matcher(str);
        while (m.find()) {
            var token = m.group();
            token = token.substring(1, token.length() - 1);
            if (token.equals("~")) {
                m.appendReplacement(sb, "\0");
            } else {
                var replacement = Matcher.quoteReplacement(callback.apply(token));
                m.appendReplacement(sb, replacement);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }


    private record MsiTable(Map<String, List<String>> columns) {

        MsiTable {
            Objects.requireNonNull(columns);
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("Table should have columns");
            }
        }

        Optional<Function<String, String>> findRow(String columnName, String fieldValue) {
            Objects.requireNonNull(columnName);
            Objects.requireNonNull(fieldValue);
            var column = columns.get(columnName);
            for (int i = 0; i != column.size(); i++) {
                if (fieldValue.equals(column.get(i))) {
                    return Optional.of(row(i));
                }
            }
            return Optional.empty();
        }

        /**
         * Loads a table from a text archive file.
         * @param idtFile path to the input text archive file
         * @return the table
         */
        static MsiTable loadFromTextArchiveFile(Path idtFile) {

            var header = IdtFileHeader.loadFromTextArchiveFile(idtFile);

            Map<String, List<String>> columns = new HashMap<>();
            header.columns.forEach(column -> {
                columns.put(column, new ArrayList<>());
            });

            try {
                var lines = Files.readAllLines(idtFile, header.charset()).toArray(String[]::new);
                for (int i = 3; i != lines.length; i++) {
                    var line = lines[i];
                    var row = line.split("\t", -1);
                    if (row.length != header.columns().size()) {
                        throw new IllegalArgumentException(String.format(
                                "Expected %d columns. Actual is %d in line %d in [%s] file",
                                header.columns().size(), row.length, i, idtFile));
                    }
                    for (int j = 0; j != row.length; j++) {
                        var field = row[j];
                        // https://learn.microsoft.com/en-us/windows/win32/msi/archive-file-format
                        field = field.replace((char)21, (char)0);
                        field = field.replace((char)27, '\b');
                        field = field.replace((char)16, '\t');
                        field = field.replace((char)25, '\n');
                        field = field.replace((char)24, '\f');
                        field = field.replace((char)17, '\r');
                        columns.get(header.columns.get(j)).add(field);
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }

            return new MsiTable(columns);
        }

        int columnCount() {
            return columns.size();
        }

        int rowCount() {
            return columns.values().stream().findAny().orElseThrow().size();
        }

        Function<String, String> row(int rowIndex) {
            return columnName -> {
                var column = Objects.requireNonNull(columns.get(Objects.requireNonNull(columnName)));
                return column.get(rowIndex);
            };
        }
    }


    private record IdtFileHeader(Charset charset, List<String> columns) {

        IdtFileHeader {
            Objects.requireNonNull(charset);
            columns.forEach(Objects::requireNonNull);
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("Table should have columns");
            }
        }

        /**
         * Loads a table header from a text archive (.idt) file.
         * @see <a href="https://learn.microsoft.com/en-us/windows/win32/msi/archive-file-format">https://learn.microsoft.com/en-us/windows/win32/msi/archive-file-format</a>
         * @see <a href="https://learn.microsoft.com/en-us/windows/win32/msi/ascii-data-in-text-archive-files">https://learn.microsoft.com/en-us/windows/win32/msi/ascii-data-in-text-archive-files</a>
         * @param path path to the input text archive file
         * @return the table header
         */
        static IdtFileHeader loadFromTextArchiveFile(Path idtFile) {
            var charset = StandardCharsets.US_ASCII;
            try (var stream = Files.lines(idtFile, charset)) {
                var headerLines = stream.limit(3).toList();
                if (headerLines.size() != 3) {
                    throw new IllegalArgumentException(String.format(
                            "[%s] file should have at least three text lines", idtFile));
                }

                var columns = headerLines.get(0).split("\t");

                var header = headerLines.get(2).split("\t", 4);
                if (header.length == 3) {
                    if (Pattern.matches("^[1-9]\\d+$", header[0])) {
                        charset = Charset.forName(header[0]);
                    } else {
                        throw new IllegalArgumentException(String.format(
                                "Unexpected charset name [%s] in [%s] file", header[0], idtFile));
                    }
                } else if (header.length != 2) {
                    throw new IllegalArgumentException(String.format(
                            "Unexpected number of fields (%d) in the 3rd line of [%s] file",
                            header.length, idtFile));
                }

                return new IdtFileHeader(charset, List.of(columns));

            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }


    private final Map<Table, MsiTable> tables;

    // https://learn.microsoft.com/en-us/windows/win32/msi/formatted
    private static final Pattern FORMATTED_STRING_TOKEN = Pattern.compile("\\[[^\\]]+\\]");

    // https://learn.microsoft.com/en-us/windows/win32/msi/property-reference#system-folder-properties
    private final Set<String> DIRECTORY_PROPERTIES = Set.of(
            "DesktopFolder",
            "LocalAppDataFolder",
            "ProgramFiles64Folder",
            "ProgramMenuFolder"
    );
}
