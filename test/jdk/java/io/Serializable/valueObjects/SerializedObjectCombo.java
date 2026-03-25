/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import combo.ComboInstance;
import combo.ComboParameter;
import combo.ComboTask;
import combo.ComboTestHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OptionalDataException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Asserts.assertFalse;

import jdk.test.lib.hexdump.HexPrinter;
import jdk.test.lib.hexdump.ObjectStreamPrinter;

/*
 * @test
 * @summary Deserialization Combo tests
 * @library /test/langtools/tools/javac/lib /test/lib .
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @build combo.ComboTestHelper SerializedObjectCombo
 * @run main/othervm --enable-preview  SerializedObjectCombo
 */


public final class SerializedObjectCombo extends ComboInstance<SerializedObjectCombo> {
    private static final Map<Path, URLClassLoader> LOADER_FOR_PATH = new ConcurrentHashMap<>();
    private static final ParamSet KIND_SET = new ParamSet("KIND",
            SerializationKind.values());
    private static final ParamSet FIELD_SET = new ParamSet("FIELD",
            2, ArgumentValue.BASIC_VALUES);
    private static final ParamSet CLASSACCESS_SET = new ParamSet("CLASSACCESS",
            new ClassAccessKind[]{ClassAccessKind.PUBLIC});
    private static final ParamSet SPECIAL_WRITE_METHODS_SET = new ParamSet("SPECIAL_WRITE_METHODS",
            WriteObjectFragments.values());
    private static final ParamSet SPECIAL_READ_METHODS_SET = new ParamSet("SPECIAL_READ_METHODS",
            ReadObjectFragments.values());
    private static final ParamSet EXTERNALIZABLE_METHODS_SET = new ParamSet("EXTERNALIZABLE_METHODS",
            ExternalizableMethodFragments.values());
    private static final ParamSet OBJECT_CONSTRUCTOR_SET = new ParamSet("OBJECT_CONSTRUCTOR",
            ObjectConstructorFragment.ANNOTATED_OBJECT_CONSTRUCTOR_FRAGMENT, ObjectConstructorFragment.NONE);
    private static final ParamSet VALUE_SET = new ParamSet("VALUE",
            ValueKind.values());
    private static final ParamSet TESTNAME_EXTENDS_SET = new ParamSet("TESTNAME_EXTENDS",
                    TestNameExtendsFragments.NONE, TestNameExtendsFragments.TESTNAME_EXTENDS_FRAGMENT);
    private static final ParamSet TOP_ABSTRACT_SET = new ParamSet("TOP_FRAGMENTS",
            TopFragments.values());
    /**
     * The base template to generate all test classes.
     * Each substitutable fragment is defined by an Enum of the alternatives.
     * Giving each a name and an array of ComboParameters with the expansion value.
     */
    private static final String TEST_SOURCE_TEMPLATE = """
            import java.io.*;
            import java.util.*;
            import jdk.internal.value.DeserializeConstructor;
            import jdk.internal.MigratedValueClass;

            #{TOP_FRAGMENTS}

            @MigratedValueClass
            #{CLASSACCESS} #{VALUE} class #{TESTNAME} #{TESTNAME_EXTENDS} #{KIND.IMPLEMENTS} {
                #{FIELD[0]} f1;
                #{FIELD[1]} f2;
                #{FIELD_ADDITIONS}
                #{CLASSACCESS} #{TESTNAME}() {
                    f1 = #{FIELD[0].RANDOM};
                    f2 = #{FIELD[1].RANDOM};
                    #{FIELD_CONSTRUCTOR_ADDITIONS}
                }
            #{OBJECT_CONSTRUCTOR}
                @Override public boolean equals(Object obj) {
                    if (obj instanceof #{TESTNAME} other) {
                        if (#{FIELD[0]}.class.isPrimitive()) {
                            if (f1 != other.f1) return false;
                        } else {
                            if (!Objects.equals(f1, other.f1)) return false;
                        }
                        if (#{FIELD[1]}.class.isPrimitive()) {
                            if (f2 != other.f2) return false;
                        } else {
                            if (!Objects.equals(f2, other.f2)) return false;
                        }
                        return true;
                    }
                    return false;
                }
                @Override public String toString() {
                    return "f1: " + String.valueOf(f1) +
                            ", f2: " + String.valueOf(f2)
                            #{FIELD_TOSTRING_ADDITIONS};
                }
            #{KIND.SPECIAL_METHODS}
                private static final long serialVersionUID = 1L;
            }
            """;

    // The unique number to qualify interface names, unique across multiple runs
    private static int uniqueId = 0;
    // Compilation errors prevent execution; set/cleared by checkCompile
    private ComboTask.Result<?> compilationResult = null;
    // The current set of parameters for the file being compiled and tested
    private final Set<ComboParameter> currParams = new HashSet<>();

    private static List<String> focusKeys = null;

    private enum CommandOption {
        SHOW_SOURCE("--show-source", "show source files"),
        VERBOSE("--verbose", "show extra information"),
        SHOW_SERIAL_STREAM("--show-serial", "show and format the serialized stream"),
        EVERYTHING("--everything", "run all tests"),
        TRACE("--trace", "set TRACE system property of ObjectInputStream (temp)"),
        MAX_COMBOS("--max-combo", "maximum number of values for each parameter", CommandOption::parseInt),
        NO_PRE_FILTER("--no-pre-filter", "disable pre-filter checks"),
        SELFTEST("--self-test", "run some self tests and exit"),
        ;
        private final String option;
        private final String usage;
        private final BiFunction<CommandOption, String, Boolean> parseArg;
        private Optional<Object> value;
        CommandOption(String option, String usage, BiFunction<CommandOption, String, Boolean> parseArg) {
            this.option = option;
            this.usage = usage;
            this.parseArg = parseArg;
            this.value = Optional.empty();
        }
        CommandOption(String option, String usage) {
            this(option, usage, null);
        }

