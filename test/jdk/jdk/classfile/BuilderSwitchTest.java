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
 * @summary Testing Classfile builder blocks.
 * @run junit BuilderSwitchTest
 */


import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Consumer;
import static jdk.internal.classfile.Classfile.*;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.components.ClassPrinter;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BuilderSwitchTest {

    public void testSwitch(Consumer<CodeBuilder> gen, int... expected) throws Throwable {
        byte[] bytes = build(ClassDesc.of("TestSwitch"), List.of(Option.generateStackmap(true)), clb ->
                clb.withFlags(ACC_PUBLIC)
                   .withMethodBody("main", MethodTypeDesc.of(CD_int, CD_int), ACC_PUBLIC | ACC_STATIC, gen));
//        ClassPrinter.toYaml(parse(bytes), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES, System.out::print);
        MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
        MethodHandle main = lookup.findStatic(lookup.lookupClass(), "main",
                MethodType.methodType(Integer.TYPE, Integer.TYPE));
        for (int i = 0; i < 10; i++) {
            System.out.print(main.invoke(i) + ", ");
        }
    }

    @Test
    public void testTableSwitchDefaultFallthrough() throws Throwable {
        testSwitch(cob -> cob
                .iload(0)
                .tableswitch(swb -> swb
                        .switchCase(6, b -> b.iinc(0,1))
                        .switchCase(4, b -> b.iinc(0,2))
                        .switchCase(3, b -> b.iinc(0,3))
                        .defaultCase(b -> b.iinc(0,100))
                        .switchCase(1, b -> b.iinc(0,4))
                        .switchCase(2, b -> b.iinc(0,5))
                        .switchCase(5, b -> b.iinc(0,6)))
                .iload(0).ireturn(),
                115, 122, 122, 121, 19, 16, 12, 122, 123, 124
        );
    }

    @Test
    public void testTableSwitchDefault() throws Throwable {
        testSwitch(cob -> cob
                .iload(0)
                .tableswitch(swb -> swb
                        .switchCase(1, b -> b.iinc(0,1).goto_(b.breakLabel()))
                        .switchCase(2, b -> b.iinc(0,2).goto_(b.breakLabel()))
                        .switchCase(3, b -> b.iinc(0,3).goto_(b.breakLabel()))
                        .defaultCase(b -> b.iinc(0,100).goto_(b.breakLabel()))
                        .switchCase(4, b -> b.iinc(0,4).goto_(b.breakLabel()))
                        .switchCase(5, b -> b.iinc(0,5).goto_(b.breakLabel()))
                        .switchCase(6, b -> b.iinc(0,6).goto_(b.breakLabel())))
                .iload(0).ireturn(),
                100, 2, 4, 6, 8, 10, 12, 107, 108, 109
        );
    }

    @Test
    public void testLookupSwitchDefaultFallthrough() throws Throwable {
        testSwitch(cob -> cob
                .iload(0)
                .lookupswitch(swb -> swb
                        .switchCase(1, b -> b.iinc(0,1))
                        .switchCase(2, b -> b.iinc(0,2))
                        .switchCase(3, b -> b.iinc(0,3))
                        .defaultCase(b -> b.iinc(0,100))
                        .switchCase(4, b -> b.iinc(0,4))
                        .switchCase(5, b -> b.iinc(0,5))
                        .switchCase(6, b -> b.iinc(0,6)))
                .iload(0).ireturn(),
                115, 122, 122, 121, 19, 16, 12, 122, 123, 124
        );
    }

    @Test
    public void testLookupSwitchDefault() throws Throwable {
        testSwitch(cob -> cob
                .iload(0)
                .lookupswitch(swb -> swb
                        .switchCase(1, b -> b.iinc(0,1).goto_(b.breakLabel()))
                        .switchCase(2, b -> b.iinc(0,2).goto_(b.breakLabel()))
                        .switchCase(3, b -> b.iinc(0,3).goto_(b.breakLabel()))
                        .defaultCase(b -> b.iinc(0,100).goto_(b.breakLabel()))
                        .switchCase(4, b -> b.iinc(0,4).goto_(b.breakLabel()))
                        .switchCase(5, b -> b.iinc(0,5).goto_(b.breakLabel()))
                        .switchCase(6, b -> b.iinc(0,6).goto_(b.breakLabel())))
                .iload(0).ireturn(),
                100, 2, 4, 6, 8, 10, 12, 107, 108, 109
        );
    }

    @Test
    public void testTableSwitchFallthrough() throws Throwable {
        testSwitch(cob -> cob
                .iload(0)
                .tableswitch(swb -> swb
                        .switchCase(1, b -> b.iinc(0,1))
                        .switchCase(2, b -> b.iinc(0,2))
                        .switchCase(3, b -> b.iinc(0,3))
                        .switchCase(4, b -> b.iinc(0,4))
                        .switchCase(5, b -> b.iinc(0,5))
                        .switchCase(6, b -> b.iinc(0,6)))
                .iload(0).ireturn(),
                0, 22, 22, 21, 19, 16, 12, 7, 8, 9
        );
    }

    @Test
    public void testTableSwitch() throws Throwable {
        testSwitch(cob -> cob
                .iload(0)
                .tableswitch(swb -> swb
                        .switchCase(1, b -> b.iinc(0,1).goto_(b.breakLabel()))
                        .switchCase(2, b -> b.iinc(0,2).goto_(b.breakLabel()))
                        .switchCase(3, b -> b.iinc(0,3).goto_(b.breakLabel()))
                        .switchCase(4, b -> b.iinc(0,4).goto_(b.breakLabel()))
                        .switchCase(5, b -> b.iinc(0,5).goto_(b.breakLabel()))
                        .switchCase(6, b -> b.iinc(0,6).goto_(b.breakLabel())))
                .iload(0).ireturn(),
                0, 2, 4, 6, 8, 10, 12, 7, 8, 9
        );
    }

    @Test
    public void testLookupSwitchFallthrough() throws Throwable {
        testSwitch(cob -> cob
                .iload(0)
                .lookupswitch(swb -> swb
                        .switchCase(1, b -> b.iinc(0,1))
                        .switchCase(2, b -> b.iinc(0,2))
                        .switchCase(3, b -> b.iinc(0,3))
                        .switchCase(4, b -> b.iinc(0,4))
                        .switchCase(5, b -> b.iinc(0,5))
                        .switchCase(6, b -> b.iinc(0,6)))
                .iload(0).ireturn(),
                0, 22, 22, 21, 19, 16, 12, 7, 8, 9
        );
    }

    @Test
    public void testLookupSwitch() throws Throwable {
        testSwitch(cob -> cob
                .iload(0)
                .lookupswitch(swb -> swb
                        .switchCase(1, b -> b.iinc(0,1).goto_(b.breakLabel()))
                        .switchCase(2, b -> b.iinc(0,2).goto_(b.breakLabel()))
                        .switchCase(3, b -> b.iinc(0,3).goto_(b.breakLabel()))
                        .switchCase(4, b -> b.iinc(0,4).goto_(b.breakLabel()))
                        .switchCase(5, b -> b.iinc(0,5).goto_(b.breakLabel()))
                        .switchCase(6, b -> b.iinc(0,6).goto_(b.breakLabel())))
                .iload(0).ireturn(),
                0, 2, 4, 6, 8, 10, 12, 7, 8, 9
        );
    }

    @Test()
    public void testDoubleDefault() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> testSwitch(cob -> cob
                .iload(0)
                .lookupswitch(swb -> swb
                        .switchCase(1, b -> b.iinc(0,1).goto_(b.breakLabel()))
                        .switchCase(2, b -> b.iinc(0,2).goto_(b.breakLabel()))
                        .switchCase(3, b -> b.iinc(0,3).goto_(b.breakLabel()))
                        .defaultCase(b -> b.iinc(0,100).goto_(b.breakLabel()))
                        .switchCase(4, b -> b.iinc(0,4).goto_(b.breakLabel()))
                        .switchCase(5, b -> b.iinc(0,5).goto_(b.breakLabel()))
                        .defaultCase(b -> b.iinc(0,100).goto_(b.breakLabel()))
                        .switchCase(6, b -> b.iinc(0,6).goto_(b.breakLabel())))
                .iload(0).ireturn()));
    }

    @Test()
    public void testDoubleCase() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> testSwitch(cob -> cob
                .iload(0)
                .lookupswitch(swb -> swb
                        .switchCase(1, b -> b.iinc(0,1).goto_(b.breakLabel()))
                        .switchCase(2, b -> b.iinc(0,2).goto_(b.breakLabel()))
                        .switchCase(3, b -> b.iinc(0,3).goto_(b.breakLabel()))
                        .defaultCase(b -> b.iinc(0,100).goto_(b.breakLabel()))
                        .switchCase(4, b -> b.iinc(0,4).goto_(b.breakLabel()))
                        .switchCase(5, b -> b.iinc(0,5).goto_(b.breakLabel()))
                        .switchCase(3, b -> b.iinc(0,3).goto_(b.breakLabel()))
                        .switchCase(6, b -> b.iinc(0,6).goto_(b.breakLabel())))
                .iload(0).ireturn()));
    }
}