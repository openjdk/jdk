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
 * JDK-8016235 : use before definition in catch block generated erroneous bytecode
 * as there is no guarantee anything in the try block has executed. 
 *
 * @test
 * @run 
 */

function f() {
    try {
	var parser = {};
    } catch (e) {
	parser = parser.context();
    }
}

function g() { 
    try {
        return "apa";
    } catch (tmp) {
	//for now, too conservative as var ex declaration exists on the function
	//level, but at least the code does not break, and the analysis is driven
	//from the catch block (the rare case), not the try block (the common case)
        var ex = new Error("DOM Exception 5");
        ex.code = ex.number = 5;
        return ex;
    }
}
