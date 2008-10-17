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
 * @bug 6346729 6705893
 * @summary Create JavaScript engine and check language and engine version
 */

import javax.script.*;
import java.io.*;

public class VersionTest  {

        private static final String JS_LANG_VERSION = "1.6";
        private static final String JS_ENGINE_VERSION = "1.6 release 2";

        public static void main(String[] args) throws Exception {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine jsengine = Helper.getJsEngine(manager);
            if (jsengine == null) {
                System.out.println("Warning: No js engine found; test vacuously passes.");
                return;
            }
            String langVersion = jsengine.getFactory().getLanguageVersion();
            if (! langVersion.equals(JS_LANG_VERSION)) {
                throw new RuntimeException("Expected JavaScript version is " +
                            JS_LANG_VERSION);
            }
            String engineVersion = jsengine.getFactory().getEngineVersion();
            if (! engineVersion.equals(JS_ENGINE_VERSION)) {
                throw new RuntimeException("Expected Rhino version is " +
                            JS_ENGINE_VERSION);
            }
        }
}
