/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Initialization script for shell when running in interactive mode.
 */

/**
 * Reads zero or more lines from standard input and returns concatenated string
 *
 * @param endMarker marker string that signals end of input
 * @param prompt prompt printed for each line
 */
Object.defineProperty(this, "input", {
    value: function input(endMarker, prompt) {
        if (!endMarker) {
            endMarker = "";
        }

        if (!prompt) {
            prompt = " >> ";
        }

        var imports = new JavaImporter(java.io, java.lang);
        var str = "";
        with (imports) {
            var reader = new BufferedReader(new InputStreamReader(System['in']));
            var line;
            while (true) {
                System.out.print(prompt);
                line = reader.readLine();
                if (line == null || line == endMarker) {
                    break;
                }
                str += line + "\n";
            }
        }

        return str;
    },
    enumerable: false,
    writable: true,
    configurable: true
});


/**
 * Reads zero or more lines from standard input and evaluates the concatenated
 * string as code
 *
 * @param endMarker marker string that signals end of input
 * @param prompt prompt printed for each line
 */
Object.defineProperty(this, "evalinput", {
    value: function evalinput(endMarker, prompt) {
        var code = input(endMarker, prompt);
        // make sure everything is evaluated in global scope!
        return this.eval(code);
    },
    enumerable: false,
    writable: true,
    configurable: true
});
