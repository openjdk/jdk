/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * This is a simple shell tool in JavaScript.
 *
 * Runs any operating system command using Java "exec". When "eval" command is
 * used, evaluates argument(s) as JavaScript code.
 */

var imports = new JavaImporter(java.io, java.lang, java.util);

function prompt() {
    java.lang.System.out.print(">");
}

with (imports) {
    var reader = new BufferedReader(new InputStreamReader(System["in"]));
    var line = null;
    prompt();
    while ((line = reader.readLine()) != null) {
        if (line != "") {
            var args = line.split(" ");
            try {
                if (args[0] == "eval") {
                    var code = line.substring("eval".length);
                    var res = eval(code);
                    if (res != undefined) {
                        print(res);
                    }
                } else {
                    var argList = new ArrayList();
                    for (i in args) { argList.add(args[i]); }                
                    var procBuilder = new ProcessBuilder(argList);
                    procBuilder.redirectErrorStream();
                    var proc = procBuilder.start();
                    var out = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    var line = null;
                    while ((line = out.readLine()) != null) {
                        System.out.println(line);
                    }
                    proc.waitFor();
                }
            } catch (e) {
                print(e);
            }
        }
        prompt();
    }
}
