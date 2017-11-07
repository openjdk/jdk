/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime.test;

import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;
import javax.script.ScriptException;


/**
 * @test
 * @bug 8186807
 * @summary JSObject gets ScriptFunction when ScriptObjectMirror is expected
 * @modules jdk.scripting.nashorn/jdk.nashorn.internal.runtime
 * @run testng/othervm -Dnashorn.unstable.relink.threshold=1 jdk.nashorn.internal.runtime.test.JDK_8186807_Test
 */

public class JDK_8186807_Test {

    @Test
    public void testScript() throws ScriptException {
        NashornScriptEngine engine = (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine();
        engine.put("a", new Func());
        engine.eval("var Assert = Java.type('org.testng.Assert')\n" +
                "var fn=function() {return 2;}\n" +
                "var arr = [ a, fn, a ];\n" +
                "var result;\n" +
                "for (i in arr) {\n" +
                "    result = Function.prototype.apply.apply(arr[i],[null,[fn]]);\n" +
                "    if(i==0 || i==2)\n" +
                "        Assert.assertTrue(result==1);\n" +
                "    if(i==1)\n" +
                "        Assert.assertTrue(result==2);\n" +
                "}");
    }

    static class Func extends AbstractJSObject {
        @Override
        public boolean isFunction() {
            return true;
        }

        @Override
        public Object call(Object thiz, Object... args) {
            assertEquals(args[0].getClass(), ScriptObjectMirror.class);
            return 1;
        }
    }
}
