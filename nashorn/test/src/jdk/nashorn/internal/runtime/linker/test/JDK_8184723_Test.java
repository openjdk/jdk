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

package jdk.nashorn.internal.runtime.linker.test;

import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

/**
 * @test
 * @bug 8184723
 * @summary jdk.nashorn.internal.runtime.linker.JSObjectLinker.callToApply erroneously asserts given arguments
 * @modules jdk.scripting.nashorn/jdk.nashorn.internal.runtime.linker
 * @run main/othervm -ea jdk.nashorn.internal.runtime.linker.test.JDK_8184723_Test
 */

public class JDK_8184723_Test {
    public static void main(String args[]) throws Exception {
        NashornScriptEngine engine = (NashornScriptEngine) new NashornScriptEngineFactory().getScriptEngine();
        AbstractJSObject obj = new AbstractJSObject() {
            @Override
            public Object getMember(String name) {
                return this;
            }

            @Override
            public Object call(Object thiz, Object... args) {
                return thiz;
            }

        };

        engine.put("a", obj);
        engine.eval("function b(){ a.apply(null,arguments);};b();");
    }
}
