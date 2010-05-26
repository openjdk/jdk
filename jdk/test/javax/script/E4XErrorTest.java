/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6346734 6705893
 * @summary We do *not* support E4X (ECMAScript for XML) in our
 * implementation. We want to throw error on XML literals
 * as early as possible rather than at "runtime" - i.e., when
 * engine looks for "XML" constructor.
 */

import javax.script.*;
import java.util.Locale;

public class E4XErrorTest {

        public static void main(String[] args) throws Exception {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine jsengine = Helper.getJsEngine(manager);
            if (jsengine == null) {
                System.out.println("Warning: No js engine found; test vacuously passes.");
                return;
            }

            // The test below depends on the error message content
            // that is loaded from resource bundles.  So, we force
            // English Locale to compare correct value..
            Locale.setDefault(Locale.US);

            try {
                jsengine.eval("var v = <html></html>;");
            } catch (ScriptException se) {
                String msg = se.getMessage();
                if (msg.indexOf("syntax error") == -1) {
                    throw new RuntimeException("syntax error expected, got " +
                                       msg);
                }
                return;
            }
            // should not reach here.. exception should have been thrown.
            throw new RuntimeException("Huh! E4X is supported??");
        }
}
