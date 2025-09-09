/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8341608
 * @summary Tests for jdeps tool with jar files with malformed classes
 * @library lib /test/lib
 * @build jdk.jdeps/com.sun.tools.jdeps.*
 * @run junit MalformedClassesTest
 */

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.attribute.SignatureAttribute;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.sun.tools.jdeps.JdepsAccess;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.util.JarUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalformedClassesTest {

    static Stream<Arguments> invalidArchives() throws Exception {
        var jarPath = Path.of("malformed-signature.jar");
        var compiledClasses = InMemoryJavaCompiler.compile(Map.ofEntries(
                Map.entry("one.One", """
                          package one;

                          import java.util.Optional;

                          class One {
                              Optional<String> st = Optional.empty();
                          }
                          """),
                Map.entry("two.Two", """
                          package two;

                          import java.lang.invoke.*;

                          class Two {
                              int i;
                              static final VarHandle I;

                              static {
                                  try {
                                      I = MethodHandles.lookup().findVarHandle(Two.class, "i", int.class);
                                  } catch (ReflectiveOperationException ex) {
                                      throw new ExceptionInInitializerError(ex);
                                  }
                              }
                          }
                          """)
        ));
        var updated = ClassFile.of().transformClass(ClassFile.of().parse(compiledClasses.get("one.One")),
                ClassTransform.transformingFields((fb, fe) -> {
                    if (fe instanceof SignatureAttribute) {
                        fb.with(SignatureAttribute.of(fb.constantPool().utf8Entry("Invalid string")));
                    } else {
                        fb.with(fe);
                    }
                }));
        var classes = new HashMap<>(compiledClasses);
        classes.put("one.One", updated);
        JarUtils.createJarFromClasses(jarPath, classes);

        Path flatDir = Path.of("flatDir");
        Files.createDirectories(flatDir);
        for (var entry : classes.entrySet()) {
            ClassFileInstaller.writeClassToDisk(entry.getKey(), entry.getValue(), flatDir.toString());
        }

        return Stream.of(
                Arguments.of("directory", flatDir, "One.class"),
                Arguments.of("jar", jarPath, "one/One.class (malformed-signature.jar)")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidArchives")
    public void testMalformedSignature(String kind, Path path, String entryName) throws IOException {
        try (var jdeps = JdepsUtil.newCommand("jdeps")) {
            jdeps.addRoot(path);
            var analyzer = jdeps.getDepsAnalyzer();
            analyzer.run();
            var archives = JdepsAccess.depsAnalyzerArchives(analyzer);
            assertEquals(1, archives.size(), archives::toString);
            var archive = archives.iterator().next();
            var skippedEntries = archive.reader().skippedEntries();
            assertEquals(1, skippedEntries.size(), skippedEntries::toString);
            var message = skippedEntries.getFirst();
            assertTrue(message.contains("ClassFileError"), message);
            assertTrue(message.contains("Invalid string"), message);
            assertTrue(message.contains(entryName), "\"" + message + "\" does not contain \"" + entryName + "\"");
        }
    }
}
