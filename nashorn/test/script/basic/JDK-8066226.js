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
 *
 JDK-8066226: Fuzzing bug: parameter counts differ in TypeConverterFactory
 *
 * @test
 * @run
 */

Object.defineProperty(Object.prototype, "accessor", {
    set: function(value) {
        print("Setting accessor on " + this + " to " + value);
    }
});

Object.defineProperty(Object.prototype, "getterOnly", {
    get: function() {
        return 1;
    }
});

function set(o) {
    print("set(" + o + ")");
    o.foo = 1;
    o.constructor = 1;
    o.accessor = 1;
    o.getterOnly = 1;
    print();
}

function setStrict(o) {
    "use strict";
    print("setStrict(" + o + ")")
    try {
        o.foo = 1;
    } catch (e) {
        print(e);
    }
    try {
        o.constructor = 1;
    } catch (e) {
        print(e);
    }
    try {
        o.accessor = 1;
    } catch (e) {
        print(e);
    }
    try {
        o.getterOnly = 1;
    } catch (e) {
        print(e);
    }
    print();
}

function setAttr(o, id) {
    print("setAttr(" + o + ", " + id + ")")
    o[id] = 1;
    print();
}

function setAttrStrict(o, id) {
    "use strict";
    print("setAttrStrict(" + o + ", " + id + ")")
    try {
        o[id] = 1;
    } catch (e) {
        print(e);
    }
    print();
}

set(1);
set("str");
set(true);
set({});
set([]);

setStrict(1);
setStrict("str");
setStrict(true);
setStrict({});
setStrict([]);

setAttr(1, "foo");
setAttr(1, "constructor");
setAttr(1, "accessor");
setAttr(1, "getterOnly");
setAttr("str", "foo");
setAttr("str", "constructor");
setAttr("str", "accessor");
setAttr("str", "getterOnly");
setAttr(true, "foo");
setAttr(true, "constructor");
setAttr(true, "accessor");
setAttr(true, "getterOnly");

setAttrStrict(1, "foo");
setAttrStrict(1, "constructor");
setAttrStrict(1, "accessor");
setAttrStrict(1, "getterOnly");
setAttrStrict("str", "foo");
setAttrStrict("str", "constructor");
setAttrStrict("str", "accessor");
setAttrStrict("str", "getterOnly");
setAttrStrict(true, "foo");
setAttrStrict(true, "constructor");
setAttrStrict(true, "accessor");
setAttrStrict(true, "getterOnly");
