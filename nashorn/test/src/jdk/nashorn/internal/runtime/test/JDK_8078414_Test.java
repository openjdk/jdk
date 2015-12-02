/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import javax.script.Bindings;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.objects.NativeArray;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.options.Options;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @test
 * @bug 8078414
 * @summary Test that arbitrary classes can't be converted to mirror's superclasses/interfaces.
 * @run testng jdk.nashorn.internal.runtime.test.JDK_8078414_Test
 */
public class JDK_8078414_Test {
    private static Context cx;
    private static Global oldGlobal;

    @BeforeClass
    public static void beforeClass() {
        // We must have a Context for the DynamicLinker that Bootstrap.getLinkerServices() will use
        oldGlobal = Context.getGlobal();
        cx = new Context(new Options(""), new ErrorManager(), null);
        Context.setGlobal(cx.createGlobal());
    }

    @AfterClass
    public static void afterClass() {
        Context.setGlobal(oldGlobal);
        oldGlobal = null;
        cx = null;
    }

    @Test
    public void testCanNotConvertArbitraryClassToMirror() {
        assertCanNotConvert(Double.class, Map.class);
        assertCanNotConvert(Double.class, Bindings.class);
        assertCanNotConvert(Double.class, JSObject.class);
        assertCanNotConvert(Double.class, ScriptObjectMirror.class);
    }

    @Test
    public void testCanConvertObjectToMirror() {
        assertCanConvertToMirror(Object.class);
    }

    @Test
    public void testCanConvertScriptObjectToMirror() {
        assertCanConvertToMirror(ScriptObject.class);
    }

    @Test
    public void testCanConvertScriptObjectSubclassToMirror() {
        assertCanConvertToMirror(NativeArray.class);
    }

    @Test
    public void testCanConvertArbitraryInterfaceToMirror() {
        // We allow arbitrary interface classes, depending on what implements them, to end up being
        // convertible to ScriptObjectMirror, as an implementation can theoretically pass an
        // "instanceof ScriptObject" guard.
        assertCanConvertToMirror(TestInterface.class);
    }

    public static interface TestInterface {
    }

    private static boolean canConvert(final Class<?> from, final Class<?> to) {
        return Bootstrap.getLinkerServices().canConvert(from, to);
    }

    private static void assertCanConvert(final Class<?> from, final Class<?> to) {
        assertTrue(canConvert(from, to));
    }

    private static void assertCanNotConvert(final Class<?> from, final Class<?> to) {
        assertFalse(canConvert(from, to));
    }

    private static void assertCanConvertToMirror(final Class<?> from) {
        assertCanConvert(from, Map.class);
        assertCanConvert(from, Bindings.class);
        assertCanConvert(from, JSObject.class);
        assertCanConvert(from, ScriptObjectMirror.class);
    }
}
