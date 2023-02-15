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

import java.util.Map;

import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.internal.ResourcePrevisitor;
import jdk.tools.jlink.internal.StringTable;

/**
 *
 * ZIP and String Sharing compression plugin
 */
public final class ShareUTF8EntriesPlugin extends AbstractPlugin implements ResourcePrevisitor {

    private static final String FILTER = "filter";
    private StringSharingPlugin ss;

    public ShareUTF8EntriesPlugin() {
        super("share-utf8-entries");
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        return ss.transform(in, out);
    }

    @Override
    public void previsit(ResourcePool resources, StringTable strings) {
        if (ss != null) {
            ss.previsit(resources, strings);
        }
    }

    @Override
    public Category getType() {
        return Category.IMAGE_TRANSFORMER;
    }

    @Override
    public boolean hasArguments() {
        return false;
    }

    @Override
    public void configure(Map<String, String> config) {
        ResourceFilter resFilter = ResourceFilter.includeFilter(config.get(FILTER));
        ss = new StringSharingPlugin(resFilter);
    }
}
