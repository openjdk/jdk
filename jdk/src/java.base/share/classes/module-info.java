/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * java.base defines and exports the core APIs of the Java SE platform.
 */

module java.base {

    exports java.io;
    exports java.lang;
    exports java.lang.annotation;
    exports java.lang.invoke;
    exports java.lang.module;
    exports java.lang.ref;
    exports java.lang.reflect;
    exports java.math;
    exports java.net;
    exports java.net.spi;
    exports java.nio;
    exports java.nio.channels;
    exports java.nio.channels.spi;
    exports java.nio.charset;
    exports java.nio.charset.spi;
    exports java.nio.file;
    exports java.nio.file.attribute;
    exports java.nio.file.spi;
    exports java.security;
    exports java.security.acl;
    exports java.security.cert;
    exports java.security.interfaces;
    exports java.security.spec;
    exports java.text;
    exports java.text.spi;
    exports java.time;
    exports java.time.chrono;
    exports java.time.format;
    exports java.time.temporal;
    exports java.time.zone;
    exports java.util;
    exports java.util.concurrent;
    exports java.util.concurrent.atomic;
    exports java.util.concurrent.locks;
    exports java.util.function;
    exports java.util.jar;
    exports java.util.regex;
    exports java.util.spi;
    exports java.util.stream;
    exports java.util.zip;
    exports javax.crypto;
    exports javax.crypto.interfaces;
    exports javax.crypto.spec;
    exports javax.net;
    exports javax.net.ssl;
    exports javax.security.auth;
    exports javax.security.auth.callback;
    exports javax.security.auth.login;
    exports javax.security.auth.spi;
    exports javax.security.auth.x500;
    exports javax.security.cert;

    // see JDK-8144062
    exports jdk;


    // the service types defined by the APIs in this module

    uses java.lang.System.LoggerFinder;
    uses java.net.ContentHandlerFactory;
    uses java.net.spi.URLStreamHandlerProvider;
    uses java.nio.channels.spi.AsynchronousChannelProvider;
    uses java.nio.channels.spi.SelectorProvider;
    uses java.nio.charset.spi.CharsetProvider;
    uses java.nio.file.spi.FileSystemProvider;
    uses java.nio.file.spi.FileTypeDetector;
    uses java.security.Provider;
    uses java.text.spi.BreakIteratorProvider;
    uses java.text.spi.CollatorProvider;
    uses java.text.spi.DateFormatProvider;
    uses java.text.spi.DateFormatSymbolsProvider;
    uses java.text.spi.DecimalFormatSymbolsProvider;
    uses java.text.spi.NumberFormatProvider;
    uses java.time.chrono.AbstractChronology;
    uses java.time.chrono.Chronology;
    uses java.time.zone.ZoneRulesProvider;
    uses java.util.spi.CalendarDataProvider;
    uses java.util.spi.CalendarNameProvider;
    uses java.util.spi.CurrencyNameProvider;
    uses java.util.spi.LocaleNameProvider;
    uses java.util.spi.ResourceBundleControlProvider;
    uses java.util.spi.ResourceBundleProvider;
    uses java.util.spi.TimeZoneNameProvider;
    uses javax.security.auth.spi.LoginModule;


    // additional qualified exports may be inserted at build time
    // see make/gensrc/GenModuleInfo.gmk

    // CORBA serialization needs reflective access
    exports sun.util.calendar to
        java.corba;

