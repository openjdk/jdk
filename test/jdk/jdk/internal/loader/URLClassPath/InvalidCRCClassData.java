/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.util.ModuleInfoWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @test
 * @bug 8341957
 * @enablePreview
 * @modules java.base/jdk.internal.module
 * @library /test/lib
 * @summary Verify that URLClassLoader checks CRC32 of class file data when
 *          defineClass fails with ClassFormatError
 * @run junit InvalidCRCData
 */
public class InvalidCRCClassData {

    String className = "foo.bar.Library";

    /**
     * Verify that URLClassLoader adds a suppressed exception when
     * a class file is rejected with ClassFormatError and the CRC32 checksum
     * of the class data file does not match the CRC32 stated in the JAR CEN header.
     *
     * @throws IOException if an unexpected IO exception occurs
     * @throws ClassNotFoundException if a class is unexpectedly not found
     */
    @Test
    public void invalidClassInvalidCRC_URLClassLoader() throws IOException, ClassNotFoundException {

        // Create a JAR file with invalid class data, invalid CRC
        Path jarFile = createJarFile(false, false);

        try (var cl = new CustomURLClassLoader(jarFile)) {
            // Expect ClassFormatError
            ClassFormatError exception = assertThrows(ClassFormatError.class, () -> {
                cl.findClass(className);
            });

            // CRC32 exception should be added as suppressed
            assertTrue(isCRC32Suppressed(exception));
        }
    }

    /**
     * A CRC mismatch itself does not cause exceptions for a valid class
     *
     * @throws IOException if an unexpected IO exception occurs
     * @throws ClassNotFoundException if a class is unexpectedly not found
     */
    @Test
    public void validClassInvalidCRC_URLClassLoader() throws IOException, ClassNotFoundException {

        // Create a JAR file with valid class data, invalid CRC
        Path jarFile = createJarFile(true, false);

        try (var cl = new CustomURLClassLoader(jarFile)) {
            assertEquals(className, cl.findClass(className).getName());
        }
    }

    /**
     * Document that a JAR file with invalid class file data and a mismatching
     * CRC32 checksum does not cause ClassFormatError to have suppressed
     * exceptions when loaded using the module class loader.
     *
     * @throws IOException if an unexpected IO exception occurs
     * @throws ClassNotFoundException if a class is unexpectedly not found
     */
    @Test
    public void invalidClassCRCMismatchModule() throws IOException, ClassNotFoundException {

        // Load a module with invalid class data and invalid CRC
        Module m1 = loadModule(false, false);

        // Expect ClassFormatError when loading class
        ClassFormatError exception = assertThrows(ClassFormatError.class, () -> {
            m1.getClassLoader().loadClass(className);
        });

        // Verify that jdk.internal.Loader does not check CRC32 on ClassFormatError
        assertFalse(isCRC32Suppressed(exception));
    }

    /**
     * Document that a JAR file with valid class file data and a mismatching
     * CRC32 checksums does not cause exceptions
     *
     * @throws IOException if an unexpected IO exception occurs
     * @throws ClassNotFoundException if a class is unexpectedly not found
     */
    @Test
    public void validClassCRCMismatchModule() throws IOException, ClassNotFoundException {

        // Load a module with valid class data, but invalid CRC
        Module m1 = loadModule(true, false);

        // Expect class to load, even with CRC mismatch
        assertEquals(className, m1.getClassLoader().loadClass(className).getName());
    }

    // Load a module from a constructed JAR file
    private Module loadModule(boolean validClass, boolean validCrc) throws IOException {
        // Create a module JAR file
        Path jarFile = createJarFile(validClass, validCrc);

        // Load the module
        ModuleFinder moduleFinder = ModuleFinder.of(jarFile);
        Configuration parent = ModuleLayer.boot().configuration();

        Configuration configuration = parent.resolve(moduleFinder, ModuleFinder.of(), Set.of("m1"));

        ModuleLayer.Controller controller = ModuleLayer.defineModulesWithOneLoader(configuration,
                Collections.singletonList(ModuleLayer.boot()),
                getClass().getClassLoader());

        return controller.layer().findModule("m1").orElseThrow();
    }

    // Return true iff CFE has a suppressed CRC32 mismatch error
    private static boolean isCRC32Suppressed(ClassFormatError exception) {
        for (Throwable t : exception.getSuppressed()) {
            if (t instanceof IOException ioe &&
                    "CRC error while extracting entry from JAR file".equals(ioe.getMessage())) {
                return true;
            }
        }
        return false;
    }

    // Create a JAR / module file for use in this test,
    // optionally with invalid class file data or invalid CRC checksum
    private Path createJarFile(boolean validClass, boolean validCrc) throws IOException {
        // Create a ZIP file with an invalid class file
        Path zipFile = Path.of("invalid-class-data.jar");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var zo = new ZipOutputStream(out)) {
            // Write library class
            String entryName = className.replace('.', '/') +".class";
            zo.putNextEntry(new ZipEntry(entryName));
            if (!validClass) {
                // Invalid class file data
                zo.write("efac".getBytes(StandardCharsets.UTF_8));
            }
            zo.write(libraryClass());

            // Write module descriptor
            zo.putNextEntry(new ZipEntry("module-info.class"));
            ModuleDescriptor descriptor = ModuleDescriptor.newModule("m1")
                    .requires("java.base")
                    .build();
            ModuleInfoWriter.write(descriptor, zo);
        }

        byte[] bytes = out.toByteArray();

        if (!validCrc) {
            // Put an invalid CRC value in the CEN header for the class file entry
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int cenOff = buf.getInt(bytes.length - ZipEntry.ENDHDR + ZipEntry.ENDOFF);
            buf.putInt(cenOff + ZipEntry.CENCRC, 0x0);
        }

        // Write the file to disk
        Files.write(zipFile, bytes);
        return zipFile;
    }

    private byte[] libraryClass() {
        return ClassFile.of().build(ClassDesc.of(className), cb -> {
            cb.withSuperclass(ConstantDescs.CD_Object);
        });
    }

    // URLClassLoader with access bridge to findClass
    class CustomURLClassLoader extends URLClassLoader {
        public CustomURLClassLoader(Path jarFile) throws MalformedURLException {
            super(new URL[] {jarFile.toUri().toURL()});
        }
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            return super.findClass(name);
        }
    }
}
