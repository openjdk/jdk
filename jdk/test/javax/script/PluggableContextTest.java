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
 * @bug 6398614 6705893
 * @summary Create a user defined ScriptContext and check
 * that script can access variables from non-standard scopes
 */

import javax.script.*;

public class PluggableContextTest {
    public static void main(String[] args) throws Exception {
        ScriptEngineManager m = new ScriptEngineManager();
        ScriptContext ctx = new MyContext();
        ctx.setAttribute("x", "hello", MyContext.APP_SCOPE);
        ScriptEngine e = Helper.getJsEngine(m);
        if (e == null) {
            System.out.println("Warning: No js engine found; test vacuously passes.");
            return;
        }
        // the following reference to 'x' throws exception
        // if APP_SCOPE is not searched.
        e.eval("x", ctx);
    }
}
