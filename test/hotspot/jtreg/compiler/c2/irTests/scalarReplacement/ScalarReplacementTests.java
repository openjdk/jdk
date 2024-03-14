/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2.irTests.scalarReplacement;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8267265
 * @summary Tests that Escape Analysis and Scalar Replacement is able to handle some simple cases.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.scalarReplacement.ScalarReplacementTests
 */
public class ScalarReplacementTests {
    private class Person {
        private String name;
        private int age;

        public Person(Person p) {
            this.name = p.getName();
            this.age = p.getAge();
        }

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
    }

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.CALL, IRNode.LOAD, IRNode.STORE, IRNode.FIELD_ACCESS, IRNode.ALLOC})
    public String stringConstant(int age) {
        Person p = new Person("Java", age);
        return p.getName();
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.CALL, IRNode.LOAD, IRNode.STORE, IRNode.FIELD_ACCESS, IRNode.ALLOC})
    public int intConstant(int age) {
        Person p = new Person("Java", age);
        return p.getAge();
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.CALL, IRNode.LOAD, IRNode.STORE, IRNode.FIELD_ACCESS, IRNode.ALLOC})
    public String nestedStringConstant(int age) {
        Person p1 = new Person("Java", age);
        Person p2 = new Person(p1);
        return p2.getName();
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.CALL, IRNode.LOAD, IRNode.STORE, IRNode.FIELD_ACCESS, IRNode.ALLOC})
    public int nestedIntConstant(int age) {
        Person p1 = new Person("Java", age);
        Person p2 = new Person(p1);
        return p2.getAge();
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.CALL, IRNode.LOAD, IRNode.STORE, IRNode.FIELD_ACCESS, IRNode.ALLOC})
    public int nestedConstants(int age1, int age2) {
        Person p = new Person(
                        new Person("Java", age1).getName(),
                        new Person("Java", age2).getAge());
        return p.getAge();
    }
}
