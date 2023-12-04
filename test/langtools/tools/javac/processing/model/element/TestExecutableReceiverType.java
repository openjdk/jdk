/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8222369 8225488
 * @summary Test behavior of ExecutableElement.getReceiverType
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestExecutableReceiverType
 * @compile -processor TestExecutableReceiverType -proc:only TestExecutableReceiverType.java
 * @compile/process -processor TestExecutableReceiverType -proc:only MethodHost
 */

import java.util.Set;
import java.lang.annotation.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

/**
 * Verify that proper type objects are returned from ExecutableElement.getReceiverType
 */
public class TestExecutableReceiverType extends JavacTestingAbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            int count = 0;
            for (ExecutableElement e : ElementFilter.methodsIn(
                  roundEnv.getElementsAnnotatedWith(ReceiverTypeKind.class))) {
                count += testExecutable(e);
            }
            for (ExecutableElement e : ElementFilter.constructorsIn(
                  roundEnv.getElementsAnnotatedWith(ReceiverTypeKind.class))) {
                count += testExecutable(e);
            }

            if (count == 0) {
                messager.printError("No executables visited.");
            }
        }
        return true;
    }

    int testExecutable(ExecutableElement executable) {
        ReceiverTypeKind expected = executable.getAnnotation(ReceiverTypeKind.class);
        TypeKind expectedKind = expected.value();
        String expectedType = expected.type();
        TypeMirror actualType = executable.getReceiverType();
        TypeKind actualKind = actualType.getKind();

        if (actualKind != expectedKind) {
            messager.printError(String.format("Unexpected TypeKind on receiver of %s:" +
                                              " expected %s\t got %s%n",
                                              executable, expectedKind, actualKind), executable);
        }
        if (!expectedType.isEmpty() && !actualType.toString().equals(expectedType)) {
            messager.printError(String.format("Unexpected receiver type of %s:" +
                                              " expected %s\t got %s%n",
                                              executable, expectedType, actualType), executable);
        }

        // Get kind from the type of the executable directly
        TypeMirror fromType = new TypeKindVisitor<TypeMirror, Object>(null) {
            @Override
            public TypeMirror visitExecutable(ExecutableType t, Object p) {
                return t.getReceiverType();
            }
        }.visit(executable.asType());
        TypeKind kindFromType = fromType.getKind();

        if (kindFromType != expectedKind) {
            messager.printError(String.format("Unexpected TypeKind on executable's asType() of %s:" +
                                              " expected %s\t got %s%n",
                                              executable, expectedKind, kindFromType), executable);
        }
        if (!expectedType.isEmpty() && !fromType.toString().equals(expectedType)) {
            messager.printError(String.format("Unexpected receiver type of %s:" +
                                              " expected %s\t got %s%n",
                                              executable, expectedType, fromType), executable);
        }
        return 1;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface ReceiverTypeKind {
    TypeKind value();
    String type() default "";
}

@Target(ElementType.TYPE_USE)
@interface TA {}

/**
 * Class to host various methods, etc.
 */
class MethodHost {
    @ReceiverTypeKind(TypeKind.NONE)
    public MethodHost() {}

    @ReceiverTypeKind(TypeKind.NONE)
    public static void foo() {return;}

    @ReceiverTypeKind(TypeKind.NONE)
    public void bar() {return;}

    @ReceiverTypeKind(value = TypeKind.DECLARED, type = "@TA MethodHost")
    public void quux(@TA MethodHost this) {return;}

    private class Nested {
        @ReceiverTypeKind(value = TypeKind.DECLARED, type = "@TA MethodHost")
        public Nested(@TA MethodHost MethodHost.this) {}

        @ReceiverTypeKind(TypeKind.NONE)
        public Nested(int foo) {}
    }

    private static class StaticNested {
        @ReceiverTypeKind(TypeKind.NONE)
        public StaticNested() {}
    }

    private static class Generic<X> {
      private class GenericNested<Y> {
        @ReceiverTypeKind(value = TypeKind.DECLARED, type = "MethodHost.@TA Generic<X>")
        GenericNested(@TA Generic<X> Generic.this) {}

        @ReceiverTypeKind(TypeKind.NONE)
        GenericNested(int x) {}
      }
    }
}
