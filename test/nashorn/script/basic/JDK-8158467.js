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
 * JDK-8158467: AccessControlException is thrown on public Java class access if "script app loader" is set to null
 *
 * @option -scripting
 * @test
 * @run
 */

var Factory = Java.type("jdk.nashorn.api.scripting.NashornScriptEngineFactory");
var fac = new Factory();

// This script has to be given RuntimePermission("nashorn.setConfig")
var e = fac["getScriptEngine(java.lang.ClassLoader)"](null);

print(e.eval("java.lang.System"));
print(e.eval("({ foo: 42})").foo);
print((e.eval("function(x) x*x"))(31));

e.put("output", print);
var runnable = e.eval(<<EOF
    new java.lang.Runnable() {
        run: function() {
            output("hello Runnable");
        }
    }
EOF);

runnable.run();

var obj = e.eval(<<EOF
new (Java.extend(Java.type("java.lang.Object"))) {
    hashCode: function() 33,
    toString: function() "I'm object"
}
EOF);

print(obj.hashCode());
print(obj.toString());

// should throw SecurityException!
try {
    e.eval("Packages.jdk.internal");
} catch (ex) {
    print(ex);
}

// should throw SecurityException!
try {
    e.eval("Java.type('jdk.internal.misc.Unsafe')");
} catch (ex) {
    print(ex);
}

// should throw SecurityException!
try {
    e.eval("Java.type('jdk.nashorn.internal.Context')");
} catch (ex) {
    print(ex);
}

// should throw ClassNotFoundException as null is script
// "app loader" [and not platform loader which loads nashorn]
e.eval(<<EOF
try {
    Java.type('jdk.nashorn.api.scripting.JSObject');
} catch (ex) {
    output(ex);
}
EOF);
