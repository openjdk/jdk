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

// Usage: jjs disassemble.js -- <.class-file-path>

// Simple .class disassembler that uses bundled ObjectWeb ASM
// classes in jdk8. WARNING: Bundled ObjectWeb ASM classes are
// not part of official jdk8 API. It can be changed/removed 
// without notice. So, this script is brittle by design!

// This example demonstrates passing arguments to script
// from jjs command line, nio and ASM usage.

// classes used
var FileSystems = Java.type("java.nio.file.FileSystems");
var Files = Java.type("java.nio.file.Files");
var System = Java.type("java.lang.System");
var PrintWriter = Java.type("java.io.PrintWriter");

// WARNING: uses non-API classes of jdk8!
var ClassReader = Java.type("jdk.internal.org.objectweb.asm.ClassReader");
var TraceClassVisitor = Java.type("jdk.internal.org.objectweb.asm.util.TraceClassVisitor");

// convert file name to Path instance
function path(file) {
    return FileSystems.default.getPath(file);
}

// read all file content as a byte[]
function readAllBytes(file) {
    return Files.readAllBytes(path(file));
}

// disassemble .class byte[] and prints output to stdout
function disassemble(bytecode) {
    var pw = new PrintWriter(System.out);
    new ClassReader(bytecode).accept(new TraceClassVisitor(pw), 0);
}

// check for command line arg (for .class file name)
if (arguments.length == 0 || !arguments[0].endsWith('.class')) {
    print("Usage: jjs disassemble -- <.class file>");
    exit(1);
}

// disassemble the given .class file
disassemble(readAllBytes(arguments[0]));
