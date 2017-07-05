/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;
import jdk.tools.jlink.plugin.Pool.ModuleDataType;
import jdk.tools.jlink.plugin.TransformerPlugin;

/**
 *
 * Strip debug attributes plugin
 */
public final class StripDebugPlugin implements TransformerPlugin {
    private static final String[] PATTERNS = {"*.diz"};
    public static final String NAME = "strip-debug";
    private final Predicate<String> predicate;
    public StripDebugPlugin() {
        try {
            predicate = new ResourceFilter(PATTERNS);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Set<PluginType> getType() {
        Set<PluginType> set = new HashSet<>();
        set.add(CATEGORY.TRANSFORMER);
        return Collections.unmodifiableSet(set);
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public void visit(Pool in, Pool out) {
        //remove *.diz files as well as debug attributes.
        in.visit((resource) -> {
            ModuleData res = resource;
            if (resource.getType().equals(ModuleDataType.CLASS_OR_RESOURCE)) {
                String path = resource.getPath();
                if (path.endsWith(".class")) {
                    if (path.endsWith("module-info.class")) {
                        // XXX. Do we have debug info? Is Asm ready for module-info?
                    } else {
                        ClassReader reader = new ClassReader(resource.getBytes());
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        reader.accept(writer, ClassReader.SKIP_DEBUG);
                        byte[] content = writer.toByteArray();
                        res = Pool.newResource(path, new ByteArrayInputStream(content), content.length);
                    }
                }
            } else if (predicate.test(res.getPath())) {
                res = null;
            }
            return res;
        }, out);
    }
}
