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

if (arguments.length == 0) {
    print("Usage: jjs ziplist -- <zip-file>");
    exit(1);
}

// list the content details of a .zip or .jar file
var file = arguments[0];

// java classes used
var Attributes = Java.type("java.util.jar.Attributes");
var FileTime = Java.type("java.nio.file.attribute.FileTime");
var JarFile = Java.type("java.util.jar.JarFile");
var ZipEntry = Java.type("java.util.zip.ZipEntry");
var ZipFile = Java.type("java.util.zip.ZipFile");

var zf = file.endsWith(".jar")? new JarFile(file) : new ZipFile(file);

var entries = zf.entries();
// make overall output a valid JSON
var zfObj = {
    name: zf.name,
    comment: zf.comment,
    size: zf.size(),
    entries: []
};

while (entries.hasMoreElements()) {
    zfObj.entries.push(entries.nextElement());
}

print(JSON.stringify(zfObj, function (key, value) {
   if (value instanceof ZipEntry) {
       return Object.bindProperties({}, value);
   } else if (value instanceof FileTime) {
       return value.toString();
   } else if (value instanceof Attributes) {
       var attrs = {};
       var itr = value.entrySet().iterator();
       while (itr.hasNext()) {
           var n = itr.next();
           attrs[n.key] = String(n.value);
       }
       return attrs;
   }

   return value;
}, ' '));

zf.close();
