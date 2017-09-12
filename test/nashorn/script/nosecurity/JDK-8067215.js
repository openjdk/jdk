/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8067215: Disable dual fields when not using optimistic types
 *
 * @test
 * @run
 * @option -Dnashorn.debug=true
 * @option -scripting
 * @fork
 */

var intType    = Java.type("int");
var doubleType = Java.type("double");
var objectType = Java.type("java.lang.Object");

var Context = Java.type("jdk.nashorn.internal.runtime.Context");
var JSType  = Java.type("jdk.nashorn.internal.runtime.JSType");
var Property = Java.type("jdk.nashorn.internal.runtime.Property");
var PropertyMap  = Java.type("jdk.nashorn.internal.runtime.PropertyMap");

// Class objects
var objectCls = Java.type("java.lang.Object").class;
var contextCls = Context.class;
var JSTypeCls = JSType.class;
var propertyCls = Property.class;
var propertyMapCls  = PropertyMap.class;

// Method objects
var getContextMethod = contextCls.getMethod("getContext");
var isRepresentableAsIntMethod = JSTypeCls.getMethod("isRepresentableAsInt", java.lang.Double.TYPE);
var hasDualFieldsMethod = propertyCls.getMethod("hasDualFields");
var getTypeMethod = propertyCls.getMethod("getType");
var findPropertyMethod = propertyMapCls.getMethod("findProperty", objectCls);

var context = getContextMethod.invoke(null);
var useDualFieldsMethod = contextCls.getMethod("useDualFields");
var dualFields = useDualFieldsMethod.invoke(context);
var optimisticTypes = $OPTIONS._optimistic_types;

if (dualFields != optimisticTypes) {
    throw new Error("Wrong dual fields setting");
}

function testMap(obj) {
    obj.x = "foo";
    obj["y"] = 0;
    Object.defineProperty(obj, "z", {value: 0.5});
    var map = Debug.map(obj);
    for (var key in obj) {
        var prop = findPropertyMethod.invoke(map, key);
        if (hasDualFieldsMethod.invoke(prop) !== dualFields) {
            throw new Error("Wrong property flags: " + prop);
        }
        if (getTypeMethod.invoke(prop) != getExpectedType(obj[key])) {
            throw new Error("Wrong property type: " + prop.getType() + " // " + getExpectedType(obj[key]));
        }
    }
}

function getExpectedType(value) {
    if (!dualFields) {
        return objectType.class;
    }
    if (typeof value === "number") {
        return isRepresentableAsIntMethod.invoke(null, value) ? intType.class : doubleType.class;
    }
    return objectType.class;
}

var o = {
    a: 1,
    b: 2.5,
    c: 0x10000000000,
    d: true
};

function C() {
    this.a = 1;
    this.b = 2.5;
    this.c = 0x10000000000;
    this.d = true;
}

var a = 1;
var b = 2.5;
var c = 0x10000000000;
var d = true;

testMap(o);
testMap(new C());
testMap(JSON.parse('{ "a": 1, "b": 2.5, "c": 1099511627776, "d": true }'));
testMap(this);
