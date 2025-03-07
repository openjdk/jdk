/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8346751
 * @summary Verify type annotations inside constant expression field initializers
            are handled correctly
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.code
 *      jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main TypeAnnotationsInConstantInit
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import toolbox.JavacTask;
import toolbox.ToolBox;

public class TypeAnnotationsInConstantInit {

    public static void main(String... args) throws Exception {
        new TypeAnnotationsInConstantInit().run();
    }

    ToolBox tb = new ToolBox();

    void run() throws Exception {
        typeAnnotationInConstantExpressionFieldInit(Paths.get("."));
    }

    void typeAnnotationInConstantExpressionFieldInit(Path base) throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          import java.lang.annotation.*;

                          @SuppressWarnings(Decl.VALUE)
                          public class Decl {
                              public static final @Nullable String VALUE = (@Nullable String) "";
                          }

                          @Retention(RetentionPolicy.RUNTIME)
                          @Target({ ElementType.TYPE_USE })
                          @interface Nullable {}
                          """);
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();
    }

}
