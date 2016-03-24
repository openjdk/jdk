/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.module.ModuleDescriptor.Version;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.module.Hasher.DependencyHashes;

import static jdk.internal.module.ClassFileAttributes.*;

/**
 * Utility class to extend a module-info.class with additional attributes.
 */

public final class ModuleInfoExtender {

    // the input stream to read the original module-info.class
    private final InputStream in;

    // the packages in the ConcealedPackages attribute
    private Set<String> conceals;

    // the value of the Version attribute
    private Version version;

    // the value of the MainClass attribute
    private String mainClass;

    // the values for the TargetPlatform attribute
    private String osName;
    private String osArch;
    private String osVersion;

    // the hashes for the Hashes attribute
    private DependencyHashes hashes;

    private ModuleInfoExtender(InputStream in) {
        this.in = in;
    }

    /**
     * Sets the set of packages for the ConcealedPackages attribute
     */
    public ModuleInfoExtender conceals(Set<String> packages) {
        this.conceals = Collections.unmodifiableSet(packages);
        return this;
    }

    /**
     * Sets the value of the Version attribute.
     */
    public ModuleInfoExtender version(Version version) {
        this.version = version;
        return this;
    }

    /**
     * Sets the value of the MainClass attribute.
     */
    public ModuleInfoExtender mainClass(String mainClass) {
        this.mainClass = mainClass;
        return this;
    }

    /**
     * Sets the values for the TargetPlatform attribute.
     */
    public ModuleInfoExtender targetPlatform(String osName,
                                             String osArch,
                                             String osVersion) {
        this.osName = osName;
        this.osArch = osArch;
        this.osVersion = osVersion;
        return this;
    }

    /**
     * The Hashes attribute will be emitted to the module-info with
     * the hashes encapsulated in the given {@code DependencyHashes}
     * object.
     */
    public ModuleInfoExtender hashes(DependencyHashes hashes) {
        this.hashes = hashes;
        return this;
    }

    /**
     * A ClassVisitor that supports adding class file attributes. If an
     * attribute already exists then the first occurence of the attribute
     * is replaced.
     */
    private static class AttributeAddingClassVisitor extends ClassVisitor {
        private Map<String, Attribute> attrs = new HashMap<>();

        AttributeAddingClassVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        void addAttribute(Attribute attr) {
            attrs.put(attr.type, attr);
        }

        @Override
        public void visitAttribute(Attribute attr) {
            String name = attr.type;
            Attribute replacement = attrs.get(name);
            if (replacement != null) {
                attr = replacement;
                attrs.remove(name);
            }
            super.visitAttribute(attr);
        }

        /**
         * Adds any remaining attributes that weren't replaced to the
         * class file.
         */
        void finish() {
            attrs.values().forEach(a -> super.visitAttribute(a));
            attrs.clear();
        }
    }

    /**
     * Outputs the modified module-info.class to the given output stream.
     * Once this method has been called then the Extender object should
     * be discarded.
     */
    public void write(OutputStream out) throws IOException {
        ClassWriter cw
            = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);

        AttributeAddingClassVisitor cv
            = new AttributeAddingClassVisitor(Opcodes.ASM5, cw);

        ClassReader cr = new ClassReader(in);

        if (conceals != null)
            cv.addAttribute(new ConcealedPackagesAttribute(conceals));
        if (version != null)
            cv.addAttribute(new VersionAttribute(version));
        if (mainClass != null)
            cv.addAttribute(new MainClassAttribute(mainClass));
        if (osName != null || osArch != null || osVersion != null)
            cv.addAttribute(new TargetPlatformAttribute(osName, osArch, osVersion));
        if (hashes != null)
            cv.addAttribute(new HashesAttribute(hashes));

        List<Attribute> attrs = new ArrayList<>();

        // prototypes of attributes that should be parsed
        attrs.add(new ModuleAttribute());
        attrs.add(new ConcealedPackagesAttribute());
        attrs.add(new VersionAttribute());
        attrs.add(new MainClassAttribute());
        attrs.add(new TargetPlatformAttribute());
        attrs.add(new HashesAttribute());

        cr.accept(cv, attrs.toArray(new Attribute[0]), 0);

        // add any attributes that didn't replace previous attributes
        cv.finish();

        // emit to the output stream
        out.write(cw.toByteArray());
    }

    /**
     * Returns an {@code Extender} that may be used to add additional
     * attributes to the module-info.class read from the given input
     * stream.
     */
    public static ModuleInfoExtender newExtender(InputStream in) {
        return new ModuleInfoExtender(in);
    }

}
