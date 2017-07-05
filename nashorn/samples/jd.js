#// Usage: jjs -cp <asmtools.jar> jd.js -- <classname> [jdis|jdec]

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

// javap-like disassembler/decoder tool that disassembles/decodes 
// java classes but with OpenJDK AsmTools disassembler/decoder syntax.
// You need to build asmtool.jar from OpenJDK codetools project
// specify it with -cp option.

// See also https://wiki.openjdk.java.net/display/CodeTools/AsmTools

function usage() {
    print("Usage: jjs -cp <asmtools.jar> jd.js -- <classname> [jdis|jdec]");
    exit(1);
}

if (arguments.length == 0) {
    usage();
}

// argument handling
// convert to internal class name
var className = arguments[0].replaceAll('\\.', '/');
var tool;
if (arguments.length > 1) {
    tool = arguments[1];
    switch (tool) {
        case 'jdis':
        case 'jdec':
            break;
        default:
            usage();
    }
} else {
    tool = "jdis"; // default tool
}

// Java classes used
var AsmTools = Java.type("org.openjdk.asmtools.Main");
var Files = Java.type("java.nio.file.Files");
var StandardCopyOption = Java.type("java.nio.file.StandardCopyOption");

// retrive input stream for .class bytes
var cl = AsmTools.class.classLoader;
var res = cl.getResource(className + ".class");

if (res) {
    var is = res.openStream();
    var tmpPath;
    try {
        // copy the content of the .class to a temp file
        tmpPath = Files.createTempFile("asmtools-", ".class");
        // mark as delete-on-exit
        tmpPath.toFile().deleteOnExit();
        Files.copy(is, tmpPath, [ StandardCopyOption.REPLACE_EXISTING ]);
    } finally {
        is.close();
    } 

    // invoke asmtools Main
    AsmTools.main([ tool, tmpPath.toString() ]);
} else {
    print("no such class: " + arguments[0]);
    exit(1);
}
