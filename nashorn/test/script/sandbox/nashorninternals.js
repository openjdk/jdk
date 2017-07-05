/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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


/**
 * Test to check that nashorn "internal" classes in codegen, parser, ir
 * packages cannot * be accessed from sandbox scripts.
 *
 * @test
 * @run
 * @security
 */

function checkClass(name) {
    try {
        Java.type(name);
        fail("should have thrown exception for: " + name);
    } catch (e) {
        if (! (e instanceof java.lang.SecurityException)) {
            fail("Expected SecurityException, but got " + e);
        }
    }
}

// Not exhaustive - but a representative list of classes
checkClass("jdk.nashorn.internal.codegen.Compiler");
checkClass("jdk.nashorn.internal.codegen.types.Type");
checkClass("jdk.nashorn.internal.ir.Node");
checkClass("jdk.nashorn.internal.ir.FunctionNode");
checkClass("jdk.nashorn.internal.ir.debug.JSONWriter");
checkClass("jdk.nashorn.internal.ir.visitor.NodeVisitor");
checkClass("jdk.nashorn.internal.lookup.MethodHandleFactory");
checkClass("jdk.nashorn.internal.objects.Global");
checkClass("jdk.nashorn.internal.parser.AbstractParser");
checkClass("jdk.nashorn.internal.parser.Parser");
checkClass("jdk.nashorn.internal.parser.JSONParser");
checkClass("jdk.nashorn.internal.parser.Lexer");
checkClass("jdk.nashorn.internal.parser.Scanner");
checkClass("jdk.nashorn.internal.runtime.Context");
checkClass("jdk.nashorn.internal.runtime.arrays.ArrayData");
checkClass("jdk.nashorn.internal.runtime.linker.Bootstrap");
checkClass("jdk.nashorn.internal.runtime.options.Option");
checkClass("jdk.nashorn.internal.runtime.regexp.RegExp");
checkClass("jdk.nashorn.internal.scripts.JO");
checkClass("jdk.nashorn.tools.Shell");
