/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class WixToolset {

    static enum WixToolsetType {
        // Wix v4+
        Wix4(WixTool.Wix4),
        // Wix v3+
        Wix3(WixTool.Candle3, WixTool.Light3);

        WixToolsetType(WixTool... tools) {
            this.tools = Set.of(tools);
        }

        Set<WixTool> getTools() {
            return tools;
        }

        private final Set<WixTool> tools;
    }

    private WixToolset(Map<WixTool, WixTool.ToolInfo> tools) {
        this.tools = tools;
    }

    WixToolsetType getType() {
        return Stream.of(WixToolsetType.values()).filter(toolsetType -> {
            return toolsetType.getTools().equals(tools.keySet());
        }).findAny().get();
    }

    Path getToolPath(WixTool tool) {
        return tools.get(tool).path;
    }

    DottedVersion getVersion() {
        return tools.values().iterator().next().version;
    }

    static WixToolset create(Set<WixTool> requiredTools, Map<WixTool, WixTool.ToolInfo> allTools) {
        var filteredTools = allTools.entrySet().stream().filter(e -> {
            return requiredTools.contains(e.getKey());
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (filteredTools.keySet().equals(requiredTools)) {
            return new WixToolset(filteredTools);
        } else {
            return null;
        }
    }

    private final Map<WixTool, WixTool.ToolInfo> tools;
}
