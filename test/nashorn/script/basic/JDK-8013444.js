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
 * JDK-8013444: JSON.parse does not invoke "reviver" callback as per spec.
 *
 * @test
 * @run
 */


var type = typeof JSON.parse('{}',function(){})
print("type is " + type);

var obj = JSON.parse('{"name": "nashorn"}',
    function(k, v) {
        if (k === "") return v;
        return v.toUpperCase();
    });
print(JSON.stringify(obj))

var array =
  JSON.parse("[1, 3, 5, 7, 9, 11]",
   function(k, v) {
      if (k === "") return v;
      return v*2;
   }
 );
print(array)
