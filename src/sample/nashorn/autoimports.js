# autoimports script requires -scripting mode

/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * It is tedious to import Java classes used in a script. Sometimes it is easier
 * use simple names of java classes and have a script auto import Java classes. 
 * You can load this script at the start of an interactive jjs session or at the
 * start of your script. This script defines a __noSuchProperty__ hook to auto 
 * import Java classes as needed and when they are referred to for the first time
 * in your script. You can also call the "autoimports" function to print script 
 * statements that you need to use in your script, i.e., have the function generate
 * a script to import Java classes used by your script so far. After running your
 * script, you can call autoimports to get the exact Java imports you need and replace
 * the autoimports load with the generated import statements (to avoid costly init of
 * the autoimports script).
 *
 * Example usage of autoimports.js in interactive mode:
 *
 *     jjs -scripting autoimports.js -
 *     jjs> Vector
 *     jjs> [JavaClass java.util.Vector]
 */

(function() {
    var ArrayList = Java.type("java.util.ArrayList");
    var HashMap = Java.type("java.util.HashMap");
    var Files = Java.type("java.nio.file.Files");
    var FileSystems = Java.type("java.nio.file.FileSystems");
    var URI = Java.type("java.net.URI");

    // initialize a class to package map by iterating all
    // classes available in the system by walking through "jrt fs"
    var fs = FileSystems.getFileSystem(URI.create("jrt:/"));
    var root = fs.getPath('/');

    var clsToPkg = new HashMap();

    function addToClsToPkg(c, p) {
        if (clsToPkg.containsKey(c)) {
            var val = clsToPkg.get(c);
            if (val instanceof ArrayList) {
                val.add(p);
            } else {
                var al = new ArrayList();
                al.add(val);
                al.add(p);
                clsToPkg.put(c, al);
            }
        } else {
            clsToPkg.put(c, p);
        }
    }

    // handle collision and allow user to choose package
    function getPkgOfCls(c) {
        var val = clsToPkg.get(c);
        if (val instanceof ArrayList) {
            var count = 1;
            print("Multiple matches for " + c + ", choose package:");
            for each (var v in val) {
                print(count + ". " + v);
                count++;
            }
            var choice = parseInt(readLine());
            if (isNaN(choice) || choice < 1 || choice > val.size()) {
                print("invalid choice: " + choice);
                return undefined;
            }
            return val.get(choice - 1);
        } else {
            return val;
        }
    }

    Files.walk(root).forEach(function(p) {
        if (Files.isRegularFile(p)) {
            var str = p.toString();
            if (str.endsWith(".class") && !str.endsWith("module-info.class")) {
                str = str.substring("/modules/".length);
                var idx = str.indexOf('/');
                if (idx != -1) {
                    str = str.substring(idx + 1);
                    if (str.startsWith("java") ||
                        str.startsWith("javax") ||
                        str.startsWith("org")) {
                        var lastIdx = str.lastIndexOf('/');
                        if (lastIdx != -1) {
                            var pkg = str.substring(0, lastIdx).replaceAll('/', '.');
                            var cls = str.substring(lastIdx + 1, str.lastIndexOf(".class"));
                            addToClsToPkg(cls, pkg);
                        }
                    }
                }
            }
        } 
    });

    var imports = new ArrayList();
    var global = this;
    var oldNoSuchProp = global.__noSuchProperty__;
    this.__noSuchProperty__ = function(name) {
        'use strict';

        if (clsToPkg.containsKey(name)) {
            var pkg = getPkgOfCls(name);
            if (pkg) {
                var clsName = pkg + "." + name;
                imports.add("var " + name + " = Java.type('" + clsName + "');");
                return global[name] = Java.type(clsName);
            }
        } else if (typeof oldNoSuchProp == 'function') {
            return oldNoSuchProp.call(this, name);
        }

        if (typeof this == 'undefined') {
            throw new ReferenceError(name);
        } else {
            return undefined;
        }
    }

    this.autoimports = function() {
        for each (var im in imports) {
            print(im);
        }
    }
})();
