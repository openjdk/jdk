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

/**
 * @test
 * @bug 8377004
 * @summary Whitebox test for MethodFinder
 * @modules java.base/jdk.internal.misc
 *          jdk.compiler
 *          jdk.zipfs
 * @run junit MethodFinderTest
 */

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.misc.MethodFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MethodFinderTest {

    private static final String JAVA_VERSION = System.getProperty("java.specification.version");

    @Test
    public void testDistinctClassLoaders() throws Exception {
        Path base = Path.of("testDistinctClassLoaders");
        Path libSrc = base.resolve("libSrc");
        Path libClasses = base.resolve("libClasses");
        Path libJava = libSrc.resolve("p").resolve("Lib.java");

        Files.createDirectories(libJava.getParent());

        Files.writeString(libJava,
                          """
                          package p;
                          public class Lib {
                              void main(String... args) {
                                  System.err.println("Lib!");
                              }
                          }
                          """);

        TestHelper.compile("--release", JAVA_VERSION, "-d", libClasses.toString(), libJava.toString());

        Path mainSrc = base.resolve("mainSrc");
        Path mainClasses = base.resolve("mainClasses");
        Path mainJava = mainSrc.resolve("p").resolve("Main.java");

        Files.createDirectories(mainJava.getParent());

        Files.writeString(mainJava,
                          """
                          package p;

                          public class Main extends Lib {
                              public void main() {
                                  System.err.println("Main!");
                              }
                          }
                          """);

        TestHelper.compile("--release", JAVA_VERSION, "--class-path", libClasses.toString(), "-d", mainClasses.toString(), mainJava.toString());

        {
            ClassLoader cl = new URLClassLoader(new URL[] {
                libClasses.toUri().toURL(),
                mainClasses.toUri().toURL()
            });
            Class<?> mainClass = cl.loadClass("p.Main");
            Method mainMethod = MethodFinder.findMainMethod(mainClass);

            //p.Main and p.Lib are in the same runtime package:
            assertEquals("p.Lib", mainMethod.getDeclaringClass().getName());
        }

        {
            ClassLoader libCl = new URLClassLoader(new URL[] {
                libClasses.toUri().toURL(),
            });
            ClassLoader mainCl = new URLClassLoader(new URL[] {
                mainClasses.toUri().toURL()
            }, libCl);
            Class<?> mainClass = mainCl.loadClass("p.Main");
            Method mainMethod = MethodFinder.findMainMethod(mainClass);

            //p.Main and p.Lib are in the different runtime packages:
            assertEquals("p.Main", mainMethod.getDeclaringClass().getName());
        }
    }

    @Test
    public void testWrongEquals() throws Exception {
        Path base = Path.of("testDistinctClassLoaders");
        Path libSrc = base.resolve("libSrc");
        Path libClasses = base.resolve("libClasses");
        Path libJava = libSrc.resolve("p").resolve("Lib.java");

        Files.createDirectories(libJava.getParent());

        Files.writeString(libJava,
                          """
                          package p;
                          public class Lib {
                              void main(String... args) {
                                  System.err.println("Lib!");
                              }
                          }
                          """);

        TestHelper.compile("--release", JAVA_VERSION, "-d", libClasses.toString(), libJava.toString());

        Path mainSrc = base.resolve("mainSrc");
        Path mainClasses = base.resolve("mainClasses");
        Path mainJava = mainSrc.resolve("p").resolve("Main.java");

        Files.createDirectories(mainJava.getParent());

        Files.writeString(mainJava,
                          """
                          package p;

                          public class Main extends Lib {
                              public void main() {
                                  System.err.println("Main!");
                              }
                          }
                          """);

        TestHelper.compile("--release", JAVA_VERSION, "--class-path", libClasses.toString(), "-d", mainClasses.toString(), mainJava.toString());

        {
            ClassLoader cl = new URLClassLoader(new URL[] {
                libClasses.toUri().toURL(),
                mainClasses.toUri().toURL()
            });
            Class<?> mainClass = cl.loadClass("p.Main");
            Method mainMethod = MethodFinder.findMainMethod(mainClass);

            //p.Main and p.Lib are in the same runtime package:
            assertEquals("p.Lib", mainMethod.getDeclaringClass().getName());
        }

        {
            class WrongEquals extends URLClassLoader {

                public WrongEquals(URL[] urls) {
                    super(urls);
                }

                public WrongEquals(URL[] urls, ClassLoader parent) {
                    super(urls, parent);
                }

                @Override
                public boolean equals(Object obj) {
                    return obj instanceof WrongEquals;
                }
            }
            ClassLoader libCl = new WrongEquals(new URL[] {
                libClasses.toUri().toURL(),
            });
            ClassLoader mainCl = new WrongEquals(new URL[] {
                mainClasses.toUri().toURL()
            }, libCl);
            Class<?> mainClass = mainCl.loadClass("p.Main");
            Method mainMethod = MethodFinder.findMainMethod(mainClass);

            //p.Main and p.Lib are in the different runtime packages:
            assertEquals("p.Main", mainMethod.getDeclaringClass().getName());
        }
    }
}
