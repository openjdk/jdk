/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Version;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.internal.misc.JavaLangModuleAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ByteVector;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import static jdk.internal.module.ClassFileConstants.*;


/**
 * Provides ASM implementations of {@code Attribute} to read and write the
 * class file attributes in a module-info class file.
 */

public final class ClassFileAttributes {

    private ClassFileAttributes() { }

    /**
     * Module_attribute {
     *   // See lang-vm.html for details.
     * }
     */
    public static class ModuleAttribute extends Attribute {
        private static final JavaLangModuleAccess JLMA
            = SharedSecrets.getJavaLangModuleAccess();

        private ModuleDescriptor descriptor;

        public ModuleAttribute(ModuleDescriptor descriptor) {
            super(MODULE);
            this.descriptor = descriptor;
        }

        public ModuleAttribute() {
            super(MODULE);
        }

        @Override
        protected Attribute read(ClassReader cr,
                                 int off,
                                 int len,
                                 char[] buf,
                                 int codeOff,
                                 Label[] labels)
        {
            ModuleAttribute attr = new ModuleAttribute();

            // module_name
            String mn = cr.readUTF8(off, buf).replace('/', '.');
            off += 2;

            // module_flags
            int module_flags = cr.readUnsignedShort(off);
            boolean open = ((module_flags & ACC_OPEN) != 0);
            off += 2;

            ModuleDescriptor.Builder builder;
            if (open) {
                builder = JLMA.newOpenModuleBuilder(mn, false);
            } else {
                builder = JLMA.newModuleBuilder(mn, false);
            }

            // requires_count and requires[requires_count]
            int requires_count = cr.readUnsignedShort(off);
            off += 2;
            for (int i=0; i<requires_count; i++) {
                String dn = cr.readUTF8(off, buf).replace('/', '.');
                int flags = cr.readUnsignedShort(off + 2);
                Set<Requires.Modifier> mods;
                if (flags == 0) {
                    mods = Collections.emptySet();
                } else {
                    mods = new HashSet<>();
                    if ((flags & ACC_TRANSITIVE) != 0)
                        mods.add(Requires.Modifier.TRANSITIVE);
                    if ((flags & ACC_STATIC_PHASE) != 0)
                        mods.add(Requires.Modifier.STATIC);
                    if ((flags & ACC_SYNTHETIC) != 0)
                        mods.add(Requires.Modifier.SYNTHETIC);
                    if ((flags & ACC_MANDATED) != 0)
                        mods.add(Requires.Modifier.MANDATED);
                }
                builder.requires(mods, dn);
                off += 4;
            }

            // exports_count and exports[exports_count]
            int exports_count = cr.readUnsignedShort(off);
            off += 2;
            if (exports_count > 0) {
                for (int i=0; i<exports_count; i++) {
                    String pkg = cr.readUTF8(off, buf).replace('/', '.');
                    off += 2;

                    int flags = cr.readUnsignedShort(off);
                    off += 2;
                    Set<Exports.Modifier> mods;
                    if (flags == 0) {
                        mods = Collections.emptySet();
                    } else {
                        mods = new HashSet<>();
                        if ((flags & ACC_SYNTHETIC) != 0)
                            mods.add(Exports.Modifier.SYNTHETIC);
                        if ((flags & ACC_MANDATED) != 0)
                            mods.add(Exports.Modifier.MANDATED);
                    }

                    int exports_to_count = cr.readUnsignedShort(off);
                    off += 2;
                    if (exports_to_count > 0) {
                        Set<String> targets = new HashSet<>();
                        for (int j=0; j<exports_to_count; j++) {
                            String t = cr.readUTF8(off, buf).replace('/', '.');
                            off += 2;
                            targets.add(t);
                        }
                        builder.exports(mods, pkg, targets);
                    } else {
                        builder.exports(mods, pkg);
                    }
                }
            }

            // opens_count and opens[opens_count]
            int open_count = cr.readUnsignedShort(off);
            off += 2;
            if (open_count > 0) {
                for (int i=0; i<open_count; i++) {
                    String pkg = cr.readUTF8(off, buf).replace('/', '.');
                    off += 2;

                    int flags = cr.readUnsignedShort(off);
                    off += 2;
                    Set<Opens.Modifier> mods;
                    if (flags == 0) {
                        mods = Collections.emptySet();
                    } else {
                        mods = new HashSet<>();
                        if ((flags & ACC_SYNTHETIC) != 0)
                            mods.add(Opens.Modifier.SYNTHETIC);
                        if ((flags & ACC_MANDATED) != 0)
                            mods.add(Opens.Modifier.MANDATED);
                    }

                    int opens_to_count = cr.readUnsignedShort(off);
                    off += 2;
                    if (opens_to_count > 0) {
                        Set<String> targets = new HashSet<>();
                        for (int j=0; j<opens_to_count; j++) {
                            String t = cr.readUTF8(off, buf).replace('/', '.');
                            off += 2;
                            targets.add(t);
                        }
                        builder.opens(mods, pkg, targets);
                    } else {
                        builder.opens(mods, pkg);
                    }
                }
            }

            // uses_count and uses_index[uses_count]
            int uses_count = cr.readUnsignedShort(off);
            off += 2;
            if (uses_count > 0) {
                for (int i=0; i<uses_count; i++) {
                    String sn = cr.readClass(off, buf).replace('/', '.');
                    builder.uses(sn);
                    off += 2;
                }
            }

            // provides_count and provides[provides_count]
            int provides_count = cr.readUnsignedShort(off);
            off += 2;
            if (provides_count > 0) {
                for (int i=0; i<provides_count; i++) {
                    String service = cr.readClass(off, buf).replace('/', '.');
                    off += 2;
                    int with_count = cr.readUnsignedShort(off);
                    off += 2;
                    List<String> providers = new ArrayList<>();
                    for (int j=0; j<with_count; j++) {
                        String cn = cr.readClass(off, buf).replace('/', '.');
                        off += 2;
                        providers.add(cn);
                    }
                    builder.provides(service, providers);
                }
            }

            attr.descriptor = builder.build();
            return attr;
        }

