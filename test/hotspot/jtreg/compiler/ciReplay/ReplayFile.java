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
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

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
        sealed interface Command permits
                VersionCommand,
                JvmtiExportCommand,
                InstanceKlassCommand,
                CiInstanceKlassCommand,
                StaticFieldCommand,
                CiMethodDataCommand,
                CiMethodCommand,
                CompileCommand {}

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
                StaticFieldCommandNullArray,
                StaticFieldCommandString,
                StaticFieldCommandInstance {}
        public record StaticFieldCommandPrimitive(String klass, String field_name, String signature, String value) implements StaticFieldCommand {}
        public record StaticFieldCommandPrimitiveArray(String klass, String field_name, String signature, int length) implements StaticFieldCommand {}
        public record StaticFieldCommandRefArray(String klass, String field_name, String signature, int length, boolean null_free, String actual_klass) implements StaticFieldCommand {}
        public record StaticFieldCommandFlatArray(String klass, String field_name, String signature, int length, boolean null_free, boolean non_atomic, String actual_klass) implements StaticFieldCommand {}
        public record StaticFieldCommandNullArray(String klass, String field_name, String signature) implements StaticFieldCommand {}
        public record StaticFieldCommandString(String klass, String field_name, String value) implements StaticFieldCommand {}
        public record StaticFieldCommandInstance(String klass, String field_name, String signature, List<String> actual_klass_or_values) implements StaticFieldCommand {}
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
        public record CompileCommand(String klass, String name, String signature, int entry_bci, int comp_level, List<CompileCommandInline> inlines) implements Command {}

        ParsedReplayFile(List<Command> commands) { this.commands = commands; }
        List<Command> commands;

        static public ParsedReplayFile parse(File file) throws IOException {
            return parse(Files.readAllLines(file.toPath()));
        }
        static public ParsedReplayFile parse(List<String> lines) {
            return new ParsedReplayFile(lines.stream().map(ParsedReplayFile::parseLine).filter(Objects::nonNull).toList());
        }
        static Command parseLine(String line) {
            List<String> pieces = Arrays.stream(line.split(" ")).filter(piece -> !piece.isEmpty()).toList();
            int comment_idx = pieces.indexOf("#");
            if (comment_idx >= 0) {
                pieces = pieces.subList(0, comment_idx);
            }
            if (pieces.isEmpty()) {
                return null;
            }
            String command = pieces.getFirst();
            var line_pieces = LinePieces.make(pieces, command);
            var cmd = switch (command) {
                case "version" -> parseVersion(line_pieces);
                case "JvmtiExport" -> parseJvmtiExport(line_pieces);
                case "instanceKlass" -> parseInstanceKlass(line_pieces);
                case "ciInstanceKlass" -> parseCiInstanceKlass(line_pieces);
                case "staticfield" -> parseStaticField(line_pieces);
                case "ciMethodData" -> parseCiMethodData(line_pieces);
                case "ciMethod" -> parseCiMethod(line_pieces);
                case "compile" -> parseCompile(line_pieces);
                default -> throw new RuntimeException("unknown command: " + command);
            };
            line_pieces.checkAtEnd();
            return cmd;
        }

        static class LinePieces {
            int pos = 0;
            List<String> pieces;

            private LinePieces(List<String> pieces) {
                this.pieces = List.copyOf(pieces);
            }

            @Override
            public String toString() {
                var before = pieces.subList(0, pos);
                var after = pieces.subList(pos, pieces.size());
                return before + ">>" + after;
            }

            static public LinePieces make(List<String> pieces, String command_name) {
                var line = new LinePieces(pieces);
                line.getKeywork(command_name);
                return line;
            }

            void checkBounds(int nb) {
                if (pos < 0)
                    throw new IndexOutOfBoundsException("negative position: " + pos);
                if (pos + nb - 1 >= pieces.size())
                    throw new IndexOutOfBoundsException("size: " + pieces.size() + "; pos: " + pos + "; nb: " + nb);
            }

            void getKeywork(String keyword) {
                checkBounds(1);
                String s = getString();
                if (!keyword.equals(s)) {
                    throw new RuntimeException("expected keyword: " + keyword + "; got: " + s);
                }
            }

            public String getString() {
                checkBounds(1);
                String s = pieces.get(pos);
                pos++;
                return s;
            }

            public List<String> getStrings(int n) {
                checkBounds(n);
                List<String> sub = pieces.subList(pos, pos + n);
                pos += n;
                return sub;
            }

            public List<String> getLeftoverStrings() {
                return getStrings(pieces.size() - pos);
            }

            public int getInt() {
                String s = getString();
                return Integer.parseInt(s);
            }

            public List<Integer> getInts(int n) {
                List<String> s = getStrings(n);
                return s.stream().map(Integer::parseInt).toList();
            }

            public Optional<Integer> getIntIfTwoIntsAvailable() {
                if (pos + 1 >= pieces.size()) {
                    return Optional.empty();
                }
                String s0 = pieces.get(pos);
                String s1 = pieces.get(pos + 1);
                try {
                    Integer.parseInt(s0);
                    Integer.parseInt(s1);
                } catch (NumberFormatException _) {
                    return Optional.empty();
                }
                return Optional.of(getInt());
            }

            public boolean getBool() {
                int s = getInt();
                return switch (s) {
                    case 0 -> false;
                    case 1 -> true;
                    default -> throw new RuntimeException("unexpected bool: " + s);
                };
            }

            public boolean getBoolKeyword(String false_kw, String true_kw) {
                String s = getString();
                if (s.equals(false_kw)) return false;
                if (s.equals(true_kw)) return true;
                throw new RuntimeException("unexepcted boolean keyword; got " + s + "; expected " + false_kw + " (for false) or " + true_kw + " (for true)");
            }

            public boolean atEnd() {
                return pos == pieces.size();
            }

            public void checkAtEnd() {
                if (!atEnd()) {
                    throw new RuntimeException("not at end; size: " + pieces.size() + "; pos: " + pos + "; pieces: " + this);
                }
            }
        }

        static VersionCommand parseVersion(LinePieces pieces) {
            int version = pieces.getInt();
            return new VersionCommand(version);
        }

        static JvmtiExportCommand parseJvmtiExport(LinePieces pieces) {
            String field = pieces.getString();
            int value = pieces.getInt();
            return new JvmtiExportCommand(field, value);
        }

        static InstanceKlassCommand parseInstanceKlass(LinePieces pieces) {
            String name = pieces.getString();
            return switch (name) {
                case "@bci" -> parseInstanceKlassBci(pieces);
                case "@cpi" -> parseInstanceKlassCpi(pieces);
                default -> new InstanceKlassCommandName(name);
            };
        }

        static InstanceKlassCommandBci parseInstanceKlassBci(LinePieces pieces) {
            String klass = pieces.getString();
            String name = pieces.getString();
            String signature = pieces.getString();
            int bci = pieces.getInt();
            List<String> location = new ArrayList<>();
            var next_s = pieces.getString();
            while (!next_s.equals(";")) {
                location.add(next_s);
                next_s = pieces.getString();
            }
            return new InstanceKlassCommandBci(klass, name, signature, bci, location);
        }

        static InstanceKlassCommandCpi parseInstanceKlassCpi(LinePieces pieces) {
            String klass = pieces.getString();
            int cpi = pieces.getInt();
            List<String> location = pieces.getLeftoverStrings();
            return new InstanceKlassCommandCpi(klass, cpi, location);
        }

        static CiInstanceKlassCommand parseCiInstanceKlass(LinePieces pieces) {
            String name = pieces.getString();
            boolean is_linked = pieces.getBool();
            boolean is_initialized = pieces.getBool();
            int length = pieces.getInt();
            List<Integer> tag = pieces.getInts(length - 1);
            return new CiInstanceKlassCommand(name, is_linked, is_initialized, length, tag);
        }

        static boolean isPrimitiveType(char c) {
            return "IBCSZJFD".contains(String.valueOf(c));
        }

        static StaticFieldCommand parseStaticField(LinePieces pieces) {
            String klass = pieces.getString();
            String field_name = pieces.getString();
            String signature = pieces.getString();
            if (isPrimitiveType(signature.charAt(0))) {
                String val = pieces.getString();
                return new StaticFieldCommandPrimitive(klass, field_name, signature, val);
            }
            if (signature.charAt(0) == '[') {
                if (isPrimitiveType(signature.charAt(1))) {
                    int length = pieces.getInt();
                    return new StaticFieldCommandPrimitiveArray(klass, field_name, signature, length);
                } else {
                    int length = pieces.getInt();
                    if (length == -1) {
                        return new StaticFieldCommandNullArray(klass, field_name, signature);
                    }
                    boolean is_flat = pieces.getBoolKeyword("ref", "flat");
                    boolean null_free = pieces.getBoolKeyword("nullable", "null-free");
                    if (is_flat) {
                        boolean non_atomic = pieces.getBoolKeyword("atomic", "non-atomic");
                        String actual_klass = pieces.getString();
                        return new StaticFieldCommandFlatArray(klass, field_name, signature, length, null_free, non_atomic, actual_klass);
                    } else {
                        String actual_klass = pieces.getString();
                        return new StaticFieldCommandRefArray(klass, field_name, signature, length, null_free, actual_klass);
                    }
                }
            }
            if (signature.equals("Ljava/lang/String;")) {
                String value = pieces.getString();
                return new StaticFieldCommandString(klass, field_name, value);
            }
            List<String> actual_klass_or_values = pieces.getLeftoverStrings();
            return new StaticFieldCommandInstance(klass, field_name, signature, actual_klass_or_values);
        }

        // oops <length> (<offset> <klass> <array properties>?)* methods <length> (<offset> <klass> <name> <signature>)*
        static CiMethodDataCommand parseCiMethodData(LinePieces pieces) {
            String klass = pieces.getString();
            String name = pieces.getString();
            String signature = pieces.getString();
            int state = pieces.getInt();
            int invocation_counter = pieces.getInt();

            pieces.getKeywork("orig");
            int orig_length = pieces.getInt();
            List<Integer> orig = pieces.getInts(orig_length);

            pieces.getKeywork("data");
            int data_length = pieces.getInt();
            List<String> data = pieces.getStrings(data_length);

            pieces.getKeywork("oops");
            int oops_length = pieces.getInt();
            List<CiMethodDataCommandOop> oops = new ArrayList<>(oops_length);

            for (int i = 0; i < oops_length; i++) {
                int offset = pieces.getInt();
                String klass_ = pieces.getString();
                Optional<Integer> properties = pieces.getIntIfTwoIntsAvailable();
                oops.add(
                        properties
                                .map(prop -> (CiMethodDataCommandOop)new CiMethodDataCommandOopArray(offset, klass_, prop))
                                .orElse(new CiMethodDataCommandOopInstance(offset, klass_))
                );
            }

            pieces.getKeywork("methods");
            int methods_length = pieces.getInt();
            List<CiMethodDataCommandMethod> methods = new ArrayList<>(methods_length);

            for (int i = 0; i < methods_length; i++) {
                int offset = pieces.getInt();
                String klass_ = pieces.getString();
                String name_ = pieces.getString();
                String signature_ = pieces.getString();
                methods.add(new CiMethodDataCommandMethod(offset, klass_, name_, signature_));
            }

            return new CiMethodDataCommand(klass, name, signature, state, invocation_counter, orig, data, oops, methods);
        }

        static CiMethodCommand parseCiMethod(LinePieces pieces) {
            String klass = pieces.getString();
            String name = pieces.getString();
            String signature = pieces.getString();
            int invocation_counter = pieces.getInt();
            int backedge_counter = pieces.getInt();
            int interpreter_invocation_count = pieces.getInt();
            int interpreter_throwout_count = pieces.getInt();
            int instructions_size = pieces.getInt();
            return new CiMethodCommand(klass, name, signature, invocation_counter, backedge_counter, interpreter_invocation_count, interpreter_throwout_count, instructions_size);
        }

        static CompileCommand parseCompile(LinePieces pieces) {
            String klass = pieces.getString();
            String name = pieces.getString();
            String signature = pieces.getString();
            int entry_bci = pieces.getInt();
            int comp_level = pieces.getInt();
            pieces.getKeywork("inline");
            int count = pieces.getInt();

            List<CompileCommandInline> inlines = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                int depth = pieces.getInt();
                int bci = pieces.getInt();
                boolean inline_late = pieces.getBool();
                String klass_ = pieces.getString();
                String name_ = pieces.getString();
                String signature_ = pieces.getString();
                inlines.add(new CompileCommandInline(depth, bci, inline_late, klass_, name_, signature_));
            }

            return new CompileCommand(klass, name, signature, entry_bci, comp_level, inlines);
        }

        static Optional<Integer> getVersion(String which, ParsedReplayFile parsed, List<String> differences) {
            List<Integer> parsed_version = parsed.commands.stream().map(cmd -> switch (cmd) { case VersionCommand(int version) -> version; default -> null; }).filter(Objects::nonNull).toList();
            if (parsed_version.size() != 1) {
                differences.add("Expected a single version command, but found " + parsed_version.size() + " in " + which);
                return Optional.empty();
            }
            return Optional.ofNullable(parsed_version.getFirst());
        }
        static void compareVersion(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            Optional<Integer> lhs_version = getVersion("lhs", lhs, differences);
            Optional<Integer> rhs_version = getVersion("rhs", rhs, differences);

            if (lhs_version.isPresent() && rhs_version.isPresent() && !lhs_version.get().equals(rhs_version.get())) {
                differences.add("Versions mismatch: lhs=" + lhs_version.get() + "; rhs=" + rhs_version.get());
            }
        }

        static <T> HashSet<T> extractSet(ParsedReplayFile parsed, BiConsumer<HashSet<T>, Command> f) {
            return parsed.commands.stream().collect(
                    HashSet::new,
                    f,
                    HashSet::addAll
            );
        }
        static <T> void diffSets(String name, HashSet<T> lhs, HashSet<T> rhs, List<String> differences) {
            lhs.forEach((v) -> {
                        if (!rhs.contains(v)) {
                            differences.add(name + " mismatch: element=" + v + " exists only in lhs");
                        }
                    }
            );
            rhs.forEach((v) -> {
                        if (!lhs.contains(v)) {
                            differences.add(name + " mismatch: element=" + v + " exists only in rhs");
                        }
                    }
            );
        }
        static <T, U> HashMap<T, U> extractMap(ParsedReplayFile parsed, BiConsumer<HashMap<T, U>, Command> f) {
            return parsed.commands.stream().collect(
                    HashMap::new,
                    f,
                    HashMap::putAll
            );
        }
        static <T, U> void diffMaps(String name, HashMap<T, U> lhs, HashMap<T, U> rhs, BiPredicate<U, U> eq_value, List<String> differences) {
            lhs.forEach((key, l_value) -> {
                        if (!rhs.containsKey(key)) {
                            differences.add(name + " mismatch: key=" + key + " exists only in lhs");
                        } else {
                            U r_value = rhs.get(key);
                            if (!eq_value.test(l_value, r_value)) {
                                differences.add(name + " mismatch: for key=" + key + "; value in lhs=" + l_value + "; value in rhs=" + r_value);
                            }
                        }
                    }
            );
            rhs.forEach((key, _) -> {
                        if (!lhs.containsKey(key)) {
                            differences.add(name + " mismatch: key=" + key + " exists only in rhs");
                        }
                    }
            );
        }

        static void compareJvmtiExport(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            BiConsumer<HashMap<String, Integer>, Command> folder = (acc, command) -> {
                if (command instanceof JvmtiExportCommand(String field, int value)) {
                    acc.put(field, value);
                }
            };
            HashMap<String, Integer> lhs_jvmti = extractMap(lhs, folder);
            HashMap<String, Integer> rhs_jvmti = extractMap(rhs, folder);
            diffMaps("JvmtiExport", lhs_jvmti, rhs_jvmti, Integer::equals, differences);
        }

        static void compareInstanceKlassNames(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            BiConsumer<HashSet<String>, Command> folder = (acc, command) -> {
                if (command instanceof InstanceKlassCommandName(String name)) {
                    acc.add(name);
                }
            };
            HashSet<String> lhs_klasses = extractSet(lhs, folder);
            HashSet<String> rhs_klasses = extractSet(rhs, folder);
            diffSets("InstanceKlass", lhs_klasses, rhs_klasses, differences);
        }
        static void compareInstanceKlassCpi(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, int cpi) {}
            BiConsumer<HashMap<Key, List<String>>, Command> folder = (acc, command) -> {
                if (command instanceof InstanceKlassCommandCpi(String klass, int cpi, List<String> location)) {
                    acc.put(new Key(klass, cpi), location);
                }
            };
            var lhs_klasses = extractMap(lhs, folder);
            var rhs_klasses = extractMap(rhs, folder);
            diffMaps("InstanceKlass", lhs_klasses, rhs_klasses, List::equals, differences);
        }
        static void compareInstanceKlassBci(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String name, String signature, int bci) {}
            BiConsumer<HashMap<Key, List<String>>, Command> folder = (acc, command) -> {
                if (command instanceof InstanceKlassCommandBci(String klass, String name, String signature, int bci, List<String> location)) {
                    acc.put(new Key(klass, name, signature, bci), location);
                }
            };
            var lhs_klasses = extractMap(lhs, folder);
            var rhs_klasses = extractMap(rhs, folder);
            diffMaps("InstanceKlass", lhs_klasses, rhs_klasses, List::equals, differences);
        }
        static void compareInstanceKlasses(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            compareInstanceKlassNames(lhs, rhs, differences);
            compareInstanceKlassCpi(lhs, rhs, differences);
            compareInstanceKlassBci(lhs, rhs, differences);
        }

        static void compareCiInstanceKlasses(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Element(String name, boolean is_linked, boolean is_initialized, int length) {}
            BiConsumer<HashSet<Element>, Command> folder = (acc, command) -> {
                if (command instanceof CiInstanceKlassCommand(String name, boolean is_linked, boolean is_initialized, int length, List<Integer> _)) {
                    acc.add(new Element(name, is_linked, is_initialized, length));
                }
            };
            var lhs_ci_klasses = extractSet(lhs, folder);
            var rhs_ci_klasses = extractSet(rhs, folder);
            diffSets("CiInstanceKlass", lhs_ci_klasses, rhs_ci_klasses, differences);
        }

        static void compareStaticFieldCommandPrimitive(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String field_name, String signature) {}
            BiConsumer<HashMap<Key, String>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandPrimitive(String klass, String field_name, String signature, String value)) {
                    acc.put(new Key(klass, field_name, signature), value);
                }
            };
            var lhs_static_fields = extractMap(lhs, folder);
            var rhs_static_fields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhs_static_fields, rhs_static_fields, String::equals, differences);
        }
        static void compareStaticFieldCommandPrimitiveArray(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String field_name, String signature) {}
            BiConsumer<HashMap<Key, Integer>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandPrimitiveArray(String klass, String field_name, String signature, int length)) {
                    acc.put(new Key(klass, field_name, signature), length);
                }
            };
            var lhs_static_fields = extractMap(lhs, folder);
            var rhs_static_fields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhs_static_fields, rhs_static_fields, Integer::equals, differences);
        }
        static void compareStaticFieldCommandRefArray(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String field_name, String signature) {}
            record Value(int length, boolean null_free, String actual_klass) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandRefArray(String klass, String field_name, String signature, int length, boolean null_free, String actual_klass)) {
                    acc.put(new Key(klass, field_name, signature), new Value(length, null_free, actual_klass));
                }
            };
            var lhs_static_fields = extractMap(lhs, folder);
            var rhs_static_fields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhs_static_fields, rhs_static_fields, Value::equals, differences);
        }
        static void compareStaticFieldCommandFlatArray(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String field_name, String signature) {}
            record Value(int length, boolean null_free, boolean non_atomic, String actual_klass) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandFlatArray(String klass, String field_name, String signature, int length, boolean null_free, boolean non_atomic, String actual_klass)) {
                    acc.put(new Key(klass, field_name, signature), new Value(length, null_free, non_atomic, actual_klass));
                }
            };
            var lhs_static_fields = extractMap(lhs, folder);
            var rhs_static_fields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhs_static_fields, rhs_static_fields, Value::equals, differences);
        }
        static void compareStaticFieldCommandNullArray(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Element(String klass, String field_name, String signature) {}
            BiConsumer<HashSet<Element>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandNullArray(String klass, String field_name, String signature)) {
                    acc.add(new Element(klass, field_name, signature));
                }
            };
            var lhs_static_fields = extractSet(lhs, folder);
            var rhs_static_fields = extractSet(rhs, folder);
            diffSets("CiInstanceKlass", lhs_static_fields, rhs_static_fields, differences);
        }
        static void compareStaticFieldCommandString(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String field_name) {}
            BiConsumer<HashMap<Key, String>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandString(String klass, String field_name, String value)) {
                    acc.put(new Key(klass, field_name), value);
                }
            };
            var lhs_static_fields = extractMap(lhs, folder);
            var rhs_static_fields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhs_static_fields, rhs_static_fields, String::equals, differences);
        }
        static void compareStaticFieldCommandInstance(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String field_name, String signature) {}
            BiConsumer<HashMap<Key, List<String>>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandInstance(String klass, String field_name, String signature, List<String> actual_klass_or_values)) {
                    acc.put(new Key(klass, field_name, signature), actual_klass_or_values);
                }
            };
            var lhs_static_fields = extractMap(lhs, folder);
            var rhs_static_fields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhs_static_fields, rhs_static_fields, List::equals, differences);
        }
        static void compareStaticFieldCommand(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            compareStaticFieldCommandPrimitive(lhs, rhs, differences);
            compareStaticFieldCommandPrimitiveArray(lhs, rhs, differences);
            compareStaticFieldCommandRefArray(lhs, rhs, differences);
            compareStaticFieldCommandFlatArray(lhs, rhs, differences);
            compareStaticFieldCommandNullArray(lhs, rhs, differences);
            compareStaticFieldCommandString(lhs, rhs, differences);
            compareStaticFieldCommandInstance(lhs, rhs, differences);
        }

        static void compareCiMethodDataCommand(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String name, String signature) {}
            record Value(int state, int invocation_counter) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof CiMethodDataCommand(String klass, String name, String signature, int state, int invocation_counter, List<Integer> _, List<String> _, List<CiMethodDataCommandOop> _, List<CiMethodDataCommandMethod> _)) {
                    acc.put(new Key(klass, name, signature), new Value(state, invocation_counter));
                }
            };
            var lhs_static_fields = extractMap(lhs, folder);
            var rhs_static_fields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhs_static_fields, rhs_static_fields, Value::equals, differences);
        }

        static void compareCiMethodCommand(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String name, String signature) {}
            record Value(int invocation_counter, int backedge_counter, int interpreter_invocation_count, int interpreter_throwout_count, int instructions_size) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof CiMethodCommand(String klass, String name, String signature, int invocation_counter, int backedge_counter, int interpreter_invocation_count, int interpreter_throwout_count, int instructions_size)) {
                    acc.put(new Key(klass, name, signature), new Value(invocation_counter, backedge_counter, interpreter_invocation_count, interpreter_throwout_count, instructions_size));
                }
            };
            var lhs_static_fields = extractMap(lhs, folder);
            var rhs_static_fields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhs_static_fields, rhs_static_fields, Value::equals, differences);
        }

        static void compareCompileCommand(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String name, String signature) {}
            record Value(int entry_bci, int comp_level, List<CompileCommandInline> inlines) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof CompileCommand(String klass, String name, String signature, int entry_bci, int comp_level, List<CompileCommandInline> inlines)) {
                    acc.put(new Key(klass, name, signature), new Value(entry_bci, comp_level, inlines));
                }
            };
            var lhs_static_fields = extractMap(lhs, folder);
            var rhs_static_fields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhs_static_fields, rhs_static_fields, Value::equals, differences);
        }

        static List<String> findDifferences(ParsedReplayFile lhs, ParsedReplayFile rhs) {
            List<String> differences = new ArrayList<>();

            compareVersion(lhs, rhs, differences);
            compareJvmtiExport(lhs, rhs, differences);
            compareInstanceKlasses(lhs, rhs, differences);
            compareCiInstanceKlasses(lhs, rhs, differences);
            compareStaticFieldCommand(lhs, rhs, differences);
            compareCiMethodDataCommand(lhs, rhs, differences);
            compareCiMethodCommand(lhs, rhs, differences);
            compareCompileCommand(lhs, rhs, differences);

            return differences;
        }
    }
}
