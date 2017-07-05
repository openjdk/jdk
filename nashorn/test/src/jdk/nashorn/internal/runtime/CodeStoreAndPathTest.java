/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.FileSystems;
import javax.script.ScriptException;
import org.testng.annotations.Test;
import javax.script.ScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;

/**
 * @test
 * @bug 8039185 8039403
 * @summary  Test for persistent code cache and path handling
 * @run testng jdk.nashorn.internal.runtime.CodeStoreAndPathTest
 */

public class CodeStoreAndPathTest {

    final String code1 = "var code1; var x = 'Hello Script'; var x1 = 'Hello Script'; "
                + "var x2 = 'Hello Script'; var x3 = 'Hello Script'; "
                + "var x4 = 'Hello Script'; var x5 = 'Hello Script';"
                + "var x6 = 'Hello Script'; var x7 = 'Hello Script'; "
                + "var x8 = 'Hello Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';"
                + "function f() {x ='Bye Script'; x1 ='Bye Script'; x2='Bye Script';"
                + "x3='Bye Script'; x4='Bye Script'; x5='Bye Script'; x6='Bye Script';"
                + "x7='Bye Script'; x8='Bye Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';}"
                + "function g() {x ='Bye Script'; x1 ='Bye Script'; x2='Bye Script';"
                + "x3='Bye Script'; x4='Bye Script'; x5='Bye Script'; x6='Bye Script';"
                + "x7='Bye Script'; x8='Bye Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';}"
                + "function h() {x ='Bye Script'; x1 ='Bye Script'; x2='Bye Script';"
                + "x3='Bye Script'; x4='Bye Script'; x5='Bye Script'; x6='Bye Script';"
                + "x7='Bye Script'; x8='Bye Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';}"
                + "function i() {x ='Bye Script'; x1 ='Bye Script'; x2='Bye Script';"
                + "x3='Bye Script'; x4='Bye Script'; x5='Bye Script'; x6='Bye Script';"
                + "x7='Bye Script'; x8='Bye Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';}";
    final String code2 = "var code2; var x = 'Hello Script'; var x1 = 'Hello Script'; "
                + "var x2 = 'Hello Script'; var x3 = 'Hello Script'; "
                + "var x4 = 'Hello Script'; var x5 = 'Hello Script';"
                + "var x6 = 'Hello Script'; var x7 = 'Hello Script'; "
                + "var x8 = 'Hello Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';"
                + "function f() {x ='Bye Script'; x1 ='Bye Script'; x2='Bye Script';"
                + "x3='Bye Script'; x4='Bye Script'; x5='Bye Script'; x6='Bye Script';"
                + "x7='Bye Script'; x8='Bye Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';}"
                + "function g() {x ='Bye Script'; x1 ='Bye Script'; x2='Bye Script';"
                + "x3='Bye Script'; x4='Bye Script'; x5='Bye Script'; x6='Bye Script';"
                + "x7='Bye Script'; x8='Bye Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';}"
                + "function h() {x ='Bye Script'; x1 ='Bye Script'; x2='Bye Script';"
                + "x3='Bye Script'; x4='Bye Script'; x5='Bye Script'; x6='Bye Script';"
                + "x7='Bye Script'; x8='Bye Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';}"
                + "function i() {x ='Bye Script'; x1 ='Bye Script'; x2='Bye Script';"
                + "x3='Bye Script'; x4='Bye Script'; x5='Bye Script'; x6='Bye Script';"
                + "x7='Bye Script'; x8='Bye Script'; var x9 = 'Hello Script'; "
                + "var x10 = 'Hello Script';}";
    // Script size < Default minimum size for storing a compiled script class
    final String code3 = "var code3; var x = 'Hello Script'; var x1 = 'Hello Script'; ";
    final String codeCache = "build/nashorn_code_cache";
    final String oldUserDir = System.getProperty("user.dir");

    public void checkCompiledScripts(DirectoryStream<Path> stream, int numberOfScripts) throws IOException {
        for (Path file : stream) {
            numberOfScripts--;
        }
        stream.close();
        assertEquals(numberOfScripts,0);
    }

    @Test
    public void pathHandlingTest() throws ScriptException, IOException {
        System.setProperty("nashorn.persistent.code.cache", codeCache);
        String[] options = new String[]{"--persistent-code-cache"};
        NashornScriptEngineFactory fac = new NashornScriptEngineFactory();
        ScriptEngine e = fac.getScriptEngine(options);
        Path expectedCodeCachePath = FileSystems.getDefault().getPath(oldUserDir + File.separator + codeCache);
        Path actualCodeCachePath = FileSystems.getDefault().getPath(System.getProperty(
                            "nashorn.persistent.code.cache")).toAbsolutePath();
        // Check that nashorn code cache is created in current working directory
        assertEquals(actualCodeCachePath, expectedCodeCachePath);
        // Check that code cache dir exists and it's not empty
        File file = new File(actualCodeCachePath.toUri());
        assertFalse(!file.isDirectory(), "No code cache directory was created!");
        assertFalse(file.list().length == 0, "Code cache directory is empty!");
    }

    @Test
    public void changeUserDirTest() throws ScriptException, IOException {
        System.setProperty("nashorn.persistent.code.cache", codeCache);
        String[] options = new String[]{"--persistent-code-cache"};
        NashornScriptEngineFactory fac = new NashornScriptEngineFactory();
        ScriptEngine e = fac.getScriptEngine(options);
        Path codeCachePath = FileSystems.getDefault().getPath(System.getProperty(
                            "nashorn.persistent.code.cache")).toAbsolutePath();
        String newUserDir = "build/newUserDir";
        // Now changing current working directory
        System.setProperty("user.dir", System.getProperty("user.dir") + File.separator + newUserDir);
        // Check that a new compiled script is stored in exisitng code cache
        e.eval(code1);
        DirectoryStream<Path> stream = Files.newDirectoryStream(codeCachePath);
        // Already one compiled script has been stored in the cache during initialization
        checkCompiledScripts(stream, 2);
        // Setting to default current working dir
        System.setProperty("user.dir", oldUserDir);
    }

    @Test
    public void codeCacheTest() throws ScriptException, IOException {
        System.setProperty("nashorn.persistent.code.cache", codeCache);
        String[] options = new String[]{"--persistent-code-cache"};
        NashornScriptEngineFactory fac = new NashornScriptEngineFactory();
        ScriptEngine e = fac.getScriptEngine(options);
        Path codeCachePath = FileSystems.getDefault().getPath(System.getProperty(
                            "nashorn.persistent.code.cache")).toAbsolutePath();
        e.eval(code1);
        e.eval(code2);
        e.eval(code3);// less than minimum size for storing
        // Already one compiled script has been stored in the cache during initialization
        // adding code1 and code2.
        DirectoryStream<Path> stream = Files.newDirectoryStream(codeCachePath);
        checkCompiledScripts(stream, 3);
    }
}
