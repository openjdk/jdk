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

/**
 * Defines tools for JAXB classes and XML schema generation,
 * including the <em>{@index schemagen schemagen tool}</em>
 * and <em>{@index xjc xjc tool}</em> tools.
 *
 * <dl style="font-family:'DejaVu Sans', Arial, Helvetica, sans serif">
 * <dt class="simpleTagLabel">Tool Guides:
 * <dd>{@extLink schemagen_tool_reference schemagen},
 *     {@extLink xjc_tool_reference xjc}
 * </dl>
 *
 * @moduleGraph
 * @since 9
 */
@Deprecated(since="9", forRemoval=true)
module jdk.xml.bind {
    requires java.activation;
    requires java.compiler;
    requires java.desktop;
    requires java.logging;
    requires java.xml;
    requires java.xml.bind;
    requires jdk.compiler;

    exports com.sun.codemodel.internal to
        jdk.xml.ws;
    exports com.sun.codemodel.internal.writer to
        jdk.xml.ws;
    exports com.sun.istack.internal.tools to
        jdk.xml.ws;
    exports com.sun.tools.internal.jxc.ap to
        jdk.xml.ws;
    exports com.sun.tools.internal.jxc.model.nav to
        jdk.xml.ws;
    exports com.sun.tools.internal.xjc to
        jdk.xml.ws;
    exports com.sun.tools.internal.xjc.api to
        jdk.xml.ws;
    exports com.sun.tools.internal.xjc.reader to
        jdk.xml.ws;
    exports com.sun.tools.internal.xjc.reader.internalizer to
        jdk.xml.ws;
    exports com.sun.tools.internal.xjc.util to
        jdk.xml.ws;
    exports com.sun.xml.internal.xsom.parser to
        jdk.xml.ws;
    // com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BindInfo uses JAXBContext
    exports com.sun.tools.internal.xjc.generator.bean to
       java.xml.bind;

    // XML document content needs to be exported
    opens com.sun.tools.internal.xjc.reader.xmlschema.bindinfo to
        java.xml.bind;

    uses com.sun.tools.internal.xjc.Plugin;

    provides com.sun.tools.internal.xjc.Plugin with
        com.sun.tools.internal.xjc.addon.accessors.PluginImpl,
        com.sun.tools.internal.xjc.addon.at_generated.PluginImpl,
        com.sun.tools.internal.xjc.addon.code_injector.PluginImpl,
        com.sun.tools.internal.xjc.addon.episode.PluginImpl,
        com.sun.tools.internal.xjc.addon.locator.SourceLocationAddOn,
        com.sun.tools.internal.xjc.addon.sync.SynchronizedMethodAddOn;
}

