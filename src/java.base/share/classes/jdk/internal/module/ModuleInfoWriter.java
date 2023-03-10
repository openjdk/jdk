/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.constant.ClassDesc;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.nio.ByteBuffer;
import java.util.Map;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.java.lang.constant.ModuleDesc;
import jdk.internal.classfile.java.lang.constant.PackageDesc;
import jdk.internal.classfile.attribute.ModuleAttribute;
import jdk.internal.classfile.attribute.ModuleMainClassAttribute;
import jdk.internal.classfile.attribute.ModuleResolutionAttribute;
import jdk.internal.classfile.attribute.ModuleTargetAttribute;

/**
 * Utility class to write a ModuleDescriptor as a module-info.class.
 */

public final class ModuleInfoWriter {

    private static final Map<ModuleDescriptor.Modifier, Integer>
        MODULE_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Modifier.OPEN, Classfile.ACC_OPEN,
            ModuleDescriptor.Modifier.SYNTHETIC, Classfile.ACC_SYNTHETIC,
            ModuleDescriptor.Modifier.MANDATED, Classfile.ACC_MANDATED
        );

    private static final Map<ModuleDescriptor.Requires.Modifier, Integer>
        REQUIRES_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Requires.Modifier.TRANSITIVE, Classfile.ACC_TRANSITIVE,
            ModuleDescriptor.Requires.Modifier.STATIC, Classfile.ACC_STATIC_PHASE,
            ModuleDescriptor.Requires.Modifier.SYNTHETIC, Classfile.ACC_SYNTHETIC,
            ModuleDescriptor.Requires.Modifier.MANDATED, Classfile.ACC_MANDATED
        );

    private static final Map<ModuleDescriptor.Exports.Modifier, Integer>
        EXPORTS_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Exports.Modifier.SYNTHETIC, Classfile.ACC_SYNTHETIC,
            ModuleDescriptor.Exports.Modifier.MANDATED, Classfile.ACC_MANDATED
        );

    private static final Map<ModuleDescriptor.Opens.Modifier, Integer>
        OPENS_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Opens.Modifier.SYNTHETIC, Classfile.ACC_SYNTHETIC,
            ModuleDescriptor.Opens.Modifier.MANDATED, Classfile.ACC_MANDATED
        );

    private ModuleInfoWriter() { }

    /**
     * Writes the given module descriptor to a module-info.class file,
     * returning it in a byte array.
     */
    private static byte[] toModuleInfo(ModuleDescriptor md,
                                       ModuleResolution mres,
                                       ModuleTarget target) {
        try {
            return Classfile.buildModule(
                ModuleAttribute.of(ModuleDesc.of(md.name()), mb -> {
                    mb.moduleFlags(md.modifiers().stream()
                            .mapToInt(mm -> MODULE_MODS_TO_FLAGS.getOrDefault(mm, 0))
                            .reduce(0, (x, y) -> (x | y)));

                    String vs = md.rawVersion().orElse(null);
                    if (vs != null) mb.moduleVersion(vs);

                    // requires
                    for (ModuleDescriptor.Requires r : md.requires()) {
                        int flags = r.modifiers().stream()
                                .mapToInt(REQUIRES_MODS_TO_FLAGS::get)
                                .reduce(0, (x, y) -> (x | y));
                        vs = r.rawCompiledVersion().orElse(null);
                        mb.requires(ModuleDesc.of(r.name()), flags, vs);
                    }

                    // exports
                    for (ModuleDescriptor.Exports e : md.exports()) {
                        int flags = e.modifiers().stream()
                                .mapToInt(EXPORTS_MODS_TO_FLAGS::get)
                                .reduce(0, (x, y) -> (x | y));
                        var targets = e.targets().stream().map(ModuleDesc::of)
                                .toArray(ModuleDesc[]::new);
                        mb.exports(PackageDesc.of(e.source()), flags, targets);
                    }

                    // opens
                    for (ModuleDescriptor.Opens opens : md.opens()) {
                        int flags = opens.modifiers().stream()
                                .mapToInt(OPENS_MODS_TO_FLAGS::get)
                                .reduce(0, (x, y) -> (x | y));
                        var targets = opens.targets().stream().map(ModuleDesc::of)
                                .toArray(ModuleDesc[]::new);
                        mb.opens(PackageDesc.of(opens.source()), flags, targets);
                    }

                    // uses
                    md.uses().stream().map(ClassDesc::of).forEach(mb::uses);

                    // provides
                    for (ModuleDescriptor.Provides p : md.provides()) {
                        mb.provides(ClassDesc.of(p.service()),
                                                 p.providers().stream()
                                                         .map(ClassDesc::of)
                                                         .toArray(ClassDesc[]::new));
                    }
                }),

                // packages
                md.packages().stream().map(PackageDesc::of).toList(),

                // extra attributes
                clb -> {
                    // ModuleMainClass attribute
                    md.mainClass().ifPresent(mc ->
                            clb.with(ModuleMainClassAttribute.of(ClassDesc.of(mc))));

                    // write ModuleResolution attribute if specified
                    if (mres != null) {
                        clb.with(ModuleResolutionAttribute.of(mres.value()));
                    }

                    // write ModuleTarget attribute if there is a target platform
                    if (target != null && !target.targetPlatform().isEmpty()) {
                        clb.with(ModuleTargetAttribute.of(target.targetPlatform()));
                    }
                });
        } catch (IllegalArgumentException iae) {
            var t = new InvalidModuleDescriptorException(iae.getMessage());
            t.initCause(iae);
            throw t;
        }
    }

    /**
     * Writes a module descriptor to the given output stream as a
     * module-info.class.
     */
    public static void write(ModuleDescriptor descriptor,
                             ModuleResolution mres,
                             ModuleTarget target,
                             OutputStream out)
        throws IOException
    {
        byte[] bytes = toModuleInfo(descriptor, mres, target);
        out.write(bytes);
    }

    /**
     * Writes a module descriptor to the given output stream as a
     * module-info.class.
     */
    public static void write(ModuleDescriptor descriptor,
                             ModuleResolution mres,
                             OutputStream out)
        throws IOException
    {
        write(descriptor, mres, null, out);
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
        write(descriptor, null, target, out);
    }

    /**
     * Writes a module descriptor to the given output stream as a
     * module-info.class.
     */
    public static void write(ModuleDescriptor descriptor, OutputStream out)
        throws IOException
    {
        write(descriptor, null, null, out);
    }

    /**
     * Returns a byte array containing the given module descriptor in
     * module-info.class format.
     */
    public static byte[] toBytes(ModuleDescriptor descriptor) {
        return toModuleInfo(descriptor, null, null);
    }

    /**
     * Returns a {@code ByteBuffer} containing the given module descriptor
     * in module-info.class format.
     */
    public static ByteBuffer toByteBuffer(ModuleDescriptor descriptor) {
        byte[] bytes = toModuleInfo(descriptor, null, null);
        return ByteBuffer.wrap(bytes);
    }
}