        @Override
        protected ByteVector write(ClassWriter cw,
                                   byte[] code,
                                   int len,
                                   int maxStack,
                                   int maxLocals)
        {
            assert descriptor != null;
            ByteVector attr = new ByteVector();

            // module_name
            String mn = descriptor.name();
            int module_name_index = cw.newUTF8(mn.replace('.', '/'));
            attr.putShort(module_name_index);

            // module_flags
            int module_flags = 0;
            if (descriptor.isOpen())
                module_flags |= ACC_OPEN;
            if (descriptor.isSynthetic())
                module_flags |= ACC_SYNTHETIC;
            attr.putShort(module_flags);

            // requires_count
            attr.putShort(descriptor.requires().size());

            // requires[requires_count]
            for (Requires md : descriptor.requires()) {
                String dn = md.name();
                int flags = 0;
                if (md.modifiers().contains(Requires.Modifier.TRANSITIVE))
                    flags |= ACC_TRANSITIVE;
                if (md.modifiers().contains(Requires.Modifier.STATIC))
                    flags |= ACC_STATIC_PHASE;
                if (md.modifiers().contains(Requires.Modifier.SYNTHETIC))
                    flags |= ACC_SYNTHETIC;
                if (md.modifiers().contains(Requires.Modifier.MANDATED))
                    flags |= ACC_MANDATED;
                int index = cw.newUTF8(dn.replace('.', '/'));
                attr.putShort(index);
                attr.putShort(flags);
            }

            // exports_count and exports[exports_count];
            attr.putShort(descriptor.exports().size());
            for (Exports e : descriptor.exports()) {
                String pkg = e.source().replace('.', '/');
                attr.putShort(cw.newUTF8(pkg));

                int flags = 0;
                if (e.modifiers().contains(Exports.Modifier.SYNTHETIC))
                    flags |= ACC_SYNTHETIC;
                if (e.modifiers().contains(Exports.Modifier.MANDATED))
                    flags |= ACC_MANDATED;
                attr.putShort(flags);

                if (e.isQualified()) {
                    Set<String> ts = e.targets();
                    attr.putShort(ts.size());
                    ts.forEach(t -> attr.putShort(cw.newUTF8(t.replace('.', '/'))));
                } else {
                    attr.putShort(0);
                }
            }


            // opens_counts and opens[opens_counts]
            attr.putShort(descriptor.opens().size());
            for (Opens obj : descriptor.opens()) {
                String pkg = obj.source().replace('.', '/');
                attr.putShort(cw.newUTF8(pkg));

                int flags = 0;
                if (obj.modifiers().contains(Opens.Modifier.SYNTHETIC))
                    flags |= ACC_SYNTHETIC;
                if (obj.modifiers().contains(Opens.Modifier.MANDATED))
                    flags |= ACC_MANDATED;
                attr.putShort(flags);

                if (obj.isQualified()) {
                    Set<String> ts = obj.targets();
                    attr.putShort(ts.size());
                    ts.forEach(t -> attr.putShort(cw.newUTF8(t.replace('.', '/'))));
                } else {
                    attr.putShort(0);
                }
            }

            // uses_count and uses_index[uses_count]
            if (descriptor.uses().isEmpty()) {
                attr.putShort(0);
            } else {
                attr.putShort(descriptor.uses().size());
                for (String s : descriptor.uses()) {
                    String service = s.replace('.', '/');
                    int index = cw.newClass(service);
                    attr.putShort(index);
                }
            }

            // provides_count and provides[provides_count]
            if (descriptor.provides().isEmpty()) {
                attr.putShort(0);
            } else {
                attr.putShort(descriptor.provides().size());
                for (Provides p : descriptor.provides()) {
                    String service = p.service().replace('.', '/');
                    attr.putShort(cw.newClass(service));
                    int with_count = p.providers().size();
                    attr.putShort(with_count);
                    for (String provider : p.providers()) {
                        attr.putShort(cw.newClass(provider.replace('.', '/')));
                    }
                }
            }

            return attr;
        }
    }

