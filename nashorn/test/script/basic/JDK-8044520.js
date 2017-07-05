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
 * JDK-8044520: Nashorn cannot execute node.js's express module
 *
 * @test
 * @run
 */

function checkNullProto() {
    var obj = {};
    obj.__proto__ = null;
    var proto = Object.getPrototypeOf(obj);
    if (typeof proto != 'object' || proto !== null) {
        fail("__proto__ can't be set to null!");
    }
}

checkNullProto();

function checkSetProto(proto) {
    var obj = {};
    obj.__proto__ = proto;
    if (Object.getPrototypeOf(obj) !== Object.prototype) {
        fail("obj.__proto__ set not ignored for " + proto);
    }
}

checkSetProto(undefined);
checkSetProto(42);
checkSetProto(false);
checkSetProto("hello");

function checkLiteralSetProto(proto) {
    var obj = { __proto__: proto };
    if (obj.__proto__ !== Object.prototype) {
        fail("object literal _proto__ set not ignored for " + proto);
    }
}

checkLiteralSetProto(undefined);
checkLiteralSetProto(34);
checkLiteralSetProto(true);
checkLiteralSetProto("world");

function checkNullProtoFromLiteral() {
    var obj = { __proto__: null };
    var proto = Object.getPrototypeOf(obj);
    if (typeof proto != 'object' || proto !== null) {
        fail("__proto__ can't be set to null!");
    }
}

checkNullProtoFromLiteral();

function checkSetPrototypeOf(proto) {
    try {
        Object.setPrototypeOf({}, proto);
        fail("should have thrown error for " + proto);
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("should have thrown TypeError, got " + e);
        }
    }
}

checkSetPrototypeOf(undefined);
checkSetPrototypeOf(43);
checkSetPrototypeOf(false);
checkSetPrototypeOf("nashorn");

function checkNullSetPrototypeOf() {
    var obj = { };
    Object.setPrototypeOf(obj, null);
    var proto = Object.getPrototypeOf(obj);
    if (typeof proto != 'object' || proto !== null) {
        fail("__proto__ can't be set to null!");
    }
}

checkNullSetPrototypeOf();

var desc = Object.getOwnPropertyDescriptor(Object.prototype, "__proto__");

function checkProtoGetterOnPrimitive(value) {
    // call __proto__ getter on primitive - check ToObject
    // is called on 'this' value as per draft spec
    if (desc.get.call(value) !== Object(value).__proto__) {
        fail("can't call __proto__ getter on " + value);
    }
}

checkProtoGetterOnPrimitive(32);
checkProtoGetterOnPrimitive(false);
checkProtoGetterOnPrimitive("great!");

function checkProtoSetterOnNonObjectThis(self) {
    try {
        desc.set.call(self);
        fail("should have thrown TypeError");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("should throw TypeError on non-object self, got " +e);
        }
    }
}

checkProtoSetterOnNonObjectThis(undefined);
checkProtoSetterOnNonObjectThis(null);

function checkProtoSetterReturnValue(obj, p) {
    if (typeof desc.set.call(obj, p) != "undefined") {
        fail("__proto__ setter does not return undefined: " + obj + " " + p);
    }
}

// try non-object 'this'. setter is expected to return undefined.
checkProtoSetterReturnValue(23);
checkProtoSetterReturnValue(false);
checkProtoSetterReturnValue("foo");

// set proper __proto__. Still return value is undefined.
checkProtoSetterReturnValue({}, {});
checkProtoSetterReturnValue({}, null);
