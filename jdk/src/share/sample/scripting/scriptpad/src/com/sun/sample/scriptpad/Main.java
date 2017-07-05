/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.sample.scriptpad;

import javax.script.*;
import java.io.*;

/**
 * This is the entry point of "Scriptpad" sample. This class creates
 * ScriptEngine and evaluates few JavaScript "files" -- which are stored
 * as resources (please refer to src/resources/*.js). Actual code for the
 * scriptpad's main functionality lives in these JavaScript files.
 */
public class Main {
    public static void main(String[] args) throws Exception {

        // create a ScriptEngineManager
        ScriptEngineManager m = new ScriptEngineManager();
        // get an instance of JavaScript script engine
        ScriptEngine engine = m.getEngineByName("js");

        // expose the current script engine as a global variable
        engine.put("engine", engine);

        // evaluate few scripts that are bundled in "resources"
        eval(engine, "conc.js");
        eval(engine, "gui.js");
        eval(engine, "scriptpad.js");
        eval(engine, "mm.js");
    }

    private static void eval(ScriptEngine engine, String name)
                            throws Exception {
        /*
         * This class is compiled into a jar file. The jar file
         * contains few scripts under /resources URL.
         */
        InputStream is = Main.class.getResourceAsStream("/resources/" + name);
        // current script file name for better error messages
        engine.put(ScriptEngine.NAME, name);
        // evaluate the script in the InputStream
        engine.eval(new InputStreamReader(is));
    }
}
