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
 * JDK-8055796: JSObject and browser JSObject linkers should provide fallback to call underlying Java methods directly
 *
 * @test
 * @run
 */

var m = new javax.script.ScriptEngineManager();
var e = m.getEngineByName("nashorn");
var jsobj = e.eval("({ foo: 33, valueOf: function() 42 })");

print("foo =", jsobj['getMember(java.lang.String)']("foo"));
print("eval =", jsobj['eval(String)']("this + 44"));
print("valueOf function? =", (jsobj.valueOf)['isFunction()']());
