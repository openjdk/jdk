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

/**
 * Nashorn project uses "final" modifier for method parameters
 * (like 'val' of Scala). This tool finds method parameters that
 * miss final modifier.
 */

// Usage: jjs -J-Djava.ext.dirs=<your_nashorn_jar_dir> find_nonfinals.js

var Class = Java.type("java.lang.Class");
var System = Java.type("java.lang.System");
var Thread = Java.type("java.lang.Thread");
var File = Java.type("java.io.File");
var JarFile = Java.type("java.util.jar.JarFile");
var Modifier = Java.type("java.lang.reflect.Modifier");

// locate nashorn.jar from java.ext.dirs
function findNashorn() {
    var paths = System.getProperty("java.ext.dirs").split(File.pathSeparator);
    for each (var p in paths) {
        var nashorn = p + File.separator + "nashorn.jar";
        if (new File(nashorn).exists()) {
            return nashorn;
        }
    }
}

// analyze a single Class and print info on non-final parameters
function analyzeClass(cls) {
    var methods = cls.getDeclaredMethods();
    for each (var method in methods) {
        var methodModifiers = method.modifiers;
        if (Modifier.isAbstract(methodModifiers) || Modifier.isNative(methodModifiers)) {
            continue;
        }
        // this requires -parameters option when compiling java sources
        var params = method.parameters;
        for each (var p in params) {
           var modifiers = p.modifiers;
           if (!Modifier.isFinal(modifiers)) {
               if (! method.name.startsWith("access$")) {
                   print(method);
                   print(" ->", p);
               }
           }
        }
    }
}

var jarFile = findNashorn();
var ctxtLoader = Thread.currentThread().contextClassLoader;

// load each class and use reflection to analyze each Class
new JarFile(jarFile).stream().forEach(
    function(entry) {
        var name = entry.name;
        if (name.endsWith(".class")) {
            var clsName = name.substring(0, name.lastIndexOf('.class'));
            clsName = clsName.replace(/\//g, '.');
            try {
                // don't initialize to avoid for possible initialization errors 
                var cls = Class.forName(clsName, false, ctxtLoader);
                analyzeClass(cls);
            } catch (e) {
                // print exception and continue analysis for other classes
                print("Failed to analyze " + clsName);
                e.printStackTrace();
            }
        }
    }
)
