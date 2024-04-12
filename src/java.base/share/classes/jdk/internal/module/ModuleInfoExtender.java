/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.constant.ClassDesc;
import java.lang.module.ModuleDescriptor.Version;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleHashInfo;
import java.lang.classfile.attribute.ModuleHashesAttribute;
import java.lang.classfile.attribute.ModuleMainClassAttribute;
import java.lang.classfile.attribute.ModulePackagesAttribute;
import java.lang.classfile.attribute.ModuleResolutionAttribute;
import java.lang.classfile.attribute.ModuleTargetAttribute;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;


/**
 * Utility class to extend a module-info.class with additional attributes.
 */

public final class ModuleInfoExtender {

    // the input stream to read the original module-info.class
    private final InputStream in;

    // the packages in the ModulePackages attribute
    private Set<String> packages;

    // the value for the module version in the Module attribute
    private Version version;

    // the value of the ModuleMainClass attribute
    private String mainClass;

    // the value for the ModuleTarget attribute
    private String targetPlatform;

    // the hashes for the ModuleHashes attribute
    private ModuleHashes hashes;

    // the value of the ModuleResolution attribute
    private ModuleResolution moduleResolution;

    private ModuleInfoExtender(InputStream in) {
        this.in = in;
    }

    /**
     * Sets the packages for the ModulePackages attribute
     *
     * @apiNote This method does not check that the package names are legal
     * package names or that the set of packages is a super set of the
     * packages in the module.
     */
    public ModuleInfoExtender packages(Set<String> packages) {
        this.packages = Collections.unmodifiableSet(packages);
        return this;
    }

    /**
     * Sets the value for the module version in the Module attribute
     */
    public ModuleInfoExtender version(Version version) {
        this.version = version;
        return this;
    }

    /**
     * Sets the value of the ModuleMainClass attribute.
     *
     * @apiNote This method does not check that the main class is a legal
     * class name in a named package.
     */
    public ModuleInfoExtender mainClass(String mainClass) {
        this.mainClass = mainClass;
        return this;
    }

    /**
     * Sets the value for the ModuleTarget attribute.
     */
    public ModuleInfoExtender targetPlatform(String targetPlatform) {
        this.targetPlatform = targetPlatform;
        return this;
    }

    /**
     * The ModuleHashes attribute will be emitted to the module-info with
     * the hashes encapsulated in the given {@code ModuleHashes}
     * object.
     */
    public ModuleInfoExtender hashes(ModuleHashes hashes) {
        this.hashes = hashes;
        return this;
    }

    /**
     * Sets the value for the ModuleResolution attribute.
     */
    public ModuleInfoExtender moduleResolution(ModuleResolution mres) {
        this.moduleResolution = mres;
        return this;
    }

    /**
     * Outputs the modified module-info.class to the given output stream.
     * Once this method has been called then the Extender object should
     * be discarded.
     */
    public void write(OutputStream out) throws IOException {
        // emit to the output stream
        out.write(toByteArray());
    }

    /**
     * Returns the bytes of the modified module-info.class.
     * Once this method has been called then the Extender object should
     * be discarded.
     */
    public byte[] toByteArray() throws IOException {
        var cc = ClassFile.of();
        var cm = cc.parse(in.readAllBytes());
        Version v = ModuleInfoExtender.this.version;
        return cc.transform(cm, ClassTransform.endHandler(clb -> {
            // ModuleMainClass attribute
            if (mainClass != null) {
                clb.with(ModuleMainClassAttribute.of(ClassDesc.of(mainClass)));
            }

            // ModulePackages attribute
            if (packages != null) {
                List<PackageDesc> packageNames = packages.stream()
                        .sorted()
                        .map(PackageDesc::of)
                        .toList();
                clb.with(ModulePackagesAttribute.ofNames(packageNames));
            }

            // ModuleTarget, ModuleResolution and ModuleHashes attributes
            if (targetPlatform != null) {
                clb.with(ModuleTargetAttribute.of(targetPlatform));
            }
            if (moduleResolution != null) {
                clb.with(ModuleResolutionAttribute.of(moduleResolution.value()));
            }
            if (hashes != null) {
                clb.with(ModuleHashesAttribute.of(
                        hashes.algorithm(),
                        hashes.hashes().entrySet().stream().map(he ->
                                ModuleHashInfo.of(ModuleDesc.of(
                                        he.getKey()),
                                        he.getValue())).toList()));
            }
        }).andThen((clb, cle) -> {
            if (v != null && cle instanceof ModuleAttribute ma) {
                clb.with(ModuleAttribute.of(
                        ma.moduleName(),
                        ma.moduleFlagsMask(),
                        clb.constantPool().utf8Entry(v.toString()),
                        ma.requires(),
                        ma.exports(),
                        ma.opens(),
                        ma.uses(),
                        ma.provides()));
            } else {
                clb.accept(cle);
            }
        }));
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
