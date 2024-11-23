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

package jdk.test.lib.util;

import java.io.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.*;

import static java.lang.constant.ConstantDescs.*;

/**
 * Java agent which instruments the opening and closing of files.
 * <p>
 * Supported file APIs include: java.io.FileInputStream, java.io.FileOutputStream,
 * java.io.RandomAccessFile, java.nio.file.Files and java.nio.channels.FileChannel
 * <p>
 * The main method in this class is used by tests as a driver to produce agent JAR files.
 * <p>
 * A typical jtreg configuration looks like:
 *
 * @library /test/lib
 * @run driver jdk.test.lib.util.OpenFilesAgent
 * @run junit/othervm -javaagent:OpenFilesAgent.jar SomeTest
 * <p>
 * See {@link OpenFiles} for an assertion API to use in testing
 *
 * @see OpenFiles
 */
public class OpenFilesAgent {

    // Run from jtreg tests as a driver to produce agent and registry JARs
    public static void main(String[] args) throws IOException {

        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");

        createJar("OpenFiles.jar", man, OpenFiles.class);

        attrs.put(new Attributes.Name("Premain-Class"), OpenFilesAgent.class.getName());
        attrs.putValue("Can-Retransform-Classes", "true");

        createJar("OpenFilesAgent.jar", man, OpenFilesAgent.class);
    }


    // Initialize the agent
    public static void premain(String args, Instrumentation instrumentation) throws IOException, UnmodifiableClassException {

        File registryJarFile = new File("OpenFiles.jar");
        if (registryJarFile.exists()) {
            JarFile registryJar = new JarFile(registryJarFile);
            // OpenFiles must be visible to the boot class loader
            instrumentation.appendToBootstrapClassLoaderSearch(registryJar);
            // Register out class file transformer
            instrumentation.addTransformer(new TrackOpenFilesTransformer(), true);

            // Check if any class we want to transform is already loaded
            Set<Class<?>> toRetransform = new HashSet<>();
            var names = Set.of("java.io.RandomAccessFile",
                    "java.io.FileInputStream",
                    "java.io.FileOutputStream",
                    "sun.nio.ch.FileChannelImpl");
            for (Class clazz : instrumentation.getAllLoadedClasses()) {
                if (names.contains(clazz.getName())) {
                    toRetransform.add(clazz);
                }
            }
            instrumentation.retransformClasses(toRetransform.toArray(Class[]::new));
        } else {
            System.err.println("Registry JAR file not found: " + registryJarFile.getAbsolutePath());
        }
    }

