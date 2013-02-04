/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.JavaCompiler.CompilationTask;

public class Helper {

    enum ContentVars {
        IMPORTCONTAINERSTMTS("\nimport java.lang.annotation.Repeatable;\n"),
        IMPORTDEPRECATED("import java.lang.Deprecated;\n"),
        IMPORTDOCUMENTED("import java.lang.annotation.Documented;\n"),
        IMPORTINHERITED("import java.lang.annotation.Inherited;\n"),
        IMPORTRETENTION("import java.lang.annotation.Retention;\n" +
                        "\nimport java.lang.annotation.RetentionPolicy;\n"),
        REPEATABLE("\n@Repeatable(FooContainer.class)\n"),
        CONTAINER("@interface FooContainer {\n" +"  Foo[] value();\n}\n"),
        BASE("@interface Foo {}\n"),
        REPEATABLEANNO("\n@Foo() @Foo()"),
        DEPRECATED("\n@Deprecated"),
        DOCUMENTED("\n@Documented"),
        INHERITED("\n@Inherited"),
        RETENTION("@Retention(RetentionPolicy.#VAL)\n");

        private String val;


        private ContentVars(String val) {
            this.val = val;
        }

        public String getVal() {
            return val;
        }
    }

    /* String template where /*<TYPE>*/ /*gets replaced by repeating anno
     * Used to generate test src for combo tests
     *   - BasicSyntaxCombo.java
     *   - TargetAnnoCombo.java
     */
    public static final String template =
            "/*PACKAGE*/\n" +
            "//pkg test;\n\n" +
            "/*TYPE*/ //class\n" +
            "class #ClassName {\n" +
            "  /*FIELD*/ //instance var\n" +
            "  public int x = 0;\n\n" +
            "  /*FIELD*/ //Enum constants\n" +
            "  TestEnum testEnum;\n\n" +
            "  /*FIELD*/ // Static field\n" +
            "  public static int num;\n\n" +
            "  /*STATIC_INI*/\n" +
            "  static { \n" + "num = 10; \n  }\n\n" +
            "  /*CONSTRUCTOR*/\n" +
            "  #ClassName() {}\n\n" +
            "  /*INSTANCE_INI*/\n" +
            "  { \n x = 10; \n }" +
            "  /*INNER_CLASS*/\n" +
            "  class innerClass {}\n" +
            "  /*METHOD*/\n" +
            "  void bar(/*PARAMETER*/ int baz) {\n" +
            "    /*LOCAL_VARIABLE*/\n" +
            "    int y = 0;\n" +
            "  }\n" +
            "}\n\n" +
            "/*TYPE*/ //Enum\n" +
            "enum TestEnum {}\n\n" +
            "/*TYPE*/ //Interface\n" +
            "interface TestInterface {}\n\n" +
            "/*TYPE*/\n" +
            "/*ANNOTATION_TYPE*/\n" +
            "@interface TestAnnotationType{}\n";

    // Create and compile FileObject using values for className and contents
    public static boolean compileCode(String className, String contents,
            DiagnosticCollector<JavaFileObject> diagnostics) {
        boolean ok = false;
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("can't get javax.tools.JavaCompiler!");
        }

        JavaFileObject file = getFile(className, contents);
        Iterable<? extends JavaFileObject> compilationUnit = Arrays.asList(file);

        CompilationTask task = compiler.getTask(null, null, diagnostics, null, null, compilationUnit);
        ok = task.call();
        return ok;

    }

    // Compile a list of FileObjects
    public static boolean compileCode(DiagnosticCollector<JavaFileObject> diagnostics, Iterable<? extends JavaFileObject> files) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("can't get javax.tools.JavaCompiler!");
        }

        CompilationTask task = compiler.getTask(null, null, diagnostics, null, null, files);
        boolean ok = task.call();
        return ok;
    }

    static JavaFileObject getFile(String name, String code) {
        JavaFileObject o = null;
        try {
            o = new JavaStringFileObject(name, code);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return o;
    }
    static class JavaStringFileObject extends SimpleJavaFileObject {
        final String theCode;
        public JavaStringFileObject(String fileName, String theCode) throws URISyntaxException {
            super(new URI("string:///" + fileName.replace('.','/') + ".java"), Kind.SOURCE);
            this.theCode = theCode;
        }
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return theCode;
        }
    }
}
