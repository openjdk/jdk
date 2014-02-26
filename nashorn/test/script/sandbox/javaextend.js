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
 * @test
 * @run
 */

function model(n) {
  return Java.type("jdk.nashorn.test.models." + n)
}

// Can't extend a final class  
try {
    Java.extend(model("FinalClass"))
} catch(e) {
    print(e)
}

// Can't extend a class with no public or protected constructor
try {
    Java.extend(model("NoAccessibleConstructorClass"))
} catch(e) {
    print(e)
}

// Can't extend a non-public class
try {
    Java.extend(model("NonPublicClass"))
} catch(e) {
    print(e)
}

// Can't extend a class with explicit non-overridable finalizer
try {
    Java.extend(model("ClassWithFinalFinalizer"))
} catch(e) {
    print(e)
}

// Can't extend a class with inherited non-overridable finalizer
try {
    Java.extend(model("ClassWithInheritedFinalFinalizer"))
} catch(e) {
    print(e)
}


// Can't extend two classes
try {
    Java.extend(java.lang.Thread,java.lang.Number)
} catch(e) {
    print(e)
}

// Make sure we can implement interfaces from the unnamed package
var c = new (Java.extend(Java.type("UnnamedPackageTestCallback")))() { call: function(s) { return s + s } }
print(c.call("abcd"))

// Basic Runnable from an object
new (Java.extend(java.lang.Runnable))({ run: function() { print("run-object") } }).run()

// Basic Runnable from a function
new (Java.extend(java.lang.Runnable))(function() { print("run-fn") }).run()

// Basic Runnable from an autoconverted function
var t = new java.lang.Thread(function() { print("run-fn-autoconvert") })
t.start()
t.join()

// SAM conversion should work on overloaded methods of same name
var os = new (Java.extend(model("OverloadedSam")))(function(s1, s2) { print("overloaded-sam: " + s1 + ", " + s2) })
os.sam("x")
os.sam("x", "y")

// Test overriding of hashCode, equals, and toString
var oo = Java.extend(model("OverrideObject"))
// First, see non-overridden values
print("oo-plain-hashCode: " + (new oo({})).hashCode())
print("oo-plain-toString: " + (new oo({})).toString())
print("oo-plain-equals  : " + (new oo({})).equals({}))
// Now, override them
print("oo-overridden-hashCode: " + (new oo({ hashCode: function() { return 6 }})).hashCode())
print("oo-overridden-toString: " + (new oo({ toString: function() { return "override-object-overriden" }})).toString())
print("oo-overridden-equals  : " + (new oo({ equals: function() { return true }})).equals({}))
// Finally, test that equals and hashCode can be overridden with functions from a prototype, but toString() can't:
function Proto() {
    return this;
}
Proto.prototype = {
    toString: function() { return "this-will-never-be-seen" }, // toString only overridden when it's own property, never from prototype
    equals: function() { return true },
    hashCode: function() { return 7 }
}
print("oo-proto-overridden-hashCode: " + (new oo(new Proto())).hashCode())
print("oo-proto-overridden-toString: " + (new oo(new Proto())).toString())
print("oo-proto-overridden-equals  : " + (new oo(new Proto())).equals({}))

// Subclass a class with a protected constructor, and one that takes
// additional constructor arguments (a token). Also demonstrates how can
// you access the Java adapter instance from the script (just store it in the
// scope, in this example, "cwa") to retrieve the token later on.
var cwa = new (Java.extend(model("ConstructorWithArgument")))("cwa-token", function() { print(cwa.token) })
cwa.doSomething()

// Do the same thing with proprietary syntax and object literal
var cwa2 = new (model("ConstructorWithArgument"))("cwa2-token") { doSomething: function() { print("cwa2-" + cwa2.token ) } }
cwa2.doSomething()

// Implement two interfaces
var desertToppingAndFloorWax = new (Java.extend(model("DessertTopping"), model("FloorWax"))) {
    pourOnDessert: function() { print("Glop; IM IN UR DESSERT NOW") },
    shineUpTheFloor: function() { print("The floor sure is shining!") }
}
var dtfwDriver = new (model("DessertToppingFloorWaxDriver"))
dtfwDriver.decorateDessert(desertToppingAndFloorWax)
dtfwDriver.waxFloor(desertToppingAndFloorWax)

// Extend a class and implement two interfaces. For additional measure, put the class in between the two interfaces
var desertToppingFloorWaxAndToothpaste = new (Java.extend(model("DessertTopping"), model("Toothpaste"), model("FloorWax"))) {
    pourOnDessert: function() { print("Yum") },
    shineUpTheFloor: function() { print("Scrub, scrub, scrub") },
    applyToBrushImpl: function() { print("It's a dessert topping! It's a floor wax! It's a toothpaste!") }
}
dtfwDriver.decorateDessert(desertToppingFloorWaxAndToothpaste)
dtfwDriver.waxFloor(desertToppingFloorWaxAndToothpaste)
desertToppingFloorWaxAndToothpaste.applyToBrush();
