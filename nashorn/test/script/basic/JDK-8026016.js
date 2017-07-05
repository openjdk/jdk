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
 * JDK-8026016: too many relinks dominate avatar.js http benchmark
 *
 * @test
 * @run
 */

function accessMegamorphic() {
    for (var i = 0; i < 26; i++) {
        var o = {};
        o[String.fromCharCode(i + 97)] = 1;
        o._;
    }
}

function invokeMegamorphic() {
    for (var i = 0; i < 26; i++) {
        var o = {};
        o[String.fromCharCode(i + 97)] = 1;
        try {
            o._(i);
        } catch (e) {
            print(e);
        }
    }
}

Object.prototype.__noSuchProperty__ = function() {
    print("no such property", Array.prototype.slice.call(arguments));
};

invokeMegamorphic();
accessMegamorphic();

Object.prototype.__noSuchMethod__ = function() {
    print("no such method", Array.prototype.slice.call(arguments));
};

invokeMegamorphic();
accessMegamorphic();

Object.prototype.__noSuchMethod__ = "nofunction";

invokeMegamorphic();
accessMegamorphic();
