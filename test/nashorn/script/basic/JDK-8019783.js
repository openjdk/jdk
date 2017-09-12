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
 * JDK-8019783: typeof does not work properly for java methods and foreign objects
 *
 * @test
 * @run
 */

function printTypeof(str) {
    print("typeof(" + str + ") =  " + eval('typeof ' + str));
}

// Java methods
printTypeof("java.lang.System.exit");
printTypeof("java.lang.System['exit']");
// full signature
printTypeof("java.lang.System['exit(int)']");
// overloaded method
printTypeof("java.security.AccessController.doPrivileged");
printTypeof("java.security.AccessController['doPrivileged']");

// foreign objects
var global = loadWithNewGlobal({ name: "t", script: "this" });
print("typeof(global.Object) = " + (typeof global.Object));
print("typeof(new global.Object()) = " + (typeof (new global.Object())));

// foreign engine objects
var m = new javax.script.ScriptEngineManager();
var engine = m.getEngineByName("nashorn");
var engineGlobal = engine.eval("this");

print("typeof(engineGlobal.Object) = " + (typeof engineGlobal.Object));
print("typeof(new engineGlobal.Object()) = " + (typeof (new engineGlobal.Object())));
