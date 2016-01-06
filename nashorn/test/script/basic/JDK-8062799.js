/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8062799: Binary logical expressions can have numeric types
 *
 * @test
 * @run
 */

(function() {
    var inspect = Java.type("jdk.nashorn.test.tools.StaticTypeInspector").inspect;
    
    var b = true;
    var i = 1;
    var d = 2.1;
    var o = "foo";

    print(inspect(b || b, "b || b"));
    print(inspect(b || i, "b || i"));
    print(inspect(b || d, "b || d"));
    print(inspect(b || o, "b || o"));
        
    print(inspect(i || b, "i || b"));
    print(inspect(i || i, "i || i"));
    print(inspect(i || d, "i || d"));
    print(inspect(i || o, "i || o"));

    print(inspect(d || b, "d || b"));
    print(inspect(d || i, "d || i"));
    print(inspect(d || d, "d || d"));
    print(inspect(d || o, "d || o"));

    print(inspect(o || b, "o || b"));
    print(inspect(o || i, "o || i"));
    print(inspect(o || d, "o || d"));
    print(inspect(o || o, "o || o"));

    print(inspect(b && b, "b && b"));
    print(inspect(b && i, "b && i"));
    print(inspect(b && d, "b && d"));
    print(inspect(b && o, "b && o"));
        
    print(inspect(i && b, "i && b"));
    print(inspect(i && i, "i && i"));
    print(inspect(i && d, "i && d"));
    print(inspect(i && o, "i && o"));

    print(inspect(d && b, "d && b"));
    print(inspect(d && i, "d && i"));
    print(inspect(d && d, "d && d"));
    print(inspect(d && o, "d && o"));

    print(inspect(o && b, "o && b"));
    print(inspect(o && i, "o && i"));
    print(inspect(o && d, "o && d"));
    print(inspect(o && o, "o && o"));
})();

    
    
        
