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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.internal.PoolImpl;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;
import jdk.tools.jlink.plugin.Pool.ModuleDataType;
import jdk.tools.jlink.plugin.TransformerPlugin;
import jdk.tools.jlink.internal.Utils;

/**
 *
 * ZIP Compression plugin
 */
public final class ZipPlugin implements TransformerPlugin {

    public static final String NAME = "zip";
    private Predicate<String> predicate;

    public ZipPlugin() {

    }

    ZipPlugin(String[] patterns) throws IOException {
        this(new ResourceFilter(patterns));
    }

    ZipPlugin(Predicate<String> predicate) {
        this.predicate = predicate;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Set<PluginType> getType() {
        Set<PluginType> set = new HashSet<>();
        set.add(CATEGORY.COMPRESSOR);
        return Collections.unmodifiableSet(set);
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public boolean hasArguments() {
        return false;
    }

    @Override
    public String getArgumentsDescription() {
        return PluginsResourceBundle.getArgument(NAME);
    }

    @Override
    public void configure(Map<String, String> config) {
        try {
            String val = config.get(NAME);
            predicate = new ResourceFilter(Utils.listParser.apply(val));
        } catch (IOException ex) {
            throw new PluginException(ex);
        }
    }

    static byte[] compress(byte[] bytesIn) {
        Deflater deflater = new Deflater();
        deflater.setInput(bytesIn);
        ByteArrayOutputStream stream = new ByteArrayOutputStream(bytesIn.length);
        byte[] buffer = new byte[1024];

        deflater.finish();
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            stream.write(buffer, 0, count);
        }

        try {
            stream.close();
        } catch (IOException ex) {
            return bytesIn;
        }

        byte[] bytesOut = stream.toByteArray();
        deflater.end();

        return bytesOut;
    }

    @Override
    public void visit(Pool in, Pool out) {
        in.visit((resource) -> {
            ModuleData res = resource;
            if (resource.getType().equals(ModuleDataType.CLASS_OR_RESOURCE)
                    && predicate.test(resource.getPath())) {
                byte[] compressed;
                compressed = compress(resource.getBytes());
                res = PoolImpl.newCompressedResource(resource,
                        ByteBuffer.wrap(compressed), getName(), null,
                        ((PoolImpl) in).getStringTable(), in.getByteOrder());
            }
            return res;
        }, out);
    }
}
