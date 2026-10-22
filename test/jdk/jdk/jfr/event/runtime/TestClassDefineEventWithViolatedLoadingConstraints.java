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

package jdk.jfr.event.runtime;

import java.io.InputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.runtime.TestClassDefineEventWithViolatedLoadingConstraints
 */

public class TestClassDefineEventWithViolatedLoadingConstraints {
    private final static String EVENT_NAME = EventNames.ClassDefine;
    private final static String CLASS_NAME = TestClassDefineEventWithViolatedLoadingConstraints.class.getName();
    private final static String DEFINED_CLASS_NAME = CLASS_NAME + "$DuplicateDefinition";
    private final static String FAKE_SOURCE_PATH = "/my/fake/synthetic/classloading/source.jar";

    static class DuplicateDefinition { }

    static class DuplicateDefinitionClassLoader extends ClassLoader {
        DuplicateDefinitionClassLoader() {
            super(null);
        }

        Class<?> define(byte[] bytes, String classname) throws Exception {
            CodeSource cs = null;
            try {
                Path fakeJar = Path.of("my", "fake", "synthetic", "classloading", "source.jar");
                cs = new CodeSource(fakeJar.toUri().toURL(), (CodeSigner[]) null);
            } catch (MalformedURLException ex) {
                throw ex;
            }
            return defineClass(classname, bytes, 0, bytes.length, new ProtectionDomain(cs, null));
        }
    }

    private static byte[] readClassBytes(Class<?> clazz) throws IOException {
        String resource = clazz.getName().replace('.', '/') + ".class";
        ClassLoader loader = clazz.getClassLoader();
        if (loader != null) {
            InputStream in = loader.getResourceAsStream(resource);
            if (in == null) {
                throw new RuntimeException("Could not find " + clazz.getName());
            }
            return in.readAllBytes();
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            byte[] duplicateDefBytes = readClassBytes(DuplicateDefinition.class);
            DuplicateDefinitionClassLoader loader = new DuplicateDefinitionClassLoader();

            // First class definition is fine and should result in a jdk.ClassDefine event.
            loader.define(duplicateDefBytes, DEFINED_CLASS_NAME);

            try {
                // Intentionally violate a class loading constraint by defining the same class
                // again with the same class loader. This should throw a java.lang.LinkageError,
                // and we should NOT get a jdk.ClassDefine event for this failed attempt.
                //
                // Most importantly, the JVM should NOT assert or crash as a consequence of JFR
                // tagging and enqueuing an InstanceKlass that violates loading constraints.
                // Because such an InstanceKlass is immediately put on the class_loader_data's deallocation list,
                // it is not registered with a JFR unload set.
                //
                // Having such an InstanceKlass enqueued is therefore a broken invariant.
                loader.define(duplicateDefBytes, DEFINED_CLASS_NAME);
                throw new RuntimeException("Expected LinkageError not thrown");
            } catch (LinkageError e) {
                // as expected
            } finally {
                recording.stop();
            }

            validate(recording);
        }
    }

    private static void validate(Recording recording) throws Exception {
        List<RecordedEvent> events = Events.fromRecording(recording);
        int numberOfDuplicateDefinitionClassDefinedEvents = 0;
        for (RecordedEvent event : events) {
            System.out.println(event);
            RecordedClassLoader definingClassLoader = event.getValue("definingClassLoader");
            if (definingClassLoader == null) {
                continue;
            }
            RecordedClass classLoader = definingClassLoader.getType();
            if (classLoader == null) {
                Asserts.assertTrue("bootstrap".equals(definingClassLoader.getName()), "not the bootstrap class loader?");
                continue;
            }
            if (DuplicateDefinitionClassLoader.class.getName().equals(classLoader.getName())) {
                RecordedClass definedClass = event.getValue("definedClass");
                Asserts.assertNotNull(definedClass, "Defined Class should not be null");
                if (DEFINED_CLASS_NAME.equals(definedClass.getName())) {
                    Asserts.assertTrue(event.getString("source").startsWith("file://"));
                    Asserts.assertTrue(event.getString("source").endsWith(FAKE_SOURCE_PATH));
                    numberOfDuplicateDefinitionClassDefinedEvents++;
                }
            }
        }
        Asserts.assertEquals(1, numberOfDuplicateDefinitionClassDefinedEvents,
                "Wrong number of class define event for " + DEFINED_CLASS_NAME + ". Expected 1, got " + numberOfDuplicateDefinitionClassDefinedEvents);
    }
}
