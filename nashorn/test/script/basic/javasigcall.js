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
 * Exact java method call - user specifies full method signature.
 *
 * @test
 * @run
 */

var map = new java.util.HashMap();

map["put(java.lang.Object,java.lang.Object)"]("py", "python");
map["put(java.lang.Object,java.lang.Object)"]("js", "javascript");

// call without signature
map["put"]("pl", "perl");
print("py=" + map.get("py"));
print("js=" + map.get("js"));
print("pl=" + map.get("pl"));

map["clear()"]();
print(map);

print(java.lang.Integer["valueOf(java.lang.String,int)"]("FF", 16));

