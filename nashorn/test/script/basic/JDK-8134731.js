/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8134731: `Function.prototype.apply` interacts incorrectly with `arguments` 
 *
 * @test
 * @run
 */

function func() {
    return (function(f){
        return function(a1, a2, a3, a4){
            return (f.apply(this, arguments));
        }
    })(function(){
        return arguments.length;
    })
}

Assert.assertTrue(func()() == 0);
Assert.assertTrue(func()(33) == 1);
Assert.assertTrue(func()(33, true) == 2);
Assert.assertTrue(func()(33, true, "hello") == 3);
Assert.assertTrue(func()(33, true, "hello", "world") == 4);
Assert.assertTrue(func()(33, true, "hello", "world", 42) == 5);
