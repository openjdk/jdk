/*
 * Copyright (c) 2006, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6474943 6705893
 * @summary Test that Rhino exception messages are
 * available from ScriptException.
 */

import java.io.*;
import javax.script.*;

public class RhinoExceptionTest {
    private static final String ERROR_MSG = "error from JavaScript";

    public static void main(String[] args) throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptEngine engine = Helper.getJsEngine(m);
        if (engine == null) {
            System.out.println("Warning: No js engine found; test vacuously passes.");
            return;
        }
        engine.put("msg", ERROR_MSG);
        try {
            engine.eval("throw new Error(msg);");
        } catch (ScriptException exp) {
            if (exp.getMessage().indexOf(ERROR_MSG) == -1) {
                throw exp;
            }
        }
        try {
            engine.eval("throw (msg);");
        } catch (ScriptException exp) {
            if (exp.getMessage().indexOf(ERROR_MSG) == -1) {
                throw exp;
            }
        }
        try {
            CompiledScript scr = ((Compilable)engine).compile("throw new Error(msg);");
            scr.eval();
        } catch (ScriptException exp) {
            if (exp.getMessage().indexOf(ERROR_MSG) == -1) {
                throw exp;
            }
        }
        try {
            CompiledScript scr = ((Compilable)engine).compile("throw msg;");
            scr.eval();
        } catch (ScriptException exp) {
            if (exp.getMessage().indexOf(ERROR_MSG) == -1) {
                throw exp;
            }
        }
    }
}
