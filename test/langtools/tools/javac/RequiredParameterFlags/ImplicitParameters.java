/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8292275
 * @summary check that implicit parameter flags are available by default
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.code
 *          java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 * @run main ImplicitParameters
 */

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.MethodParameterInfo;
import jdk.internal.classfile.attribute.MethodParametersAttribute;
import com.sun.tools.javac.code.Flags;
import toolbox.Assert;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ImplicitParameters extends TestRunner {
    private static final int CHECKED_FLAGS = Classfile.ACC_MANDATED | Classfile.ACC_SYNTHETIC;
    private static final int NO_FLAGS = 0;

    public ImplicitParameters() {
        super(System.err);
    }

    public static void main(String[] args) throws Exception {
        new ImplicitParameters().runTests();
    }

    @Override
    protected void runTests() throws Exception {
        Path base = Path.of(".").toAbsolutePath();
        compileClasses(base);
        runTests(method -> new Object[]{ readClassFile(base.resolve("classes"), method) });
    }

    private void compileClasses(Path base) throws IOException {
        // Keep this in sync with test/jdk/java/lang/reflect/AccessFlag/RequiredMethodParameterFlagTest.java
        String outer = """
                class Outer {
                    class Inner {
                        public Inner(Inner notMandated) {}
                    }

                    Inner anonymousInner = this.new Inner(null) {};

                    Object anonymous = new Object() {};

                    private void instanceMethod(int i) {
                        class Task implements Runnable {
                            final int j;

                            Task(int j) {
                                this.j = j;
                            }

                            @Override
                            public void run() {
                                System.out.println(Outer.this.toString() + (i * j));
                            }
                        }

                        new Task(5).run();
                    }

                    enum MyEnum {
                        ;
                        MyEnum(String s, int i) {}
                    }

                    record MyRecord(int a, Object b) {
                        MyRecord {}
                    }
                }
                """;
        Path src = base.resolve("src");
        ToolBox tb = new ToolBox();
        tb.writeJavaFiles(src, outer);
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.SUCCESS)
                .writeAll();
    }

    private ClassModel readClassFile(Path classes, Method method) {
        String className = method.getAnnotation(ClassName.class).value();
        try {
            return Classfile.of().parse(classes.resolve("Outer$" + className + ".class"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface ClassName {
        String value();
    }

    @Test
    @ClassName("Inner")
    public void testInnerClassConstructor(ClassModel classFile) {
        checkParameters(classFile.methods().get(0), Classfile.ACC_MANDATED, 0);
    }

    @Test
    @ClassName("1Task")
    public void testLocalClassConstructor(ClassModel classFile) {
        for (MethodModel method : classFile.methods()) {
            if (method.methodName().equalsString(ConstantDescs.INIT_NAME)) {
                checkParameters(method, Classfile.ACC_MANDATED, NO_FLAGS, Classfile.ACC_SYNTHETIC);
                break;
            }
        }
    }

    @Test
    @ClassName("1")
    public void testAnonymousClassExtendingInnerClassConstructor(ClassModel classFile) {
        checkParameters(classFile.methods().get(0), Classfile.ACC_MANDATED, NO_FLAGS, NO_FLAGS);
    }

    @Test
    @ClassName("2")
    public void testAnonymousClassConstructor(ClassModel classFile) {
        checkParameters(classFile.methods().get(0), Classfile.ACC_MANDATED);
    }

    @Test
    @ClassName("MyEnum")
    public void testValueOfInEnum(ClassModel classFile) {
        for (MethodModel method : classFile.methods()) {
            if (method.methodName().equalsString("valueOf")) {
                checkParameters(method, Classfile.ACC_MANDATED);
                break;
            }
        }
    }

    @Test
    @ClassName("MyEnum")
    public void testEnumClassConstructor(ClassModel classFile) {
        for (MethodModel method : classFile.methods()) {
            if (method.methodName().equalsString(ConstantDescs.INIT_NAME)) {
                checkParameters(method, Classfile.ACC_SYNTHETIC, Classfile.ACC_SYNTHETIC, NO_FLAGS, NO_FLAGS);
                break;
            }
        }
    }

    @Test
    @ClassName("MyRecord")
    public void testCompactConstructor(ClassModel classFile) {
        checkParameters(classFile.methods().get(0), Classfile.ACC_MANDATED, Classfile.ACC_MANDATED);
    }

    private void checkParameters(MethodModel method, int... parametersFlags) {
        MethodParametersAttribute methodParameters = method.findAttribute(Attributes.METHOD_PARAMETERS).orElseThrow();
        Assert.checkNonNull(methodParameters, "MethodParameters attribute must be present");
        List<MethodParameterInfo> table = methodParameters.parameters();
        Assert.check(table.size() == parametersFlags.length, () -> "Expected " + parametersFlags.length
                + " MethodParameters entries, found " + table.size());
        for (int i = 0; i < methodParameters.parameters().size(); i++) {
            int foundFlags = table.get(i).flagsMask() & CHECKED_FLAGS;
            int desiredFlags = parametersFlags[i] & CHECKED_FLAGS;
            Assert.check(foundFlags == desiredFlags, () -> "Expected mandated and synthethic flags to be "
                    + convertFlags(desiredFlags) + ", found " + convertFlags(foundFlags));
        }
    }

    private static String convertFlags(int flags) {
        return ((flags & Classfile.ACC_MANDATED) == Classfile.ACC_MANDATED) + " and " + ((flags & Classfile.ACC_SYNTHETIC) == Classfile.ACC_SYNTHETIC);
    }
}
