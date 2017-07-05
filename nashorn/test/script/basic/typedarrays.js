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
 * typedarray test.
 *
 * @test
 * @run 
 */


var typeDefinitions = [
Int8Array, 
Uint8Array, 
Uint8ClampedArray, 
Int16Array, 
Uint16Array, 
Int32Array, 
Uint32Array, 
Float32Array, 
Float64Array, 
];

var mem1 = new ArrayBuffer(1024);
mem1.byteLength;
mem1.slice(512);
mem1.slice(512, 748);

var size = 128;
var arr = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
var arr2 = [99, 89];
var partial = [];
var all = [];

typeDefinitions.forEach(function(arrayDef) {
    var p = arrayDef.prototype;
    var sub = [];
    sub.push(new arrayDef(mem1, arrayDef.BYTES_PER_ELEMENT, 3));   
    sub.push(new arrayDef(size));
    sub.push(new arrayDef(arr));
    //push the instances, they will be reused to do instance based construction
    partial.push({
        instances:sub, 
        type:arrayDef
    });
    
    all.concat(all, sub);
    
});

partial.forEach(function(inst) {
    // build new instances with TypeArray instance as parameter.
    partial.forEach(function(other) {
        other.instances.forEach(function(otherInstance) {
            var ii = new inst.type(otherInstance);
            all.push(ii);
        });
    })
});

all.forEach(function(instance) {
    // cover instance props and functions
    var arr = Object.getOwnPropertyNames(instance);
    arr.forEach(function(p) {
        var val = instance[p];
        if(!isNaN(p)){
            val[p] = 99;
        }       
    });
        
    instance.set(instance, 0);
    instance.set(instance);
    instance.set(arr2);
    instance.subarray(5, 9);
    instance.subarray(5);
});
