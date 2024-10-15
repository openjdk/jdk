/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/* @test
 * @bug 8329126 8329421
 * @summary check that native methods get compiled and printed
 *
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/native compiler.calls.NativeCalls
 */

package compiler.calls;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class NativeCalls {
    static Method emptyStaticNativeMethod;
    static Method callNativeMethod;
    static {
        init();
    }
    static void init() {
        System.loadLibrary("NativeCalls");
        try {
            emptyStaticNativeMethod = NativeCalls.class.getDeclaredMethod("emptyStaticNative");
            callNativeMethod = NativeCalls.class.getDeclaredMethod("callNative");
        } catch (NoSuchMethodException nsme) {
            throw new Error("TEST BUG: can't find test method", nsme);
        }
    }

    native static void emptyStaticNative();

    static void callNative() {
        emptyStaticNative();
    }

    static public void main(String[] args) throws Exception {

        ArrayList<String> baseOptions = new ArrayList<String>();
        baseOptions.add("-XX:+UnlockDiagnosticVMOptions");
        baseOptions.add("-XX:+WhiteBoxAPI");
        baseOptions.add("-Xbootclasspath/a:.");
        baseOptions.add("-Xbatch");
        baseOptions.add("-XX:-UseOnStackReplacement");
        baseOptions.add("-XX:+PrintCompilation");
        baseOptions.add(Executor.class.getName());
        String nativeMethodName = NativeCalls.class.getName() + "::" + emptyStaticNativeMethod.getName();
        List<Variant> variants = List.of(new Variant(List.of("-XX:+TieredCompilation"), "true", "false"),
                                         new Variant(List.of("-XX:-TieredCompilation"), "true", "false"),
                                         new Variant(List.of("-XX:+TieredCompilation",
                                                             "-XX:+PreferInterpreterNativeStubs"), "false", "false"),
                                         new Variant(List.of("-XX:-TieredCompilation",
                                                             "-XX:+PreferInterpreterNativeStubs"), "false", "false"),
                                         new Variant(List.of("-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1"), "true", "false"),
                                         new Variant(List.of("-XX:+TieredCompilation", "-XX:TieredStopAtLevel=2"), "true", "false"),
                                         new Variant(List.of("-XX:+TieredCompilation", "-XX:TieredStopAtLevel=3"), "true", "false"),
                                         new Variant(List.of("-XX:+TieredCompilation", "-XX:TieredStopAtLevel=4"), "true", "false"),
                                         new Variant(List.of("-XX:+TieredCompilation",
                                                             "-XX:CompileCommand=print," + nativeMethodName), "true", "true"),
                                         new Variant(List.of("-XX:-TieredCompilation",
                                                             "-XX:CompileCommand=print," + nativeMethodName), "true", "true"),
                                         new Variant(List.of("-XX:-TieredCompilation",
                                                             "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintAssembly"), "true", "true"),
                                         new Variant(List.of("-XX:-TieredCompilation",
                                                             "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintNativeNMethods"), "true", "true"),
                                         new Variant(List.of("-XX:+TieredCompilation",
                                                             "-XX:CompileCommand=exclude," + nativeMethodName), "false", "false"),
                                         new Variant(List.of("-XX:-TieredCompilation",
                                                             "-XX:CompileCommand=exclude," + nativeMethodName), "false", "false"));
        for (Variant v : variants) {
            ArrayList<String> command = new ArrayList<String>(v.options);
            command.addAll(baseOptions);
            command.add(v.compile);
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(command);
            OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
            analyzer.shouldHaveExitValue(0);
            System.out.println(analyzer.getOutput());
            if (Boolean.valueOf(v.print).booleanValue() &&
                analyzer.asLines().stream().
                filter(Pattern.compile("Compiled method.+" + nativeMethodName + ".*").asPredicate()).
                findAny().isEmpty()) {
                throw new Error(nativeMethodName + " not printed");
            }
        }
    }

    public static class Variant {
        Collection<String> options;
        String compile;
        String print;
        public Variant(Collection<String> options, String compile, String print) {
            this.options = options;
            this. compile = compile;
            this. print = print;
        }
    }

    public static class Executor {

        static WhiteBox wb = WhiteBox.getWhiteBox();

        static public void main(String[] args) {

            if (args.length != 1) {
                throw new Error("Expected two arguments");
            }
            boolean compile = Boolean.valueOf(args[0]);
            for (int i = 0; i < 20_000; i++) {
                callNative();
            }
            if (wb.getMethodCompilationLevel(callNativeMethod) > 0) {
                if (compile && !wb.isMethodCompiled(emptyStaticNativeMethod)) {
                    throw new Error("TEST BUG: '" + emptyStaticNativeMethod + "' should be compiled");
                }
                if (!compile && wb.isMethodCompiled(emptyStaticNativeMethod)) {
                    throw new Error("TEST BUG: '" + emptyStaticNativeMethod + "' should not be compiled");
                }
            }
        }
    }
}