    /**
     * Create a JAR file containing the class file for the given class, including
     * any nested classes.
     *
     * @param filename name of the JAR file to produce
     * @param man      the Manifest for the JAR file
     * @param clazz    the class file to create a JAR file for
     * @throws IOException if an error occurs
     */
    private static void createJar(String filename, Manifest man, Class<?> clazz) throws IOException {
        URL classFileResource = clazz.getResource(clazz.getSimpleName() + ".class");

        File[] files = new File(classFileResource.getFile()).getParentFile().listFiles();

        String dir = clazz.getPackage().getName().replace('.', '/') + "/";

        try (var out = new BufferedOutputStream(new FileOutputStream(new File(filename)));
             var jo = new JarOutputStream(out, man)) {
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.equals(clazz.getSimpleName() + ".class") ||
                            name.startsWith(clazz.getSimpleName() + "$")) {
                        jo.putNextEntry(new JarEntry(dir + name));
                        try (var in = new FileInputStream(file)) {
                            in.transferTo(jo);
                        }
                    }
                }
            }
        }
    }

    private static class TrackOpenFilesTransformer implements ClassFileTransformer {

        static final ClassDesc CD_Registry = ClassDesc.of("jdk.test.lib.util.OpenFiles");
        static final ClassDesc CD_File = ClassDesc.of("java.io.File");
        static final ClassDesc CD_FileDescriptor = ClassDesc.of("java.io.FileDescriptor");
        static final ClassDesc CD_Closeable = ClassDesc.of("java.io.Closeable");
        static final ClassDesc CD_FileChannel = ClassDesc.of("java.nio.channels.FileChannel");

        static final MethodTypeDesc MD_openFile = MethodTypeDesc.of(CD_void, CD_File, CD_Object);
        static final MethodTypeDesc MD_openString = MethodTypeDesc.of(CD_void, CD_String, CD_Object);
        static final MethodTypeDesc MD_closeFile = MethodTypeDesc.of(CD_void, CD_Object);
        static final MethodTypeDesc MD_fisOpen = MethodTypeDesc.of(CD_void, CD_String);
        static final MethodTypeDesc MD_fosOpen = MethodTypeDesc.of(CD_void, CD_String, CD_boolean);
        static final MethodTypeDesc MD_rafInit = MethodTypeDesc.of(CD_void, CD_File, CD_String, CD_boolean);
        static final MethodTypeDesc MD_fcImplOpen = MethodTypeDesc.of(CD_FileChannel,
                CD_FileDescriptor,
                CD_String,
                CD_boolean,
                CD_boolean,
                CD_boolean,
                CD_boolean,
                CD_Closeable);

        @Override
        public byte[] transform(Module module,
                                ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) throws IllegalClassFormatException {

            try {
                return switch (className) {
                    case "java/io/RandomAccessFile" ->
                            transformRandomAccessFile(classfileBuffer);
                    case "java/io/FileInputStream" ->
                            transformFileInputStream(classfileBuffer);
                    case "java/io/FileOutputStream" ->
                            transformFileOutputStream(classfileBuffer);
                    case "sun/nio/ch/FileChannelImpl" ->
                            transformFileChannelImpl(classfileBuffer);
                    default -> null;
                };
            } catch (Throwable e) {
                System.err.println("Error transforming class " + className);
                e.printStackTrace();
                return null;
            }
        }

        // Instrument RandomAccessFile to call OpenFiles::openFile, OpenFiles::closeFile
        byte[] transformRandomAccessFile(byte[] classfileBuffer) {
            ClassFile cf = ClassFile.of();
            ClassModel mod = cf.parse(classfileBuffer);
            return cf.transformClass(mod, (cb, ce) -> {
                switch (ce) {
                    // Constructor RandomAccessFile(File, String, boolean)
                    case MethodModel method
                            when method.methodName().equalsString("<init>")
                            && method.methodTypeSymbol().equals(MD_rafInit) ->

                            cb.transformMethod(method, MethodTransform.transformingCode(
                                    (builder, element) -> {
                                        switch (element) {
                                            // Call openFile before RETURN
                                            case ReturnInstruction ri -> {
                                                builder.aload(1) // File
                                                        .aload(0)        // RandomAccessFile (this)
                                                        .invokestatic(CD_Registry, "openFile", MD_openFile)
                                                        .with(ri);
                                            }

                                            default -> builder.with(element);
                                        }

                                    }));

                    // RandomAccessFile::close
                    case MethodModel method
                            when method.methodName().equalsString("close")
                            && method.methodTypeSymbol().equals(MTD_void) ->

                            cb.transformMethod(method, MethodTransform.transformingCode(
                                    (builder, element) -> {
                                        switch (element) {
                                            // Call close before RETURN
                                            case ReturnInstruction ri -> {
                                                builder.aload(0)        // RandomAccessFile (this)
                                                        .invokestatic(CD_Registry, "closeFile", MD_closeFile)
                                                        .with(ri);
                                            }

                                            default -> builder.with(element);
                                        }

                                    }));

                    default -> cb.accept(ce);
                }
            });
        }


        // Instrument FileInputStream to call OpenFiles::openFile, OpenFiles::closeFile
        byte[] transformFileInputStream(byte[] classfileBuffer) {
            ClassFile cf = ClassFile.of();
            ClassModel mod = cf.parse(classfileBuffer);
            return cf.transformClass(mod, (cb, ce) -> {
                switch (ce) {
                    // FileInputStream::open(String)
                    case MethodModel method
                            when method.methodName().equalsString("open")
                            && method.methodTypeSymbol().equals(MD_fisOpen) ->

                            cb.transformMethod(method, MethodTransform.transformingCode(
                                    (builder, element) -> {
                                        switch (element) {
                                            // Call openFile before RETURN
                                            case ReturnInstruction ri -> {
                                                builder.aload(1) // String
                                                        .aload(0)        // FileInputStream (this)
                                                        .invokestatic(CD_Registry, "openFile", MD_openString)
                                                        .with(ri);
                                            }

                                            default -> builder.with(element);
                                        }

                                    }));

                    // FileInputStream::close
                    case MethodModel method
                            when method.methodName().equalsString("close")
                            && method.methodTypeSymbol().equals(MTD_void) ->

                            cb.transformMethod(method, MethodTransform.transformingCode(
                                    (builder, element) -> {
                                        switch (element) {
                                            // Call close before RETURN
                                            case ReturnInstruction ri -> {
                                                builder.aload(0)        // FileInputStream (this)
                                                        .invokestatic(CD_Registry, "closeFile", MD_closeFile)
                                                        .with(ri);
                                            }

                                            default -> builder.with(element);
                                        }

                                    }));

                    default -> cb.accept(ce);
                }
            });
        }

        // Instrument FileOutputStream to call OpenFiles::openFile, OpenFiles::closeFile
        byte[] transformFileOutputStream(byte[] classfileBuffer) {
            ClassFile cf = ClassFile.of();
            ClassModel mod = cf.parse(classfileBuffer);
            return cf.transformClass(mod, (cb, ce) -> {
                switch (ce) {
                    // FileInputStream::open(String)
                    case MethodModel method
                            when method.methodName().equalsString("open")
                            && method.methodTypeSymbol().equals(MD_fosOpen) ->

                            cb.transformMethod(method, MethodTransform.transformingCode(
                                    (builder, element) -> {
                                        switch (element) {
                                            // Call openFile before RETURN
                                            case ReturnInstruction ri -> {
                                                builder.aload(1) // String
                                                        .aload(0)        // FileOutputStream (this)
                                                        .invokestatic(CD_Registry, "openFile", MD_openString)
                                                        .with(ri);
                                            }

                                            default -> builder.with(element);
                                        }

                                    }));

                    // FileInputStream::close
                    case MethodModel method
                            when method.methodName().equalsString("close")
                            && method.methodTypeSymbol().equals(MTD_void) ->

                            cb.transformMethod(method, MethodTransform.transformingCode(
                                    (builder, element) -> {
                                        switch (element) {
                                            // Call close before RETURN
                                            case ReturnInstruction ri -> {
                                                builder.aload(0)        // FileOutputStream (this)
                                                        .invokestatic(CD_Registry, "closeFile", MD_closeFile)
                                                        .with(ri);
                                            }

                                            default -> builder.with(element);
                                        }

                                    }));

                    default -> cb.accept(ce);
                }
            });
        }


        // Instrument FileChannelImpl::open and FileChannelImpl:closeImpl
        byte[] transformFileChannelImpl(byte[] classfileBuffer) {
            ClassFile cf = ClassFile.of();
            ClassModel mod = cf.parse(classfileBuffer);
            return cf.transformClass(mod, (cb, ce) -> {
                switch (ce) {
                    // FileChannelImpl::open(Path, Set, FileAttributes)
                    case MethodModel method
                            when method.methodName().equalsString("open")
                            && method.methodTypeSymbol().equals(MD_fcImplOpen) ->

                            cb.transformMethod(method, MethodTransform.transformingCode(
                                    (builder, element) -> {
                                        switch (element) {
                                            // Call openFile before RETURN
                                            case ReturnInstruction ri -> {
                                                builder.dup() // FileChannel
                                                        .aload(1) // String path
                                                        .swap()
                                                        .invokestatic(CD_Registry, "openFile", MD_openString)
                                                        .with(ri);
                                            }

                                            default -> builder.with(element);
                                        }

                                    }));


                    case MethodModel method
                            when method.methodName().equalsString("implCloseChannel")
                            && method.methodTypeSymbol().equals(MTD_void) ->

                            cb.transformMethod(method, MethodTransform.transformingCode(
                                    (builder, element) -> {
                                        switch (element) {
                                            // Call close before RETURN
                                            case ReturnInstruction ri -> {
                                                builder.aload(0)        // FileChannel (this)
                                                        .invokestatic(CD_Registry, "closeFile", MD_closeFile)
                                                        .with(ri);
                                            }
                                            default -> builder.with(element);
                                        }

                                    }));

                    default -> cb.accept(ce);
                }
            });
        }
    }
}
