/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Layer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;
import java.util.Set;
import javax.script.ScriptEngineFactory;

import static jdk.testlibrary.ProcessTools.executeTestJava;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @test
 * @library /lib/testlibrary
 * @modules java.scripting
            jdk.compiler
 * @build ServicesTest CompilerUtils jdk.testlibrary.*
 * @run testng ServicesTest
 * @summary Tests ServiceLoader to locate service providers on the module path
 *          and class path. Also tests ServiceLoader with a custom Layer.
 */

@Test
public class ServicesTest {

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path CLASSES_DIR = Paths.get("classes");
    private static final Path MODS_DIR = Paths.get("mods");

    // modules to compile to the module path
    private static final String MODULES[] = { "test", "bananascript" };

    // directories of classes to compile to the class path
    private static final String CLASSES[] = { "pearscript" };

    // resources to copy to the class path
    private static final String RESOURCES[] = {
        "pearscript/META-INF/services/javax.script.ScriptEngineFactory"
    };


    /**
     * Compiles all modules and classes used by the test
     */
    @BeforeTest
    public void setup() throws Exception {

        // modules
        for (String mn : MODULES ) {
            Path src = SRC_DIR.resolve(mn);
            Path mods = MODS_DIR.resolve(mn);
            assertTrue(CompilerUtils.compile(src, mods));
        }

        // classes
        for (String dir : CLASSES) {
            Path src = SRC_DIR.resolve(dir);
            assertTrue(CompilerUtils.compile(src, CLASSES_DIR));
        }

        // copy resources
        for (String rn : RESOURCES) {
            Path file = Paths.get(rn.replace('/', File.separatorChar));
            Path source = SRC_DIR.resolve(file);

            // drop directory to get a target of classes/META-INF/...
            Path target = CLASSES_DIR.resolve(file.subpath(1, file.getNameCount()));

            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        }

    }


    /**
     * Run test with -modulepath.
     *
     * BananaScriptEngine should be found.
     */
    public void runWithModulePath() throws Exception {
        int exitValue
            = executeTestJava("-mp", MODS_DIR.toString(),
                              "-m", "test/test.Main",
                              "BananaScriptEngine")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Run test with -modulepath and -classpath.
     *
     * Both BananaScriptEngine and PearScriptEngine should be found
     */
    public void runWithModulePathAndClassPath() throws Exception {
        int exitValue
            = executeTestJava("-mp", MODS_DIR.toString(),
                              "-cp", CLASSES_DIR.toString(),
                              "-m", "test/test.Main",
                              "BananaScriptEngine", "PearScriptEngine")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Exercise ServiceLoader.load(Layer, Class).
     */
    public void testWithCustomLayer() throws Exception {

        ServiceLoader<ScriptEngineFactory> sl;

        // BananaScriptEngine should not be in the boot Layer
        sl = ServiceLoader.load(Layer.boot(), ScriptEngineFactory.class);
        assertTrue(find("BananaScriptEngine", sl) == null);

        // create a custom Layer
        ModuleFinder finder = ModuleFinder.of(MODS_DIR);
        Layer bootLayer = Layer.boot();
        Configuration parent = bootLayer.configuration();
        Configuration cf
            = parent.resolveRequiresAndUses(finder, ModuleFinder.empty(), Set.of());
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        Layer layer = bootLayer.defineModulesWithOneLoader(cf, scl);

        assertTrue(layer.findModule("bananascript").isPresent());
        ClassLoader loader = layer.findLoader("bananascript");

        sl = ServiceLoader.load(layer, ScriptEngineFactory.class);
        ScriptEngineFactory factory = find("BananaScriptEngine", sl);
        assertTrue(factory != null);
        assertEquals(factory.getClass().getModule().getName(), "bananascript");
        assertTrue(factory.getClass().getClassLoader() == loader);

    }

    /**
     * Find the given scripting engine (by name) via the ScriptEngineFactory
     * that ServiceLoader has found.
     */
    static ScriptEngineFactory find(String name,
                                    ServiceLoader<ScriptEngineFactory> sl) {
        for (ScriptEngineFactory factory : sl) {
            if (factory.getEngineName().equals(name))
                return factory;
        }
        return null;
    }


}

