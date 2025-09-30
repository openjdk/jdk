/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins;

import java.util.function.Predicate;
import java.lang.IllegalArgumentException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.attribute.SourceDebugExtensionAttribute;
import java.util.Map;

import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 *
 * Strip java debug attributes plugin
 *
 * Usage: --strip-java-debug-attributes(=+parameter_names)
 */
public final class StripJavaDebugAttributesPlugin extends AbstractPlugin {
    public static final String NAME = "strip-java-debug-attributes";
    public static final String DROP_METHOD_PARAMETER_NAMES = "+parameter-names";

    private final Predicate<String> predicate;
    private boolean isDroppingMethodNames;

    public StripJavaDebugAttributesPlugin() {
        this((path) -> false);
    }

    StripJavaDebugAttributesPlugin(Predicate<String> predicate) {
        super(NAME);
        this.predicate = predicate;
        isDroppingMethodNames = false;
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public boolean hasRawArgument() {
        return true;
    }

    @Override
    public boolean isArgumentOptional() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        var rawArg = config.get(NAME);
        if (rawArg != null) {
            if (rawArg.isEmpty()) {
                return;
            } else if (rawArg.equals(DROP_METHOD_PARAMETER_NAMES)) {
                isDroppingMethodNames = true;
            } else {
                // We only support one value for now, other values is illegal.
                throw new IllegalArgumentException(
                        PluginsResourceBundle.getMessage("err.illegal.argument", rawArg));
            }
        }
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        //remove *.diz files as well as debug attributes.
        in.transformAndCopy((resource) -> {
            ResourcePoolEntry res = resource;
            if (resource.type().equals(ResourcePoolEntry.Type.CLASS_OR_RESOURCE)) {
                String path = resource.path();
                if (path.endsWith(".class")) {
                    if (path.endsWith("module-info.class")) {
                        // XXX. Do we have debug info?
                    } else {
                        var clm = newClassReader(path, resource,
                                ClassFile.DebugElementsOption.DROP_DEBUG,
                                ClassFile.LineNumbersOption.DROP_LINE_NUMBERS);

                        MethodTransform mt;
                        if (isDroppingMethodNames) {
                            mt = MethodTransform.dropping(me -> me instanceof MethodParametersAttribute)
                                    .andThen(MethodTransform.transformingCode(CodeTransform.ACCEPT_ALL));
                        } else {
                            mt = MethodTransform.transformingCode(CodeTransform.ACCEPT_ALL);
                        }

                        byte[] content = ClassFile.of().transformClass(clm, ClassTransform
                                        .dropping(cle -> cle instanceof SourceFileAttribute
                                                            || cle instanceof SourceDebugExtensionAttribute)
                                              .andThen(ClassTransform.transformingMethods(mt)));
                        res = resource.copyWithContent(content);
                    }
                }
            } else if (predicate.test(res.path())) {
                res = null;
            }
            return res;
        }, out);

        return out.build();
    }
}