    /**
     * ModulePackages attribute.
     *
     * <pre> {@code
     *
     * ModulePackages_attribute {
     *   // index to CONSTANT_utf8_info structure in constant pool representing
     *   // the string "ModulePackages"
     *   u2 attribute_name_index;
     *   u4 attribute_length;
     *
     *   // the number of entries in the packages table
     *   u2 packages_count;
     *   { // index to CONSTANT_CONSTANT_utf8_info structure with the package name
     *     u2 package_index
     *   } packages[package_count];
     *
     * }</pre>
     */
    public static class ModulePackagesAttribute extends Attribute {
        private final Set<String> packages;

        public ModulePackagesAttribute(Set<String> packages) {
            super(MODULE_PACKAGES);
            this.packages = packages;
        }

        public ModulePackagesAttribute() {
            this(null);
        }

        @Override
        protected Attribute read(ClassReader cr,
                                 int off,
                                 int len,
                                 char[] buf,
                                 int codeOff,
                                 Label[] labels)
        {
            // package count
            int package_count = cr.readUnsignedShort(off);
            off += 2;

            // packages
            Set<String> packages = new HashSet<>();
            for (int i=0; i<package_count; i++) {
                String pkg = cr.readUTF8(off, buf).replace('/', '.');
                packages.add(pkg);
                off += 2;
            }

            return new ModulePackagesAttribute(packages);
        }

        @Override
        protected ByteVector write(ClassWriter cw,
                                   byte[] code,
                                   int len,
                                   int maxStack,
                                   int maxLocals)
        {
            assert packages != null;

            ByteVector attr = new ByteVector();

            // package_count
            attr.putShort(packages.size());

            // packages
            packages.stream()
                .map(p -> p.replace('.', '/'))
                .forEach(p -> attr.putShort(cw.newUTF8(p)));

            return attr;
        }

    }

