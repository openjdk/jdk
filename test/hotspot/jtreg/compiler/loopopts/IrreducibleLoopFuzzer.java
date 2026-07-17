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
 * @bug 8299214
 * @key randomness
 * @summary Jasm Fuzzer for irreducible loops.
 * @library /test/lib /
 * @compile ../../compiler/lib/verify/Verify.java
 * @run driver ${test.main.class}
 */

package compiler.loopopts;

// TODO: all needed?
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Set;
import java.util.Random;
import jdk.test.lib.Utils;
import java.util.stream.IntStream;

import compiler.lib.compile_framework.CompileFramework;

// TODO: all needed?
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import static compiler.lib.template_framework.Template.$;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.Expression;
import compiler.lib.template_framework.library.Expression.Nesting;
import compiler.lib.template_framework.library.Operations;
import compiler.lib.template_framework.library.TestFrameworkClass;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.ShortCarriesFloat16Type;
import compiler.lib.template_framework.library.VectorElementType;
import compiler.lib.template_framework.library.VectorType;

/**
 * Fuzzer for irreducible loops.
 * Regular method compilations of Java code is structured, so no irreducible loops.
 * OSR can create some irreducible loops, but with Jasm we have the full freedom
 * to create arbitrary code graphs.
 */
public class IrreducibleLoopFuzzer {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJasmSourceCode("compiler.loopopts.templated.Templated", generate());

        // Compile the source file.
        comp.compile();

        comp.invoke("compiler.loopopts.templated.Templated", "test", new Object[] {} );
    }

    public static String generate() {
        return """
               package compiler/loopopts/templated;

               super public class Templated {
                   public static Method test:"()V"
                   stack 20 locals 20
                   {
                       return;
                   }
               }
               """;
    }
}