        /**
         * Evaluate and parse an array of command line args
         * @param args array of strings
         * @return true if parsing succeeded
         */
        static boolean parseOptions(String[] args) {
            boolean unknownArg = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                Optional<CommandOption> knownOpt = Arrays.stream(CommandOption.values())
                        .filter(o -> o.option.equals(arg))
                        .findFirst();
                if (knownOpt.isEmpty()) { // Not a recognized option
                    if (arg.startsWith("-")) {
                        System.err.println("Unrecognized option: " + arg);
                        unknownArg = true;
                    } else {
                        // Take the remaining non-option args as selectors of keys to be run
                        String[] keys = Arrays.copyOfRange(args, i, args.length);
                        focusKeys = List.of(keys);
                    }
                } else {
                    CommandOption option = knownOpt.get();
                    if (option.parseArg == null) {
                        option.setValue(true);
                    } else {
                        i++;
                        if (i >= args.length || args[i].startsWith("--")) {
                            System.err.println("Missing argument for " + option.option);
                            continue;
                        }
                        option.parseArg.apply(option, args[i]);
                    }
                }
            }
            return !unknownArg;
        }
        static void showUsage() {
            System.out.println("""
                Usage:
                """);
            Arrays.stream(CommandOption.values()).forEach(o -> System.out.printf("  %-15s: %s\n", o.option, o.usage));
        }
        boolean present() {
            return value != null && value.isPresent();
        }
        void setValue(Object o) {
            value = Optional.ofNullable(o);
        }
        private static boolean parseInt(CommandOption option, String arg) {
            try {
                int count = Integer.parseInt(arg);
                option.setValue(count);
            } catch (NumberFormatException nfe) {
                System.out.println("--max-combo argument not a number: " + arg);
            }
            return true;
        }
        // Get the int value from the option, defaulting if not valid or present
        private int getInt(int otherMax) {
            Object obj = value == null ? otherMax : value.orElseGet(() -> otherMax);
            return (obj instanceof Integer i) ? i : otherMax;
        }
    }

    private static URLClassLoader getLoaderFor(Path path) {
        return LOADER_FOR_PATH.computeIfAbsent(path,
                p -> {
                    try {
                        // new URLClassLoader for path
                        Files.createDirectories(p);
                        URL[] urls = {p.toUri().toURL()};
                        return new URLClassLoader(p.toString(), urls, null);
                    } catch (IOException ioe) {
                        throw new UncheckedIOException(ioe);
                    }
                });
    }

    // Map an array of strings to an array of ComboParameter.Constants.
    @SuppressWarnings("unchecked")
    private static ComboParameter.Constant<String>[] paramsForStrings(String... strings) {
        return Arrays.stream(strings)
                .map(ComboParameter.Constant::new).toArray(ComboParameter.Constant[]::new);
    }

    /**
     * Main to generate combinations and run the tests.
     *
     * @param args may contain "--verbose" to show source of every file
     * @throws Exception In case of failure
     */
    public static void main(String... args) throws Exception {
        if (!CommandOption.parseOptions(args)) {
            CommandOption.showUsage();
            System.exit(1);
        }

        Arrays.stream(CommandOption.values())
                .filter(o -> o.present())
                .forEach( o1 -> System.out.printf("   %15s: %s\n", o1.option, o1.value
                ));

        if (CommandOption.SELFTEST.present()) {
            selftest();
            return;
        }

        // Sets of all possible ComboParameters (substitutions)
        Set<ParamSet> allParams = Set.of(
                VALUE_SET,
                KIND_SET,
                TOP_ABSTRACT_SET,
                OBJECT_CONSTRUCTOR_SET,
                TESTNAME_EXTENDS_SET,
                CLASSACCESS_SET,
                SPECIAL_READ_METHODS_SET,
                SPECIAL_WRITE_METHODS_SET,
                EXTERNALIZABLE_METHODS_SET,
                FIELD_SET
        );

        // Test variations of all code shapes
        var helper = new ComboTestHelper<SerializedObjectCombo>();
        int maxCombos = CommandOption.MAX_COMBOS.getInt(2);

        Set<ParamSet> subSet = CommandOption.EVERYTHING.present() ? allParams
                : computeSubset(allParams, focusKeys, maxCombos);
        withDimensions(helper, subSet);
        if (CommandOption.VERBOSE.present()) {
            System.out.println("Keys; maximum combinations: " + maxCombos);
            subSet.stream()
                    .sorted((p, q) -> String.CASE_INSENSITIVE_ORDER.compare(p.key(), q.key()))
                    .forEach(p -> System.out.println("    " + p.key + ": " + Arrays.toString(p.params)));
        }
        helper.withFilter(SerializedObjectCombo::filter)
                .withFailMode(ComboTestHelper.FailMode.FAIL_FAST)
                .run(SerializedObjectCombo::new);
    }

    private static void withDimensions(ComboTestHelper<SerializedObjectCombo> helper, Set<ParamSet> subSet) {
        subSet.forEach(p -> {
            if (p.count() == 1)
                helper.withDimension(p.key(), SerializedObjectCombo::saveParameter, p.params());
            else
                helper.withArrayDimension(p.key(), SerializedObjectCombo::saveParameter, p.count(), p.params());
        });
    }

    // Return a subset of ParamSets with the non-focused ParamSet's truncated to a max number of values
    private static Set<ParamSet> computeSubset(Set<ParamSet> allParams, List<String> focusKeys, int maxKeys) {
        if (focusKeys == null || focusKeys.isEmpty())
            return allParams;
        Set<ParamSet> r = allParams.stream().map(p ->
                        (focusKeys.contains(p.key())) ? p
                                : new ParamSet(p.key, p.count(), Arrays.copyOfRange(p.params(), 0, Math.min(p.params().length, maxKeys))))
                .collect(Collectors.toUnmodifiableSet());
        return r;
    }

    /**
     * Print the source files to System out
     *
     * @param task the compilation task
     */
    static void showSources(ComboTask task) {
        task.getSources()
                .forEach(fo -> {
                    System.out.println("Source: " + fo.getName());
                    System.out.println(getSource(fo));
                });
    }

    /**
     * Return the contents of the source file
     *
     * @param fo a file object
     * @return the contents of the source file
     */
    static String getSource(JavaFileObject fo) {
        try (Reader reader = fo.openReader(true)) {
            char[] buf = new char[100000];
            var len = reader.read(buf);
            return new String(buf, 0, len);
        } catch (IOException ioe) {
            return "IOException: " + fo.getName() + ", ex: " + ioe.getMessage();
        }
    }

    /**
     * Dump the serial stream.
     *
     * @param bytes the bytes of the stream
     */
    private static void showSerialStream(byte[] bytes) {
        HexPrinter.simple().dest(System.out).formatter(ObjectStreamPrinter.formatter()).format(bytes);
    }

    /**
     * Serialize an object into byte array.
     */
    private static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bs)) {
            out.writeObject(obj);
        }
        return bs.toByteArray();
    }

    /**
     * Deserialize an object from byte array using the requested classloader.
     */
    private static Object deserialize(byte[] ba, ClassLoader loader) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new LoaderObjectInputStream(new ByteArrayInputStream(ba), loader)) {
            return in.readObject();
        }
    }


    @Override
    public int id() {
        return ++uniqueId;
    }

    private void fail(String msg, Throwable thrown) {
        super.fail(msg);
        thrown.printStackTrace(System.out);
    }

    /**
     * Save a parameter.
     *
     * @param param a ComboParameter
     */
    private void saveParameter(ComboParameter param) {
        saveParameter(param, 0);
    }

    /**
     * Save an indexed parameter.
     *
     * @param param a ComboParameter
     * @param index unused
     */
    private void saveParameter(ComboParameter param, int index) {
        currParams.add(param);
    }

    /**
     * Filter out needless tests (mostly with more variations of arguments than needed).
     * Usually, these are compile time failures, or code shapes that cannot succeed.
     *
     * @return true to run the test, false if not
     */
    boolean filter() {
        if (!CommandOption.NO_PRE_FILTER.present()) {
            for (CodeShape shape : CodeShape.values()) {
                if (shape.test(currParams)) {
                    if (CommandOption.VERBOSE.present()) {
                        System.out.println("IGNORING: " + shape);
                    }
                    return false;
                }
            }
        }
        if (CommandOption.VERBOSE.present()) {
            System.out.println("TESTING: ");
            showParams();
        }
        return true;
    }

    /**
     * Generate the source files from the parameters and test a single combination.
     * Two versions are compiled into different directories and separate class loaders.
     * They differ only with the addition of a field to the generated class.
     * Then each class is serialized and deserialized by the other class,
     * testing simple evolution in the process.
     *
     * @throws IOException catch all IOException
     */
    @Override
    public void doWork() throws IOException {
        String cp = System.getProperty("test.classes");
        String className = "Class_" + this.id();

        // new URLClassLoader for path
        final Path firstPath = Path.of(cp, "1st");
        URLClassLoader firstLoader = getLoaderFor(firstPath);
        final Path secondPath = Path.of(cp, "2nd");
        URLClassLoader secondLoader = getLoaderFor(secondPath);

        // Create a map of additional constants that are resolved without the combo overhead.
        final Map<String, ComboParameter.Constant<String>> params = new HashMap<>();
        params.put("TESTNAME", new ComboParameter.Constant<>(className));
        params.put("SPECIAL_METHODS_SERIALIZABLE", new ComboParameter.Constant<>("#{SPECIAL_READ_METHODS} #{SPECIAL_WRITE_METHODS}"));
        params.put("SPECIAL_METHODS_EXTERNALIZABLE", new ComboParameter.Constant<>("#{EXTERNALIZABLE_METHODS}"));
        params.put("FIELD_ADDITIONS", new ComboParameter.Constant<>(""));
        params.put("FIELD_CONSTRUCTOR_ADDITIONS", new ComboParameter.Constant<>(""));
        params.put("FIELD_TOSTRING_ADDITIONS", new ComboParameter.Constant<>(""));

        final ComboTask firstTask = generateAndCompile(firstPath, className, params);

        if (firstTask == null) {
            return; // Skip execution, errors already reported
        }

        if (CommandOption.EVERYTHING.present()) {
            params.put("FIELD_ADDITIONS", new ComboParameter.Constant<>("int fExtra;"));
            params.put("FIELD_CONSTRUCTOR_ADDITIONS", new ComboParameter.Constant<>("this.fExtra = 99;"));
            params.put("FIELD_TOSTRING_ADDITIONS", new ComboParameter.Constant<>("+ \", fExtra: String.valueOf(fExtra)\""));
            final ComboTask secondTask = generateAndCompile(secondPath, className, params);
            if (secondTask == null) {
                return; // Skip execution, errors already reported
            }

            doTestWork(className, firstTask, firstLoader, secondLoader);
            doTestWork(className, secondTask, secondLoader, firstLoader);
        } else {
            doTestWork(className, firstTask, firstLoader, firstLoader);
        }
    }

    /**
     * Test that two versions of the class can be serialized using one version and deserialized
     * by the other version.
     * The two classes have the same name and have been compiled into different classloaders.
     * The original and result objects are compared using .equals if there is only 1 classloader.
     * If the classloaders are different the `toString()` output for each object is compared loosely.
     * (One must be the prefix of the other)
     *
     * @param className    the class name
     * @param task         the task context (for source and parameters to report failures)
     * @param firstLoader  the first classloader
     * @param secondLoader the second classloader
     */
    private void doTestWork(String className, ComboTask task, ClassLoader firstLoader, ClassLoader secondLoader) {
        byte[] bytes = null;
        try {
            Class<?> tc = Class.forName(className, true, firstLoader);
            Object testObj = tc.getDeclaredConstructor().newInstance();
            bytes = serialize(testObj);
            if (CommandOption.VERBOSE.present()) {
                System.out.println("Testing: " + task.getSources());
                if (CommandOption.SHOW_SOURCE.present()) {
                    showParams();
                    showSources(task);
                }
                if (CommandOption.SHOW_SERIAL_STREAM.present()) {
                    showSerialStream(bytes);
                }
            }

            if (CodeShape.BAD_SO_CONSTRUCTOR.test(currParams)) {
                // should have thrown ICE due to mismatch between value class and missing constructor
                System.out.println(CodeShape.BAD_SO_CONSTRUCTOR.explain(currParams));
                fail(CodeShape.BAD_SO_CONSTRUCTOR.explain(currParams));
            }

            Object actual = deserialize(bytes, secondLoader);
            if (testObj.getClass().getClassLoader().equals(actual.getClass().getClassLoader())) {
                assertEquals(testObj, actual, "Round-trip comparison fail using .equals");
            } else {
                // The instances are from different classloaders and can't be compared directly
                final String s1 = testObj.toString();
                final String s2 = actual.toString();
                assertTrue(s1.startsWith(s2) || s2.startsWith(s1),
                        "Round-trip comparison fail using toString(): s1: " + s1 + ", s2: " + s2);
            }
        } catch (InvalidClassException ice) {
            for (CodeShape shape : CodeShape.values()){
                if (ice.equals(shape.exception)) {
                    if (shape.test(currParams)) {
                        if (CommandOption.VERBOSE.present()) {
                            System.out.println("OK: " + shape.explain(currParams));
                        } else {
                            // unexpected ICE
                            ice.printStackTrace(System.out);
                            showParams();
                            showSources(task);
                            if (bytes != null)
                                showSerialStream(bytes);
                            fail(ice.getMessage());
                        }
                    }
                }
            }
        } catch (EOFException | OptionalDataException eof) {
            // Ignore if conditions of the source invite EOF
            if (0 == CodeShape.shapesThrowing(EOFException.class).peek(s -> {
                // Ignore: Serialized Object to reads custom data but none written
                if (CommandOption.VERBOSE.present()) {
                    System.out.println("OK: " + s.explain(currParams));
                }
            }).count()) {
                eof.printStackTrace(System.out);
                showParams();
                showSources(task);
                showSerialStream(bytes);
                fail(eof.getMessage(), eof);
            }
        } catch (ClassFormatError cfe) {
            System.out.println(cfe.toString());
        } catch (NotSerializableException nse) {
            if (CodeShape.BAD_EXT_VALUE.test(currParams)) {
                // Expected Value class that is Externalizable w/o writeReplace
            } else {
                // unexpected NSE
                nse.printStackTrace(System.out);
                showParams();
                showSources(task);
                fail(nse.getMessage(), nse);
            }
        } catch (Throwable ex) {
            ex.printStackTrace(System.out);
            showParams();
            showSources(task);
            fail(ex.getMessage());
        }
    }

    // Side effect of error is compilationResult.hasErrors() > 0
    private ComboTask generateAndCompile(Path path, String className, Map<String, ComboParameter.Constant<String>> params) {
        ComboTask task = newCompilationTask()
                .withSourceFromTemplate(className,
                        TEST_SOURCE_TEMPLATE,
                        r -> params.computeIfAbsent(r, s -> new ComboParameter.Constant<>("UNKNOWN_" + s)))
                .withOption("-d")
                .withOption(path.toString())
                .withOption("--enable-preview")
                .withOption("--add-modules")
                .withOption("java.base")
                .withOption("--add-exports")
                .withOption("java.base/jdk.internal=ALL-UNNAMED")
                .withOption("--add-exports")
                .withOption("java.base/jdk.internal.value=ALL-UNNAMED")
                .withOption("--source")
                .withOption(Integer.toString(Runtime.version().feature()));
        ;
        task.generate(this::checkCompile);
        if (compilationResult.hasErrors()) {
            boolean match = false;
            for (CodeShape shape : CodeShape.values()){
                if (CompileException.class.equals(shape.exception)) {
                    if (shape.test(currParams)) {
                        // shape matches known error
                        if (!uniqueParams.contains(shape))  {
                            System.out.println("// Unique: " + shape);
                            uniqueParams.add(shape);
                        }
                        match = true;
                    }
                }
            }
            if (match)
                return null;
            // Unexpected compilation error
            showDiags(compilationResult);
            showSources(task);
            showParams();
            fail("Compilation failure");
        }
        return task;
    }

    private static Set<CodeShape> uniqueParams = new HashSet<>();

    private String paramToString(ComboParameter param) {
        String name = param.getClass().getName();
        return name.substring(name.indexOf('$') + 1) + "::" +
                param + ": " + truncate(param.expand(null), 60);
    }

    private void showParams() {
        currParams.stream()
                .sorted((p, q) -> String.CASE_INSENSITIVE_ORDER.compare(paramToString(p), paramToString(q)))
                .forEach(p -> System.out.println("    " + paramToString(p)));
    }

    private void showParams(ComboParameter... params) {
        for (ComboParameter param : params) {
            System.out.println(">>> " + paramToString(param) + ", present: "
                    + currParams.contains(param));
        }
    }

    private static String truncate(String s, int maxLen) {
        int nl = s.indexOf("\n");
        if (nl >= 0)
            maxLen = nl;
        if (maxLen < s.length()) {
            return s.substring(0, maxLen).concat("...");
        } else {
            return s;
        }
    }

    /**
     * Report any compilation errors.
     *
     * @param res the result
     */
    void checkCompile(ComboTask.Result<?> res) {
        compilationResult = res;
    }

    void showDiags(ComboTask.Result<?> res) {
        res.diagnosticsForKind(Diagnostic.Kind.ERROR).forEach(SerializedObjectCombo::showDiag);
        res.diagnosticsForKind(Diagnostic.Kind.WARNING).forEach(SerializedObjectCombo::showDiag);
    }

    static void showDiag(Diagnostic<? extends JavaFileObject> diag) {
        System.out.println(diag.getKind() + ": " + diag.getMessage(Locale.ROOT));
        System.out.println("File: " + diag.getSource() +
                " line: " + diag.getLineNumber() + ", col: " + diag.getColumnNumber());
    }

    private static class CodeShapePredicateOp<T> implements Predicate<T> {
        private final Predicate<T> first;
        private final Predicate<T> other;
        private final String op;

        CodeShapePredicateOp(Predicate<T> first, Predicate<T> other, String op) {
            if ("OR" != op && "AND" != op && "NOT" != op)
                throw new IllegalArgumentException("unknown op: " + op);
            this.first = first;
            this.other = other;
            this.op = op;
        }

        @Override
        public boolean test(T comboParameters) {
            return switch (op) {
                case "NOT" -> !first.test(comboParameters);
                case "OR" -> first.test(comboParameters) || other.test(comboParameters);
                case "AND" -> first.test(comboParameters) && other.test(comboParameters);
                default -> throw new IllegalArgumentException("unknown op: " + op);
            };
        }
        @Override
        public Predicate<T> and(Predicate<? super T> other) {
            return new CodeShapePredicateOp(this, other,"AND");
        }


        @Override
        public Predicate<T>  negate() {
            return new CodeShapePredicateOp(this, null,"NOT");
        }

        @Override
        public Predicate<T> or(Predicate<? super T> other) {
            return new CodeShapePredicateOp(this, other,"OR");
        }
        public String toString() {
            return switch (op) {
                case "NOT" -> op + " " + first;
                case "OR" -> "(" + first + " " + op + " " + other + ")";
                case "AND" -> "(" + first + " " + op + " " + other + ")";
                default -> throw new IllegalArgumentException("unknown op: " + op);
            };
        }
    }

    interface CodeShapePredicate extends Predicate<Set<ComboParameter>> {
        @Override
        default boolean test(Set<ComboParameter> comboParameters) {
            return comboParameters.contains(this);
        }

        @Override
        default Predicate<Set<ComboParameter>> and(Predicate<? super Set<ComboParameter>> other) {
            return new CodeShapePredicateOp(this, other,"AND");
        }


        @Override
        default Predicate<Set<ComboParameter>>  negate() {
            return new CodeShapePredicateOp(this, null,"NOT");
        }

        @Override
        default Predicate<Set<ComboParameter>> or(Predicate<? super Set<ComboParameter>> other) {
            return new CodeShapePredicateOp(this, other,"OR");
        }
    }

    /**
     * A set of code shapes that are interesting, usually indicating an error
     * compile time, or runtime based on the shape of the code and the dependencies between
     * the code fragments.
     * The descriptive text may be easier to understand than the boolean expression of the fragments.
     * They can also be to filter out test cases that would not succeed.
     * Or can be used after a successful deserialization to check
     * if an exception should have been thrown.
     */
    private enum CodeShape implements Predicate<Set<ComboParameter>> {
        BAD_SO_CONSTRUCTOR("Value class does not have a constructor annotated with DeserializeConstructor",
                InvalidClassException.class,
                ValueKind.VALUE,
                ObjectConstructorFragment.ANNOTATED_OBJECT_CONSTRUCTOR_FRAGMENT.negate()
                ),
        BAD_EXT_VALUE("Externalizable can not be a value class",
                CompileException.class,
                SerializationKind.EXTERNALIZABLE,
                ValueKind.VALUE),
        BAD_EXT_METHODS("Externalizable methods but not Externalizable",
                CompileException.class,
                ExternalizableMethodFragments.EXTERNALIZABLE_METHODS,
                SerializationKind.EXTERNALIZABLE.negate()),
        BAD_EXT_NO_METHODS("Externalizable but no implementation of readExternal or writeExternal",
                CompileException.class,
                SerializationKind.EXTERNALIZABLE,
                ExternalizableMethodFragments.EXTERNALIZABLE_METHODS.negate()),
        BAD_VALUE_NON_ABSTRACT_SUPER("Can't inherit from non-abstract super or abstract super with fields",
                CompileException.class,
                ValueKind.VALUE,
                TestNameExtendsFragments.TESTNAME_EXTENDS_FRAGMENT,
                TopFragments.ABSTRACT_NO_FIELDS.negate()),
        BAD_MISSING_SUPER("Extends TOP_ without TOP_ superclass",
                CompileException.class,
                TestNameExtendsFragments.TESTNAME_EXTENDS_FRAGMENT,
                TopFragments.NONE),
        BAD_READ_CUSTOM_METHODS("Custom read fragment but no custom write fragment",
                EOFException.class,
                ReadObjectFragments.READ_OBJECT_FIELDS_CUSTOM_FRAGMENT
                        .or(ReadObjectFragments.READ_OBJECT_DEFAULT_CUSTOM_FRAGMENT),
                WriteObjectFragments.WRITE_OBJECT_FIELDS_CUSTOM_FRAGMENT
                        .or(WriteObjectFragments.WRITE_OBJECT_DEFAULT_CUSTOM_FRAGMENT).negate()
                ),
        BAD_RW_CUSTOM_METHODS("Custom write fragment but no custom read fragment",
                null,
                WriteObjectFragments.WRITE_OBJECT_FIELDS_CUSTOM_FRAGMENT
                        .or(WriteObjectFragments.WRITE_OBJECT_DEFAULT_CUSTOM_FRAGMENT),
                ReadObjectFragments.READ_OBJECT_FIELDS_CUSTOM_FRAGMENT
                        .or(ReadObjectFragments.READ_OBJECT_DEFAULT_CUSTOM_FRAGMENT).negate()),
        BAD_VALUE_READOBJECT_METHODS("readObjectXXX(OIS) methods incompatible with Value class",
                CompileException.class,
                ReadObjectFragments.READ_OBJECT_FIELDS_FRAGMENT
                        .or(ReadObjectFragments.READ_OBJECT_DEFAULT_FRAGMENT)
                        .or(ReadObjectFragments.READ_OBJECT_FIELDS_CUSTOM_FRAGMENT)
                        .or(ReadObjectFragments.READ_OBJECT_DEFAULT_CUSTOM_FRAGMENT),
                ValueKind.VALUE),
        ;

        private final String description;
        private final Class<? extends Exception> exception;
        private final List<Predicate<Set<ComboParameter>>> predicates;
        CodeShape(String desc, Class<? extends Exception> exception, Predicate<Set<ComboParameter>>... predicates) {
            this.description = desc;
            this.exception = exception;
            this.predicates = List.of(predicates);
        }

        // Return a stream of CodeShapes throwing the exception
        static Stream<CodeShape> shapesThrowing(Class<?> exception) {
            return Arrays.stream(values()).filter(s -> exception.equals(s.exception));

        }

        /**
         * {@return true if all of the predicates are true in the set of ComboParameters}
         * @param comboParameters a set of ComboParameters
         */
        @Override
        public boolean test(Set<ComboParameter> comboParameters) {
            for (Predicate<Set<ComboParameter>> p : predicates) {
                if (!p.test(comboParameters))
                    return false;
            }
            return true;
        }

        /**
         * {@return a string describing the predicate in relation to a set of parameters}
         * @param comboParameters a set of active ComboParameters.
         */
        public String explain(Set<ComboParameter> comboParameters) {
            StringBuffer sbTrue = new StringBuffer();
            StringBuffer sbFalse = new StringBuffer();
            for (Predicate<Set<ComboParameter>> p : predicates) {
                ((p.test(comboParameters)) ? sbTrue : sbFalse)
                        .append(p).append(", ");
            }
            return description + "\n" +"Missing: " + sbFalse + "\nTrue: " + sbTrue;
        }
        public String toString() {
            return super.toString() + "::" + description + ", params: " + predicates;
        }
    }

    /**
     * TopAbstract Fragments
     */
    enum TopFragments implements ComboParameter, CodeShapePredicate {
        NONE(""),
        ABSTRACT_NO_FIELDS("""
                @MigratedValueClass
                abstract #{VALUE} class TOP_#{TESTNAME} implements Serializable {
                    #{CLASSACCESS} TOP_#{TESTNAME}() {}
                }
                """),
        ABSTRACT_ONE_FIELD("""
                @MigratedValueClass
                abstract #{VALUE} class TOP_#{TESTNAME} implements Serializable {
                    private int t1;
                    #{CLASSACCESS} TOP_#{TESTNAME}() {
                        t1 = 1;
                    }
                }
                """),
        NO_FIELDS("""
                @MigratedValueClass
                #{VALUE} class TOP_#{TESTNAME} implements Serializable {
                    #{CLASSACCESS} TOP_#{TESTNAME}() {}
                }
                """),
        ONE_FIELD("""
                @MigratedValueClass
                #{VALUE} class TOP_#{TESTNAME} implements Serializable {
                    private int t1;
                    #{CLASSACCESS} TOP_#{TESTNAME}() {
                        t1 = 1;
                    }
                }
                """),
        ;

        private final String template;

        TopFragments(String template) {
            this.template = template;
        }

        @Override
        public String expand(String optParameter) {
            return template;
        }
    }

    /**
     * TopAbstract Fragments
     */
    enum TestNameExtendsFragments implements ComboParameter, CodeShapePredicate {
        NONE(""),
        TESTNAME_EXTENDS_FRAGMENT("extends TOP_#{TESTNAME}"),
        ;

        private final String template;

        TestNameExtendsFragments(String template) {
            this.template = template;
        }

        @Override
        public String expand(String optParameter) {
            return template;
        }
    }

    /**
     * SerializedObjectCustom Fragments
     */
    enum SerializedObjectCustomFragments implements ComboParameter, CodeShapePredicate {
        NONE(""),
        ;

        private final String template;

        SerializedObjectCustomFragments(String template) {
            this.template = template;
        }

        @Override
        public String expand(String optParameter) {
            return template;
        }
    }

    /**
     * ExternalizableMethod Fragments
     */
    enum ExternalizableMethodFragments implements ComboParameter, CodeShapePredicate {
        NONE(""),
        EXTERNALIZABLE_METHODS("""
                    public void writeExternal(ObjectOutput oos) throws IOException {
                        oos.write#{FIELD[0].READFIELD}(f1);
                        oos.write#{FIELD[1].READFIELD}(f2);
                    }

                    public void readExternal(ObjectInput ois) throws IOException, ClassNotFoundException {
                        f1 = (#{FIELD[0]})ois.read#{FIELD[0].READFIELD}();
                        f2 = (#{FIELD[1]})ois.read#{FIELD[1].READFIELD}();
                    }
                """),
        ;

        private final String template;

        ExternalizableMethodFragments(String template) {
            this.template = template;
        }

        @Override
        public String expand(String optParameter) {
            return template;
        }
    }

    /**
     * ObjectConstructorFragment Fragments
     */
    enum ObjectConstructorFragment implements ComboParameter, CodeShapePredicate {
        NONE(""),
        ANNOTATED_OBJECT_CONSTRUCTOR_FRAGMENT("""
                    @DeserializeConstructor
                    #{CLASSACCESS} #{TESTNAME}(#{FIELD[0]} f1, #{FIELD[1]} f2) {
                        this.f1 = f1;
                        this.f2 = f2;
                        #{FIELD_CONSTRUCTOR_ADDITIONS}
                    }

                    @DeserializeConstructor
                    #{CLASSACCESS} #{TESTNAME}(#{FIELD[0]} f1, #{FIELD[1]} f2, int fExtra) {
                        this.f1 = f1;
                        this.f2 = f2;
                        #{FIELD_CONSTRUCTOR_ADDITIONS}
                    }
                """),

        ;

        private final String template;

        ObjectConstructorFragment(String template) {
            this.template = template;
        }

        @Override
        public String expand(String optParameter) {
            return template;
        }
    }

    /**
     * WriteObject templates
     */
    enum WriteObjectFragments implements ComboParameter, CodeShapePredicate {
        NONE(""),
        WRITE_OBJECT_DEFAULT_FRAGMENT("""
                    private void writeObject(ObjectOutputStream oos) throws IOException {
                        oos.defaultWriteObject();
                    }
                """),
        WRITE_OBJECT_FIELDS_FRAGMENT("""
                    private void writeObject(ObjectOutputStream oos) throws IOException {
                        ObjectOutputStream.PutField fields = oos.putFields();
                        fields.put("f1", f1);
                        fields.put("f2", f2);
                        oos.writeFields();
                    }
                """),
        WRITE_OBJECT_DEFAULT_CUSTOM_FRAGMENT("""
                    private void writeObject(ObjectOutputStream oos) throws IOException {
                        oos.defaultWriteObject();
                        // Write custom data
                        oos.write#{FIELD[0].READFIELD}(#{FIELD[0].DEFAULT});
                        oos.write#{FIELD[1].READFIELD}(#{FIELD[1].DEFAULT});
                    }
                """),
        WRITE_OBJECT_FIELDS_CUSTOM_FRAGMENT("""
                    private void writeObject(ObjectOutputStream oos) throws IOException {
                        ObjectOutputStream.PutField fields = oos.putFields();
                        fields.put("f1", f1);
                        fields.put("f2", f2);
                        oos.writeFields();
                        // Write custom data
                        oos.write#{FIELD[0].READFIELD}(#{FIELD[0].DEFAULT});
                        oos.write#{FIELD[1].READFIELD}(#{FIELD[1].DEFAULT});
                    }
                """),
        ;

        private final String template;

        WriteObjectFragments(String template) {
            this.template = template;
        }

        @Override
        public String expand(String optParameter) {
            return template;
        }
    }

    /**
     * ReadObject templates
     */
    enum ReadObjectFragments implements ComboParameter, CodeShapePredicate {
        NONE(""),
        READ_OBJECT_DEFAULT_FRAGMENT("""
                    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
                        ois.defaultReadObject();
                    }
                """),
        READ_OBJECT_FIELDS_FRAGMENT("""
                    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
                        ObjectInputStream.GetField fields = ois.readFields();
                        this.f1 = (#{FIELD[0]})fields.get("f1", #{FIELD[0].DEFAULT});
                        this.f2 = (#{FIELD[1]})fields.get("f2", #{FIELD[1].DEFAULT});
                    }
                 """),
        READ_OBJECT_DEFAULT_CUSTOM_FRAGMENT("""
                    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
                        ois.defaultReadObject();
                        // Read custom data
                        #{FIELD[0]} d1 = (#{FIELD[0]})ois.read#{FIELD[0].READFIELD}();
                        #{FIELD[1]} d2 = (#{FIELD[1]})ois.read#{FIELD[1].READFIELD}();
                        assert Objects.equals(#{FIELD[0].DEFAULT}, d1) : "reading custom data1, actual: " + d1;
                        assert Objects.equals(#{FIELD[1].DEFAULT}, d2) : "reading custom data2, actual: " + d2;
                    }
                """),
        READ_OBJECT_FIELDS_CUSTOM_FRAGMENT("""
                    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
                        ObjectInputStream.GetField fields = ois.readFields();
                        this.f1 = (#{FIELD[0]})fields.get("f1", #{FIELD[0].DEFAULT});
                        this.f2 = (#{FIELD[1]})fields.get("f2", #{FIELD[1].DEFAULT});
                        // Read custom data
                        #{FIELD[0]} d1 = (#{FIELD[0]})ois.read#{FIELD[0].READFIELD}();
                        #{FIELD[1]} d2 = (#{FIELD[1]})ois.read#{FIELD[1].READFIELD}();
                        assert Objects.equals(#{FIELD[0].DEFAULT}, d1) : "reading custom data1, actual: " + d1;
                        assert Objects.equals(#{FIELD[1].DEFAULT}, d2) : "reading custom data2, actual: " + d2;
                    }
                 """),
        ;

        private final String template;

        ReadObjectFragments(String template) {
            this.template = template;
        }

        @Override
        public String expand(String optParameter) {
            return template;
        }
    }

    /**
     * Value and Identity kinds.
     */
    enum ValueKind implements ComboParameter, CodeShapePredicate {
        VALUE("value"),
        IDENTITY(""),
        ;

        private final String template;

        ValueKind(String template) {
            this.template = template;
        }

        @Override
        public String expand(String optParameter) {
            return template;
        }
    }

    enum SerializationKind implements ComboParameter, CodeShapePredicate {
        SERIALIZABLE("SER", "implements Serializable"),
        EXTERNALIZABLE("EXT", "implements Externalizable"),
        ;

        private final String key;
        private final String declaration;

        SerializationKind(String key, String declaration) {
            this.key = key;
            this.declaration = declaration;
        }

        public String expand(String optParameter) {
            return switch (optParameter) {
                case null -> key;
                case "IMPLEMENTS" -> declaration;
                default ->
                        "#{" + optParameter + "_" + this + "}";   // everything ELSE turn into requested key with suffix
            };
        }
    }

    /**
     * Class Access kinds.
     */
    enum ClassAccessKind implements ComboParameter, CodeShapePredicate {
        PUBLIC("public"),
        PACKAGE(""),
        ;

        private final String classAccessTemplate;

        ClassAccessKind(String classAccessTemplate) {
            this.classAccessTemplate = classAccessTemplate;
        }

        @Override
        public String expand(String optParameter) {
            return classAccessTemplate;
        }
    }

    /**
     * Type of arguments to insert in method signatures
     */
    enum ArgumentValue implements ComboParameter, CodeShapePredicate {
        BOOLEAN("boolean", true),
        BYTE("byte", (byte) 127),
        CHAR("char", 'Z'),
        SHORT("short", (short) 0x7fff),
        INT("int", 0x7fffffff),
        LONG("long", 0x7fffffffffffffffL),
        FLOAT("float", 1.0F),
        DOUBLE("double", 1.0d),
        STRING("String", "xyz");

        static final ArgumentValue[] BASIC_VALUES = {INT, STRING};

        private final String argumentsValueTemplate;
        private final Object value;

        ArgumentValue(String argumentsValueTemplate, Object value) {
            this.argumentsValueTemplate = argumentsValueTemplate;
            this.value = value;
        }

        @Override
        public String expand(String optParameter) {
            return switch (optParameter) {
                case null -> argumentsValueTemplate;
                case "TITLECASE" -> Character.toTitleCase(argumentsValueTemplate.charAt(0)) +
                        argumentsValueTemplate.substring(1);
                case "DEFAULT" -> switch (this) {
                    case BOOLEAN -> "false";
                    case BYTE -> "(byte)-1";
                    case CHAR -> "'" + "!" + "'";
                    case SHORT -> "(short)-1";
                    case INT -> "-1";
                    case LONG -> "-1L";
                    case FLOAT -> "-1.0f";
                    case DOUBLE -> "-1.0d";
                    case STRING -> '"' + "n/a" + '"';
                };
                case "READFIELD" -> switch (this) {
                    case BOOLEAN -> "Boolean";
                    case BYTE -> "Byte";
                    case CHAR -> "Char";
                    case SHORT -> "Short";
                    case INT -> "Int";
                    case LONG -> "Long";
                    case FLOAT -> "Float";
                    case DOUBLE -> "Double";
                    case STRING -> "Object";
                };
                case "RANDOM" -> switch (this) {  // or can be Random
                    case BOOLEAN -> Boolean.toString(!(boolean) value);
                    case BYTE -> "(byte)" + value + 1;
                    case CHAR -> "'" + value + "'";
                    case SHORT -> "(short)" + value + 1;
                    case INT -> "-2";
                    case LONG -> "-2L";
                    case FLOAT -> (1.0f + (float) value) + "f";
                    case DOUBLE -> (1.0d + (float) value) + "d";
                    case STRING -> "\"" + value + "!\"";
                };
                default -> switch (this) {
                    case BOOLEAN -> value.toString();
                    case BYTE -> "(byte)" + value;
                    case CHAR -> "'" + value + "'";
                    case SHORT -> "(short)" + value;
                    case INT -> "-1";
                    case LONG -> "-1L";
                    case FLOAT -> value + "f";
                    case DOUBLE -> value + "d";
                    case STRING -> '"' + (String) value + '"';
                };
            };
        }
    }

    /**
     * Set of Parameters to fill in template.
     *
     * @param key    the key
     * @param params the ComboParameters (one or more)
     */
    record ParamSet(String key, int count, ComboParameter... params) {
        /**
         * Set of parameter strings for fill in template.
         * The strings are mapped to CompboParameter.Constants.
         *
         * @param key     the key
         * @param strings varargs strings
         */
        ParamSet(String key, String... strings) {
            this(key, 1, paramsForStrings(strings));
        }

        /**
         * Set of parameter strings for fill in template.
         * The strings are mapped to CompboParameter.Constants.
         *
         * @param key     the key
         * @param strings varargs strings
         */
        ParamSet(String key, int count, String... strings) {
            this(key, count, paramsForStrings(strings));
        }

        /**
         * Set of parameters for fill in template.
         * The strings are mapped to CompboParameter.Constants.
         *
         * @param key    the key
         * @param params varargs strings
         */
        ParamSet(String key, ComboParameter... params) {
            this(key, 1, params);
        }
    }

    /**
     * Marks conditions that should match compile time errors
     */
    static class CompileException extends RuntimeException {
        CompileException(String msg) {
            super(msg);
        }
    }

    /**
     * Custom ObjectInputStream to be resolve classes from a specific class loader.
     */
    private static class LoaderObjectInputStream extends ObjectInputStream {
        private final ClassLoader loader;

        public LoaderObjectInputStream(InputStream in, ClassLoader loader) throws IOException {
            super(in);
            this.loader = loader;
        }

        /**
         * Override resolveClass to be resolve classes from the specified loader.
         *
         * @param desc an instance of class {@code ObjectStreamClass}
         * @return the class
         * @throws ClassNotFoundException if the class is not found
         */
        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException {
            String name = desc.getName();
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ex) {
                Class<?> cl = Class.forPrimitiveName(name);
                if (cl != null) {
                    return cl;
                } else {
                    throw ex;
                }
            }
        }
    }

    private abstract class MyCompilationTask extends ComboInstance {

    }
    private static void selftest() {
        Set<ComboParameter> params = Set.of(ValueKind.VALUE, SerializationKind.EXTERNALIZABLE);
        assertTrue(ValueKind.VALUE.test(params), "VALUE");
        assertTrue(SerializationKind.EXTERNALIZABLE.test(params), "SerializationKind.EXTERNALIZABLE");
        assertFalse(CodeShape.BAD_EXT_VALUE.test(params));
    }
}
