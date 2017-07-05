/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * Destructuring is not implemented
 *
 * @test
 * @run
 * @option --language=es6
 */


function check(code) {
   try {
      eval(code);
   } catch (e) {
      print(String(e).replace(/\\/g, "/"))
   }
}

check("var { x: y } = obj;");
check("let { x: y } = obj;");
check("const { x: y } = obj;");
check("({ x: y }) = obj;");
check("for (var { x: y } of obj) ;");
check("for (let { x: y } of obj) ;");
check("var { x, y } = obj;");
check("let { x, y } = obj;");
check("const { x, y } = obj;");
check("({ x, y }) = obj;");
check("for (var { x, y } of obj) ;");
check("for (let { x, y } of obj) ;");
check("var [a, b] = obj;");
check("let [a, b] = obj;");
check("const [a, b] = obj;");
check("[a, b] = obj;");
check("for ([a, b] of obj) ;");
check("for (var [a, b] of obj) ;");
check("for (let [a, b] of obj) ;");
check("(function({ x: y }) { return x; })()");
check("(function({ x }) { return x; })()");
check("(function([x]) { return x; })()");
check("for (var [[x, y, z] = [4, 5, 6]] = [7, 8, 9]; iterCount < 1; ) ;");
check("for ([ arrow = () => {} ] of [[]]) ;");
check("try { throw null;} catch({}) { }");
check("try { throw {} } catch ({}) { }");
check("try { throw [] } catch ([,]) { }");
check("try { throw { w: [7, undefined, ] }} catch ({ w: [x, y, z] = [4, 5, 6] }) { }");
check("try { throw { a: 2, b: 3} } catch ({a, b}) { }");
check("try { throw [null] } catch ([[x]]) { }");
check("try { throw { w: undefined } } catch ({ w: { x, y, z } = { x: 4, y: 5, z: 6 } }) { }");