    /**
     * ModuleVersion attribute.
     *
     * <pre> {@code
     *
     * ModuleVersion_attribute {
     *   // index to CONSTANT_utf8_info structure in constant pool representing
     *   // the string "ModuleVersion"
     *   u2 attribute_name_index;
     *   u4 attribute_length;
     *
     *   // index to CONSTANT_CONSTANT_utf8_info structure with the version
     *   u2 version_index;
     * }
     *
     * } </pre>
     */
    public static class ModuleVersionAttribute extends Attribute {
        private final Version version;

        public ModuleVersionAttribute(Version version) {
            super(MODULE_VERSION);
            this.version = version;
        }

        public ModuleVersionAttribute() {
            this(null);
        }

        @Override
        protected Attribute read(ClassReader cr,
                                 int off,
                                 int len,
                                 char[] buf,
                                 int codeOff,
                                 Label[] labels)
        {
            String value = cr.readUTF8(off, buf);
            return new ModuleVersionAttribute(Version.parse(value));
        }

        @Override
        protected ByteVector write(ClassWriter cw,
                                   byte[] code,
                                   int len,
                                   int maxStack,
                                   int maxLocals)
        {
            ByteVector attr = new ByteVector();
            int index = cw.newUTF8(version.toString());
            attr.putShort(index);
            return attr;
        }
    }

    /**
     * ModuleMainClass attribute.
     *
     * <pre> {@code
     *
     * MainClass_attribute {
     *   // index to CONSTANT_utf8_info structure in constant pool representing
     *   // the string "ModuleMainClass"
     *   u2 attribute_name_index;
     *   u4 attribute_length;
     *
     *   // index to CONSTANT_Class_info structure with the main class name
     *   u2 main_class_index;
     * }
     *
     * } </pre>
     */
    public static class ModuleMainClassAttribute extends Attribute {
        private final String mainClass;

        public ModuleMainClassAttribute(String mainClass) {
            super(MODULE_MAIN_CLASS);
            this.mainClass = mainClass;
        }

        public ModuleMainClassAttribute() {
            this(null);
        }

        @Override
        protected Attribute read(ClassReader cr,
                                 int off,
                                 int len,
                                 char[] buf,
                                 int codeOff,
                                 Label[] labels)
        {
            String value = cr.readClass(off, buf);
            return new ModuleMainClassAttribute(value);
        }

        @Override
        protected ByteVector write(ClassWriter cw,
                                   byte[] code,
                                   int len,
                                   int maxStack,
                                   int maxLocals)
        {
            ByteVector attr = new ByteVector();
            int index = cw.newClass(mainClass);
            attr.putShort(index);
            return attr;
        }
    }

    /**
     * ModuleTarget attribute.
     *
     * <pre> {@code
     *
     * TargetPlatform_attribute {
     *   // index to CONSTANT_utf8_info structure in constant pool representing
     *   // the string "ModuleTarget"
     *   u2 attribute_name_index;
     *   u4 attribute_length;
     *
     *   // index to CONSTANT_CONSTANT_utf8_info structure with the OS name
     *   u2 os_name_index;
     *   // index to CONSTANT_CONSTANT_utf8_info structure with the OS arch
     *   u2 os_arch_index
     *   // index to CONSTANT_CONSTANT_utf8_info structure with the OS version
     *   u2 os_version_index;
     * }
     *
     * } </pre>
     */
    public static class ModuleTargetAttribute extends Attribute {
        private final String osName;
        private final String osArch;
        private final String osVersion;

        public ModuleTargetAttribute(String osName, String osArch, String osVersion) {
            super(MODULE_TARGET);
            this.osName = osName;
            this.osArch = osArch;
            this.osVersion = osVersion;
        }

        public ModuleTargetAttribute() {
            this(null, null, null);
        }

