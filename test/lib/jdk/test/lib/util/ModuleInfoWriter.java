/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.constant.ClassDesc;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.AccessFlag;
import java.nio.ByteBuffer;
import java.util.Map;
import java.lang.classfile.ClassFile;
import java.lang.constant.PackageDesc;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleExportInfo;
import java.lang.classfile.attribute.ModuleMainClassAttribute;
import java.lang.classfile.attribute.ModuleOpenInfo;
import java.lang.classfile.attribute.ModulePackagesAttribute;
import java.lang.classfile.attribute.ModuleResolutionAttribute;
import java.lang.classfile.attribute.ModuleRequireInfo;
import java.lang.classfile.attribute.ModuleTargetAttribute;
import java.lang.classfile.constantpool.ModuleEntry;
import jdk.internal.module.ModuleResolution;
import jdk.internal.module.ModuleTarget;

/**
 * Utility class to write a ModuleDescriptor as a module-info.class.
 */

public final class ModuleInfoWriter {

    private static final Map<ModuleDescriptor.Modifier, Integer>
        MODULE_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Modifier.OPEN, ClassFile.ACC_OPEN,
            ModuleDescriptor.Modifier.SYNTHETIC, ClassFile.ACC_SYNTHETIC,
            ModuleDescriptor.Modifier.MANDATED, ClassFile.ACC_MANDATED
        );

    private static final Map<ModuleDescriptor.Requires.Modifier, Integer>
        REQUIRES_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Requires.Modifier.TRANSITIVE, ClassFile.ACC_TRANSITIVE,
            ModuleDescriptor.Requires.Modifier.STATIC, ClassFile.ACC_STATIC_PHASE,
            ModuleDescriptor.Requires.Modifier.SYNTHETIC, ClassFile.ACC_SYNTHETIC,
            ModuleDescriptor.Requires.Modifier.MANDATED, ClassFile.ACC_MANDATED
        );

    private static final Map<ModuleDescriptor.Exports.Modifier, Integer>
        EXPORTS_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Exports.Modifier.SYNTHETIC, ClassFile.ACC_SYNTHETIC,
            ModuleDescriptor.Exports.Modifier.MANDATED, ClassFile.ACC_MANDATED
        );

    private static final Map<ModuleDescriptor.Opens.Modifier, Integer>
        OPENS_MODS_TO_FLAGS = Map.of(
            ModuleDescriptor.Opens.Modifier.SYNTHETIC, ClassFile.ACC_SYNTHETIC,
            ModuleDescriptor.Opens.Modifier.MANDATED, ClassFile.ACC_MANDATED
        );

    private ModuleInfoWriter() { }

    /**
     * Writes the given module descriptor to a module-info.class file,
     * returning it in a byte array.
     */
    private static byte[] toModuleInfo(ModuleDescriptor md,
                                       ModuleResolution mres,
                                       ModuleTarget target) {
        //using low-level module building to avoid validation in ModuleDesc and allow invalid names
        return ClassFile.of().build(ClassDesc.of("module-info"), clb -> {
            clb.withFlags(AccessFlag.MODULE);
            var cp = clb.constantPool();
            clb.with(ModuleAttribute.of(cp.moduleEntry(cp.utf8Entry(md.name())), mb -> {
                    mb.moduleFlags(md.modifiers().stream()
                            .mapToInt(mm -> MODULE_MODS_TO_FLAGS.getOrDefault(mm, 0))
                            .reduce(0, (x, y) -> (x | y)));

                    md.rawVersion().ifPresent(vs -> mb.moduleVersion(vs));

                    // requires
                    for (ModuleDescriptor.Requires r : md.requires()) {
                        int flags = r.modifiers().stream()
                                .mapToInt(REQUIRES_MODS_TO_FLAGS::get)
                                .reduce(0, (x, y) -> (x | y));
                        mb.requires(ModuleRequireInfo.of(
                                cp.moduleEntry(cp.utf8Entry(r.name())),
                                flags,
                                r.rawCompiledVersion().map(cp::utf8Entry).orElse(null)));
                    }

                    // exports
                    for (ModuleDescriptor.Exports e : md.exports()) {
                        int flags = e.modifiers().stream()
                                .mapToInt(EXPORTS_MODS_TO_FLAGS::get)
                                .reduce(0, (x, y) -> (x | y));
                        var targets = e.targets().stream().map(mn -> cp.moduleEntry(cp.utf8Entry(mn)))
                                .toArray(ModuleEntry[]::new);
                        mb.exports(ModuleExportInfo.of(
                                cp.packageEntry(cp.utf8Entry(e.source())),
                                flags,
                                targets));
                    }

                    // opens
                    for (ModuleDescriptor.Opens opens : md.opens()) {
                        int flags = opens.modifiers().stream()
                                .mapToInt(OPENS_MODS_TO_FLAGS::get)
                                .reduce(0, (x, y) -> (x | y));
                        var targets = opens.targets().stream().map(mn -> cp.moduleEntry(cp.utf8Entry(mn)))
                                .toArray(ModuleEntry[]::new);
                        mb.opens(ModuleOpenInfo.of(
                                cp.packageEntry(cp.utf8Entry(opens.source())),
                                flags,
                                targets));
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
                }));

                // packages
                var packages = md.packages().stream().sorted().map(PackageDesc::of).toList();
                if (!packages.isEmpty()) {
                    clb.with(ModulePackagesAttribute.ofNames(packages));
                }

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
