/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 * 
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 * 
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// create Java String array of 5 elements
var StringArray = Java.type("java.lang.String[]");
var a = new StringArray(5);

// Accessing elements and length access is by usual Java syntax
a[0] = "scripting is great!";
print(a.length);
print(a[0]);

// convert a script array to Java array
var anArray = [1, "13", false];
var javaIntArray = Java.to(anArray, "int[]");
print(javaIntArray[0]);// prints 1
print(javaIntArray[1]); // prints 13, as string "13" was converted to number 13 as per ECMAScript ToNumber conversion
print(javaIntArray[2]);// prints 0, as boolean false was converted to number 0 as per ECMAScript ToNumber conversion

// convert a Java array to a JavaScript array
var File = Java.type("java.io.File");
var listCurDir = new File(".").listFiles();
var jsList = Java.from(listCurDir);
print(jsList);
