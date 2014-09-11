/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8057678: Tests for let&const keywords in Nashorn
 *
 * @test
 * @run
 * @option --language=es6
 * @option -scripting
 */


function tryIt (code) {
    try {
        eval(code)
    } catch (e) {
        print(e)
    }
}

tryIt(<<CODE
      let a = 2;
      do {
        a--;
        let b = a;
      } while (a > 0);
      print(a)
      print(b)
CODE)

tryIt(<<CODE
       let a = 2
       while(a > 0) {
            a--
            let b = a
       }
       print(a)
       print(b)
CODE)

tryIt(<<CODE
       let a = 2
       while(a > 0) {
            a--
            const b = a
       }
       print(a)
       print(b)
CODE)

tryIt(<<CODE
       let a = 2;
       do {
         a--;
         const b = a;
       } while (a > 0);
       print(a)
       print(b)
CODE)
