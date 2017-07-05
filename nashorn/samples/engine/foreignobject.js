#// Usage: jjs -scripting foreignobject.js

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

// cross nashorn engine scripting
// access script objects from other engines in natural syntax

var ScriptEngineManager = Java.type("javax.script.ScriptEngineManager");
// create manager
var manager = new ScriptEngineManager();
// create engine
var engine = manager.getEngineByName("js");

// eval code!
engine.eval(<<CODE
    var obj = {
        foo: 42,
        func: function() {
            print("func: " + this.foo);
        }
    };
CODE);

// Nashorn engine returns script objects as instance of
// the class jdk.nashorn.api.scripting.ScriptObjectMirror
// But nashorn's dynalink linker can treat these objects
// specially to support natural script syntax to access..
// In Java code, you need to use ScriptObjectMirror's 
// methods though. Once again, script world is simpler :-)

var foreignObj = engine.get("obj");
// access properties, functions of it
// with natural syntax
print(foreignObj.foo);
foreignObj.func();
print(typeof foreignObj.func);

// access engine's global
var foreignGlobal = engine.eval("this");
// create objects in engine's world from here!
print(new foreignGlobal.Object());
print(new foreignGlobal.Date());