        @Override
        protected Attribute read(ClassReader cr,
                                 int off,
                                 int len,
                                 char[] buf,
                                 int codeOff,
                                 Label[] labels)
        {

            String osName = null;
            String osArch = null;
            String osVersion = null;

            int name_index = cr.readUnsignedShort(off);
            if (name_index != 0)
                osName = cr.readUTF8(off, buf);
            off += 2;

            int arch_index = cr.readUnsignedShort(off);
            if (arch_index != 0)
                osArch = cr.readUTF8(off, buf);
            off += 2;

            int version_index = cr.readUnsignedShort(off);
            if (version_index != 0)
                osVersion = cr.readUTF8(off, buf);
            off += 2;

            return new ModuleTargetAttribute(osName, osArch, osVersion);
        }

        @Override
        protected ByteVector write(ClassWriter cw,
                                   byte[] code,
                                   int len,
                                   int maxStack,
                                   int maxLocals)
        {
            ByteVector attr = new ByteVector();

            int name_index = 0;
            if (osName != null && osName.length() > 0)
                name_index = cw.newUTF8(osName);
            attr.putShort(name_index);

            int arch_index = 0;
            if (osArch != null && osArch.length() > 0)
                arch_index = cw.newUTF8(osArch);
            attr.putShort(arch_index);

            int version_index = 0;
            if (osVersion != null && osVersion.length() > 0)
                version_index = cw.newUTF8(osVersion);
            attr.putShort(version_index);

            return attr;
        }
    }

    /**
     * ModuleHashes attribute.
     *
     * <pre> {@code
     *
     * ModuleHashes_attribute {
     *   // index to CONSTANT_utf8_info structure in constant pool representing
     *   // the string "ModuleHashes"
     *   u2 attribute_name_index;
     *   u4 attribute_length;
     *
     *   // index to CONSTANT_utf8_info structure with algorithm name
     *   u2 algorithm_index;
     *
     *   // the number of entries in the hashes table
     *   u2 hashes_count;
     *   {   u2 module_name_index
     *       u2 hash_length;
     *       u1 hash[hash_length];
     *   } hashes[hashes_count];
     *
     * } </pre>
     */
    static class ModuleHashesAttribute extends Attribute {
        private final ModuleHashes hashes;

        ModuleHashesAttribute(ModuleHashes hashes) {
            super(MODULE_HASHES);
            this.hashes = hashes;
        }

        ModuleHashesAttribute() {
            this(null);
        }

        @Override
        protected Attribute read(ClassReader cr,
                                 int off,
                                 int len,
                                 char[] buf,
                                 int codeOff,
                                 Label[] labels)
        {
            String algorithm = cr.readUTF8(off, buf);
            off += 2;

            int hashes_count = cr.readUnsignedShort(off);
            off += 2;

            Map<String, byte[]> map = new HashMap<>();
            for (int i=0; i<hashes_count; i++) {
                String mn = cr.readUTF8(off, buf).replace('/', '.');
                off += 2;

                int hash_length = cr.readUnsignedShort(off);
                off += 2;
                byte[] hash = new byte[hash_length];
                for (int j=0; j<hash_length; j++) {
                    hash[j] = (byte) (0xff & cr.readByte(off+j));
                }
                off += hash_length;

                map.put(mn, hash);
            }

            ModuleHashes hashes = new ModuleHashes(algorithm, map);

            return new ModuleHashesAttribute(hashes);
        }

        @Override
        protected ByteVector write(ClassWriter cw,
                                   byte[] code,
                                   int len,
                                   int maxStack,
                                   int maxLocals)
        {
            ByteVector attr = new ByteVector();

            int index = cw.newUTF8(hashes.algorithm());
            attr.putShort(index);

            Set<String> names = hashes.names();
            attr.putShort(names.size());

            for (String mn : names) {
                byte[] hash = hashes.hashFor(mn);
                assert hash != null;
                attr.putShort(cw.newUTF8(mn.replace('.', '/')));

                attr.putShort(hash.length);
                for (byte b: hash) {
                    attr.putByte(b);
                }
            }

            return attr;
        }
    }

}
