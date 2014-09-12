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
 * @option -scripting
 * @run
 */

function main() {
    var JSObject;
    try {
        JSObject = Java.type("netscape.javascript.JSObject");
    } catch (e) {
        if (e instanceof java.lang.ClassNotFoundException) {
            // pass vacuously by emitting the .EXPECTED file content
            var str = readFully(__DIR__ + "JDK-8055796_2.js.EXPECTED");
            print(str.substring(0, str.length - 1));
            return;
        } else {
            fail("unexpected exception on JSObject", e);
        }
    }
    test(JSObject);
}

function test(JSObject) {
    var bjsobj = new (Java.extend(JSObject))() {
        getMember: function(name) {
            if (name == "func") {
                return function(arg) {
                    print("func called with " + arg);
                }
            }
            return name.toUpperCase();
        },

        getSlot: function(index) {
            return index*index;
        },

        setMember: function(name, value) {
            print(name + " set to " + value);
        },

        setSlot: function(index, value) {
            print("[" + index + "] set to " + value);
        }
    };

    print("getMember('foo') =", bjsobj['getMember(String)']('foo'));
    print("getSlot(6) =", bjsobj['getSlot(int)'](6));
    bjsobj['setMember(String, Object)']('bar', 'hello');
    bjsobj['setSlot(int, Object)'](10, 42);
}

main();
