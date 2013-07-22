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
 * JDK-8020324: Implement Object.bindProperties(target, source) for beans
 *
 * @test
 * @run
 */

var PropertyBind = Java.type("jdk.nashorn.test.models.PropertyBind")
var bean = new PropertyBind

var obj1 = {}
Object.bindProperties(obj1, bean)

printBanner("Two-way read-write instance field")
printEval("obj1.publicInt = 13")
printEval("bean.publicInt")
printEval("bean.publicInt = 15")
printEval("obj1.publicInt")

printBanner("Read only public instance field")
printEval("obj1.publicFinalInt")
printEval("obj1.publicFinalInt = 16")
printEval("obj1.publicFinalInt")
printEval("bean.publicFinalInt")

printBanner("Two-way read-write instance property")
printEval("obj1.readWrite = 17")
printEval("bean.readWrite")
printEval("bean.readWrite = 18")
printEval("obj1.readWrite")
printEval("obj1.getReadWrite()")
printEval("obj1.setReadWrite(19)")
printEval("obj1.readWrite")
printEval("bean.readWrite")

printBanner("Read only instance property")
printEval("obj1.readOnly")
printEval("obj1.readOnly = 20")
printEval("obj1.readOnly")
printEval("obj1.getReadOnly()")
printEval("bean.getReadOnly()")

printBanner("Write only instance property")
printEval("obj1.writeOnly = 21")
printEval("obj1.writeOnly")
printEval("bean.writeOnly")
printEval("bean.peekWriteOnly()")

var obj2 = {}
Object.bindProperties(obj2, PropertyBind)

printBanner("Two-way read-write public static field")
printEval("obj2.publicStaticInt = 22")
printEval("PropertyBind.publicStaticInt")
printEval("PropertyBind.publicStaticInt = 23")
printEval("obj2.publicStaticInt")

printBanner("Read only public static field")
printEval("obj2.publicStaticFinalInt")
printEval("obj2.publicStaticFinalInt = 24")
printEval("obj2.publicStaticFinalInt")
printEval("PropertyBind.publicStaticFinalInt")

printBanner("Two-way read-write static property")
printEval("obj2.staticReadWrite = 25")
printEval("PropertyBind.staticReadWrite")
printEval("PropertyBind.staticReadWrite = 26")
printEval("obj2.staticReadWrite")
printEval("obj2.getStaticReadWrite()")
printEval("obj2.setStaticReadWrite(27)")
printEval("obj2.staticReadWrite")
printEval("PropertyBind.staticReadWrite")

printBanner("Read only static property")
printEval("obj2.staticReadOnly")
printEval("obj2.staticReadOnly = 28")
printEval("obj2.staticReadOnly")
printEval("obj2.getStaticReadOnly()")
printEval("PropertyBind.getStaticReadOnly()")

printBanner("Write only static property")
printEval("obj2.staticWriteOnly = 29")
printEval("obj2.staticWriteOnly")
printEval("PropertyBind.staticWriteOnly")
printEval("PropertyBind.peekStaticWriteOnly()")

printBanner("Sanity check to ensure property values remained what they were")
printEval("obj1.publicInt")
printEval("bean.publicInt")
printEval("obj1.publicFinalInt")
printEval("bean.publicFinalInt")
printEval("obj1.readWrite")
printEval("bean.readWrite")
printEval("obj1.readOnly")
printEval("bean.readOnly")
printEval("bean.peekWriteOnly()")

printEval("obj2.publicStaticInt")
printEval("PropertyBind.publicStaticInt")
printEval("obj2.publicStaticFinalInt")
printEval("PropertyBind.publicStaticFinalInt")
printEval("obj2.staticReadWrite")
printEval("PropertyBind.staticReadWrite")
printEval("obj2.staticReadOnly")
printEval("PropertyBind.staticReadOnly")
printEval("PropertyBind.peekStaticWriteOnly()")


function printEval(s) {
    print(s + ": " + eval(s))
}

function printBanner(s) {
    print()
    print("==== " + s + " ====")
}
