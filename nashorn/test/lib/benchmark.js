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
 * A simple benchmark module that can be loaded
 * in order to time how many times a method executes
 * in a fixed number of seconds
 */

var benchmark = function(method, timeInMillis, args) {
    var hz,
    period,
    startTime = new Date,
    runs = 0;
    do {
	method.apply(args);
	runs++;
	totalTime = new Date - startTime;
    } while (totalTime < timeInMillis);
    
    // convert ms to seconds
    totalTime /= 1000;
    
    // period → how long per operation
    period = totalTime / runs;

    // hz → the number of operations per second
    hz = 1 / period;

    return hz;
};

