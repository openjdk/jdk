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
 * @bug 8024694 8334870
 * @summary Check javac can handle various Runtime(In)VisibleParameterAnnotations attribute combinations
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main ParameterAnnotations
*/

import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class ParameterAnnotations extends TestRunner {

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new ParameterAnnotations().runTests();
    }

    ParameterAnnotations() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testEnum(Path base) throws Exception {
        //not parameterized:
        doTest(base,
               """
               import java.lang.annotation.*;
               public enum E {
                   A(0);
                   E(@Visible @Invisible long i) {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "E",
                MethodTransform.ACCEPT_ALL,
               "@Invisible @Visible long");
        //parameterized:
        doTest(base,
               """
               import java.lang.annotation.*;
               public enum E {
                   A(0);
                   <T> E(@Visible @Invisible long i) {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "E",
                MethodTransform.ACCEPT_ALL,
               "@Invisible @Visible long");
        //not parameterized, and no Signature attribute:
        doTest(base,
               """
               import java.lang.annotation.*;
               public enum E {
                   A(0);
                   E(@Visible @Invisible long i) {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "E",
               NO_SIGNATURE,
               "java.lang.String, int, @Invisible @Visible long");
        //not parameterized, and no Signature and MethodParameters attribute:
        doTest(base,
               """
               import java.lang.annotation.*;
               public enum E {
                   A(0);
                   E(@Visible @Invisible long i) {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "E",
               NO_SIGNATURE_NO_METHOD_PARAMETERS,
               "java.lang.String, int, @Invisible @Visible long");
    }

    @Test
    public void testInnerClass(Path base) throws Exception {
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public class I {
                       public I(@Visible @Invisible long l) {}
                       public String toString() {
                           return T.this.toString(); //force outer this capture
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$I",
                MethodTransform.ACCEPT_ALL,
               "@Invisible @Visible long");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public class I {
                       public <T> I(@Visible @Invisible long l) {}
                       public String toString() {
                           return T.this.toString(); //force outer this capture
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$I",
                MethodTransform.ACCEPT_ALL,
               "@Invisible @Visible long");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public class I {
                       public I(@Visible @Invisible long l) {}
                       public String toString() {
                           return T.this.toString(); //force outer this capture
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$I",
               NO_SIGNATURE,
               "@Invisible @Visible long");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public class I {
                       public I(@Visible @Invisible long l) {}
                       public String toString() {
                           return T.this.toString(); //force outer this capture
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$I",
               NO_SIGNATURE_NO_METHOD_PARAMETERS,
               "@Invisible @Visible long");
    }

    @Test
    public void testCapturingLocal(Path base) throws Exception {
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public void test(int i) {
                       class I {
                           public I(@Visible @Invisible long l) {}
                           public String toString() {
                               return T.this.toString() + i; //force outer this capture
                           }
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$1I",
                MethodTransform.ACCEPT_ALL,
               "@Invisible @Visible long");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public void test(int i) {
                       class I {
                           public <T> I(@Visible @Invisible long l) {}
                           public String toString() {
                               return T.this.toString() + i; //force outer this capture
                           }
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$1I",
                MethodTransform.ACCEPT_ALL,
               "@Invisible @Visible long");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public void test(int i) {
                       class I {
                           public I(@Visible @Invisible long l) {}
                           public String toString() {
                               return T.this.toString() + i; //force outer this capture
                           }
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$1I",
                NO_SIGNATURE,
               "T, @Invisible @Visible long, int");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public void test(int i) {
                       class I {
                           public I(@Visible @Invisible long l) {}
                           public String toString() {
                               return T.this.toString() + i; //force outer this capture
                           }
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$1I",
                NO_SIGNATURE_NO_METHOD_PARAMETERS,
               "T, @Invisible @Visible long, int");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   {
                       int i = 0;
                       class I {
                           public I(@Visible @Invisible long l) {}
                           public String toString() {
                               return T.this.toString() + i; //force outer this capture
                           }
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$1I",
                MethodTransform.ACCEPT_ALL,
               "@Invisible @Visible long");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   {
                       int i = 0;
                       class I {
                           public <T> I(@Visible @Invisible long l) {}
                           public String toString() {
                               return T.this.toString() + i; //force outer this capture
                           }
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$1I",
                MethodTransform.ACCEPT_ALL,
               "@Invisible @Visible long");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   {
                       int i = 0;
                       class I {
                           public I(@Visible @Invisible long l) {}
                           public String toString() {
                               return T.this.toString() + i; //force outer this capture
                           }
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$1I",
                NO_SIGNATURE,
               "T, @Invisible @Visible long, int");
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   {
                       int i = 0;
                       class I {
                           public I(@Visible @Invisible long l) {}
                           public String toString() {
                               return T.this.toString() + i; //force outer this capture
                           }
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$1I",
                NO_SIGNATURE_NO_METHOD_PARAMETERS,
               "T, @Invisible @Visible long, int");
    }

    @Test
    public void testSyntheticTests(Path base) throws Exception {
        //Signature attribute will defined one parameter, but the
        //Runtime(In)VisibleParameterAnnotations will define 3 parameters:
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public void test(int i) {
                       class I {
                           public I(@Visible @Invisible long l) {}
                           public String toString() {
                               return T.this.toString() + i; //force outer this capture
                           }
                       }
                   }
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T$1I",
                new MethodTransform() {
                    @Override
                    public void accept(MethodBuilder builder, MethodElement element) {
                        if (element instanceof RuntimeInvisibleParameterAnnotationsAttribute annos) {
                            assert annos.parameterAnnotations().size() == 1;
                            builder.accept(RuntimeInvisibleParameterAnnotationsAttribute.of(List.of(List.of(), annos.parameterAnnotations().get(0), List.of())));
                        } else if (element instanceof RuntimeVisibleParameterAnnotationsAttribute annos) {
                            assert annos.parameterAnnotations().size() == 1;
                            builder.accept(RuntimeVisibleParameterAnnotationsAttribute.of(List.of(List.of(), annos.parameterAnnotations().get(0), List.of())));
                        } else {
                            builder.accept(element);
                        }
                    }
                },
               "@Invisible @Visible long");
        //no Signature attribute, no synthetic parameters,
        //but less entries in Runtime(In)VisibleParameterAnnotations than parameters
        //no way to map anything:
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public T(int i, @Visible @Invisible long l, String s) {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T",
                new MethodTransform() {
                    @Override
                    public void accept(MethodBuilder builder, MethodElement element) {
                        if (element instanceof RuntimeInvisibleParameterAnnotationsAttribute annos) {
                            assert annos.parameterAnnotations().size() == 3;
                            builder.accept(RuntimeInvisibleParameterAnnotationsAttribute.of(List.of(annos.parameterAnnotations().get(1))));
                        } else if (element instanceof RuntimeVisibleParameterAnnotationsAttribute annos) {
                            assert annos.parameterAnnotations().size() == 3;
                            builder.accept(RuntimeVisibleParameterAnnotationsAttribute.of(List.of(annos.parameterAnnotations().get(1))));
                        } else {
                            builder.accept(element);
                        }
                    }
                },
               "int, long, java.lang.String",
               "- compiler.warn.runtime.invisible.parameter.annotations: T.class",
               "1 warning");
        //no Signature attribute, no synthetic parameters,
        //but more entries in Runtime(In)VisibleParameterAnnotations than parameters
        //no way to map anything:
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public T(@Visible @Invisible long l) {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T",
                new MethodTransform() {
                    @Override
                    public void accept(MethodBuilder builder, MethodElement element) {
                        if (element instanceof RuntimeInvisibleParameterAnnotationsAttribute annos) {
                            assert annos.parameterAnnotations().size() == 1;
                            builder.accept(RuntimeInvisibleParameterAnnotationsAttribute.of(List.of(List.of(), annos.parameterAnnotations().get(0), List.of())));
                        } else if (element instanceof RuntimeVisibleParameterAnnotationsAttribute annos) {
                            assert annos.parameterAnnotations().size() == 1;
                            builder.accept(RuntimeVisibleParameterAnnotationsAttribute.of(List.of(List.of(), annos.parameterAnnotations().get(0), List.of())));
                        } else {
                            builder.accept(element);
                        }
                    }
                },
               "long",
               "- compiler.warn.runtime.invisible.parameter.annotations: T.class",
               "1 warning");
        //mismatched lengths on RuntimeVisibleParameterAnnotations and
        //RuntimeInvisibleParameterAnnotations:
        doTest(base,
               """
               import java.lang.annotation.*;
               public class T {
                   public T(@Visible @Invisible long l) {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "T",
                new MethodTransform() {
                    @Override
                    public void accept(MethodBuilder builder, MethodElement element) {
                        if (element instanceof RuntimeInvisibleParameterAnnotationsAttribute annos) {
                            assert annos.parameterAnnotations().size() == 1;
                            builder.accept(annos); //keep intact
                        } else if (element instanceof RuntimeVisibleParameterAnnotationsAttribute annos) {
                            assert annos.parameterAnnotations().size() == 1;
                            builder.accept(RuntimeVisibleParameterAnnotationsAttribute.of(List.of(List.of(), annos.parameterAnnotations().get(0), List.of())));
                        } else {
                            builder.accept(element);
                        }
                    }
                },
               "long",
               "- compiler.warn.runtime.visible.invisible.param.annotations.mismatch: T.class",
               "1 warning");
    }

    @Test
    public void testRecord(Path base) throws Exception {
        //implicit constructor:
        doTest(base,
               """
               import java.lang.annotation.*;
               public record R(int i, @Visible @Invisible long l, String s) {
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "R",
                MethodTransform.ACCEPT_ALL,
               "int, @Invisible @Visible long, java.lang.String");
        doTest(base,
               """
               import java.lang.annotation.*;
               public record R(int i, @Visible @Invisible long l, String s) {
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "R",
                NO_SIGNATURE,
               "int, @Invisible @Visible long, java.lang.String");
        doTest(base,
               """
               import java.lang.annotation.*;
               public record R(int i, @Visible @Invisible long l, String s) {
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "R",
                NO_SIGNATURE_NO_METHOD_PARAMETERS,
               "int, @Invisible @Visible long, java.lang.String");
        //compact constructor:
        doTest(base,
               """
               import java.lang.annotation.*;
               public record R(int i, @Visible @Invisible long l, String s) {
                   public R {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "R",
                MethodTransform.ACCEPT_ALL,
               "int, @Invisible @Visible long, java.lang.String");
        doTest(base,
               """
               import java.lang.annotation.*;
               public record R(int i, @Visible @Invisible long l, String s) {
                   public R {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "R",
                NO_SIGNATURE,
               "int, @Invisible @Visible long, java.lang.String");
        doTest(base,
               """
               import java.lang.annotation.*;
               public record R(int i, @Visible @Invisible long l, String s) {
                   public R {}
               }
               @Retention(RetentionPolicy.RUNTIME)
               @interface Visible {}
               @interface Invisible {}
               """,
               "R",
                NO_SIGNATURE_NO_METHOD_PARAMETERS,
               "int, @Invisible @Visible long, java.lang.String");
    }

    private MethodTransform NO_SIGNATURE =
            MethodTransform.dropping(element -> element instanceof SignatureAttribute);

    private MethodTransform NO_SIGNATURE_NO_METHOD_PARAMETERS =
            MethodTransform.dropping(element -> element instanceof SignatureAttribute ||
                                     element instanceof MethodParametersAttribute);

    private void doTest(Path base, String code, String binaryNameToCheck,
                        MethodTransform changeConstructor, String expectedOutput,
                        String... expectedDiagnostics) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src, code);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.SUCCESS)
            .writeAll();

        Path classfile = classes.resolve(binaryNameToCheck + ".class");
        ClassFile cf = ClassFile.of();

        ClassModel model = cf.parse(classfile);

        byte[] newClassFile = cf.transformClass(model,
                                                ClassTransform.transformingMethods(m -> m.methodName()
                                                                                         .equalsString("<init>"),
                                                                                  changeConstructor));

        try (OutputStream out = Files.newOutputStream(classfile)) {
            out.write(newClassFile);
        }

        Task.Result result = new JavacTask(tb)
                .processors(new TestAP())
                .options("-classpath", classes.toString(),
                        "-XDrawDiagnostics",
                        "-Xlint:classfile")
                .outdir(classes)
                .classes(binaryNameToCheck)
                .run(Task.Expect.SUCCESS)
                .writeAll();
        List<String> out = result.getOutputLines(Task.OutputKind.STDOUT);
        if (!out.equals(List.of(expectedOutput))) {
            throw new AssertionError("Expected: " + List.of(expectedOutput) + ", but got: " + out);
        }
        List<String> diagnostics =
                new ArrayList<>(result.getOutputLines(Task.OutputKind.DIRECT));
        diagnostics.remove("");
        if (!diagnostics.equals(List.of(expectedDiagnostics))) {
            throw new AssertionError("Expected: " + List.of(expectedDiagnostics) + ", but got: " + diagnostics);
        }
    }

    @SupportedAnnotationTypes("*")
    public static final class TestAP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (TypeElement clazz : ElementFilter.typesIn(roundEnv.getRootElements())) {
                for (ExecutableElement el : ElementFilter.constructorsIn(clazz.getEnclosedElements())) {
                    String sep = "";

                    for (VariableElement p : el.getParameters()) {
                        System.out.print(sep);
                        if (!p.getAnnotationMirrors().isEmpty()) {
                            System.out.print(p.getAnnotationMirrors()
                                              .stream()
                                              .map(m -> m.toString())
                                              .collect(Collectors.joining(" ")));
                            System.out.print(" ");
                        }
                        System.out.print(p.asType());
                        sep = ", ";
                    }

                    System.out.println();
                }
            }

            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }
}
