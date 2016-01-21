/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.jshell.tool.JShellTool;
import jdk.jshell.SourceCodeAnalysis.Suggestion;

import static java.util.stream.Collectors.toList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class ReplToolTesting {

    private final static String DEFAULT_STARTUP_MESSAGE = "|  Welcome to";
    final static List<ImportInfo> START_UP_IMPORTS = Stream.of(
                    "java.util.*",
                    "java.io.*",
                    "java.math.*",
                    "java.net.*",
                    "java.util.concurrent.*",
                    "java.util.prefs.*",
                    "java.util.regex.*")
                    .map(s -> new ImportInfo("import " + s + ";", "", s))
                    .collect(toList());
    final static List<MethodInfo> START_UP_METHODS = Stream.of(
                    new MethodInfo("void printf(String format, Object... args) { System.out.printf(format, args); }",
                            "(String,Object...)void", "printf"))
                    .collect(toList());
    final static List<String> START_UP = Collections.unmodifiableList(
            Stream.concat(START_UP_IMPORTS.stream(), START_UP_METHODS.stream())
            .map(s -> s.getSource())
            .collect(toList()));

    private WaitingTestingInputStream cmdin = null;
    private ByteArrayOutputStream cmdout = null;
    private ByteArrayOutputStream cmderr = null;
    private PromptedCommandOutputStream console = null;
    private TestingInputStream userin = null;
    private ByteArrayOutputStream userout = null;
    private ByteArrayOutputStream usererr = null;

    private List<MemberInfo> keys;
    private Map<String, VariableInfo> variables;
    private Map<String, MethodInfo> methods;
    private Map<String, ClassInfo> classes;
    private Map<String, ImportInfo> imports;
    private boolean isDefaultStartUp = true;

    public JShellTool repl = null;

    public interface ReplTest {
        void run(boolean after);
    }

    public void setCommandInput(String s) {
        cmdin.setInput(s);
    }

    public final static Pattern idPattern = Pattern.compile("^\\s+(\\d+)");
    public Consumer<String> assertList() {
        return s -> {
            List<String> lines = Stream.of(s.split("\n"))
                    .filter(l -> !l.isEmpty())
                    .collect(Collectors.toList());
            int previousId = Integer.MIN_VALUE;
            assertEquals(lines.size(), keys.size(), "Number of keys");
            for (int i = 0; i < lines.size(); ++i) {
                String line = lines.get(i);
                Matcher matcher = idPattern.matcher(line);
                assertTrue(matcher.find(), "Snippet id not found: " + line);
                String src = keys.get(i).getSource();
                assertTrue(line.endsWith(src), "Line '" + line + "' does not end with: " + src);
                int id = Integer.parseInt(matcher.group(1));
                assertTrue(previousId < id,
                        String.format("The previous id is not less than the next one: previous: %d, next: %d",
                                previousId, id));
                previousId = id;
            }
        };
    }

    private final static Pattern extractPattern = Pattern.compile("^\\| *(.*)$");
    private Consumer<String> assertMembers(String message, Map<String, ? extends MemberInfo> set) {
        return s -> {
            List<String> lines = Stream.of(s.split("\n"))
                    .filter(l -> !l.isEmpty())
                    .collect(Collectors.toList());
            assertEquals(lines.size(), set.size(), message + " : expected: " + set.keySet() + "\ngot:\n" + lines);
            for (String line : lines) {
                Matcher matcher = extractPattern.matcher(line);
                assertTrue(matcher.find(), line);
                String src = matcher.group(1);
                MemberInfo info = set.get(src);
                assertNotNull(info, "Not found snippet with signature: " + src + ", line: "
                        + line + ", keys: " + set.keySet() + "\n");
            }
        };
    }

    public Consumer<String> assertVariables() {
        return assertMembers("Variables", variables);
    }

    public Consumer<String> assertMethods() {
        return assertMembers("Methods", methods);
    }

    public Consumer<String> assertClasses() {
        return assertMembers("Classes", classes);
    }

    public Consumer<String> assertImports() {
        return assertMembers("Imports", imports);
    }

    public String getCommandOutput() {
        String s = normalizeLineEndings(cmdout.toString());
        cmdout.reset();
        return s;
    }

    public String getCommandErrorOutput() {
        String s = normalizeLineEndings(cmderr.toString());
        cmderr.reset();
        return s;
    }

    public void setUserInput(String s) {
        userin.setInput(s);
    }

    public String getUserOutput() {
        String s = normalizeLineEndings(userout.toString());
        userout.reset();
        return s;
    }

    public String getUserErrorOutput() {
        String s = normalizeLineEndings(usererr.toString());
        usererr.reset();
        return s;
    }

    public void test(ReplTest... tests) {
        test(new String[0], tests);
    }

    public void test(String[] args, ReplTest... tests) {
        test(true, args, tests);
    }

    public void test(boolean isDefaultStartUp, String[] args, ReplTest... tests) {
        test(isDefaultStartUp, args, DEFAULT_STARTUP_MESSAGE, tests);
    }

    public void test(boolean isDefaultStartUp, String[] args, String startUpMessage, ReplTest... tests) {
        this.isDefaultStartUp = isDefaultStartUp;
        initSnippets();
        ReplTest[] wtests = new ReplTest[tests.length + 3];
        wtests[0] = a -> assertCommandCheckOutput(a, "<start>",
                s -> assertTrue(s.startsWith(startUpMessage), "Expected start-up message '" + startUpMessage + "' Got: " + s));
        wtests[1] = a -> assertCommand(a, "/debug 0", null);
        System.arraycopy(tests, 0, wtests, 2, tests.length);
        wtests[tests.length + 2] = a -> assertCommand(a, "/exit", null);
        testRaw(args, wtests);
    }

    private void initSnippets() {
        keys = new ArrayList<>();
        variables = new HashMap<>();
        methods = new HashMap<>();
        classes = new HashMap<>();
        imports = new HashMap<>();
        if (isDefaultStartUp) {
            methods.putAll(
                START_UP_METHODS.stream()
                    .collect(Collectors.toMap(Object::toString, Function.identity())));
            imports.putAll(
                START_UP_IMPORTS.stream()
                    .collect(Collectors.toMap(Object::toString, Function.identity())));
        }
    }

    public void testRaw(String[] args, ReplTest... tests) {
        cmdin = new WaitingTestingInputStream();
        cmdout = new ByteArrayOutputStream();
        cmderr = new ByteArrayOutputStream();
        console = new PromptedCommandOutputStream(tests);
        userin = new TestingInputStream();
        userout = new ByteArrayOutputStream();
        usererr = new ByteArrayOutputStream();
        repl = new JShellTool(
                cmdin,
                new PrintStream(cmdout),
                new PrintStream(cmderr),
                new PrintStream(console),
                userin,
                new PrintStream(userout),
                new PrintStream(usererr));
        repl.testPrompt = true;
        try {
            repl.start(args);
        } catch (Exception ex) {
            fail("Repl tool died with exception", ex);
        }
        // perform internal consistency checks on state, if desired
        String cos = getCommandOutput();
        String ceos = getCommandErrorOutput();
        String uos = getUserOutput();
        String ueos = getUserErrorOutput();
        assertTrue((cos.isEmpty() || cos.startsWith("|  Goodbye")),
                "Expected a goodbye, but got: " + cos);
        assertTrue(ceos.isEmpty(), "Expected empty error output, got: " + ceos);
        assertTrue(uos.isEmpty(), "Expected empty output, got: " + uos);
        assertTrue(ueos.isEmpty(), "Expected empty error output, got: " + ueos);
    }

    public void assertReset(boolean after, String cmd) {
        assertCommand(after, cmd, "|  Resetting state.\n");
        initSnippets();
    }

    public void evaluateExpression(boolean after, String type, String expr, String value) {
        String output = String.format("\\| *Expression values is: %s\n|" +
                " *.*temporary variable (\\$\\d+) of type %s", value, type);
        Pattern outputPattern = Pattern.compile(output);
        assertCommandCheckOutput(after, expr, s -> {
            Matcher matcher = outputPattern.matcher(s);
            assertTrue(matcher.find(), "Output: '" + s + "' does not fit pattern: '" + output + "'");
            String name = matcher.group(1);
            VariableInfo tempVar = new TempVariableInfo(expr, type, name, value);
            variables.put(tempVar.toString(), tempVar);
            addKey(after, tempVar);
        });
    }

    public void loadVariable(boolean after, String type, String name) {
        loadVariable(after, type, name, null, null);
    }

    public void loadVariable(boolean after, String type, String name, String expr, String value) {
        String src = expr == null
                ? String.format("%s %s", type, name)
                : String.format("%s %s = %s", type, name, expr);
        VariableInfo var = expr == null
                ? new VariableInfo(src, type, name)
                : new VariableInfo(src, type, name, value);
        addKey(after, var, variables);
        addKey(after, var);
    }

    public void assertVariable(boolean after, String type, String name) {
        assertVariable(after, type, name, null, null);
    }

    public void assertVariable(boolean after, String type, String name, String expr, String value) {
        String src = expr == null
                ? String.format("%s %s", type, name)
                : String.format("%s %s = %s", type, name, expr);
        VariableInfo var = expr == null
                ? new VariableInfo(src, type, name)
                : new VariableInfo(src, type, name, value);
        assertCommandCheckOutput(after, src, var.checkOutput());
        addKey(after, var, variables);
        addKey(after, var);
    }

    public void loadMethod(boolean after, String src, String signature, String name) {
        MethodInfo method = new MethodInfo(src, signature, name);
        addKey(after, method, methods);
        addKey(after, method);
    }

    public void assertMethod(boolean after, String src, String signature, String name) {
        MethodInfo method = new MethodInfo(src, signature, name);
        assertCommandCheckOutput(after, src, method.checkOutput());
        addKey(after, method, methods);
        addKey(after, method);
    }

    public void loadClass(boolean after, String src, String type, String name) {
        ClassInfo clazz = new ClassInfo(src, type, name);
        addKey(after, clazz, classes);
        addKey(after, clazz);
    }

    public void assertClass(boolean after, String src, String type, String name) {
        ClassInfo clazz = new ClassInfo(src, type, name);
        assertCommandCheckOutput(after, src, clazz.checkOutput());
        addKey(after, clazz, classes);
        addKey(after, clazz);
    }

    public void loadImport(boolean after, String src, String type, String name) {
        ImportInfo i = new ImportInfo(src, type, name);
        addKey(after, i, imports);
        addKey(after, i);
    }

    public void assertImport(boolean after, String src, String type, String name) {
        ImportInfo i = new ImportInfo(src, type, name);
        assertCommandCheckOutput(after, src, i.checkOutput());
        addKey(after, i, imports);
        addKey(after, i);
    }

    private <T extends MemberInfo> void addKey(boolean after, T memberInfo, Map<String, T> map) {
        if (after) {
            map.entrySet().removeIf(e -> e.getValue().equals(memberInfo));
            map.put(memberInfo.toString(), memberInfo);
        }
    }

    private <T extends MemberInfo> void addKey(boolean after, T memberInfo) {
        if (after) {
            for (int i = 0; i < keys.size(); ++i) {
                MemberInfo m = keys.get(i);
                if (m.equals(memberInfo)) {
                    keys.set(i, memberInfo);
                    return;
                }
            }
            keys.add(memberInfo);
        }
    }

    private void dropKey(boolean after, String cmd, String name, Map<String, ? extends MemberInfo> map) {
        assertCommand(after, cmd, "");
        if (after) {
            map.remove(name);
            for (int i = 0; i < keys.size(); ++i) {
                MemberInfo m = keys.get(i);
                if (m.toString().equals(name)) {
                    keys.remove(i);
                    return;
                }
            }
            throw new AssertionError("Key not found: " + name + ", keys: " + keys);
        }
    }

    public void dropVariable(boolean after, String cmd, String name) {
        dropKey(after, cmd, name, variables);
    }

    public void dropMethod(boolean after, String cmd, String name) {
        dropKey(after, cmd, name, methods);
    }

    public void dropClass(boolean after, String cmd, String name) {
        dropKey(after, cmd, name, classes);
    }

    public void dropImport(boolean after, String cmd, String name) {
        dropKey(after, cmd, name, imports);
    }

    public void assertCommand(boolean after, String cmd, String out) {
        assertCommand(after, cmd, out, "", null, "", "");
    }

    public void assertCommandCheckOutput(boolean after, String cmd, Consumer<String> check) {
        if (!after) {
            assertCommand(false, cmd, null);
        } else {
            String got = getCommandOutput();
            check.accept(got);
            assertCommand(true, cmd, null);
        }
    }

    public void assertCommand(boolean after, String cmd, String out, String err,
            String userinput, String print, String usererr) {
        if (!after) {
            if (userinput != null) {
                setUserInput(userinput);
            }
            setCommandInput(cmd + "\n");
        } else {
            assertOutput(getCommandOutput(), out, "command");
            assertOutput(getCommandErrorOutput(), err, "command error");
            assertOutput(getUserOutput(), print, "user");
            assertOutput(getUserErrorOutput(), usererr, "user error");
        }
    }

    public void assertCompletion(boolean after, String code, boolean isSmart, String... expected) {
        if (!after) {
            setCommandInput("\n");
        } else {
            assertCompletion(code, isSmart, expected);
        }
    }

    public void assertCompletion(String code, boolean isSmart, String... expected) {
        List<String> completions = computeCompletions(code, isSmart);
        assertEquals(completions, Arrays.asList(expected), "Command: " + code + ", output: " +
                completions.toString());
    }

    private List<String> computeCompletions(String code, boolean isSmart) {
        JShellTool repl = this.repl != null ? this.repl
                                      : new JShellTool(null, null, null, null, null, null, null);
        int cursor =  code.indexOf('|');
        code = code.replace("|", "");
        assertTrue(cursor > -1, "'|' not found: " + code);
        List<Suggestion> completions =
                repl.commandCompletionSuggestions(code, cursor, new int[1]); //XXX: ignoring anchor for now
        return completions.stream()
                          .filter(s -> isSmart == s.isSmart)
                          .map(s -> s.continuation)
                          .distinct()
                          .collect(Collectors.toList());
    }

    public Consumer<String> assertStartsWith(String prefix) {
        return (output) -> assertTrue(output.startsWith(prefix), "Output: \'" + output + "' does not start with: " + prefix);
    }

    public void assertOutput(String got, String expected, String kind) {
        if (expected != null) {
            assertEquals(got, expected, "Kind: " + kind + ".\n");
        }
    }

    private String normalizeLineEndings(String text) {
        return text.replace(System.getProperty("line.separator"), "\n");
    }

    public static abstract class MemberInfo {
        public final String source;
        public final String type;
        public final String name;

        public MemberInfo(String source, String type, String name) {
            this.source = source;
            this.type = type;
            this.name = name;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        public abstract Consumer<String> checkOutput();

        public String getSource() {
            return source;
        }
    }

    public static class VariableInfo extends MemberInfo {

        public final String value;
        public final String initialValue;

        public VariableInfo(String src, String type, String name) {
            super(src, type, name);
            this.initialValue = null;
            switch (type) {
                case "byte":
                case "short":
                case "int":
                case "long":
                    value = "0";
                    break;
                case "boolean":
                    value = "false";
                    break;
                case "char":
                    value = "''";
                    break;
                case "float":
                case "double":
                    value = "0.0";
                    break;
                default:
                    value = "null";
            }
        }

        public VariableInfo(String src, String type, String name, String value) {
            super(src, type, name);
            this.value = value;
            this.initialValue = value;
        }

        @Override
        public Consumer<String> checkOutput() {
            String pattern = String.format("\\| *\\w+ variable %s of type %s", name, type);
            if (initialValue != null) {
                pattern += " with initial value " + initialValue;
            }
            Predicate<String> checkOutput = Pattern.compile(pattern).asPredicate();
            final String finalPattern = pattern;
            return output -> assertTrue(checkOutput.test(output),
                    "Output: " + output + " does not fit pattern: " + finalPattern);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof VariableInfo) {
                VariableInfo v = (VariableInfo) o;
                return name.equals(v.name);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("%s %s = %s", type, name, value);
        }

        @Override
        public String getSource() {
            String src = super.getSource();
            return src.endsWith(";") ? src : src + ";";
        }
    }

    public static class TempVariableInfo extends VariableInfo {

        public TempVariableInfo(String src, String type, String name, String value) {
            super(src, type, name, value);
        }

        @Override
        public String getSource() {
            return source;
        }
    }

    public static class MethodInfo extends MemberInfo {

        public final String signature;

        public MethodInfo(String source, String signature, String name) {
            super(source, signature.substring(0, signature.lastIndexOf(')') + 1), name);
            this.signature = signature;
        }

        @Override
        public Consumer<String> checkOutput() {
            String expectedOutput = String.format("\\| *\\w+ method %s", name);
            Predicate<String> checkOutput = Pattern.compile(expectedOutput).asPredicate();
            return s -> assertTrue(checkOutput.test(s), "Expected: '" + expectedOutput + "', actual: " + s);
        }


        @Override
        public boolean equals(Object o) {
            if (o instanceof MemberInfo) {
                MemberInfo m = (MemberInfo) o;
                return name.equals(m.name) && type.equals(m.type);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("%s %s", name, signature);
        }
    }

    public static class ClassInfo extends MemberInfo {

        public ClassInfo(String source, String type, String name) {
            super(source, type, name);
        }

        @Override
        public Consumer<String> checkOutput() {
            String fullType = type.equals("@interface")? "annotation interface" : type;
            String expectedOutput = String.format("\\| *\\w+ %s %s", fullType, name);
            Predicate<String> checkOutput = Pattern.compile(expectedOutput).asPredicate();
            return s -> assertTrue(checkOutput.test(s), "Expected: '" + expectedOutput + "', actual: " + s);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ClassInfo) {
                ClassInfo c = (ClassInfo) o;
                return name.equals(c.name);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("%s %s", type, name);
        }
    }

    public static class ImportInfo extends MemberInfo {
        public ImportInfo(String source, String type, String fullname) {
            super(source, type, fullname);
        }

        @Override
        public Consumer<String> checkOutput() {
            return s -> assertTrue("".equals(s), "Expected: '', actual: " + s);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ImportInfo) {
                ImportInfo i = (ImportInfo) o;
                return name.equals(i.name) && type.equals(i.type);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("import %s%s", type.equals("static") ? "static " : "", name);
        }
    }

    class WaitingTestingInputStream extends TestingInputStream {

        @Override
        synchronized void setInput(String s) {
            super.setInput(s);
            notify();
        }

        synchronized void waitForInput() {
            boolean interrupted = false;
            try {
                while (available() == 0) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                        // fall through and retry
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public int read() {
            waitForInput();
            return super.read();
        }

        @Override
        public int read(byte b[], int off, int len) {
            waitForInput();
            return super.read(b, off, len);
        }
    }

    class PromptedCommandOutputStream extends OutputStream {
        private final ReplTest[] tests;
        private int index = 0;
        PromptedCommandOutputStream(ReplTest[] tests) {
            this.tests = tests;
        }

        @Override
        public synchronized void write(int b) {
            if (b == 5 || b == 6) {
                if (index < (tests.length - 1)) {
                    tests[index].run(true);
                    tests[index + 1].run(false);
                } else {
                    fail("Did not exit Repl tool after test");
                }
                ++index;
            } // For now, anything else is thrown away
        }

        @Override
        public synchronized void write(byte b[], int off, int len) {
            if ((off < 0) || (off > b.length) || (len < 0)
                    || ((off + len) - b.length > 0)) {
                throw new IndexOutOfBoundsException();
            }
            for (int i = 0; i < len; ++i) {
                write(b[off + i]);
            }
        }
    }
}
