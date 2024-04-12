/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 0000000
 * @summary Exercise runtime handing of templated strings.
 * @enablePreview true
 */

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import javax.tools.ToolProvider;

public class StringTemplateTest {
    enum Category{GENERAL, CHARACTER, INTEGRAL, BIG_INT, FLOATING, BIG_FLOAT, DATE};

    static final String[] GENERAL = {"true", "false", "(Object)null", "STR", "BO", "BOOL", "(Boolean)null"};
    static final String[] CHARS = {"C", "CHAR", "(Character)null"};
    static final String[] INTS = {"L", "LONG", "I", "INT", "S", "SHORT", "BY", "BYTE", "Long.MAX_VALUE", "Long.MIN_VALUE", "(Long)null", "(Integer)null", "(Short)null", "(Byte)null"};
    static final String[] BIGINTS = {};
    static final String[] FLOATS = {"F", "FLOAT", "D", "DOUBLE", "Double.NEGATIVE_INFINITY", "Double.NaN", "Double.MAX_VALUE", "(Double)null", "(Float)null"};
    static final String[] BIGFLOATS = {};
    static final String[] DATES = {};

    final Random r = new Random(1);

    String randomValue(Category category) {
        return switch (category) {
            case GENERAL -> randomChoice(
                    GENERAL,
                    () -> randomValue(Category.CHARACTER),
                    () -> randomValue(Category.INTEGRAL),
                    () -> randomValue(Category.BIG_INT),
                    () -> randomValue(Category.FLOATING),
                    () -> randomValue(Category.BIG_FLOAT),
                    () -> randomValue(Category.DATE),
                    () -> "\"" + randomString(r.nextInt(10)) + "\"");
            case CHARACTER -> randomChoice(
                    CHARS,
                    () -> "\'" + randomString(1) + "\'");
            case INTEGRAL -> randomChoice(
                    INTS,
                    () -> "(byte)" + String.valueOf(r.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE)),
                    () -> "(short)" + String.valueOf(r.nextInt(Short.MIN_VALUE, Short.MAX_VALUE)),
                    () -> String.valueOf(r.nextInt()),
                    () -> r.nextLong() + "l");
            case BIG_INT -> randomChoice(
                    BIGINTS,
                    () -> "new java.math.BigInteger(\"" + r.nextLong() + "\")");
            case FLOATING -> randomChoice(
                    FLOATS,
                    () -> String.valueOf(r.nextDouble()),
                    () -> r.nextFloat() + "f");
            case BIG_FLOAT -> randomChoice(
                    BIGFLOATS,
                    () -> "new java.math.BigDecimal(" + r.nextDouble() + ")");
            case DATE -> randomChoice(
                    DATES,
                    () -> "new java.util.Date(" + r.nextLong() + "l)",
                    () -> r.nextLong() + "l");
        };
    }

    String randomChoice(Supplier<String>... suppl) {
        return suppl[r.nextInt(suppl.length)].get();
    }

    String randomChoice(String... values) {
        return values[r.nextInt(values.length)];
    }

    String randomChoice(String[] values, Supplier<String>... suppl) {
        int i = r.nextInt(values.length + suppl.length);
        return i < values.length ? values[i] : suppl[i - values.length].get();
    }

    String randomString(int length) {
        var sb = new StringBuilder(length << 2);
        while (length-- > 0) {
            char ch = (char)r.nextInt(9, 128);
            var s = switch (ch) {
                case '\t' -> "\\t";
                case '\'' -> "\\\'";
                case '"' -> "\\\"";
                case '\r' -> "\\r";
                case '\\' -> "\\\\";
                case '\n' -> "\\n";
                case '\f' -> "\\f";
                case '\b' -> "\\b";
                default -> ch + "";
            };
            sb.append(s);
        }
        return sb.toString();
    }

    String randomFormat(Category category) {
        char c;
        return "%" + switch (category) {
            case GENERAL -> randomWidth("-") + randomPrecision() + randomChar("bBhHsS");
            case CHARACTER -> randomWidth("-") + randomChar("cC");
            case INTEGRAL -> switch (c = randomChar("doxX")) {
                case 'd' -> randomFlags("+ ,(");
                default -> randomFlags("");
            } + randomWidth("-0") + c;
            case BIG_INT -> switch (c = randomChar("doxX")) {
                case 'd' -> randomFlags("+ ,(");
                default -> randomFlags("+ (");
            } + randomWidth("-0") + c;
            case FLOATING -> switch (c = randomChar("eEfaAgG")) {
                case 'a', 'A' -> randomFlags("+ ") + randomWidth("-0");
                case 'e', 'E' -> randomFlags("+ (") + randomWidth("-0") + randomPrecision();
                default -> randomFlags("+ ,(") + randomWidth("-0") + randomPrecision();
            } + c;
            case BIG_FLOAT -> switch (c = randomChar("eEfgG")) {
                case 'e', 'E' -> randomFlags("+ (") + randomWidth("-0") + randomPrecision();
                default -> randomFlags("+ ,(") + randomWidth("-0") + randomPrecision();
            } + c;
            case DATE ->  randomWidth("-") + randomChar("tT") + randomChar("BbhAaCYyjmdeRTrDFc");
        };
    }

    String randomFlags(String flags) {
        var sb = new StringBuilder(flags.length());
        for (var f : flags.toCharArray()) {
            if (r.nextBoolean() && (f != ' ' || sb.length() == 0 || sb.charAt(sb.length() - 1) != '+')) sb.append(f);
        }
        return sb.toString();
    }

    char randomChar(String chars) {
        return chars.charAt(r.nextInt(chars.length()));
    }

    String randomWidth(String flags) {
        var f = r.nextInt(flags.length() + 1);
        return r.nextBoolean() ? (r.nextBoolean() ? flags.charAt(r.nextInt(flags.length())) : "") + String.valueOf(r.nextInt(10) + 1) : "";
    }

    String randomPrecision() {
        return r.nextBoolean() ? '.' + String.valueOf(r.nextInt(10) + 1) : "";
    }

    public Class<?> compile() throws Exception {
        var classes = new HashMap<String, byte[]>();
        var fileManager = new ForwardingJavaFileManager(ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
            @Override
            public ClassLoader getClassLoader(JavaFileManager.Location location) {
                return new ClassLoader() {
                    @Override
                    public Class<?> loadClass(String name) throws ClassNotFoundException {
                        try {
                            return super.loadClass(name);
                        } catch (ClassNotFoundException e) {
                            byte[] classData = classes.get(name);
                            return defineClass(name, classData, 0, classData.length);
                        }
                    }
                };
            }
            @Override
            public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String name, JavaFileObject.Kind kind, FileObject originatingSource) throws UnsupportedOperationException {
                return new SimpleJavaFileObject(URI.create(name + ".class"), JavaFileObject.Kind.CLASS) {
                    @Override
                    public OutputStream openOutputStream() {
                        return new FilterOutputStream(new ByteArrayOutputStream()) {
                            @Override
                            public void close() throws IOException {
                                classes.put(name, ((ByteArrayOutputStream)out).toByteArray());
                            }
                        };
                    }
                };
            }
        };
        var source = genSource();
