/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.ciReplay;

import jdk.test.lib.Asserts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReplayFile {
    private final Path replayFilePath;
    private final List<String> replayFile;

    public ReplayFile(String replayFileName) {
        try {
            this.replayFilePath = Paths.get(replayFileName);
            this.replayFile = Files.readAllLines(replayFilePath);
        } catch (IOException ioe) {
            throw new Error("Failed to read/write replay data: " + ioe, ioe);
        }
    }

    public void removeLineStartingWith(String oldLine) {
        replaceLineStartingWith(oldLine, "");
    }

    public String findLineStartingWith(String toFind) {
        return replayFile.stream()
                .filter(line -> line.startsWith(toFind))
                .findFirst()
                .orElse("");
    }

    public void replaceLineStartingWith(String oldLine, String newLine) {
        boolean foundOldLine = false;
        List<String> newReplayFile = new ArrayList<>();
        for (String line : replayFile) {
            if (line.startsWith(oldLine)) {
                foundOldLine = true;
                if (!newLine.isEmpty()) {
                    // Only add if non-empty. Otherwise, line removal.
                    newReplayFile.add(newLine);
                }
            } else {
                newReplayFile.add(line);
            }
        }
        Asserts.assertTrue(foundOldLine, "Did not find oldLine \"" + oldLine + "\" in " + replayFilePath);
        try {
            Files.write(replayFilePath, newReplayFile, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ioe) {
            throw new Error("Failed to read/write replay data: " + ioe, ioe);
        }
    }

    static public class ParsedReplayFile {
        sealed interface Command permits VersionCommand, JvmtiExportCommand, InstanceKlassCommand, CiInstanceKlassCommand, StaticFieldCommand, CiMethodDataCommand, CiMethodCommand, CompileCommand {}

        // version <version>
        public record VersionCommand(int version) implements Command {}
        // JvmtiExport <field> <value>
        public record JvmtiExportCommand(String field, int value) implements Command {}
        // instanceKlass <name>
        //             | @bci <klass> <name> <signature> <bci> <location>* ;
        //             | @cpi <klass> <cpi> <location>* ;
        sealed interface InstanceKlassCommand extends Command permits InstanceKlassCommandName, InstanceKlassCommandBci, InstanceKlassCommandCpi {}
        public record InstanceKlassCommandName(String name) implements InstanceKlassCommand {}
        public record InstanceKlassCommandBci(String klass, String name, String signature, int bci, List<String> location) implements InstanceKlassCommand {}
        public record InstanceKlassCommandCpi(String klass, int cpi, List<String> location) implements InstanceKlassCommand {}
        // ciInstanceKlass <name> <is_linked> <is_initialized> <length> tag*
        public record CiInstanceKlassCommand(String name, boolean is_linked, boolean is_initialized, int length, List<Integer> tag) implements Command {}
        // staticfield <klass> <field_name> [IBCSZJFD] <value>
        //                                | "[" [IBCSZJFD] <length>
        //                                | "[" <klass> <length> "ref" ("nullable" | "null-free") <klass>
        //                                                      | "flat" ("nullable" | "null-free") ("atomic" | "non-atomic") <klass>
        //                                | "Ljava/lang/String;" <value>
        //                                | <klass> <klass>?
        sealed interface StaticFieldCommand extends Command permits
                StaticFieldCommandPrimitive,
                StaticFieldCommandPrimitiveArray,
                StaticFieldCommandRefArray,
                StaticFieldCommandFlatArray,
                StaticFieldCommandString,
                StaticFieldCommandInstance {}
        public record StaticFieldCommandPrimitive(String signature, String value) implements StaticFieldCommand {}
        public record StaticFieldCommandPrimitiveArray(String signature, int length) implements StaticFieldCommand {}
        public record StaticFieldCommandRefArray(String signature, int length, boolean null_free, String actual_klass) implements StaticFieldCommand {}
        public record StaticFieldCommandFlatArray(String signature, int length, boolean null_free, boolean non_atomic, String actual_klass) implements StaticFieldCommand {}
        public record StaticFieldCommandString(String value) implements StaticFieldCommand {}
        public record StaticFieldCommandInstance(String signature, String actual_klass) implements StaticFieldCommand {}
        // ciMethodData <klass> <name> <signature> <state> <invocation_counter> orig <length> <byte>* data <length> <ptr>* oops <length> (<offset> <klass> <array properties>?)* methods <length> (<offset> <klass> <name> <signature>)*
        sealed interface CiMethodDataCommandOop permits CiMethodDataCommandOopInstance, CiMethodDataCommandOopArray {}
        public record CiMethodDataCommandOopInstance(int offset, String klass) implements CiMethodDataCommandOop {}
        public record CiMethodDataCommandOopArray(int offset, String klass, int array_properties) implements CiMethodDataCommandOop {}
        public record CiMethodDataCommandMethod(int offset, String klass, String name, String signature) {}
        public record CiMethodDataCommand(String klass, String name, String signature, int state, int invocation_counter, List<Integer> orig, List<String> data, List<CiMethodDataCommandOop> oops, List<CiMethodDataCommandMethod> methods) implements Command {}
        // ciMethod <klass> <name> <signature> <invocation_counter> <backedge_counter> <interpreter_invocation_count> <interpreter_throwout_count> <instructions_size>
        public record CiMethodCommand(String klass, String name, String signature, int invocation_counter, int backedge_counter, int interpreter_invocation_count, int interpreter_throwout_count, int instructions_size) implements Command {}
        // compile <klass> <name> <signature> <entry_bci> <comp_level> inline <count> (<depth> <bci> <inline_late> <klass> <name> <signature>)*
        public record CompileCommandInline(int depth, int bci, boolean inline_late, String klass, String name, String signature) {}
        public record CompileCommand(String klass, String name, String signature, int entry_bci, int comp_level, int count, List<CompileCommandInline> inlines) implements Command {}

        ParsedReplayFile(List<Command> parsed) { this.parsed = parsed; }
        List<Command> parsed;

        static public ParsedReplayFile parse(File file) throws IOException {
            return parse(Files.readAllLines(file.toPath()));
        }
        static public ParsedReplayFile parse(List<String> lines) {
            return new ParsedReplayFile(lines.stream().map(ParsedReplayFile::parseLine).toList());
        }
        static Command parseLine(String line) {
            List<String> pieces = Arrays.stream(line.split(" ")).filter(piece -> !piece.isEmpty()).toList();
            int comment_idx = pieces.indexOf("#");
            if (comment_idx > 0) {
                pieces = pieces.subList(0, comment_idx);
            }
            String command = pieces.get(0);
            return switch (command) {
                case "version" -> parseVersion(pieces);
                case "JvmtiExport" -> parseJvmtiExport(pieces);
                case "instanceKlass" -> parseInstanceKlass(pieces);
                case "ciInstanceKlass" -> parseCiInstanceKlass(pieces);
                case "staticfield" -> parseStaticField(pieces);
                case "ciMethodData" -> parseCiMethodData(pieces);
                case "ciMethod" -> parseCiMethod(pieces);
                case "compile" -> parseCompile(pieces);
                default -> throw new RuntimeException();
            };
        }

        static VersionCommand parseVersion(List<String> pieces) { throw new UnsupportedOperationException(); }
        static JvmtiExportCommand parseJvmtiExport(List<String> pieces) { throw new UnsupportedOperationException(); }
        static InstanceKlassCommand parseInstanceKlass(List<String> pieces) { throw new UnsupportedOperationException(); }
        static CiInstanceKlassCommand parseCiInstanceKlass(List<String> pieces) { throw new UnsupportedOperationException(); }
        static StaticFieldCommand parseStaticField(List<String> pieces) { throw new UnsupportedOperationException(); }
        static CiMethodDataCommand parseCiMethodData(List<String> pieces) { throw new UnsupportedOperationException(); }
        static CiMethodCommand parseCiMethod(List<String> pieces) { throw new UnsupportedOperationException(); }
        static CompileCommand parseCompile(List<String> pieces) { throw new UnsupportedOperationException(); }
    }
}
