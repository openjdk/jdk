#// Usage: jjs -scripting -cp . jsobject.js

/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

// This sample demonstrats how to expose a
// script friendly object from your java code
// by implementing jdk.nashorn.api.scripting.JSObject

// compile the java program
`javac BufferArray.java`;

// print error, if any and exit
if ($ERR != '') {
    print($ERR);
    exit($EXIT);
}

// create BufferArray
var BufferArray = Java.type("BufferArray");
var bb = new BufferArray(10);

// 'magic' methods called to retrieve set/get
// properties on BufferArray instance
var len = bb.length;
print("bb.length = " + len)
for (var i = 0; i < len; i++) {
    bb[i] = i*i;
}

for (var i = 0; i < len; i++) {
    print(bb[i]);
}

// get underlying buffer by calling a method
// on BufferArray magic object

// 'buf' is a function member
print(typeof bb.buf);
var buf = bb.buf();

// use retrieved underlying nio buffer
var cap = buf.capacity();
print("buf.capacity() = " + cap);
for (var i = 0; i < cap; i++) {
   print(buf.get(i));
}
