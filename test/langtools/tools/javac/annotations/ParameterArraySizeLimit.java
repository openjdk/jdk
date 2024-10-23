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
 * @summary Test ParameterArraySizeLimit
 * @library /tools/lib
 * @run main ParameterArraySizeLimit
 */


import java.io.BufferedWriter;
import java.io.FileWriter;
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


public class ParameterArraySizeLimit {

    public static void main(String[] args) throws IOException {

        int[] values = new int[]{65536, 65537, 512000};

        for (var value : values) {

            Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
            String className = MessageFormat.format("ClassAnnotationWithLength_{0,number,#}.java", value);
            Path out = tmpDir.resolve(className);

            createJavaFile(value, out);
            checkParamArrayWarning(className, out);
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
        task.analyze();

        List<Diagnostic<? extends JavaFileObject>> diagnosticList = d.getDiagnostics();

        if (diagnosticList.isEmpty()) {
            throw new RuntimeException("No diagnostic found");
        }

        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticList) {
            if (!(diagnostic.getKind() == Diagnostic.Kind.WARNING
                    && diagnostic.getMessage(null)
                    .equals("Annotation array element too large, length exceeds limit of 65535"))) {
                throw new RuntimeException("Unexpected diagnostic: " + diagnostic.getMessage(null));
            }
        }
    }

    private static void createJavaFile(int value, Path out) throws IOException {
        String customAnno = """
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @interface CustomAnno {
                    String value() default "default value";
                    long[] arr() ;
                    int count() default 0;
                }
                """;
        String annotation = MessageFormat.format("""
                            public class ClassAnnotationWithLength_{0,number,#} '{'
                            @CustomAnno(value = "custom", count = 42, arr='{'
                """, value);

        String end = """
                     })

                    static int x = 3;

                    public void myAnnotatedMethod() { }
                }
                """;

        BufferedWriter bufferedWriter = Files.newBufferedWriter(out);
        bufferedWriter.write(customAnno);
        bufferedWriter.write(annotation);

        for (int i = 0; i < value; i++) {
            bufferedWriter.write("-1,");
        }

        bufferedWriter.write("-1");
        bufferedWriter.write(end);
        bufferedWriter.close();
    }
}
