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
 * @bug 8372635
 * @enablePreview
 * @summary Verify temporary variables created for pattern matching are
 *          marked as synthetic.
 * @compile -g SyntheticVariables.java
 * @run main SyntheticVariables
 */

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SyntheticVariables {

    public static void main(String[] args) throws IOException {
        try (InputStream in = Test.class.getClassLoader().getResource(Test.class.getName().replace('.', '/') + ".class").openStream()) {
            ClassModel model = ClassFile.of().parse(in.readAllBytes());
            Map<String, MethodModel> name2Method = model.methods().stream().collect(Collectors.toMap(m -> m.methodName().stringValue(), m -> m));
            assertEquals(Set.of("str", "b", "b2", "this", "l", "o"), localVars(name2Method.get("testInMethod")));
            assertEquals(Set.of("str", "b", "b2", "l", "o"), localVars(name2Method.get("lambda$testInLambda$0")));
        }
    }

    private static Set<String> localVars(MethodModel method) {
        CodeAttribute code = method.findAttribute(Attributes.code()).orElseThrow();
        LocalVariableTableAttribute lvt = code.findAttribute(Attributes.localVariableTable()).orElseThrow();
        return lvt.localVariables()
                  .stream()
                  .map(info -> info.name().stringValue())
                  .collect(Collectors.toSet());
    }

    private static void assertEquals(Set<String> expected, Set<String> actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Unexpected value, expected: " + expected +
                                     ", got: " + actual);
        }
    }

    public record Test(Object o) {
        private void testInMethod() {
            Object o = create();

            //synthetic variable for the instanceof's expression
            boolean b = o.toString() instanceof String str && str.isEmpty();

            System.err.println(b);

            //synthetic variable for the switch's selector and index:
            switch (create()) {
                //synthetic variable for the nested component values,
                //and for the synthetic catch over the getters
                case Test(Test(String str)) when str.isEmpty() -> System.err.println(1);
                case Test(Test(String str)) -> System.err.println(2);
                default -> System.err.println(2);
            }

            List<Integer> l = List.of(0);

            //synthetic variable for case where the static and dynamic types
            //don't match for primitive patterns:
            boolean b2 = l.get(0) instanceof int;

            System.err.println(b2);
        }

        private void testInLambda() {
            Object o = create();
            Runnable r = () -> {

                //synthetic variable for the instanceof's expression
                boolean b = o.toString() instanceof String str && str.isEmpty();

                System.err.println(b);

                //synthetic variable for the switch's selector and index:
                switch (create()) {
                    //synthetic variable for the nested component values,
                    //and for the synthetic catch over the getters
                    case Test(Test(String str)) when str.isEmpty() -> System.err.println(1);
                    case Test(Test(String str)) -> System.err.println(2);
                    default -> System.err.println(2);
                }

                List<Integer> l = List.of(0);

                //synthetic variable for case where the static and dynamic types
                //don't match for primitive patterns:
                boolean b2 = l.get(0) instanceof int;

                System.err.println(b2);
            };
        }
        private static String val = "v";
        public static Test create() {
            try {
                return new Test(new Test(val));
            } finally {
                val += val;
            }
        }
    }
}
