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

    public static record KField (String name, String type) {}

    public static class Klass {
        public final String name;
        public final Klass superKlass;
        public final HashSet<Klass> subKlasses;
        public final ArrayList<KField> fields;

        public Klass(String name, Klass superKlass) {
            this.name = name;
            this.superKlass = superKlass;
            this.subKlasses = new HashSet<Klass>();
            if (superKlass != null) {
                superKlass.subKlasses.add(this);
            }
            this.fields = new ArrayList<KField>();
        }

        private int countFields() {
            int count = fields.size();
            if (superKlass != null) {
                count += superKlass.countFields();
            }
            return count;
        }

        private KField pickField(int r) {
            if (r < fields.size()) {
                return fields.get(r);
            }
            return superKlass.pickField(r - fields.size());
        }

        public KField randomField() {
            int r = RANDOM.nextInt(countFields());
            return pickField(r);
        }
    }

    static class KlassHierarchy {
        private static int ID = 0;

        private final HashSet<Klass> rootKlasses;
        private final HashMap<String,Klass> klasses; // for finding
        private final ArrayList<Klass> klassesList;  // for sampling

        public KlassHierarchy() {
            this.rootKlasses = new HashSet<Klass>();
            this.klasses = new HashMap<String,Klass>();
            this.klassesList = new ArrayList<Klass>();
        }

        public Klass makeKlass(String superKlassName, Scope scope) {
            if (superKlassName == null) {
                Klass klass = new Klass("K" + (ID++) + "K", null);
                rootKlasses.add(klass);
                klasses.put(klass.name, klass);
                klassesList.add(klass);
                return klass;
            } else {
                Klass superKlass = find(superKlassName, scope);
                Klass klass = new Klass(superKlassName + "_" + (ID++) + "K", superKlass);
                klasses.put(klass.name, klass);
                klassesList.add(klass);
                return klass;
            }
        }

        public Klass find(String name, Scope scope) {
            Klass klass = klasses.get(name);
            if (klass == null) {
                scope.print();
                throw new RuntimeException("Could not find klass " + name);
            }
            return klass;
        }

        public String makeField(String klassName, String type, Scope scope) {
            Klass klass = find(klassName, scope);
            String name = "field" + (ID++);
            klass.fields.add(new KField(name, type));
            return name;
        }

        public Klass randomKlass() {
            int r = RANDOM.nextInt(klassesList.size());
            return klassesList.get(r);
        }

        public String randomField(String klassName, Scope scope) {
            Klass klass = find(klassName, scope);
            KField field = klass.randomField();
            return field.name;
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
            public static Object $test() {
                // Allocate Object of klass #{klass:my_random_klass}.
                #{klass} k = new #{klass}();
                // Set random field #{field:my_random_field(klass=#klass)}
                k.#{field} = #{:int_con};
                return k;
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
            // $my_base_klass with #{num_sub_klasses:int_con(lo=0,hi=5)} sub classes.
            public static class #{klass:my_new_klass} {
                // Generate #{num_fields:int_con(lo=1,hi=4)} fields:
                #{:repeat(call=my_define_new_field,repeat=#num_fields,klass=#klass)}
            }

            #{:repeat(call=my_sub_klass,repeat=#num_sub_klasses,super=#klass)}
            """
        ));

        codeGenerators.add(new Template("my_sub_klass",
            """
            // $my_sub_klass
            public static class #{klass:my_new_klass(super=#super)} extends #{super} {
                // Generate #{num_fields:int_con(lo=0,hi=2)} fields:
                #{:repeat(call=my_define_new_field,repeat=#num_fields,klass=#klass)}
            }
            """
        ));

        codeGenerators.add(new Template("my_define_new_field",
            """
            // $my_define_new_field for #{klass} of type #{type:choose(from=int|long)}.
            public #{type} #{:my_new_field(klass=#klass,type=#type)} = 0;
            """
        ));

        // Creates a new klass. If "super" provided, it is a subklass of "super". Returns the name of the klass.
        codeGenerators.add(new ProgrammaticCodeGenerator("my_new_klass", (Scope scope, Parameters parameters) -> {
            parameters.checkOnlyHas(scope, "super");
            String superKlassName = parameters.getOrNull("super");
            Klass klass = hierarchy.makeKlass(superKlassName, scope);
            scope.stream.addCodeToLine(klass.name);
        }, 0));

        // Creates a new field in a klass, with the specified type. Returns the name of the field.
        codeGenerators.add(new ProgrammaticCodeGenerator("my_new_field", (Scope scope, Parameters parameters) -> {
            parameters.checkOnlyHas(scope, "klass", "type");
            String klassName = parameters.get("klass", scope);
            String type = parameters.get("type", scope);
            String fieldName = hierarchy.makeField(klassName, type, scope);
            scope.stream.addCodeToLine(fieldName);
        }, 0));

        codeGenerators.add(new ProgrammaticCodeGenerator("my_random_klass", (Scope scope, Parameters parameters) -> {
            parameters.checkOnlyHas(scope); // no agrs
            Klass klass = hierarchy.randomKlass();
            scope.stream.addCodeToLine(klass.name);
        }, 0));

        codeGenerators.add(new ProgrammaticCodeGenerator("my_random_field", (Scope scope, Parameters parameters) -> {
            parameters.checkOnlyHas(scope, "klass");
            String klassName = parameters.get("klass", scope);
            String fieldName = hierarchy.randomField(klassName, scope);
            scope.stream.addCodeToLine(fieldName);
        }, 0));

        return new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);
    }
}
