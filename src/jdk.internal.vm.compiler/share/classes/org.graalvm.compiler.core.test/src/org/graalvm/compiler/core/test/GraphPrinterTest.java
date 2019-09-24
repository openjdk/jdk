/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.graalvm.compiler.printer.CanonicalStringGraphPrinter;
import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Tests related to graph printing.
 */
public class GraphPrinterTest extends GraalCompilerTest {

    /**
     * Tests that a self-recursive object does not cause stack overflow when formatted as a string.
     */
    @Test
    public void testGraphPrinterDoesNotStackOverflow() {
        CanonicalStringGraphPrinter printer = new CanonicalStringGraphPrinter(getSnippetReflection());
        Object[] topArray = {null};
        Object[] parent = topArray;
        Object[] lastArray = null;
        for (int i = 0; i < 5; i++) {
            lastArray = new Object[1];
            parent[0] = lastArray;
            parent = lastArray;
        }
        lastArray[0] = topArray;
        JavaConstant constant = getSnippetReflection().forObject(topArray);
        printer.format(constant);
    }
}
