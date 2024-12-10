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
 * @summary Example with custom Library. We generate some random classes and use them.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.examples.TestCustomLibraryForClassFuzzing
 */

package template_framework.examples;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import jdk.test.lib.Utils;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.*;

public class TestCustomLibraryForClassFuzzing {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generate());

        // Compile the source file.
        comp.compile();

        // Object ret = p.xyz.InnerTest.main();
        Object ret = comp.invoke("p.xyz.InnerTest", "main", new Object[] {new String[0]});
        System.out.println("res: " + ret);
    }

    public static class Klass {
        public final String name;
        public final Klass superKlass;
        public final HashSet<Klass> subKlasses;

        public Klass(String name, Klass superKlass) {
            this.name = name;
            this.superKlass = superKlass;
            this.subKlasses = new HashSet<Klass>();
        }
    }

    static class KlassHierarchy {
        private static int ID = 0;

        private final HashSet<Klass> rootKlasses;
        private final HashMap<String,Klass> klasses;

        public KlassHierarchy() {
            this.rootKlasses = new HashSet<Klass>();
            this.klasses = new HashMap<String,Klass>();
        }

        public Klass makeKlass() {
            Klass klass = new Klass("K" + (ID++) + "K", null);
            rootKlasses.add(klass);
            klasses.put(klass.name, klass);
            return klass;
        }
    }

    // Generate a source Java file as String
    public static String generate() {
        // Generate classes
        KlassHierarchy hierarchy = new KlassHierarchy();

        // Create a new instantiator with the custom Library.
        CodeGeneratorLibrary customLibrary = createCustomLibrary(hierarchy);
        TestClassInstantiator instantiator = new TestClassInstantiator("p.xyz", "InnerTest", customLibrary);

        // Generate code for classes
	// Note: we get a random num_klasses 1..9, and call my_base_klass that many times.
        Template klassTemplate = new Template("my_klass",
            """
            // KlassHierarchy with #{num_klasses:int_con(lo=1,hi=10)} base classes.
            #{:repeat(call=my_base_klass,repeat=#num_klasses)}
            """
        );
        instantiator.add(klassTemplate, null, null);

        // Generate tests
        Template mainTemplate = new Template("my_main",
            """
            $test();
            """
        );
        Template testTemplate = new Template("my_test",
            """
            public static void $test() {
            }
            """
        );
        instantiator.repeat(10).add(null, mainTemplate, testTemplate);

        // Collect everything into a String.
        return instantiator.instantiate();
    }

    // We take the standard library, and add some more generators to it.
    public static CodeGeneratorLibrary createCustomLibrary(KlassHierarchy hierarchy) {
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_base_klass",
            """
            // $my_base_klass
            public static class #{klass:my_new_klass} {
                // #{klass}
            }
            """
        ));

        codeGenerators.add(new ProgrammaticCodeGenerator("my_new_klass", (Scope scope, Parameters parameters) -> {
            parameters.checkOnlyHas(scope, "klass", "super");
            Klass klass = hierarchy.makeKlass();
            scope.stream.addCodeToLine(klass.name);
        }, 0));

        return new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);
    }
}
