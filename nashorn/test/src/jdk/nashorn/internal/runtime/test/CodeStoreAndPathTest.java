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
package jdk.nashorn.internal.runtime.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.annotations.Test;

/**
 * @ignore Fails with jtreg, but passes with ant test run. Ignore for now.
 * @test
 * @bug 8039185 8039403
 * @summary  Test for persistent code cache and path handling
 * @run testng jdk.nashorn.internal.runtime.test.CodeStoreAndPathTest
 */
@SuppressWarnings("javadoc")
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

    private static final String[] ENGINE_OPTIONS_OPT   = new String[]{"--persistent-code-cache", "--optimistic-types=true"};
    private static final String[] ENGINE_OPTIONS_NOOPT = new String[]{"--persistent-code-cache", "--optimistic-types=false"};

    @Test
    public void pathHandlingTest() {
        System.setProperty("nashorn.persistent.code.cache", codeCache);
        final NashornScriptEngineFactory fac = new NashornScriptEngineFactory();

        fac.getScriptEngine(ENGINE_OPTIONS_NOOPT);

        final Path expectedCodeCachePath = FileSystems.getDefault().getPath(oldUserDir + File.separator + codeCache);
        final Path actualCodeCachePath = FileSystems.getDefault().getPath(System.getProperty(
                            "nashorn.persistent.code.cache")).toAbsolutePath();
        // Check that nashorn code cache is created in current working directory
        assertEquals(actualCodeCachePath, expectedCodeCachePath);
        // Check that code cache dir exists and it's not empty
        final File file = new File(actualCodeCachePath.toUri());
        assertFalse(!file.isDirectory(), "No code cache directory was created!");
        assertFalse(file.list().length == 0, "Code cache directory is empty!");
    }

    @Test
    public void changeUserDirTest() throws ScriptException, IOException {
        System.setProperty("nashorn.persistent.code.cache", codeCache);
        final NashornScriptEngineFactory fac = new NashornScriptEngineFactory();
        final ScriptEngine e = fac.getScriptEngine(ENGINE_OPTIONS_NOOPT);
        final Path codeCachePath = getCodeCachePath(false);
        final String newUserDir = "build/newUserDir";
        // Now changing current working directory
        System.setProperty("user.dir", System.getProperty("user.dir") + File.separator + newUserDir);
        try {
            // Check that a new compiled script is stored in existing code cache
            e.eval(code1);
            final DirectoryStream<Path> stream = Files.newDirectoryStream(codeCachePath);
            checkCompiledScripts(stream, 1);
            // Setting to default current working dir
        } finally {
            System.setProperty("user.dir", oldUserDir);
        }
    }

    @Test
    public void codeCacheTest() throws ScriptException, IOException {
        System.setProperty("nashorn.persistent.code.cache", codeCache);
        final NashornScriptEngineFactory fac = new NashornScriptEngineFactory();
        final ScriptEngine e = fac.getScriptEngine(ENGINE_OPTIONS_NOOPT);
        final Path codeCachePath = getCodeCachePath(false);
        e.eval(code1);
        e.eval(code2);
        e.eval(code3);// less than minimum size for storing
        // adding code1 and code2.
        final DirectoryStream<Path> stream = Files.newDirectoryStream(codeCachePath);
        checkCompiledScripts(stream, 2);
    }

    @Test
    public void codeCacheTestOpt() throws ScriptException, IOException {
        System.setProperty("nashorn.persistent.code.cache", codeCache);
        final NashornScriptEngineFactory fac = new NashornScriptEngineFactory();
        final ScriptEngine e = fac.getScriptEngine(ENGINE_OPTIONS_OPT);
        final Path codeCachePath = getCodeCachePath(true);
        e.eval(code1);
        e.eval(code2);
        e.eval(code3);// less than minimum size for storing
        // adding code1 and code2.
        final DirectoryStream<Path> stream = Files.newDirectoryStream(codeCachePath);
        checkCompiledScripts(stream, 4);
    }

    private static Path getCodeCachePath(final boolean optimistic) {
        final String codeCache = System.getProperty("nashorn.persistent.code.cache");
        final Path codeCachePath = FileSystems.getDefault().getPath(codeCache).toAbsolutePath();
        final String[] files = codeCachePath.toFile().list();
        for (final String file : files) {
            if (file.endsWith("_opt") == optimistic) {
                return codeCachePath.resolve(file);
            }
        }
        throw new AssertionError("Code cache path not found");
    }

    private static void checkCompiledScripts(final DirectoryStream<Path> stream, final int numberOfScripts) throws IOException {
        int n = numberOfScripts;
        for (@SuppressWarnings("unused") final Path file : stream) {
            n--;
        }
        stream.close();
        assertEquals(n, 0);
    }

}
