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
 * NASHORN-406 : Property descriptor properties should be enumerable 
 *
 * @test
 * @run
 */

var obj = {
    foo : 10,
    get bar() { return 32; },
    set bar(x) {},
};

function checkData() {
   var desc = Object.getOwnPropertyDescriptor(obj, "foo");
   var enumSeen = false, writeSeen = false, 
       configSeen = false, valueSeen = false;
   for (i in desc) {
       switch(i) {
           case 'enumerable':
               enumSeen = true; break;
           case 'writable':
               writeSeen = true; break;
           case 'configurable':
               configSeen = true; break;
           case 'value':
               valueSeen = true; break;
       }
   }
   
   return enumSeen && writeSeen && configSeen && valueSeen;
}

if (!checkData()) {
    fail("data descriptor check failed");
}

function checkAccessor() {
   var desc = Object.getOwnPropertyDescriptor(obj, "bar");
   var enumSeen = false, getterSeen = false, 
       configSeen = false, setterSeen = false;
   for (i in desc) {
       switch(i) {
           case 'enumerable':
               enumSeen = true; break;
           case 'configurable':
               configSeen = true; break;
           case 'get':
               getterSeen = true; break;
           case 'set':
               setterSeen = true; break;
        }
   }

   return enumSeen && configSeen && getterSeen && setterSeen;
}

if (!checkAccessor()) {
    fail("accessor descriptor check failed");
}
