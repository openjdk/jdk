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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.internal.PoolImpl;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.TransformerPlugin;
import jdk.tools.jlink.internal.ImagePluginStack;
import jdk.tools.jlink.internal.ResourcePrevisitor;
import jdk.tools.jlink.internal.StringTable;
import jdk.tools.jlink.internal.Utils;

/**
 *
 * ZIP and String Sharing compression plugin
 */
public final class DefaultCompressPlugin implements TransformerPlugin, ResourcePrevisitor {
    public static final String NAME = "compress";
    public static final String FILTER = "filter";
    public static final String LEVEL_0 = "0";
    public static final String LEVEL_1 = "1";
    public static final String LEVEL_2 = "2";

    private StringSharingPlugin ss;
    private ZipPlugin zip;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void visit(Pool in, Pool out) {
        if (ss != null && zip != null) {
            Pool output = new ImagePluginStack.OrderedResourcePool(in.getByteOrder(),
                    ((PoolImpl) in).getStringTable());
            ss.visit(in, output);
            zip.visit(output, out);
        } else if (ss != null) {
            ss.visit(in, out);
        } else if (zip != null) {
            zip.visit(in, out);
        }
    }

    @Override
    public void previsit(Pool resources, StringTable strings) {
        if (ss != null) {
            ss.previsit(resources, strings);
        }
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
        return true;
    }

    @Override
    public String getArgumentsDescription() {
       return PluginsResourceBundle.getArgument(NAME);
    }

    @Override
    public void configure(Map<String, String> config) {
        try {
            String filter = config.get(FILTER);
            String[] patterns = filter == null ? null
                    : Utils.listParser.apply(filter);
            ResourceFilter resFilter = new ResourceFilter(patterns);
            String level = config.get(NAME);
            if (level != null) {
                switch (level) {
                    case LEVEL_0:
                        ss = new StringSharingPlugin(resFilter);
                        break;
                    case LEVEL_1:
                        zip = new ZipPlugin(resFilter);
                        break;
                    case LEVEL_2:
                        ss = new StringSharingPlugin(resFilter);
                        zip = new ZipPlugin(resFilter);
                        break;
                    default:
                        throw new PluginException("Invalid level " + level);
                }
            } else {
                ss = new StringSharingPlugin(resFilter);
                zip = new ZipPlugin(resFilter);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