//        System.out.println(source);
        if (ToolProvider.getSystemJavaCompiler().getTask(null, fileManager, null,
                List.of("--enable-preview", "-source", String.valueOf(Runtime.version().feature())), null,
                List.of(SimpleJavaFileObject.forSource(URI.create("StringTemplateTest$.java"), source))
           ).call()) {
            return fileManager.getClassLoader(CLASS_OUTPUT).loadClass("StringTemplateTest$");
        } else {
            throw new AssertionError("compilation failed");
        }
    }

    String genFragments(Category c) {
        var fragments = new LinkedList<String>();
        for (int i = 0; i < 1500; i++) {
            var format = randomFormat(c);
            var value = randomValue(c);
            var qValue = value.replace("\\", "\\\\").replace("\"", "\\\"");
            fragments.add(STR."test(FMT.\"\{format}\\{\{value}}\", \"\{format}\", \"\{qValue}\", \{value}, log);");
        }
        return String.join("\n        ", fragments);
    }

    String genSource() {
        return STR."""
            import java.util.FormatProcessor;
            import java.util.Locale;

            public class StringTemplateTest$ {
                static final FormatProcessor FMT = FormatProcessor.create(Locale.US);
                static String STR = "this is static String";
                static char C = 'c';
                static Character CHAR = 'C';
                static long L = -12345678910l;
                static Long LONG = 9876543210l;
                static int I = 42;
                static Integer INT = -49;
                static boolean BO = true;
                static Boolean BOOL = false;
                static short S = 13;
                static Short SHORT = -17;
                static byte BY = -3;
                static Byte BYTE = 12;
                static float F = 4.789f;
                static Float FLOAT = -0.000006f;
                static double D = 6545745.6734654563;
                static Double DOUBLE = -4323.7645676574;

                public static void run(java.util.List<String> log) {
                    runGeneral(log);
                    runCharacter(log);
                    runIntegral(log);
                    runBigInt(log);
                    runFloating(log);
                    runBigFloat(log);
                    runDate(log);
                }
                public static void runGeneral(java.util.List<String> log) {
                    \{genFragments(Category.GENERAL)}
                }
                public static void runCharacter(java.util.List<String> log) {
                    \{genFragments(Category.CHARACTER)}
                }
                public static void runIntegral(java.util.List<String> log) {
                    \{genFragments(Category.INTEGRAL)}
                }
                public static void runBigInt(java.util.List<String> log) {
                    \{genFragments(Category.BIG_INT)}
                }
                public static void runFloating(java.util.List<String> log) {
                    \{genFragments(Category.FLOATING)}
                }
                public static void runBigFloat(java.util.List<String> log) {
                    \{genFragments(Category.BIG_FLOAT)}
                }
                public static void runDate(java.util.List<String> log) {
                    \{genFragments(Category.DATE)}
                }
                static void test(String fmt, String format, String expression, Object value, java.util.List<String> log) {
                    var formatted = String.format(java.util.Locale.US, format, value);
                    if (!fmt.equals(formatted)) {
                        log.add("  format: '%s' expression: '%s' value: '%s' expected: '%s' found: '%s'".formatted(format, expression, value, formatted, fmt));
                    }
                }
            }
            """;
    }

    public static void main(String... args) throws Exception {
        var log = new LinkedList<String>();
        new StringTemplateTest().compile().getMethod("run", List.class).invoke(null, log);
        if (!log.isEmpty()) {
            log.forEach(System.out::println);
            throw new AssertionError(STR."failed \{log.size()} tests");
        }
    }
}
