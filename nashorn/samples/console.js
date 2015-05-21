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

/**
 * Simple Web Console-like support for Nashorn. In addition to
 * Web console object methods, this console add methods of
 * java.io.Console as well. Note:not all web console methods are 
 * implemented but useful subset is implemented.
 *
 * See also: https://developer.mozilla.org/en/docs/Web/API/console
 */


if (typeof console == 'undefined') {

(function() {
    var LocalDateTime = Java.type("java.time.LocalDateTime");
    var System = Java.type("java.lang.System");
    var jconsole = System.console();

    // add a new global variable called "console"
    this.console = {
    };

    function addConsoleMethods() {
        // expose methods of java.io.Console as an extension
        var placeholder = "-*-";
        // put a placeholder for each name from java.lang.Object
        var objMethods = Object.bindProperties({}, new java.lang.Object());
        for (var m in objMethods) {
            console[m] = placeholder;
        }

        // bind only the methods of java.io.Console
        // This bind will skip java.lang.Object methods as console
        // has properties of same name.
        Object.bindProperties(console, jconsole);

        // Now, delete java.lang.Object methods
        for (var m in console) {
            if (console[m] == placeholder) {
                delete console[m];
            }
        }
    }

    addConsoleMethods();

    function consoleLog(type, msg) {
        // print type of message, then time.
        jconsole.format("%s [%s] ", type, LocalDateTime.now().toString());
        if (typeof msg == 'string') {
            jconsole.format(msg + "\n", Array.prototype.slice.call(arguments, 2));
        } else {
            // simple space separated values and newline at the end
            var arr = Array.prototype.slice.call(arguments, 1);
            jconsole.format("%s\n", arr.join(" "));
        }
    }

    console.toString = function() "[object Console]";

    // web console functions

    console.assert = function(expr) {
        if (! expr) {
            arguments[0] = "Assertion Failed:"; 
            consoleLog.apply(console, arguments);
            // now, stack trace at the end
            jconsole.format("%s\n", new Error().stack);
        }
    };

    // dummy clear to avoid error!
    console.clear = function() {};

    var counter = {
        get: function(label) {
            if (! this[label]) {
                return this[label] = 1;
            } else {
                return ++this[label];
            }
        }
    };
   
    // counter 
    console.count = function(label) {
        label = label? String(label) : "<no label>";
        jconsole.format("%s: %d\n",label, counter.get(label).intValue());
    }

    // logging
    console.error = consoleLog.bind(jconsole, "ERROR");
    console.info = consoleLog.bind(jconsole, "INFO");
    console.log = console.info;
    console.debug = console.log;
    console.warn = consoleLog.bind(jconsole, "WARNING");

    // print stack trace
    console.trace = function() {
        jconsole.format("%s\n", new Error().stack);
    };
})();

}
