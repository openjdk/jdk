/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.Test;

/**
 * JDK-8044518: Ensure exceptions related to optimistic recompilation are not serializable
 *
 * @test
 * @run testng jdk.nashorn.internal.runtime.ExceptionsNotSerializable
 */
@SuppressWarnings("javadoc")
public class ExceptionsNotSerializable {
    @Test
    public void rewriteExceptionNotSerializable() throws ScriptException {
        // NOTE: we must create a RewriteException in a context of a Nashorn engine, as it uses Global.newIntance()
        // internally.
        final ScriptEngine e = new NashornScriptEngineFactory().getScriptEngine();
        e.put("f", new Runnable() {
            @Override
            public void run() {
                tryToSerialize(RewriteException.create(null, new Object[0], new String[0]));
            }
        });
        e.eval("f()");
    }

    @Test
    public void unwarrantedOptimismExceptionNotSerializable() {
        tryToSerialize(new UnwarrantedOptimismException(new Double(1.0), 128));
    }

    private static void tryToSerialize(final Object obj) {
        try {
            new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(obj);
            fail();
        } catch (final NotSerializableException e) {
            assertEquals(e.getMessage(), obj.getClass().getName());
        } catch (final IOException e) {
            fail("", e);
        }

    }
}
