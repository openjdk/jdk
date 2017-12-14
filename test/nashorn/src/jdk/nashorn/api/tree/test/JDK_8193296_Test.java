/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.api.tree.test;

import jdk.nashorn.api.tree.CompilationUnitTree;
import jdk.nashorn.api.tree.ExpressionStatementTree;
import jdk.nashorn.api.tree.FunctionCallTree;
import jdk.nashorn.api.tree.Parser;
import jdk.nashorn.api.tree.Tree;
import jdk.nashorn.api.tree.UnaryTree;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @test
 * @bug 8184723
 * @summary Parser should not eagerly transform delete expressions
 * @run testng jdk.nashorn.api.tree.test.JDK_8193296_Test
 */
public class JDK_8193296_Test {
    @Test
    public void test() {
        Parser p = Parser.create();
        CompilationUnitTree t = p.parse("test", "function x() { }; delete x();", System.out::println);
        Assert.assertEquals(t.getSourceElements().size(), 2);
        Tree delt = ((ExpressionStatementTree)t.getSourceElements().get(1)).getExpression();
        Assert.assertTrue(delt instanceof UnaryTree, delt.getClass().getName());
        UnaryTree del = (UnaryTree)delt;
        Assert.assertEquals(del.getKind(), Tree.Kind.DELETE);
        Assert.assertTrue(del.getExpression() instanceof FunctionCallTree, del.getExpression().getClass().getName());
    }
}
