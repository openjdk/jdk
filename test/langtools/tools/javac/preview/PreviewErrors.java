/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8226585
 * @summary Verify behavior w.r.t. preview feature API errors and warnings
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      java.base/jdk.internal
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @build combo.ComboTestHelper
 * @compile --enable-preview -source ${jdk.version} PreviewErrors.java
 * @run main/othervm --enable-preview PreviewErrors
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import combo.ComboInstance;
import combo.ComboParameter;
import combo.ComboTask;
import combo.ComboTestHelper;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;

import jdk.internal.PreviewFeature;

import toolbox.JavacTask;
import toolbox.ToolBox;

public class PreviewErrors extends ComboInstance<PreviewErrors> {

    protected ToolBox tb;

    PreviewErrors() {
        super();
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new ComboTestHelper<PreviewErrors>()
                .withDimension("ESSENTIAL", (x, essential) -> x.essential = essential, EssentialAPI.values())
                .withDimension("PREVIEW", (x, preview) -> x.preview = preview, Preview.values())
                .withDimension("LINT", (x, lint) -> x.lint = lint, Lint.values())
                .withDimension("SUPPRESS", (x, suppress) -> x.suppress = suppress, Suppress.values())
                .withDimension("FROM", (x, from) -> x.from = from, PreviewFrom.values())
                .run(PreviewErrors::new);
    }

    private EssentialAPI essential;
    private Preview preview;
    private Lint lint;
    private Suppress suppress;
    private PreviewFrom from;

    @Override
    public void doWork() throws IOException {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path srcJavaBase = src.resolve("java.base");
        Path classes = base.resolve("classes");
        Path classesJavaBase = classes.resolve("java.base");

        Files.createDirectories(classesJavaBase);

        String previewAPI = """
                            package preview.api;
                            public class Extra {
                                @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.${preview}
                                                             ${essential})
                                public static void test() { }
                                @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.${preview}
                                                             ${essential})
                                public static class Clazz {}
                            }
                            """.replace("${preview}", PreviewFeature.Feature.values()[0].name())
                               .replace("${essential}", essential.expand(null));

         if (from == PreviewFrom.CLASS) {
            tb.writeJavaFiles(srcJavaBase, previewAPI);

            new JavacTask(tb)
                    .outdir(classesJavaBase)
                    .options("--patch-module", "java.base=" + srcJavaBase.toString())
                    .files(tb.findJavaFiles(srcJavaBase))
                    .run()
                    .writeAll();
         }

        ComboTask task = newCompilationTask()
                .withSourceFromTemplate("""
                                        package test;
                                        public class Test {
                                            #{SUPPRESS}
                                            public void test() {
                                                preview.api.Extra.test();
                                                preview.api.Extra.Clazz c;
                                            }
                                        }
                                        """)
                .withOption("-XDrawDiagnostics")
                .withOption("-source")
                .withOption(String.valueOf(Runtime.version().feature()));

        if (from == PreviewFrom.CLASS) {
            task.withOption("--patch-module")
                .withOption("java.base=" + classesJavaBase.toString())
                .withOption("--add-exports")
                .withOption("java.base/preview.api=ALL-UNNAMED");
        } else {
            task.withSourceFromTemplate("Extra", previewAPI)
                .withOption("--add-exports")
                .withOption("java.base/jdk.internal=ALL-UNNAMED");
        }

        if (preview.expand(null)!= null) {
            task = task.withOption(preview.expand(null));
        }

        if (lint.expand(null) != null) {
            task = task.withOption(lint.expand(null));
        }

        task.generate(result -> {
                Set<String> actual = Arrays.stream(Diagnostic.Kind.values())
                                            .flatMap(kind -> result.diagnosticsForKind(kind).stream())
                                            .map(d -> d.getLineNumber() + ":" + d.getColumnNumber() + ":" + d.getCode())
                                            .collect(Collectors.toSet());
                Set<String> expected;
                boolean ok;
                if (essential == EssentialAPI.YES) {
                    if (preview == Preview.YES) {
                        if (suppress == Suppress.YES) {
                            expected = Set.of();
                        } else if (lint == Lint.ENABLE_PREVIEW) {
                            expected = Set.of("5:26:compiler.warn.is.preview", "6:26:compiler.warn.is.preview");
                        } else {
                            expected = Set.of("-1:-1:compiler.note.preview.filename",
                                              "-1:-1:compiler.note.preview.recompile");
                        }
                        ok = true;
                    } else {
                        expected = Set.of("5:26:compiler.err.is.preview", "6:26:compiler.err.is.preview");
                        ok = false;
                    }
                } else {
                    if (suppress == Suppress.YES) {
                        expected = Set.of();
                    } else if ((preview == Preview.YES && (lint == Lint.NONE || lint == Lint.DISABLE_PREVIEW)) ||
                               (preview == Preview.NO && lint == Lint.DISABLE_PREVIEW)) {
                        expected = Set.of("-1:-1:compiler.note.preview.filename",
                                          "-1:-1:compiler.note.preview.recompile");
                    } else {
                        expected = Set.of("5:26:compiler.warn.is.preview", "6:26:compiler.warn.is.preview");
                    }
                    ok = true;
                }
                if (ok) {
                    if (!result.get().iterator().hasNext()) {
                        throw new IllegalStateException("Did not succeed as expected." + actual);
                    }
                } else {
                    if (result.get().iterator().hasNext()) {
                        throw new IllegalStateException("Succeed unexpectedly.");
                    }
                }
                if (!expected.equals(actual)) {
                    throw new IllegalStateException("Unexpected output for " + essential + ", " + preview + ", " + lint + ", " + suppress + ", " + from + ": actual: \"" + actual + "\", expected: \"" + expected + "\"");
                }
            });
    }

    public enum EssentialAPI implements ComboParameter {
        YES(", essentialAPI=true"),
        NO(", essentialAPI=false");

        private final String code;

        private EssentialAPI(String code) {
            this.code = code;
        }

        public String expand(String optParameter) {
            return code;
        }
    }

    public enum Preview implements ComboParameter {
        YES("--enable-preview"),
        NO(null);

        private final String opt;

        private Preview(String opt) {
            this.opt = opt;
        }

        public String expand(String optParameter) {
            return opt;
        }
    }

    public enum Lint implements ComboParameter {
        NONE(null),
        ENABLE_PREVIEW("-Xlint:preview"),
        DISABLE_PREVIEW("-Xlint:-preview");

        private final String opt;

        private Lint(String opt) {
            this.opt = opt;
        }

        public String expand(String optParameter) {
            return opt;
        }
    }

    public enum Suppress implements ComboParameter {
        YES("@SuppressWarnings(\"preview\")"),
        NO("");

        private final String code;

        private Suppress(String code) {
            this.code = code;
        }

        public String expand(String optParameter) {
            return code;
        }
    }

    public enum PreviewFrom implements ComboParameter {
        CLASS,
        SOURCE;

        private PreviewFrom() {
        }

        public String expand(String optParameter) {
            throw new IllegalStateException();
        }
    }
}
