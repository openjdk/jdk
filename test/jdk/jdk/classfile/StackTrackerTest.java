/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing CodeStackTracker in CodeBuilder.
 * @run junit StackTrackerTest
 */
import java.util.List;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.constant.ConstantDescs;
import java.lang.classfile.*;
import jdk.internal.classfile.components.CodeStackTracker;
import static java.lang.classfile.TypeKind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * StackTrackerTest
 */
class StackTrackerTest {

    @Test
    void testStackTracker() {
        ClassFile.of().build(ClassDesc.of("Foo"), clb ->
            clb.withMethodBody("m", MethodTypeDesc.of(ConstantDescs.CD_Void), 0, cob -> {
                var stackTracker = CodeStackTracker.of(DOUBLE, FLOAT); //initial stack tracker pre-set
                cob.transforming(stackTracker, stcb -> {
                    assertIterableEquals(stackTracker.stack().get(), List.of(DOUBLE, FLOAT));
                    stcb.aload(0);
                    assertIterableEquals(stackTracker.stack().get(), List.of(REFERENCE, DOUBLE, FLOAT));
                    stcb.lconst_0();
                    assertIterableEquals(stackTracker.stack().get(), List.of(LONG, REFERENCE, DOUBLE, FLOAT));
                    stcb.trying(tryb -> {
                        assertIterableEquals(stackTracker.stack().get(), List.of(LONG, REFERENCE, DOUBLE, FLOAT));
                        tryb.iconst_1();
                        assertIterableEquals(stackTracker.stack().get(), List.of(INT, LONG, REFERENCE, DOUBLE, FLOAT));
                        tryb.ifThen(thb -> {
                            assertIterableEquals(stackTracker.stack().get(), List.of(LONG, REFERENCE, DOUBLE, FLOAT));
                            thb.loadConstant(ClassDesc.of("Phee"));
                            assertIterableEquals(stackTracker.stack().get(), List.of(REFERENCE, LONG, REFERENCE, DOUBLE, FLOAT));
                            thb.athrow();
                            assertFalse(stackTracker.stack().isPresent());
                        });
                        assertIterableEquals(stackTracker.stack().get(), List.of(LONG, REFERENCE, DOUBLE, FLOAT));
                        tryb.return_();
                        assertFalse(stackTracker.stack().isPresent());
                    }, catchb -> catchb.catching(ClassDesc.of("Phee"), cb -> {
                        assertIterableEquals(stackTracker.stack().get(), List.of(REFERENCE));
                        cb.athrow();
                        assertFalse(stackTracker.stack().isPresent());
                    }));
                });
                assertTrue(stackTracker.maxStackSize().isPresent());
                assertEquals((int)stackTracker.maxStackSize().get(), 7);
            }));
    }

    @Test
    void testTrackingLost() {
        ClassFile.of().build(ClassDesc.of("Foo"), clb ->
            clb.withMethodBody("m", MethodTypeDesc.of(ConstantDescs.CD_Void), 0, cob -> {
                var stackTracker = CodeStackTracker.of();
                cob.transforming(stackTracker, stcb -> {
                    assertIterableEquals(stackTracker.stack().get(), List.of());
                    var l1 = stcb.newLabel();
                    stcb.goto_(l1); //forward jump
                    assertFalse(stackTracker.stack().isPresent()); //no stack
                    assertTrue(stackTracker.maxStackSize().isPresent()); //however still tracking
                    var l2 = stcb.newBoundLabel(); //back jump target
                    assertFalse(stackTracker.stack().isPresent()); //no stack
                    assertTrue(stackTracker.maxStackSize().isPresent()); //however still tracking
                    stcb.loadConstant(ClassDesc.of("Phee")); //stack instruction on unknown stack cause tracking lost
                    assertFalse(stackTracker.stack().isPresent()); //no stack
                    assertFalse(stackTracker.maxStackSize().isPresent()); //because tracking lost
                    stcb.athrow();
                    stcb.labelBinding(l1); //forward jump target
                    assertTrue(stackTracker.stack().isPresent()); //stack known here
                    assertFalse(stackTracker.maxStackSize().isPresent()); //no max stack size because tracking lost in back jump
                    stcb.goto_(l2); //back jump
                    assertFalse(stackTracker.stack().isPresent()); //no stack
                    assertFalse(stackTracker.maxStackSize().isPresent()); //still no max stack size because tracking previously lost
                });
            }));
    }
}
