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
        public record CiInstanceKlassCommand(String name, boolean isLinked, boolean isInitialized, int length, List<Integer> tag) implements Command {}
        // staticfield <klass> <field_name> [IBCSZJFD] <value>
        //                                | "[" [IBCSZJFD] <length>
        //                                | <array-of-klass> <length> "ref" ("nullable" | "null-free") <klass>
        //                                                          | "flat" ("nullable" | "null-free") ("atomic" | "non-atomic") <klass>
        //                                | "Ljava/lang/String;" <value>
        //                                | <klass> <klass>?
        sealed interface StaticFieldCommand extends Command permits
                StaticFieldCommandPrimitive,
                StaticFieldCommandPrimitiveArray,
                StaticFieldCommandRefArray,
                StaticFieldCommandFlatArray,
                StaticFieldCommandNullArray,
                StaticFieldCommandString,
                StaticFieldCommandInstance {
            String klass();
            String fieldName();
            String signature();
        }
        public record StaticFieldCommandPrimitive(String klass, String fieldName, String signature, String value) implements StaticFieldCommand {}
        public record StaticFieldCommandPrimitiveArray(String klass, String fieldName, String signature, int length) implements StaticFieldCommand {}
        public record StaticFieldCommandRefArray(String klass, String fieldName, String signature, int length, boolean nullFree, String actualKlass) implements StaticFieldCommand {}
        public record StaticFieldCommandFlatArray(String klass, String fieldName, String signature, int length, boolean nullFree, boolean nonAtomic, String actualKlass) implements StaticFieldCommand {}
        public record StaticFieldCommandNullArray(String klass, String fieldName, String signature) implements StaticFieldCommand {}
        public record StaticFieldCommandString(String klass, String fieldName, String value) implements StaticFieldCommand {
            public String signature() { return "Ljava/lang/String;"; }
        }
        public record StaticFieldCommandInstance(String klass, String fieldName, String signature, List<String> actualKlassOrValues) implements StaticFieldCommand {}
        // ciMethodData <klass> <name> <signature> <state> <invocationCounter> orig <length> <byte>* data <length> <ptr>* oops <length> (<offset> <klass> <array properties>?)* methods <length> (<offset> <klass> <name> <signature>)*
        sealed interface CiMethodDataCommandOop permits CiMethodDataCommandOopInstance, CiMethodDataCommandOopArray {}
        public record CiMethodDataCommandOopInstance(int offset, String klass) implements CiMethodDataCommandOop {}
        public record CiMethodDataCommandOopArray(int offset, String klass, int arrayProperties) implements CiMethodDataCommandOop {}
        public record CiMethodDataCommandMethod(int offset, String klass, String name, String signature) {}
        public record CiMethodDataCommand(String klass, String name, String signature, int state, int invocationCounter, List<Integer> orig, List<String> data, List<CiMethodDataCommandOop> oops, List<CiMethodDataCommandMethod> methods) implements Command {}
        // ciMethod <klass> <name> <signature> <invocation_counter> <backedge_counter> <interpreter_invocation_count> <interpreter_throwout_count> <instructions_size>
        public record CiMethodCommand(String klass, String name, String signature, int invocationCounter, int backedgeCounter, int interpreterInvocationCount, int interpreterThrowoutCount, int instructionsSize) implements Command {}
        // compile <klass> <name> <signature> <entry_bci> <comp_level> inline <count> (<depth> <bci> <inline_late> <klass> <name> <signature>)*
        public record CompileCommandInline(int depth, int bci, boolean inlineLate, String klass, String name, String signature) {}
        public record CompileCommand(String klass, String name, String signature, int entryBci, int compLevel, List<CompileCommandInline> inlines) implements Command {}

        ParsedReplayFile(List<Command> commands) { this.commands = commands; }
        List<Command> commands;

        // Set by sanity checking
        boolean checked = false;
        // Set by indexing, only after sanity checking
        public record StaticField(String klass, String name) {}
        HashMap<StaticField, StaticFieldCommand> staticFieldCommands = null;

        static public ParsedReplayFile parse(File file) throws IOException {
            return parse(Files.readAllLines(file.toPath()));
        }
        static public ParsedReplayFile parse(List<String> lines) {
            return new ParsedReplayFile(lines.stream().map(ParsedReplayFile::parseLine).filter(Objects::nonNull).toList());
        }
        static Command parseLine(String line) {
            List<String> pieces = Arrays.stream(line.split(" ")).filter(piece -> !piece.isEmpty()).toList();
            int commentIdx = pieces.indexOf("#");
            if (commentIdx >= 0) {
                pieces = pieces.subList(0, commentIdx);
            }
            if (pieces.isEmpty()) {
                return null;
            }
            String command = pieces.getFirst();
            var linePieces = LinePieces.make(pieces, command);
            var cmd = switch (command) {
                case "version" -> parseVersion(linePieces);
                case "JvmtiExport" -> parseJvmtiExport(linePieces);
                case "instanceKlass" -> parseInstanceKlass(linePieces);
                case "ciInstanceKlass" -> parseCiInstanceKlass(linePieces);
                case "staticfield" -> parseStaticField(linePieces);
                case "ciMethodData" -> parseCiMethodData(linePieces);
                case "ciMethod" -> parseCiMethod(linePieces);
                case "compile" -> parseCompile(linePieces);
                default -> throw new RuntimeException("unknown command: " + command);
            };
            linePieces.checkAtEnd();
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

            static public LinePieces make(List<String> pieces, String commandName) {
                var line = new LinePieces(pieces);
                line.getKeywork(commandName);
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

            public boolean getBoolKeyword(String falseKw, String trueKw) {
                String s = getString();
                if (s.equals(falseKw)) return false;
                if (s.equals(trueKw)) return true;
                throw new RuntimeException("unexepcted boolean keyword; got " + s + "; expected " + falseKw + " (for false) or " + trueKw + " (for true)");
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
            var nextS = pieces.getString();
            while (!nextS.equals(";")) {
                location.add(nextS);
                nextS = pieces.getString();
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
            boolean isLinked = pieces.getBool();
            boolean isInitialized = pieces.getBool();
            int length = pieces.getInt();
            List<Integer> tag = pieces.getInts(length - 1);
            return new CiInstanceKlassCommand(name, isLinked, isInitialized, length, tag);
        }

        static boolean isPrimitiveType(char c) {
            return "IBCSZJFD".contains(String.valueOf(c));
        }

        static StaticFieldCommand parseStaticField(LinePieces pieces) {
            String klass = pieces.getString();
            String fieldName = pieces.getString();
            String signature = pieces.getString();
            if (isPrimitiveType(signature.charAt(0))) {
                String val = pieces.getString();
                return new StaticFieldCommandPrimitive(klass, fieldName, signature, val);
            }
            if (signature.charAt(0) == '[') {
                if (isPrimitiveType(signature.charAt(1))) {
                    int length = pieces.getInt();
                    return new StaticFieldCommandPrimitiveArray(klass, fieldName, signature, length);
                } else {
                    int length = pieces.getInt();
                    if (length == -1) {
                        return new StaticFieldCommandNullArray(klass, fieldName, signature);
                    }
                    boolean isFlat = pieces.getBoolKeyword("ref", "flat");
                    boolean nullFree = pieces.getBoolKeyword("nullable", "null-free");
                    if (isFlat) {
                        boolean nonAtomic = pieces.getBoolKeyword("atomic", "non-atomic");
                        String actualKlass = pieces.getString();
                        return new StaticFieldCommandFlatArray(klass, fieldName, signature, length, nullFree, nonAtomic, actualKlass);
                    } else {
                        String actualKlass = pieces.getString();
                        return new StaticFieldCommandRefArray(klass, fieldName, signature, length, nullFree, actualKlass);
                    }
                }
            }
            if (signature.equals("Ljava/lang/String;")) {
                String value = pieces.getString();
                return new StaticFieldCommandString(klass, fieldName, value);
            }
            List<String> actualKlassOrValues = pieces.getLeftoverStrings();
            return new StaticFieldCommandInstance(klass, fieldName, signature, actualKlassOrValues);
        }

        // oops <length> (<offset> <klass> <array properties>?)* methods <length> (<offset> <klass> <name> <signature>)*
        static CiMethodDataCommand parseCiMethodData(LinePieces pieces) {
            String klass = pieces.getString();
            String name = pieces.getString();
            String signature = pieces.getString();
            int state = pieces.getInt();
            int invocationCounter = pieces.getInt();

            pieces.getKeywork("orig");
            int origLength = pieces.getInt();
            List<Integer> orig = pieces.getInts(origLength);

            pieces.getKeywork("data");
            int datalength = pieces.getInt();
            List<String> data = pieces.getStrings(datalength);

            pieces.getKeywork("oops");
            int oopsLength = pieces.getInt();
            List<CiMethodDataCommandOop> oops = new ArrayList<>(oopsLength);

            for (int i = 0; i < oopsLength; i++) {
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
            int methodsLength = pieces.getInt();
            List<CiMethodDataCommandMethod> methods = new ArrayList<>(methodsLength);

            for (int i = 0; i < methodsLength; i++) {
                int offset = pieces.getInt();
                String klass_ = pieces.getString();
                String name_ = pieces.getString();
                String signature_ = pieces.getString();
                methods.add(new CiMethodDataCommandMethod(offset, klass_, name_, signature_));
            }

            return new CiMethodDataCommand(klass, name, signature, state, invocationCounter, orig, data, oops, methods);
        }

        static CiMethodCommand parseCiMethod(LinePieces pieces) {
            String klass = pieces.getString();
            String name = pieces.getString();
            String signature = pieces.getString();
            int invocationCounter = pieces.getInt();
            int backedgeCounter = pieces.getInt();
            int interpreterInvocationCount = pieces.getInt();
            int interpreterThrowoutCount = pieces.getInt();
            int instructionsSize = pieces.getInt();
            return new CiMethodCommand(klass, name, signature, invocationCounter, backedgeCounter, interpreterInvocationCount, interpreterThrowoutCount, instructionsSize);
        }

        static CompileCommand parseCompile(LinePieces pieces) {
            String klass = pieces.getString();
            String name = pieces.getString();
            String signature = pieces.getString();
            int entryBci = pieces.getInt();
            int compLevel = pieces.getInt();
            pieces.getKeywork("inline");
            int count = pieces.getInt();

            List<CompileCommandInline> inlines = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                int depth = pieces.getInt();
                int bci = pieces.getInt();
                boolean inlineLate = pieces.getBool();
                String klass_ = pieces.getString();
                String name_ = pieces.getString();
                String signature_ = pieces.getString();
                inlines.add(new CompileCommandInline(depth, bci, inlineLate, klass_, name_, signature_));
            }

            return new CompileCommand(klass, name, signature, entryBci, compLevel, inlines);
        }

        List<String> checkSanity() {
            record Method(String klass, String name, String signature) {}
            record Field(String klass, String name) {}
            List<String> insanities = new ArrayList<>();

            int seenVersionCommands = 0;
            Set<Method> seenCiMethod = new HashSet<>();
            Set<Method> seenCiMethodData = new HashSet<>();
            Set<Method> seenCompile = new HashSet<>();
            Set<String> seenKlasses = new HashSet<>();
            Map<Field, String> seenFields = new HashMap<>();
            for (Command c : commands) {
                switch (c) {
                    case CiInstanceKlassCommand(String name, boolean isLinked, boolean isInitialized, int length, List<Integer> tag) -> seenKlasses.add(name);
                    case StaticFieldCommand cmd -> {
                        String klass = cmd.klass();
                        String fieldName = cmd.fieldName();
                        if (!seenKlasses.contains(klass)) {
                            insanities.add("Static field command " + cmd + " seen before the corresponding ciInstanceKlass command.");
                        }
                        var field = new Field(klass, fieldName);
                        if (seenFields.containsKey(field)) {
                            insanities.add("Already seen the static field " + klass + "::" + fieldName + " with signature " + seenFields.get(field) + ". This time, it had signature " + cmd.signature() + ".");
                        } else {
                            seenFields.put(field, cmd.signature());
                        }
                    }
                    case CompileCommand(String klass, String name, String signature, int entryBci, int compLevel, List<CompileCommandInline> inlines) -> {
                        var method = new Method(klass, name, signature);
                        seenCompile.add(method);
                        if (!seenCiMethod.contains(method)) {
                            insanities.add("Found \"compile\" command without a \"ciMethod\" command for the same method.");
                        }
                        if (!seenCiMethodData.contains(method)) {
                            insanities.add("Found \"compile\" command without a \"ciMethodData\" command for the same method.");
                        }
                    }
                    case CiMethodCommand(String klass, String name, String signature, int invocationCounter, int backedgeCounter, int interpreterInvocationCount, int interpreterThrowoutCount, int instructionsSize) ->
                        seenCiMethod.add(new Method(klass, name, signature));
                    case CiMethodDataCommand(String klass, String name, String signature, int state, int invocationCounter, List<Integer> orig, List<String> data, List<CiMethodDataCommandOop> oops, List<CiMethodDataCommandMethod> methods) ->
                        seenCiMethodData.add(new Method(klass, name, signature));
                    case VersionCommand _ ->
                        seenVersionCommands++;
                    case InstanceKlassCommand _,
                         JvmtiExportCommand _ -> {
                    }
                }
            }

            if (seenCompile.isEmpty()) {
                insanities.add("No \"compile\" command found.");
            }

            if (seenVersionCommands == 0) {
                insanities.add("No \"version\" command found.");
            } else if (seenVersionCommands > 1) {
                insanities.add("Found too many \"version\" commands: " + seenVersionCommands);
            }
            checked = true;
            return insanities;
        }

        // Use it only after checkSanity.
        void index() {
            Asserts.assertTrue(checked);
            staticFieldCommands = new HashMap<>();

            for (Command c : commands) {
                switch (c) {
                    case StaticFieldCommand cmd -> {
                        String klass = cmd.klass();
                        String fieldName = cmd.fieldName();
                        staticFieldCommands.put(new StaticField(klass, fieldName), cmd);
                    }
                    case CiInstanceKlassCommand _,
                         CiMethodCommand _,
                         CiMethodDataCommand _,
                         CompileCommand _,
                         InstanceKlassCommand _,
                         JvmtiExportCommand _,
                         VersionCommand _ -> {}
                }

            }
        }

        static Optional<Integer> getVersion(ParsedReplayFile parsed) {
            return parsed.commands.stream().map(cmd -> switch (cmd) { case VersionCommand(int version) -> version; default -> null; }).filter(Objects::nonNull).findAny();
        }
        static void compareVersion(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            Optional<Integer> lhsVersion = getVersion(lhs);
            Optional<Integer> rhsVersion = getVersion(rhs);

            if (lhsVersion.isPresent() && rhsVersion.isPresent() && !lhsVersion.get().equals(rhsVersion.get())) {
                differences.add("Versions mismatch: lhs=" + lhsVersion.get() + "; rhs=" + rhsVersion.get());
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
        static <T, U> void diffMaps(String name, HashMap<T, U> lhs, HashMap<T, U> rhs, BiPredicate<U, U> eqValue, List<String> differences) {
            lhs.forEach((key, lValue) -> {
                        if (!rhs.containsKey(key)) {
                            differences.add(name + " mismatch: key=" + key + " exists only in lhs");
                        } else {
                            U rValue = rhs.get(key);
                            if (!eqValue.test(lValue, rValue)) {
                                differences.add(name + " mismatch: for key=" + key + "; value in lhs=" + lValue + "; value in rhs=" + rValue);
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
            HashMap<String, Integer> lhsJvmti = extractMap(lhs, folder);
            HashMap<String, Integer> rhsJvmti = extractMap(rhs, folder);
            diffMaps("JvmtiExport", lhsJvmti, rhsJvmti, Integer::equals, differences);
        }

        static void compareInstanceKlassNames(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            BiConsumer<HashSet<String>, Command> folder = (acc, command) -> {
                if (command instanceof InstanceKlassCommandName(String name)) {
                    acc.add(name);
                }
            };
            HashSet<String> lhsKlasses = extractSet(lhs, folder);
            HashSet<String> rhsKlasses = extractSet(rhs, folder);
            diffSets("InstanceKlass", lhsKlasses, rhsKlasses, differences);
        }
        static void compareInstanceKlassCpi(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, int cpi) {}
            BiConsumer<HashMap<Key, List<String>>, Command> folder = (acc, command) -> {
                if (command instanceof InstanceKlassCommandCpi(String klass, int cpi, List<String> location)) {
                    acc.put(new Key(klass, cpi), location);
                }
            };
            var lhsKlasses = extractMap(lhs, folder);
            var rhsKlasses = extractMap(rhs, folder);
            diffMaps("InstanceKlass", lhsKlasses, rhsKlasses, List::equals, differences);
        }
        static void compareInstanceKlassBci(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String name, String signature, int bci) {}
            BiConsumer<HashMap<Key, List<String>>, Command> folder = (acc, command) -> {
                if (command instanceof InstanceKlassCommandBci(String klass, String name, String signature, int bci, List<String> location)) {
                    acc.put(new Key(klass, name, signature, bci), location);
                }
            };
            var lhsKlasses = extractMap(lhs, folder);
            var rhsKlasses = extractMap(rhs, folder);
            diffMaps("InstanceKlass", lhsKlasses, rhsKlasses, List::equals, differences);
        }
        static void compareInstanceKlasses(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            compareInstanceKlassNames(lhs, rhs, differences);
            compareInstanceKlassCpi(lhs, rhs, differences);
            compareInstanceKlassBci(lhs, rhs, differences);
        }

        static void compareCiInstanceKlasses(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Element(String name, boolean isLinked, boolean isInitialized, int length) {}
            BiConsumer<HashSet<Element>, Command> folder = (acc, command) -> {
                if (command instanceof CiInstanceKlassCommand(String name, boolean isLinked, boolean isInitialized, int length, List<Integer> _)) {
                    acc.add(new Element(name, isLinked, isInitialized, length));
                }
            };
            var lhsCiLlasses = extractSet(lhs, folder);
            var rhsCiLlasses = extractSet(rhs, folder);
            diffSets("CiInstanceKlass", lhsCiLlasses, rhsCiLlasses, differences);
        }

        static void compareStaticFieldCommandPrimitive(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String fieldName, String signature) {}
            BiConsumer<HashMap<Key, String>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandPrimitive(String klass, String fieldName, String signature, String value)) {
                    acc.put(new Key(klass, fieldName, signature), value);
                }
            };
            var lhsStaticFields = extractMap(lhs, folder);
            var rhsStaticFields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhsStaticFields, rhsStaticFields, String::equals, differences);
        }
        static void compareStaticFieldCommandPrimitiveArray(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String fieldName, String signature) {}
            BiConsumer<HashMap<Key, Integer>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandPrimitiveArray(String klass, String fieldName, String signature, int length)) {
                    acc.put(new Key(klass, fieldName, signature), length);
                }
            };
            var lhsStaticFields = extractMap(lhs, folder);
            var rhsStaticFields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhsStaticFields, rhsStaticFields, Integer::equals, differences);
        }
        static void compareStaticFieldCommandRefArray(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String fieldName, String signature) {}
            record Value(int length, boolean nullFree, String actualKlass) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandRefArray(String klass, String fieldName, String signature, int length, boolean nullFree, String actualKlass)) {
                    acc.put(new Key(klass, fieldName, signature), new Value(length, nullFree, actualKlass));
                }
            };
            var lhsStaticFields = extractMap(lhs, folder);
            var rhsStaticFields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhsStaticFields, rhsStaticFields, Value::equals, differences);
        }
        static void compareStaticFieldCommandFlatArray(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String fieldName, String signature) {}
            record Value(int length, boolean nullFree, boolean nonAtomic, String actualKlass) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandFlatArray(String klass, String fieldName, String signature, int length, boolean nullFree, boolean nonAtomic, String actualKlass)) {
                    acc.put(new Key(klass, fieldName, signature), new Value(length, nullFree, nonAtomic, actualKlass));
                }
            };
            var lhsStaticFields = extractMap(lhs, folder);
            var rhsStaticFields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhsStaticFields, rhsStaticFields, Value::equals, differences);
        }
        static void compareStaticFieldCommandNullArray(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Element(String klass, String fieldName, String signature) {}
            BiConsumer<HashSet<Element>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandNullArray(String klass, String fieldName, String signature)) {
                    acc.add(new Element(klass, fieldName, signature));
                }
            };
            var lhsStaticFields = extractSet(lhs, folder);
            var rhsStaticFields = extractSet(rhs, folder);
            diffSets("CiInstanceKlass", lhsStaticFields, rhsStaticFields, differences);
        }
        static void compareStaticFieldCommandString(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String fieldName) {}
            BiConsumer<HashMap<Key, String>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandString(String klass, String fieldName, String value)) {
                    acc.put(new Key(klass, fieldName), value);
                }
            };
            var lhsStaticFields = extractMap(lhs, folder);
            var rhsStaticFields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhsStaticFields, rhsStaticFields, String::equals, differences);
        }
        static void compareStaticFieldCommandInstance(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String fieldName, String signature) {}
            BiConsumer<HashMap<Key, List<String>>, Command> folder = (acc, command) -> {
                if (command instanceof StaticFieldCommandInstance(String klass, String fieldName, String signature, List<String> actualKlassOrValues)) {
                    acc.put(new Key(klass, fieldName, signature), actualKlassOrValues);
                }
            };
            var lhsStaticFields = extractMap(lhs, folder);
            var rhsStaticFields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhsStaticFields, rhsStaticFields, List::equals, differences);
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
            record Value(int state, int invocationCounter) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof CiMethodDataCommand(String klass, String name, String signature, int state, int invocationCounter, List<Integer> _, List<String> _, List<CiMethodDataCommandOop> _, List<CiMethodDataCommandMethod> _)) {
                    acc.put(new Key(klass, name, signature), new Value(state, invocationCounter));
                }
            };
            var lhsStaticFields = extractMap(lhs, folder);
            var rhsStaticFields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhsStaticFields, rhsStaticFields, Value::equals, differences);
        }

        static void compareCiMethodCommand(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String name, String signature) {}
            record Value(int invocationCounter, int backedgeCounter, int interpreterInvocationCount, int interpreterThrowoutCount, int instructionsSize) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof CiMethodCommand(String klass, String name, String signature, int invocationCounter, int backedgeCounter, int interpreterInvocationCount, int interpreterThrowoutCount, int instructionsSize)) {
                    acc.put(new Key(klass, name, signature), new Value(invocationCounter, backedgeCounter, interpreterInvocationCount, interpreterThrowoutCount, instructionsSize));
                }
            };
            var lhsStaticFields = extractMap(lhs, folder);
            var rhsStaticFields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhsStaticFields, rhsStaticFields, Value::equals, differences);
        }

        static void compareCompileCommand(ParsedReplayFile lhs, ParsedReplayFile rhs, List<String> differences) {
            record Key(String klass, String name, String signature) {}
            record Value(int entryBci, int compLevel, List<CompileCommandInline> inlines) {}
            BiConsumer<HashMap<Key, Value>, Command> folder = (acc, command) -> {
                if (command instanceof CompileCommand(String klass, String name, String signature, int entryBci, int compLevel, List<CompileCommandInline> inlines)) {
                    acc.put(new Key(klass, name, signature), new Value(entryBci, compLevel, inlines));
                }
            };
            var lhsStaticFields = extractMap(lhs, folder);
            var rhsStaticFields = extractMap(rhs, folder);
            diffMaps("CiInstanceKlass", lhsStaticFields, rhsStaticFields, Value::equals, differences);
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

        Optional<StaticFieldCommand> findStaticFieldCommand(String klass, String fieldName) {
            Asserts.assertNotNull(staticFieldCommands);  // Must be already indexed
            var f = new StaticField(klass, fieldName);
            if (!staticFieldCommands.containsKey(f)) {
                return Optional.empty();
            }
            return Optional.ofNullable(staticFieldCommands.get(f));
        }
    }
}
