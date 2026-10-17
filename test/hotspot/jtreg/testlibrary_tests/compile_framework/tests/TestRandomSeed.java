/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8380670
 * @summary Test seed propagation to generated code loaded by the Compile Framework.
 * @library /test/lib /
 * @run main/othervm -Djdk.test.lib.random.seed=-1 compile_framework.tests.TestRandomSeed default
 * @run main/othervm -Djdk.test.lib.random.seed=-1 compile_framework.tests.TestRandomSeed explicit
 */

package compile_framework.tests;

import compiler.lib.compile_framework.CompileFramework;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Random;

public class TestRandomSeed {
    public static void main(String[] args) throws Exception {
        // Model jtreg's separate test loader even when running in othervm mode.
        // Do not initialize Utils in the system loader before generated code uses it.
        String[] paths = System.getProperty("test.class.path").split(File.pathSeparator);
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) {
            urls[i] = Path.of(paths[i]).toUri().toURL();
        }
        String original = System.getProperty(Utils.SEED_PROPERTY_NAME);
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Class<?> runner = Class.forName(Generator.class.getName(), true, loader);
            runner.getMethod("run", boolean.class).invoke(null, args[0].equals("default"));
        } finally {
            System.setProperty(Utils.SEED_PROPERTY_NAME, original);
        }
    }

    public static class Generator {
        public static void run(boolean clearSeed) {
            long seed = Utils.SEED;
            Random expected = new Random(seed);
            Random random = Utils.getRandomInstance();
            Asserts.assertEQ(expected.nextLong(), random.nextLong());
            long generatedSeed = seed;
            if (clearSeed) {
                // Keep the chosen seed in this Utils, but require the framework to
                // propagate it to the independently initialized generated-code Utils.
                System.clearProperty(Utils.SEED_PROPERTY_NAME);
            } else {
                // An explicit override must not be replaced by the already chosen seed.
                generatedSeed = 0;
                System.setProperty(Utils.SEED_PROPERTY_NAME, Long.toString(generatedSeed));
            }

            CompileFramework compiler = new CompileFramework();
            compiler.addJavaSourceCode("Generated", """
                    public class Generated {
                        public static long seed() {
                            return jdk.test.lib.Utils.SEED;
                        }
                        public static java.util.Random random() {
                            return jdk.test.lib.Utils.getRandomInstance();
                        }
                    }
                    """);
            compiler.compile();
            Asserts.assertEQ(generatedSeed, (long) compiler.invoke("Generated", "seed", new Object[0]),
                             "Generated code must use the inherited or explicitly overridden seed");
            Random generated = (Random) compiler.invoke("Generated", "random", new Object[0]);
            Asserts.assertTrue(generated != random, "The random states must remain independent");
            Random generatedExpected = new Random(generatedSeed);
            Asserts.assertEQ(generatedExpected.nextLong(), generated.nextLong());

            new CompileFramework();
            Asserts.assertTrue(Utils.getRandomInstance() == random, "Generator Random must not be replaced");
            Asserts.assertTrue(compiler.invoke("Generated", "random", new Object[0]) == generated,
                               "Generated Random must not be replaced");
            Asserts.assertEQ(expected.nextLong(), random.nextLong(), "Generator state must not be reset");
            Asserts.assertEQ(generatedExpected.nextLong(), generated.nextLong(), "Generated state must not be reset");
        }
    }
}
