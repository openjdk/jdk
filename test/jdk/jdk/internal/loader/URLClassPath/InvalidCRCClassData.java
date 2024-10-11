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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @test
 * @bug 8341957
 * @enablePreview
 * @modules java.base/jdk.internal.module
 * @library /test/lib
 * @summary Verify unspecified but long-standing behavior of URLClassLoader and
 *          the module system class loader with respect to invalid class file data
 *          and invalid JAR file CRC checksums
 * @run junit InvalidCRCClassData
 */
public class InvalidCRCClassData {

    // Name of the class being loaded in this test
    private static String className = "foo.bar.Library";

    /*
     * Provide expected behaviors for all eight combinations of
     *   - URLClassLoader or module system class loader
     *   - Valid or invalid class file data
     *   - Valid or invalid class file JAR CRC32 checksums
     */
    public static Stream<Arguments> parameters() throws IOException {
        return Stream.of(
                // Verify URLClassLoader:
                // Invalid class data + invalid CRC: ClassFormatError, suppressed exception
                Arguments.of(ucl(false, false), ClassFormatError.class, true),
                // Invalid class data + valid CRC: ClassFormatError, no suppressed exception
                Arguments.of(ucl(false, true), ClassFormatError.class, false),
                // Valid class data + invalid CRC: No exception
                Arguments.of(ucl(true, false), null, false),
                // Valid class data + valid CRC: No exception
                Arguments.of(ucl(true, true), null, false),

                // Verify module system class loader:
                // Invalid class data + invalid CRC: ClassFormatError, no suppressed exception
                Arguments.of(module(false, false), ClassFormatError.class, false),
                // Invalid class data + valid CRC: ClassFormatError, no suppressed exception
                Arguments.of(module(false, true), ClassFormatError.class, false),
                // Valid class data + invalid CRC: No exception
                Arguments.of(module(true, false), null, false),
                // Valid class data + valid CRC: No exception
                Arguments.of(module(true, true), null, false)
        );
    }

    /**
     * Verify behavior of a class loader with respect to invalid class file data
     * and/or invalid JAR file CRC checksums.
     * @param ctx representing URLClassLoader or module system class loader with the backing JAR file
     * @param expectedException Exception to expect during class loading
     * @param expectSuppressed Whether to expect a suppressed CRC32 exception on the ClassFormatError
     *
     * @throws ClassNotFoundException if an class cannot be found unexpectedly
     * @throws IOException if an unexpected IO error occurs
     */
    @ParameterizedTest
    @MethodSource("parameters")
    public void verifyClassLoading(Supplier<ClassLoadingContext> ctx,
                                   Class<? extends ClassFormatError> expectedException,
                                   boolean expectSuppressed)
            throws ClassNotFoundException, IOException
    {
        // Get the context for the class loader and JAR file
        var context = ctx.get();

        try {
            if (expectedException != null) {
                // Verify that ClassFormatError is thrown
                ClassFormatError cfe = assertThrows(expectedException, () -> {
                    context.getClassLoader().loadClass(className);
                });
                // Check whether CRC mismatch caused suppressed exception
                assertEquals(expectSuppressed, isCRC32Suppressed(cfe));
            } else {
                // Class should load normally
                assertEquals(className, context.getClassLoader().loadClass(className).getName());
            }
        } finally {
            // Clean up after this test
            Files.deleteIfExists(context.getJarFile());
        }
    }

    // Return true iff ClassFormatError has a suppressed CRC32 mismatch IOException
    private static boolean isCRC32Suppressed(ClassFormatError exception) {
        for (Throwable t : exception.getSuppressed()) {
            if (t instanceof IOException ioe &&
                    "CRC error while extracting entry from JAR file".equals(ioe.getMessage())) {
                return true;
            }
        }
        return false;
    }

    // Abstraction of URLClassLoader / module system class loader context
    interface ClassLoadingContext {
        ClassLoader getClassLoader();
        Path getJarFile();
    }

    // A ClassLoadingContext for loading classes using URLClassLoader
    static class URLClassLoading implements ClassLoadingContext {
        private final Path jarFile;

        private final URLClassLoader loader;

        URLClassLoading(Path jarFile, URLClassLoader loader) {
            this.jarFile = jarFile;
            this.loader = loader;
        }

        @Override
        public ClassLoader getClassLoader() {
            return loader;
        }
        @Override
        public Path getJarFile() {
            return jarFile;
        }

    }


    // A ClassLoadingContext for loading classes using the module system
    private static class ModuleClassLoading implements ClassLoadingContext {

        private final Module module;
        private final Path jarFile;

        private ModuleClassLoading(Module module, Path jarFile) {
            this.module = module;
            this.jarFile = jarFile;
        }

        @Override
        public ClassLoader getClassLoader() {
            return module.getClassLoader();
        }

        @Override
        public Path getJarFile() {
            return jarFile;
        }
    }

    // Create a context for loading classes from a JAR file using URLClassLoader
    private static Supplier<ClassLoadingContext> ucl(boolean validClass, boolean validCrc) throws IOException {
        return () -> {
            try {
                Path jarFile = createJarFile(validClass, validCrc);
                return new URLClassLoading(jarFile, new URLClassLoader(new URL[] {jarFile.toUri().toURL()}));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    // Create a context for loading classes from a JAR file using the module system
    private static Supplier<ClassLoadingContext> module(boolean validClass, boolean validCrc) throws IOException {
        return () -> {
            try {
                Path jarFile = createJarFile(validClass, validCrc);
                // Load the module
                ModuleFinder moduleFinder = ModuleFinder.of(jarFile);
                Configuration parent = ModuleLayer.boot().configuration();

                Configuration configuration = parent.resolve(moduleFinder, ModuleFinder.of(), Set.of("m1"));

                ModuleLayer.Controller controller = ModuleLayer.defineModulesWithOneLoader(configuration,
                        Collections.singletonList(ModuleLayer.boot()),
                        InvalidCRCClassData.class.getClassLoader());


                Module m1 = controller.layer().findModule("m1").orElseThrow();
                return new ModuleClassLoading(m1, jarFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    // Create a JAR / module file for use in this test,
    // optionally with invalid class file data and/or invalid CRC checksum
    private static Path createJarFile(boolean validClass, boolean validCrc) throws IOException {
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

    // Build a valid class file byte array
    private static byte[] libraryClass() {
        return ClassFile.of().build(ClassDesc.of(className), cb -> {
            cb.withSuperclass(ConstantDescs.CD_Object);
        });
    }
}
