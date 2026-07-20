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

/*
 * @test
 * @summary Check if error is thrown if annotation array exceeds limit
 * @library /tools/lib
 * @run main ParameterArrayLimit
 */

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import javax.tools.*;

import com.sun.source.util.JavacTask;

public class ParameterArrayLimit {

    public static void main(String[] args) throws IOException {

        int[] values = new int[]{65536, 65537, 512000};
        String[] retPolicies = {"RUNTIME", "CLASS"};

        for (var value : values) {
            Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));

            for (String retPolicy : retPolicies) {
                String className = MessageFormat.format("ClassAnnotationWithLength_{0,number,#}_{1}.java",
                        value,
                        retPolicy);
                Path out = tmpDir.resolve(className);
                createAnnotationFile(out, value, retPolicy, false);
                checkParamArrayWarning(className, out);
            }

            for (String retPolicy : retPolicies) {
                String className = MessageFormat.format("TypeAnnotationWithLength_{0,number,#}_{1}.java",
                        value,
                        retPolicy);
                Path out = tmpDir.resolve(className);
                createAnnotationFile(out, value, retPolicy, true);
                checkParamArrayWarning(className, out);
            }
        }
    }

    private static void checkParamArrayWarning(String className, Path out) throws IOException {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> d = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) javaCompiler.getTask(
                null,
                null,
                d,
                null,
                null,
                Collections.singletonList(
                        SimpleJavaFileObject.forSource(
                                URI.create("myfo:/" + className),
                                Files.readString(out)
                        )));
        task.call();

        List<Diagnostic<? extends JavaFileObject>> diagnosticList = d.getDiagnostics();
        if (diagnosticList.isEmpty()) {
            throw new RuntimeException("No diagnostic found");
        }

        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticList) {
            if (!(diagnostic.getKind() == Diagnostic.Kind.ERROR
                    && diagnostic.getCode()
                    .equals("compiler.err.annotation.array.too.large"))) {
                throw new RuntimeException("Unexpected diagnostic: " + diagnostic.getMessage(null));
            }
        }
    }

    private static void createAnnotationFile(Path out, int value, String retPolicy, boolean isTypeAnnotation) throws IOException {
        StringBuilder sb = new StringBuilder();

        if (isTypeAnnotation) {
            sb.append(MessageFormat.format("""
                    import java.lang.annotation.*;
                    @Retention(RetentionPolicy.{0})
                    @Target(ElementType.TYPE_USE)
                    @interface TypeAnno '{'
                        long[] arr();
                    '}'
                    """, retPolicy));
            sb.append(MessageFormat.format("""
                                public class TypeAnnotationWithLength_{0,number,#}_{1}'{'
                                @TypeAnno(arr = '{'
                    """, value, retPolicy));
        } else {
            sb.append(MessageFormat.format("""
                    import java.lang.annotation.*;
                    @Retention(RetentionPolicy.{0})
                    @interface MyCustomAnno '{'
                        String value() default "default value";
                        long[] arr();
                        int count() default 0;
                    '}'
                    """, retPolicy));
            sb.append(MessageFormat.format("""
                                public class ClassAnnotationWithLength_{0,number,#}_{1}'{'
                                @MyCustomAnno(value = "custom", count = 42, arr = '{'
                    """, value, retPolicy));
        }

        sb.append("-1,".repeat(Math.max(0, value - 1)));
        sb.append("-1})");

        sb.append("""
                     static int x = 3;

                     public void myAnnotatedMethod() { }
                }
                """);

        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(out)) {
            bufferedWriter.write(sb.toString());
        }
    }
}
