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

module java.xml.bind {
    requires public java.activation;
    requires public java.xml;
    requires java.compiler;
    requires java.desktop;
    requires java.logging;

    uses javax.xml.bind.JAXBContextFactory;

    exports javax.xml.bind;
    exports javax.xml.bind.annotation;
    exports javax.xml.bind.annotation.adapters;
    exports javax.xml.bind.attachment;
    exports javax.xml.bind.helpers;
    exports javax.xml.bind.util;
    exports com.sun.istack.internal to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.istack.internal.localization to
        java.xml.ws,
        jdk.xml.ws;
    exports com.sun.istack.internal.logging to
        java.xml.ws,
        jdk.xml.ws;
    exports com.sun.xml.internal.bind to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.bind.annotation to
        jdk.xml.bind;
    exports com.sun.xml.internal.bind.api to
        java.xml.ws,
        jdk.xml.bind;
    exports com.sun.xml.internal.bind.api.impl to
        java.xml.ws,
        jdk.xml.bind;
    exports com.sun.xml.internal.bind.marshaller to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.bind.unmarshaller to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.bind.util to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.bind.v2 to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.bind.v2.model.annotation to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.bind.v2.model.core to
        jdk.xml.bind;
    exports com.sun.xml.internal.bind.v2.model.impl to
        jdk.xml.bind;
    exports com.sun.xml.internal.bind.v2.model.nav to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.bind.v2.model.runtime to
        java.xml.ws;
    exports com.sun.xml.internal.bind.v2.model.util to
        jdk.xml.bind;
    exports com.sun.xml.internal.bind.v2.runtime to
        java.xml.ws,
        jdk.xml.bind;
    exports com.sun.xml.internal.bind.v2.runtime.unmarshaller to
        java.xml.ws;
    exports com.sun.xml.internal.bind.v2.schemagen to
        java.xml.ws,
        jdk.xml.bind;
    exports com.sun.xml.internal.bind.v2.schemagen.episode to
        jdk.xml.bind;
    exports com.sun.xml.internal.bind.v2.schemagen.xmlschema to
        java.xml.ws;
    exports com.sun.xml.internal.bind.v2.util to
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.fastinfoset to
        java.xml.ws;
    exports com.sun.xml.internal.fastinfoset.stax to
        java.xml.ws;
    exports com.sun.xml.internal.fastinfoset.vocab to
        java.xml.ws;
    exports com.sun.xml.internal.org.jvnet.fastinfoset to
        java.xml.ws;
    exports com.sun.xml.internal.org.jvnet.mimepull to
        java.xml.ws;
    exports com.sun.xml.internal.org.jvnet.staxex to
        java.xml.ws;
    exports com.sun.xml.internal.org.jvnet.staxex.util to
        java.xml.ws;
    exports com.sun.xml.internal.txw2 to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.txw2.annotation to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
    exports com.sun.xml.internal.txw2.output to
        java.xml.ws,
        jdk.xml.bind,
        jdk.xml.ws;
}
