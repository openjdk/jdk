# Usage: jjs -cp buffer_indexing_linker.jar buffer_index.js

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

// This script depends on buffer indexing dynalink linker. Without that
// linker in classpath, this script will fail to run properly.

function str(buf) {
    var s = ''
    for (var i = 0; i < buf.length; i++)
        s += buf[i] + ","
    return s
}

var ByteBuffer = Java.type("java.nio.ByteBuffer")
var bb = ByteBuffer.allocate(10)
for (var i = 0; i < bb.length; i++)
    bb[i] = i*i
print(str(bb))

var CharBuffer = Java.type("java.nio.CharBuffer")
var cb = CharBuffer.wrap("hello world")
print(str(cb))

var RandomAccessFile = Java.type("java.io.RandomAccessFile")
var raf = new RandomAccessFile("buffer_index.js", "r")
var chan = raf.getChannel()
var fileSize = chan.size()
var buf = ByteBuffer.allocate(fileSize)
chan.read(buf)
chan.close()

var str = ''
for (var i = 0; i < buf.length; i++)
    str += String.fromCharCode(buf[i])
print(str)
