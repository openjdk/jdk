/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.mxtool.junit;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Finds classes in given jar files that contain methods annotated by a given set of annotations.
 */
public class FindClassesByAnnotatedMethods {

    /**
     * Finds classes in a given set of jar files that contain at least one method with an annotation
     * from a given set of annotations. The qualified name and containing jar file (separated by a
     * space) is written to {@link System#out} for each matching class.
     *
     * @param args jar file names, annotations and snippets patterns. Annotations are those starting
     *            with "@" and can be either qualified or unqualified annotation class names,
     *            snippets patterns are those starting with {@code "snippetsPattern:"} and the rest
     *            are jar file names
     */
    public static void main(String... args) throws Throwable {
        Set<String> qualifiedAnnotations = new HashSet<>();
        Set<String> unqualifiedAnnotations = new HashSet<>();
        for (String arg : args) {
            if (isAnnotationArg(arg)) {
                String annotation = arg.substring(1);
                int lastDot = annotation.lastIndexOf('.');
                if (lastDot != -1) {
                    qualifiedAnnotations.add(annotation);
                } else {
                    String unqualifed = annotation.substring(lastDot + 1);
                    unqualifiedAnnotations.add(unqualifed);
                }
            }
        }

        for (String jarFilePath : args) {
            if (isSnippetArg(jarFilePath) || isAnnotationArg(jarFilePath)) {
                continue;
            }
            JarFile jarFile = new JarFile(jarFilePath);
            Enumeration<JarEntry> e = jarFile.entries();
            int unsupportedClasses = 0;
            System.out.print(jarFilePath);
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || !je.getName().endsWith(".class")) {
                    continue;
                }
                Set<String> methodAnnotationTypes = new HashSet<>();
                DataInputStream stream = new DataInputStream(new BufferedInputStream(jarFile.getInputStream(je), (int) je.getSize()));
                boolean isSupported = true;
                try {
                    readClassfile(stream, methodAnnotationTypes);
                } catch (UnsupportedClassVersionError ucve) {
                    isSupported = false;
                    unsupportedClasses++;
                }
                String className = je.getName().substring(0, je.getName().length() - ".class".length()).replaceAll("/", ".");
                if (!isSupported) {
                    System.out.print(" !" + className);
                }
                for (String annotationType : methodAnnotationTypes) {
                    if (!qualifiedAnnotations.isEmpty()) {
                        if (qualifiedAnnotations.contains(annotationType)) {
                            System.out.print(" " + className);
                        }
                    }
                    if (!unqualifiedAnnotations.isEmpty()) {
                        final int lastDot = annotationType.lastIndexOf('.');
                        if (lastDot != -1) {
                            String simpleName = annotationType.substring(lastDot + 1);
                            int lastDollar = simpleName.lastIndexOf('$');
                            if (lastDollar != -1) {
                                simpleName = simpleName.substring(lastDollar + 1);
                            }
                            if (unqualifiedAnnotations.contains(simpleName)) {
                                System.out.print(" " + className);
                            }
                        }
                    }
                }
            }
            if (unsupportedClasses != 0) {
                System.err.printf("Warning: %d classes in %s skipped as their class file version is not supported by %s%n", unsupportedClasses, jarFilePath,
                                FindClassesByAnnotatedMethods.class.getSimpleName());
            }
            System.out.println();
        }
    }

    private static boolean isAnnotationArg(String arg) {
        return arg.charAt(0) == '@';
    }

    private static boolean isSnippetArg(String arg) {
        return arg.startsWith("snippetsPattern:");
    }

    /*
     * Small bytecode parser that extract annotations.
     */
    private static final int MAJOR_VERSION_JAVA7 = 51;
    private static final int MAJOR_VERSION_OFFSET = 44;
    private static final byte CONSTANT_Utf8 = 1;
    private static final byte CONSTANT_Integer = 3;
    private static final byte CONSTANT_Float = 4;
    private static final byte CONSTANT_Long = 5;
    private static final byte CONSTANT_Double = 6;
    private static final byte CONSTANT_Class = 7;
    private static final byte CONSTANT_Fieldref = 9;
    private static final byte CONSTANT_String = 8;
    private static final byte CONSTANT_Methodref = 10;
    private static final byte CONSTANT_InterfaceMethodref = 11;
    private static final byte CONSTANT_NameAndType = 12;
    private static final byte CONSTANT_MethodHandle = 15;
    private static final byte CONSTANT_MethodType = 16;
    private static final byte CONSTANT_Dynamic = 17;
    private static final byte CONSTANT_InvokeDynamic = 18;

    private static void readClassfile(DataInputStream stream, Collection<String> methodAnnotationTypes) throws IOException {
        // magic
        int magic = stream.readInt();
        assert magic == 0xCAFEBABE;

        int minor = stream.readUnsignedShort();
        int major = stream.readUnsignedShort();
        if (major < MAJOR_VERSION_JAVA7) {
            throw new UnsupportedClassVersionError("Unsupported class file version: " + major + "." + minor);
        }
        // Starting with JDK8, ignore a classfile that has a newer format than the current JDK.
        String javaVersion = System.getProperties().get("java.specification.version").toString();
        int majorJavaVersion;
        if (javaVersion.startsWith("1.")) {
            javaVersion = javaVersion.substring(2);
            majorJavaVersion = Integer.parseInt(javaVersion);
        } else {
            majorJavaVersion = Integer.parseInt(javaVersion);
        }
        if (major > MAJOR_VERSION_OFFSET + majorJavaVersion) {
            throw new UnsupportedClassVersionError("Unsupported class file version: " + major + "." + minor);
        }

        String[] cp = readConstantPool(stream, major, minor);

        // access_flags, this_class, super_class
        stream.skipBytes(6);

        // interfaces
        stream.skipBytes(stream.readUnsignedShort() * 2);

        // fields
        skipFields(stream);

        // methods
        readMethods(stream, cp, methodAnnotationTypes);
    }

    private static void skipFully(DataInputStream stream, int n) throws IOException {
        long skipped = 0;
        do {
            long s = stream.skip(n - skipped);
            skipped += s;
            if (s == 0 && skipped != n) {
                // Check for EOF (i.e., truncated class file)
                if (stream.read() == -1) {
                    throw new IOException("truncated stream");
                }
                skipped++;
            }
        } while (skipped != n);
    }

    private static String[] readConstantPool(DataInputStream stream, int major, int minor) throws IOException {
        int count = stream.readUnsignedShort();
        String[] cp = new String[count];

        int i = 1;
        while (i < count) {
            byte tag = stream.readByte();
            switch (tag) {
                case CONSTANT_Class:
                case CONSTANT_String:
                case CONSTANT_MethodType: {
                    skipFully(stream, 2);
                    break;
                }
                case CONSTANT_InterfaceMethodref:
                case CONSTANT_Methodref:
                case CONSTANT_Fieldref:
                case CONSTANT_NameAndType:
                case CONSTANT_Float:
                case CONSTANT_Integer:
                case CONSTANT_Dynamic:
                case CONSTANT_InvokeDynamic: {
                    skipFully(stream, 4);
                    break;
                }
                case CONSTANT_Long:
                case CONSTANT_Double: {
                    skipFully(stream, 8);
                    break;
                }
                case CONSTANT_Utf8: {
                    cp[i] = stream.readUTF();
                    break;
                }
                case CONSTANT_MethodHandle: {
                    skipFully(stream, 3);
                    break;
                }
                default: {
                    throw new InternalError(String.format("Invalid constant pool tag: " + tag + ". Maybe %s needs updating for changes introduced by class file version %d.%d?",
                                    FindClassesByAnnotatedMethods.class, major, minor));
                }
            }
            if ((tag == CONSTANT_Double) || (tag == CONSTANT_Long)) {
                i += 2;
            } else {
                i += 1;
            }
        }
        return cp;
    }

    private static void skipAttributes(DataInputStream stream) throws IOException {
        int attributesCount;
        attributesCount = stream.readUnsignedShort();
        for (int i = 0; i < attributesCount; i++) {
            stream.skipBytes(2); // name_index
            int attributeLength = stream.readInt();
            skipFully(stream, attributeLength);
        }
    }

    private static void readMethodAttributes(DataInputStream stream, String[] cp, Collection<String> methodAnnotationTypes) throws IOException {
        int attributesCount;
        attributesCount = stream.readUnsignedShort();
        for (int i = 0; i < attributesCount; i++) {
            String attributeName = cp[stream.readUnsignedShort()];
            int attributeLength = stream.readInt();

            if (attributeName.equals("RuntimeVisibleAnnotations")) {
                int numAnnotations = stream.readUnsignedShort();
                for (int a = 0; a != numAnnotations; a++) {
                    readAnnotation(stream, cp, methodAnnotationTypes);
                }
            } else {
                skipFully(stream, attributeLength);
            }
        }
    }

    private static void readAnnotation(DataInputStream stream, String[] cp, Collection<String> methodAnnotationTypes) throws IOException {
        int typeIndex = stream.readUnsignedShort();
        int pairs = stream.readUnsignedShort();
        String type = cp[typeIndex];
        String className = type.substring(1, type.length() - 1).replace('/', '.');
        methodAnnotationTypes.add(className);
        readAnnotationElements(stream, cp, pairs, true, methodAnnotationTypes);
    }

    private static void readAnnotationElements(DataInputStream stream, String[] cp, int pairs, boolean withElementName, Collection<String> methodAnnotationTypes) throws IOException {
        for (int p = 0; p < pairs; p++) {
            if (withElementName) {
                skipFully(stream, 2);
            }
            int tag = stream.readByte();
            switch (tag) {
                case 'B':
                case 'C':
                case 'D':
                case 'F':
                case 'I':
                case 'J':
                case 'S':
                case 'Z':
                case 's':
                case 'c':
                    skipFully(stream, 2);
                    break;
                case 'e':
                    skipFully(stream, 4);
                    break;
                case '@':
                    readAnnotation(stream, cp, methodAnnotationTypes);
                    break;
                case '[': {
                    int numValues = stream.readUnsignedShort();
                    readAnnotationElements(stream, cp, numValues, false, methodAnnotationTypes);
                    break;
                }
            }
        }
    }

    private static void skipFields(DataInputStream stream) throws IOException {
        int count = stream.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            stream.skipBytes(6); // access_flags, name_index, descriptor_index
            skipAttributes(stream);
        }
    }

    private static void readMethods(DataInputStream stream, String[] cp, Collection<String> methodAnnotationTypes) throws IOException {
        int count = stream.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            skipFully(stream, 6); // access_flags, name_index, descriptor_index
            readMethodAttributes(stream, cp, methodAnnotationTypes);
        }
    }
}
