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

module jdk.jlink {
    exports jdk.tools.jlink;
    exports jdk.tools.jlink.plugin;
    exports jdk.tools.jlink.builder;

    requires jdk.internal.opt;
    requires jdk.jdeps;

    uses jdk.tools.jlink.plugin.TransformerPlugin;
    uses jdk.tools.jlink.plugin.PostProcessorPlugin;

    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.FileCopierPlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.StripDebugPlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.ExcludePlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.ExcludeFilesPlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.SystemModuleDescriptorPlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.StripNativeCommandsPlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.SortResourcesPlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.DefaultCompressPlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.OptimizationPlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.ExcludeVMPlugin;
    provides jdk.tools.jlink.plugin.TransformerPlugin with jdk.tools.jlink.internal.plugins.IncludeLocalesPlugin;
    provides jdk.tools.jlink.plugin.PostProcessorPlugin with jdk.tools.jlink.internal.plugins.ReleaseInfoPlugin;
}
