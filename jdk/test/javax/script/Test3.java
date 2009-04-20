/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6249843 6705893
 * @summary Test engine and global scopes
 */

import javax.script.*;
import java.io.*;
import java.util.*;

public class Test3 {
        public static void main(String[] args) throws Exception {
            System.out.println("\nTest3\n");
            final Reader reader = new FileReader(
                new File(System.getProperty("test.src", "."), "Test3.js"));
            ScriptEngineManager m = new ScriptEngineManager();
            final ScriptEngine engine = Helper.getJsEngine(m);
            if (engine == null) {
                System.out.println("Warning: No js engine found; test vacuously passes.");
                return;
            }
            Bindings en = new SimpleBindings();
            engine.setBindings(en, ScriptContext.ENGINE_SCOPE);
            en.put("key", "engine value");
            Bindings gn = new SimpleBindings();
            engine.setBindings(gn, ScriptContext.GLOBAL_SCOPE);
            gn.put("key", "global value");
            engine.eval(reader);
        }
}
