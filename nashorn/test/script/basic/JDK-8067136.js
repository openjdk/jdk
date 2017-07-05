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
 * JDK-8067136: BrowserJSObjectLinker does not handle call on JSObjects
 *
 * @test
 * @option -scripting
 * @run
 */

// call on netscape.javascript.JSObject

function main() {
    var JSObject;
    try {
        JSObject = Java.type("netscape.javascript.JSObject");
    } catch (e) {
        if (e instanceof java.lang.ClassNotFoundException) {
            // pass vacuously by emitting the .EXPECTED file content
            var str = readFully(__DIR__ + "JDK-8067136.js.EXPECTED");
            print(str.substring(0, str.length - 1));
            return;
        } else{
            fail("unexpected exception for JSObject", e);
        }
    }
    test(JSObject);
}

function test(JSObject) {
    var obj = new (Java.extend(JSObject))() {
        getMember: function(name) {
            if (name == "func") {
                return new (Java.extend(JSObject)) {
                    call: function(n) {
                        print("func called");
                    }
                }
            }
            return name.toUpperCase();
        },

    };

    obj.func();
}

main();
