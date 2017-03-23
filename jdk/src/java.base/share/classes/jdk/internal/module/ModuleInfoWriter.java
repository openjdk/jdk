/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.module;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;

import static jdk.internal.module.ClassFileAttributes.*;
import static jdk.internal.module.ClassFileConstants.ACC_MODULE;

/**
 * Utility class to write a ModuleDescriptor as a module-info.class.
 */

public final class ModuleInfoWriter {

    private ModuleInfoWriter() { }

    /**
     * Writes the given module descriptor to a module-info.class file,
     * returning it in a byte array.
     */
    private static byte[] toModuleInfo(ModuleDescriptor md, ModuleTarget target) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_9, ACC_MODULE, "module-info", null, null, null);
        cw.visitAttribute(new ModuleAttribute(md));

        // for tests: write the ModulePackages attribute when there are packages
        // that aren't exported or open
        Stream<String> exported = md.exports().stream()
                .map(ModuleDescriptor.Exports::source);
        Stream<String> open = md.opens().stream()
                .map(ModuleDescriptor.Opens::source);
        long exportedOrOpen = Stream.concat(exported, open).distinct().count();
        if (md.packages().size() > exportedOrOpen)
            cw.visitAttribute(new ModulePackagesAttribute(md.packages()));

        // write ModuleMainClass if the module has a main class
        md.mainClass().ifPresent(mc -> cw.visitAttribute(new ModuleMainClassAttribute(mc)));

        // write ModuleTarget if there is a platform OS/arch
        if (target != null) {
            cw.visitAttribute(new ModuleTargetAttribute(target.osName(),
                                                        target.osArch()));
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Writes a module descriptor to the given output stream as a
     * module-info.class.
     */
    public static void write(ModuleDescriptor descriptor,
                             ModuleTarget target,
                             OutputStream out)
        throws IOException
    {
        byte[] bytes = toModuleInfo(descriptor, target);
        out.write(bytes);
    }

    /**
     * Writes a module descriptor to the given output stream as a
     * module-info.class.
     */
    public static void write(ModuleDescriptor descriptor, OutputStream out)
        throws IOException
    {
        write(descriptor, null, out);
    }

    /**
     * Returns a {@code ByteBuffer} containing the given module descriptor
     * in module-info.class format.
     */
    public static ByteBuffer toByteBuffer(ModuleDescriptor descriptor) {
        byte[] bytes = toModuleInfo(descriptor, null);
        return ByteBuffer.wrap(bytes);
    }
}
