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

import jdk.nashorn.api.tree.Parser;
import jdk.nashorn.api.tree.SimpleTreeVisitorES6;
import org.testng.annotations.Test;

/**
 * 8188098: NPE in SimpleTreeVisitorES6 visitor when parsing a tagged template literal
 *
 * @test
 * @run testng jdk.nashorn.api.tree.test.JDK_8188098_Test
 */
public class JDK_8188098_Test {
    @Test
    public void test() {
        Parser p = Parser.create("--language=es6");
        p.parse("test", "foo`hello world`", System.out::println).
            accept(new SimpleTreeVisitorES6<Void, Void>(), null);
    }
}