    exports com.sun.security.ntlm to
        java.security.sasl;
    exports jdk.internal.jimage to
        jdk.jlink;
    exports jdk.internal.jimage.decompressor to
        jdk.jlink;
    exports jdk.internal.logger to
        java.logging;
    exports jdk.internal.org.objectweb.asm to
        jdk.jlink,
        jdk.scripting.nashorn,
        jdk.vm.ci;
    exports jdk.internal.org.objectweb.asm.tree to
        jdk.jlink;
    exports jdk.internal.org.objectweb.asm.util to
        jdk.jlink,
        jdk.scripting.nashorn;
    exports jdk.internal.org.objectweb.asm.tree.analysis to
        jdk.jlink;
    exports jdk.internal.org.objectweb.asm.commons to
        jdk.scripting.nashorn;
    exports jdk.internal.org.objectweb.asm.signature to
        jdk.scripting.nashorn;
    exports jdk.internal.math to
        java.desktop;
    exports jdk.internal.module to
        java.instrument,
        java.management,
        java.xml,
        jdk.dynalink,
        jdk.jartool,
        jdk.jlink,
        jdk.scripting.nashorn;
    exports jdk.internal.misc to
        java.corba,
        java.desktop,
        java.logging,
        java.management,
        java.naming,
        java.rmi,
        java.security.jgss,
        java.sql,
        java.xml,
        jdk.charsets,
        jdk.scripting.nashorn,
        jdk.unsupported,
        jdk.vm.ci;
    exports jdk.internal.perf to
        java.desktop,
        java.management,
        jdk.jvmstat;
    exports jdk.internal.ref to
        java.desktop;
    exports jdk.internal.reflect to
        java.corba,
        java.logging,
        java.sql,
        java.sql.rowset,
        jdk.dynalink,
        jdk.scripting.nashorn,
        jdk.unsupported;
    exports jdk.internal.vm.annotation to
        jdk.unsupported;
    exports jdk.internal.util.jar to
        jdk.jartool;
    exports jdk.internal.vm to
        java.management,
        jdk.jvmstat;
    exports sun.net to
        java.httpclient;
    exports sun.net.ext to
        jdk.net;
    exports sun.net.dns to
        java.security.jgss,
        jdk.naming.dns;
    exports sun.net.util to
        java.desktop,
        jdk.jconsole,
        jdk.naming.dns;
    exports sun.net.www to
        java.desktop,
        jdk.jartool;
    exports sun.net.www.protocol.http to
        java.security.jgss;
    exports sun.nio.ch to
        java.management,
        jdk.crypto.pkcs11,
        jdk.sctp;
    exports sun.nio.cs to
        java.desktop,
        jdk.charsets;
    exports sun.reflect.annotation to
        jdk.compiler;
    exports sun.reflect.generics.reflectiveObjects to
        java.desktop;
    exports sun.reflect.misc to
        java.corba,
        java.desktop,
        java.datatransfer,
        java.management,
        java.rmi,
        java.sql.rowset,
        java.xml,
        java.xml.ws;
    exports sun.security.action to
        java.desktop,
        java.security.jgss,
        jdk.crypto.pkcs11;
    exports sun.security.internal.interfaces to
        jdk.crypto.pkcs11;
    exports sun.security.internal.spec to
        jdk.crypto.pkcs11;
    exports sun.security.jca to
        java.smartcardio,
        java.xml.crypto,
        jdk.crypto.ec,
        jdk.crypto.pkcs11,
        jdk.naming.dns;
    exports sun.security.pkcs to
        jdk.crypto.ec,
        jdk.jartool;
    exports sun.security.provider to
        java.rmi,
        java.security.jgss,
        jdk.crypto.pkcs11,
        jdk.policytool,
        jdk.security.auth;
    exports sun.security.provider.certpath to
        java.naming;
    exports sun.security.rsa to
        jdk.crypto.pkcs11;
    exports sun.security.ssl to
        java.security.jgss;
    exports sun.security.tools to
        jdk.jartool;
    exports sun.security.util to
        java.desktop,
        java.naming,
        java.rmi,
        java.security.jgss,
        java.security.sasl,
        java.smartcardio,
        jdk.crypto.ec,
        jdk.crypto.pkcs11,
        jdk.jartool,
        jdk.policytool,
        jdk.security.auth,
        jdk.security.jgss;
    exports sun.security.x509 to
        jdk.crypto.ec,
        jdk.crypto.pkcs11,
        jdk.jartool,
        jdk.security.auth;
    exports sun.text.resources to
        jdk.localedata;
    exports sun.util.resources to
        jdk.localedata;
    exports sun.util.locale.provider to
        java.desktop,
        jdk.localedata;
    exports sun.util.logging to
        java.desktop,
        java.logging,
        java.prefs;

    // JDK-internal service types
    uses jdk.internal.logger.DefaultLoggerFinder;
    uses sun.security.ssl.ClientKeyExchangeService;
    uses sun.util.spi.CalendarProvider;
    uses sun.util.locale.provider.LocaleDataMetaInfo;
    uses sun.util.resources.LocaleData.CommonResourceBundleProvider;
    uses sun.util.resources.LocaleData.SupplementaryResourceBundleProvider;


    // Built-in service providers that are located via ServiceLoader

    provides java.nio.file.spi.FileSystemProvider with
        jdk.internal.jrtfs.JrtFileSystemProvider;
}

