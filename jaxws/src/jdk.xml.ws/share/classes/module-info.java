/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Defines tools for JAX-WS classes and WSDL generation,
 * including the <em>{@index wsgen wsgen tool}</em>
 * and <em>{@index wsimport wsimport tool}</em> tools.
 *
 * <dl style="font-family:'DejaVu Sans', Arial, Helvetica, sans serif">
 * <dt class="simpleTagLabel">Tool Guides:
 * <dd>{@extLink wsgen_tool_reference wsgen},
 *     {@extLink wsimport_tool_reference wsimport}
 * </dl>
 *
 * @moduleGraph
 * @since 9
 */
@Deprecated(since="9", forRemoval=true)
module jdk.xml.ws {
    requires java.compiler;
    requires java.logging;
    requires java.rmi;
    requires java.xml;
    requires java.xml.bind;
    requires java.xml.ws;
    requires jdk.xml.bind;

    uses com.sun.tools.internal.ws.wscompile.Plugin;

    provides com.sun.tools.internal.ws.wscompile.Plugin with
        com.sun.tools.internal.ws.wscompile.plugin.at_generated.PluginImpl;
}

