/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Testing StackTracker in CodeBuilder.
 * @run testng StackTrackerTest
 */
import java.util.List;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.constant.ConstantDescs;
import jdk.classfile.*;
import jdk.classfile.transforms.StackTracker;
import static jdk.classfile.TypeKind.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * StackTrackerTest
 */
@Test
public class StackTrackerTest {

    public void testStackTracker() {
        Classfile.build(ClassDesc.of("Foo"), clb ->
            clb.withMethodBody("m", MethodTypeDesc.of(ConstantDescs.CD_Void), 0, cob -> {
                var stackTracker = new StackTracker();
                cob.transforming(stackTracker, stcb -> {
                    assertEquals(stackTracker.stack().get(), List.of());
                    stcb.aload(0);
                    assertEquals(stackTracker.stack().get(), List.of(ReferenceType));
                    stcb.lconst_0();
                    assertEquals(stackTracker.stack().get(), List.of(LongType, ReferenceType));
                    stcb.trying(tryb -> {
                        assertEquals(stackTracker.stack().get(), List.of(LongType, ReferenceType));
                        tryb.iconst_1();
                        assertEquals(stackTracker.stack().get(), List.of(IntType, LongType, ReferenceType));
                        tryb.ifThen(thb -> {
                            assertEquals(stackTracker.stack().get(), List.of(LongType, ReferenceType));
                            thb.constantInstruction(ClassDesc.of("Phee"));
                            assertEquals(stackTracker.stack().get(), List.of(ReferenceType, LongType, ReferenceType));
                            thb.athrow();
                            assertFalse(stackTracker.stack().isPresent());
                        });
                        assertEquals(stackTracker.stack().get(), List.of(LongType, ReferenceType));
                        tryb.return_();
                        assertFalse(stackTracker.stack().isPresent());
                    }, catchb -> catchb.catching(ClassDesc.of("Phee"), cb -> {
                        assertEquals(stackTracker.stack().get(), List.of(ReferenceType));
                        cb.athrow();
                        assertFalse(stackTracker.stack().isPresent());
                    }));
                });
                assertTrue(stackTracker.maxStackSize().isPresent());
                assertEquals((int)stackTracker.maxStackSize().get(), 4);
            }));
    }

    public void testTrackingLost() {
        Classfile.build(ClassDesc.of("Foo"), clb ->
            clb.withMethodBody("m", MethodTypeDesc.of(ConstantDescs.CD_Void), 0, cob -> {
                var stackTracker = new StackTracker();
                cob.transforming(stackTracker, stcb -> {
                    assertEquals(stackTracker.stack().get(), List.of());
                    var l1 = stcb.newLabel();
                    stcb.goto_(l1); //forward jump
                    assertFalse(stackTracker.stack().isPresent()); //no stack
                    assertTrue(stackTracker.maxStackSize().isPresent()); //however still tracking
                    var l2 = stcb.newBoundLabel(); //back jump target
                    assertFalse(stackTracker.stack().isPresent()); //no stack
                    assertTrue(stackTracker.maxStackSize().isPresent()); //however still tracking
                    stcb.constantInstruction(ClassDesc.of("Phee")); //stack instruction on unknown stack cause tracking lost
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
