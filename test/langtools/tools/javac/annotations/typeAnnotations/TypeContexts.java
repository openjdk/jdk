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

/*
 * @test
 * @bug 8392772
 * @summary Verify behavior w.r.t. type contexts
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit TypeContexts
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.OutputKind;
import toolbox.ToolBox;

public class TypeContexts {
    private ToolBox tb = new ToolBox();
    private Path base;

    @Test
    public void testMethodSelector() throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        List<String> log;
        List<String> expected;

        tb.writeJavaFiles(src,
                """
                package p;

                import java.lang.annotation.Target;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                public class Test {
                    @Target(ElementType.TYPE_USE)
                    @interface Ann1 {}
                    @Target(ElementType.TYPE_USE)
                    @interface Ann2 {}
                    @interface Ann3 {}
                    private void test() {
                        java.lang.@Ann1 System.out.println("");
                        java.lang.@Ann1 @Ann2 System.out.println("");
                        java.lang.System.@Ann3 out.println("");
                    }
                }
                """);

        log = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);
        expected = List.of(
            "Test.java:15:19: compiler.err.type.annotation.inadmissible.not.type.context: (compiler.misc.type.annotation.1: @p.Test.Ann1)",
            "Test.java:16:19: compiler.err.type.annotation.inadmissible.not.type.context: (compiler.misc.type.annotation: @p.Test.Ann1,@p.Test.Ann2)",
            "Test.java:17:26: compiler.err.annotation.type.not.applicable.to.type: p.Test.Ann3",
            "3 errors"
        );

        Assertions.assertEquals(expected, log);

        tb.writeJavaFiles(src,
                """
                package p;

                import java.lang.annotation.Target;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                public class Test {
                    @Target(ElementType.TYPE_USE)
                    @interface Ann1 {}
                    @Target(ElementType.TYPE_USE)
                    @interface Ann2 {}
                    @interface Ann3 {}
                    private void test() {
                        java.lang.System.@Ann1 out.println("");
                        java.lang.System.@Ann1 @Ann2 out.println("");
                        java.lang.System.@Ann3 out.println("");
                    }
                }
                """);

        log = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);
        expected = List.of(
            "Test.java:15:26: compiler.err.type.annotation.inadmissible.not.type.context: (compiler.misc.type.annotation.1: @p.Test.Ann1)",
            "Test.java:16:26: compiler.err.type.annotation.inadmissible.not.type.context: (compiler.misc.type.annotation: @p.Test.Ann1,@p.Test.Ann2)",
            "Test.java:17:26: compiler.err.annotation.type.not.applicable.to.type: p.Test.Ann3",
            "3 errors"
        );

        Assertions.assertEquals(expected, log);
    }

    @Test
    public void testMethodReference() throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        List<String> log;
        List<String> expected;

        tb.writeJavaFiles(src,
                """
                package p;

                import java.lang.annotation.Target;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import static java.lang.System.out;

                public class Test {
                    @Target(ElementType.TYPE_USE)
                    @interface Ann1 {}
                    @Target(ElementType.TYPE_USE)
                    @interface Ann2 {}
                    @interface Ann3 {}
                    private void test() {
                        Runnable r = @Ann1 out::println;
                    }
                }
                """);

        log = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);
        expected = List.of(
            "Test.java:16:22: compiler.err.type.annotation.inadmissible.not.type.context: (compiler.misc.type.annotation.1: @p.Test.Ann1)",
            "1 error"
        );

        Assertions.assertEquals(expected, log);
    }

    @Test
    public void testAllTypeContexts() throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(src,
                """
                package p;

                import java.lang.annotation.Target;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.util.List;
                import java.util.function.Consumer;
                import java.util.function.IntFunction;
                import java.util.function.Supplier;

                public class Test {
                    @Target(ElementType.TYPE_USE)
                    @interface Ann1 {}
                    //as per JLS 4.11
                    //1:
                    public class AnnotatedSuperClass extends java.lang.@Ann1 Object
                                                     implements java.io.@Ann1 Serializable {}
                    //2:
                    public interface AnnotatedSuperInterface extends java.io.@Ann1 Serializable {}
                    //3:
                    public java.lang.@Ann1 Object get() { return null; }
                    public @interface A {
                        public java.lang.@Ann1 String get();
                    }
                    //4:
                    public void doThrow() throws java.lang.@Ann1 Exception {}
                    public class DoThrow {
                        public DoThrow() throws java.lang.@Ann1 Exception {}
                    }
                    //5:
                    public class GenericClass<T extends java.lang.@Ann1 Object> {}
                    public class GenericInterface<T extends java.lang.@Ann1 Object> {}
                    public <T extends java.lang.@Ann1 Object> void genericMethod() {}
                    public class GenericConstructor {
                        public <T extends java.lang.@Ann1 Object> GenericConstructor() {}
                    }
                    //6:
                    public class FieldClass {
                        public java.lang.@Ann1 Object field;
                    }
                    public interface FieldInterface {
                        public java.lang.@Ann1 String field = "";
                    }
                    public enum FieldEnum {
                        ;
                        public java.lang.@Ann1 Object field;
                    }
                    //7:
                    public void parameter(java.lang.@Ann1 Object p) {}
                    public class Parameter {
                        public Parameter(java.lang.@Ann1 Object p) {}
                    }
                    public void lambda() {
                        Consumer<String> c = (java.lang.@Ann1 String s) -> {};
                    }
                    //8:
                    public void receiver(p.@Ann1 Test this) {}
                    //9:
                    private void localVar() throws Exception {
                        java.lang.@Ann1 Object v = null;
                        for (java.lang.@Ann1 Object _ = null; v != null; ) {}
                        for (java.lang.@Ann1 Object _ : List.of()) {}
                        try (java.lang.AutoCloseable _ = null) {}
                        var _ = v instanceof java.lang.@Ann1 String str;
                    }
                    //10:
                    private void exception() {
                        try (java.lang.AutoCloseable _ = null) {
                        } catch (java.lang.@Ann1 Exception ex) {}
                    }
                    //11:
                    public record R(java.lang.@Ann1 Object component) {}
                    //12:
                    class ConstructorInvocation {
                        <T> ConstructorInvocation(T t) {}
                        ConstructorInvocation() {
                            <java.lang.@Ann1 Object>this(null);
                        }
                    }
                    class ClassInstanceCreation {
                        <T> ClassInstanceCreation() {}
                        Object o = new <java.lang.@Ann1 Object>ClassInstanceCreation();
                    }
                    <T> void testMethodInvocation() {
                        this.<java.lang.@Ann1 Object>testMethodInvocation();
                        Runnable r = this::<java.lang.@Ann1 Object>testMethodInvocation;
                    }
                    //13:
                    class ClassInstanceCreationTypeClass<T> {
                        ClassInstanceCreationTypeClass() {}
                        Object o1 = new ClassInstanceCreationTypeClass<java.lang.@Ann1 Object>();
                        Object o2 = new ClassInstanceCreationTypeClass<java.lang.@Ann1 Object>() {};
                    }
                    interface ClassInstanceCreationTypeInterface<T> {
                        Object o = new ClassInstanceCreationTypeInterface<java.lang.@Ann1 Object>() {};
                    }
                    //14:
                    void testNewArray() {
                        Object _ = new java.lang.String @Ann1 [0];
                        Object _ = new java.lang.String @Ann1 [0] @Ann1 [];
                    }
                    //15:
                    void testCast() {
                        Object _ = (java.lang.@Ann1 String) null;
                    }
                    //16:
                    void testInstanceof(Object in) {
                        Object _ = in instanceof java.lang.@Ann1 String;
                    }
                    //17:
                    class MethodReference {
                        Supplier<String> s1 = Test.@Ann1 MethodReference::getString;
                        Supplier<MethodReference> s2 = Test.@Ann1 MethodReference::new;
                        IntFunction<MethodReference[]> s3 = Test.@Ann1 MethodReference @Ann1[]::new;
                        public static String getString() { return null;}
                    }
                }
                """);

        new JavacTask(tb)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .outdir(classes)
            .run(Task.Expect.SUCCESS)
            .writeAll();
    }

    @BeforeEach
    void setBase(TestInfo testInfo) {
        base = Path.of(testInfo.getTestMethod().orElseThrow().getName());
    }

}
