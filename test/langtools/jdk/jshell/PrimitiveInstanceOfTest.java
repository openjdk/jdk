/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304487 8325257
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @build KullaTesting TestingInputStream
 * @run junit PrimitiveInstanceOfTest
 */
import jdk.jshell.JShell;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PrimitiveInstanceOfTest extends KullaTesting {

    @Test
    public void testInstanceOf() {
        assertEval("int i = 42;");
        assertEval("i instanceof Integer");
        assertEval("i instanceof int");
    }

    @Test
    public void testInstanceOfRef() {
        assertEval("Integer i = 42;");
        assertEval("i instanceof Integer");
        assertEval("i instanceof Number");
    }

    @Test
    public void testInstanceOfObjectToPrimitive() {
        assertEval("Object o = 1L;");
        assertEval("o instanceof long");
        assertEval("o instanceof Long");
    }

    @Test
    public void testInstanceOfPrimitiveToPrimitiveInvokingExactnessMethod() {
        assertEval("int b = 1024;");
        assertEval("b instanceof byte");
    }

    @BeforeEach
    public void setUp() {
        super.setUp(bc -> bc.compilerOptions("--source", System.getProperty("java.specification.version"), "--enable-preview").remoteVMOptions("--enable-preview"));
    }
}
