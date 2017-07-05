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

/*
 * Simple unzip tool using #nashorn and #java
 * zip fs file system interface.
 */
 
if (arguments.length == 0) {
    print("Usage: jjs zipfs.js -- <.zip/.jar file> [out dir]");
    exit(1);
}
 
var File = Java.type("java.io.File");
// output directory where zip is extracted
var outDir = arguments[1];
if (!outDir) {
    outDir = ".";
} else {
    if (! new File(outDir).isDirectory()) {
        print(outDir + " directory does not exist!");
        exit(1);
    }
}
 
var Files = Java.type("java.nio.file.Files");
var FileSystems = Java.type("java.nio.file.FileSystems");
var Paths = Java.type("java.nio.file.Paths");
 
var zipfile = Paths.get(arguments[0])
var fs = FileSystems.newFileSystem(zipfile, null);
var root = fs.rootDirectories[0];
 
// walk root and handle each Path
Files.walk(root).forEach(
    function(p) {
        var outPath = outDir +
            p.toString().replace('/', File.separatorChar);
        print(outPath);
        if (Files.isDirectory(p)) {
            // create directories as needed
            new File(outPath).mkdirs();
        } else {
            // copy a 'file' resource
            Files.copy(p, new File(outPath).toPath());
        }
    }
);
 
// done
fs.close(); 
