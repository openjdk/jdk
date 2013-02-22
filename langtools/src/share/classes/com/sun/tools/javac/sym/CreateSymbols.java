/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.sym;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.ClassWriter;
import com.sun.tools.javac.jvm.Pool;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.CLASS;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Used to generate a "symbol file" representing rt.jar that only
 * includes supported or legacy proprietary API.  Valid annotation
 * processor options:
 *
 * <dl>
 * <dt>com.sun.tools.javac.sym.Jar</dt>
 * <dd>Specifies the location of rt.jar.</dd>
 * <dt>com.sun.tools.javac.sym.Dest</dt>
 * <dd>Specifies the destination directory.</dd>
 * </dl>
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Peter von der Ah\u00e9
 */
@SupportedOptions({
    "com.sun.tools.javac.sym.Jar",
    "com.sun.tools.javac.sym.Dest",
    "com.sun.tools.javac.sym.Profiles"})
@SupportedAnnotationTypes("*")
public class CreateSymbols extends AbstractProcessor {

    static Set<String> getLegacyPackages() {
        ResourceBundle legacyBundle
            = ResourceBundle.getBundle("com.sun.tools.javac.resources.legacy");
        Set<String> keys = new HashSet<String>();
        for (Enumeration<String> e = legacyBundle.getKeys(); e.hasMoreElements(); )
            keys.add(e.nextElement());
        return keys;
    }

    public boolean process(Set<? extends TypeElement> tes, RoundEnvironment renv) {
        try {
            if (renv.processingOver())
                createSymbols();
        } catch (IOException e) {
            processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, e.getLocalizedMessage());
        } catch (Throwable t) {
            t.printStackTrace();
            Throwable cause = t.getCause();
            if (cause == null)
                cause = t;
            processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, cause.getLocalizedMessage());
        }
        return true;
    }

    void createSymbols() throws IOException {
        Set<String> legacy = getLegacyPackages();
        Set<String> legacyProprietary = getLegacyPackages();
        Set<String> documented = new HashSet<String>();
        Set<PackageSymbol> packages =
            ((JavacProcessingEnvironment)processingEnv).getSpecifiedPackages();
        Map<String,String> pOptions = processingEnv.getOptions();
        String jarName = pOptions.get("com.sun.tools.javac.sym.Jar");
        if (jarName == null)
            throw new RuntimeException("Must use -Acom.sun.tools.javac.sym.Jar=LOCATION_OF_JAR");
        String destName = pOptions.get("com.sun.tools.javac.sym.Dest");
        if (destName == null)
            throw new RuntimeException("Must use -Acom.sun.tools.javac.sym.Dest=LOCATION_OF_JAR");
        String profileSpec=pOptions.get("com.sun.tools.javac.sym.Profiles");
        if (profileSpec == null)
            throw new RuntimeException("Must use -Acom.sun.tools.javac.sym.Profiles=PROFILES_SPEC");
        Profiles profiles = Profiles.read(new File(profileSpec));

        for (PackageSymbol psym : packages) {
            String name = psym.getQualifiedName().toString();
            legacyProprietary.remove(name);
            documented.add(name);
        }

        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = tool.getStandardFileManager(null, null, null);
        Location jarLocation = StandardLocation.locationFor(jarName);
        File jarFile = new File(jarName);
        fm.setLocation(jarLocation, List.of(jarFile));
        fm.setLocation(StandardLocation.CLASS_PATH, List.<File>nil());
        fm.setLocation(StandardLocation.SOURCE_PATH, List.<File>nil());
        {
            ArrayList<File> bootClassPath = new ArrayList<File>();
            bootClassPath.add(jarFile);
            for (File path : fm.getLocation(StandardLocation.PLATFORM_CLASS_PATH)) {
                if (!new File(path.getName()).equals(new File("rt.jar")))
                    bootClassPath.add(path);
            }
            System.err.println("Using boot class path = " + bootClassPath);
            fm.setLocation(StandardLocation.PLATFORM_CLASS_PATH, bootClassPath);
        }
        // System.out.println(fm.getLocation(StandardLocation.PLATFORM_CLASS_PATH));
        File destDir = new File(destName);
        if (!destDir.exists())
            if (!destDir.mkdirs())
                throw new RuntimeException("Could not create " + destDir);
        fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(destDir));
        Set<String> hiddenPackages = new HashSet<String>();
        Set<String> crisp = new HashSet<String>();
        List<String> options = List.of("-XDdev");
        // options = options.prepend("-doe");
        // options = options.prepend("-verbose");
        JavacTaskImpl task = (JavacTaskImpl)
            tool.getTask(null, fm, null, options, null, null);
        com.sun.tools.javac.main.JavaCompiler compiler =
            com.sun.tools.javac.main.JavaCompiler.instance(task.getContext());
        ClassWriter writer = ClassWriter.instance(task.getContext());
        Symtab syms = Symtab.instance(task.getContext());
        Names names = Names.instance(task.getContext());
        Attribute.Compound proprietaryAnno =
            new Attribute.Compound(syms.proprietaryType,
                                   List.<Pair<Symbol.MethodSymbol,Attribute>>nil());
        Attribute.Compound[] profileAnnos = new Attribute.Compound[profiles.getProfileCount() + 1];
        Symbol.MethodSymbol profileValue = (MethodSymbol) syms.profileType.tsym.members().lookup(names.value).sym;
        for (int i = 1; i < profileAnnos.length; i++) {
            profileAnnos[i] = new Attribute.Compound(syms.profileType,
                    List.<Pair<Symbol.MethodSymbol, Attribute>>of(
                    new Pair<Symbol.MethodSymbol, Attribute>(profileValue, new Attribute.Constant(syms.intType, i))));
        }

        Type.moreInfo = true;
        Types types = Types.instance(task.getContext());
        Pool pool = new Pool(types);
        for (JavaFileObject file : fm.list(jarLocation, "", EnumSet.of(CLASS), true)) {
            String className = fm.inferBinaryName(jarLocation, file);
            int index = className.lastIndexOf('.');
            String pckName = index == -1 ? "" : className.substring(0, index);
            boolean addLegacyAnnotation = false;
            if (documented.contains(pckName)) {
                if (!legacy.contains(pckName))
                    crisp.add(pckName);
                // System.out.println("Documented: " + className);
            } else if (legacyProprietary.contains(pckName)) {
                addLegacyAnnotation = true;
                // System.out.println("Legacy proprietary: " + className);
            } else {
                // System.out.println("Hidden " + className);
                hiddenPackages.add(pckName);
                continue;
            }
            TypeSymbol sym = (TypeSymbol)compiler.resolveIdent(className);
            if (sym.kind != Kinds.TYP) {
                if (className.indexOf('$') < 0) {
                    System.err.println("Ignoring (other) " + className + " : " + sym);
                    System.err.println("   " + sym.getClass().getSimpleName() + " " + sym.type);
                }
                continue;
            }
            sym.complete();
            if (sym.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
                System.err.println("Ignoring (bad) " + sym.getQualifiedName());
                continue;
            }
            ClassSymbol cs = (ClassSymbol) sym;
            if (addLegacyAnnotation) {
                cs.annotations.prepend(List.of(proprietaryAnno));
            }
            int p = profiles.getProfile(cs.fullname.toString().replace(".", "/"));
            if (0 < p && p < profileAnnos.length)
                cs.annotations.prepend(List.of(profileAnnos[p]));
            writeClass(pool, cs, writer);
        }

        if (false) {
            for (String pckName : crisp)
                System.out.println("Crisp: " + pckName);
            for (String pckName : hiddenPackages)
                System.out.println("Hidden: " + pckName);
            for (String pckName : legacyProprietary)
                System.out.println("Legacy proprietary: " + pckName);
            for (String pckName : documented)
                System.out.println("Documented: " + pckName);
        }
    }

    void writeClass(final Pool pool, final ClassSymbol cs, final ClassWriter writer)
        throws IOException
    {
        try {
            pool.reset();
            cs.pool = pool;
            writer.writeClass(cs);
            for (Scope.Entry e = cs.members().elems; e != null; e = e.sibling) {
                if (e.sym.kind == Kinds.TYP) {
                    ClassSymbol nestedClass = (ClassSymbol)e.sym;
                    nestedClass.complete();
                    writeClass(pool, nestedClass, writer);
                }
            }
        } catch (ClassWriter.StringOverflow ex) {
            throw new RuntimeException(ex);
        } catch (ClassWriter.PoolOverflow ex) {
            throw new RuntimeException(ex);
        }
    }

    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    // used for debugging
    public static void main(String... args) {
        String rt_jar = args[0];
        String dest = args[1];
        args = new String[] {
            "-Xbootclasspath:" + rt_jar,
            "-XDprocess.packages",
            "-proc:only",
            "-processor",
            "com.sun.tools.javac.sym.CreateSymbols",
            "-Acom.sun.tools.javac.sym.Jar=" + rt_jar,
            "-Acom.sun.tools.javac.sym.Dest=" + dest,
            // <editor-fold defaultstate="collapsed">
            "java.applet",
            "java.awt",
            "java.awt.color",
            "java.awt.datatransfer",
            "java.awt.dnd",
            "java.awt.event",
            "java.awt.font",
            "java.awt.geom",
            "java.awt.im",
            "java.awt.im.spi",
            "java.awt.image",
            "java.awt.image.renderable",
            "java.awt.print",
            "java.beans",
            "java.beans.beancontext",
            "java.io",
            "java.lang",
            "java.lang.annotation",
            "java.lang.instrument",
            "java.lang.management",
            "java.lang.ref",
            "java.lang.reflect",
            "java.math",
            "java.net",
            "java.nio",
            "java.nio.channels",
            "java.nio.channels.spi",
            "java.nio.charset",
            "java.nio.charset.spi",
            "java.rmi",
            "java.rmi.activation",
            "java.rmi.dgc",
            "java.rmi.registry",
            "java.rmi.server",
            "java.security",
            "java.security.acl",
            "java.security.cert",
            "java.security.interfaces",
            "java.security.spec",
            "java.sql",
            "java.text",
            "java.text.spi",
            "java.util",
            "java.util.concurrent",
            "java.util.concurrent.atomic",
            "java.util.concurrent.locks",
            "java.util.jar",
            "java.util.logging",
            "java.util.prefs",
            "java.util.regex",
            "java.util.spi",
            "java.util.zip",
            "javax.accessibility",
            "javax.activation",
            "javax.activity",
            "javax.annotation",
            "javax.annotation.processing",
            "javax.crypto",
            "javax.crypto.interfaces",
            "javax.crypto.spec",
            "javax.imageio",
            "javax.imageio.event",
            "javax.imageio.metadata",
            "javax.imageio.plugins.jpeg",
            "javax.imageio.plugins.bmp",
            "javax.imageio.spi",
            "javax.imageio.stream",
            "javax.jws",
            "javax.jws.soap",
            "javax.lang.model",
            "javax.lang.model.element",
            "javax.lang.model.type",
            "javax.lang.model.util",
            "javax.management",
            "javax.management.loading",
            "javax.management.monitor",
            "javax.management.relation",
            "javax.management.openmbean",
            "javax.management.timer",
            "javax.management.modelmbean",
            "javax.management.remote",
            "javax.management.remote.rmi",
            "javax.naming",
            "javax.naming.directory",
            "javax.naming.event",
            "javax.naming.ldap",
            "javax.naming.spi",
            "javax.net",
            "javax.net.ssl",
            "javax.print",
            "javax.print.attribute",
            "javax.print.attribute.standard",
            "javax.print.event",
            "javax.rmi",
            "javax.rmi.CORBA",
            "javax.rmi.ssl",
            "javax.script",
            "javax.security.auth",
            "javax.security.auth.callback",
            "javax.security.auth.kerberos",
            "javax.security.auth.login",
            "javax.security.auth.spi",
            "javax.security.auth.x500",
            "javax.security.cert",
            "javax.security.sasl",
            "javax.sound.sampled",
            "javax.sound.sampled.spi",
            "javax.sound.midi",
            "javax.sound.midi.spi",
            "javax.sql",
            "javax.sql.rowset",
            "javax.sql.rowset.serial",
            "javax.sql.rowset.spi",
            "javax.swing",
            "javax.swing.border",
            "javax.swing.colorchooser",
            "javax.swing.filechooser",
            "javax.swing.event",
            "javax.swing.table",
            "javax.swing.text",
            "javax.swing.text.html",
            "javax.swing.text.html.parser",
            "javax.swing.text.rtf",
            "javax.swing.tree",
            "javax.swing.undo",
            "javax.swing.plaf",
            "javax.swing.plaf.basic",
            "javax.swing.plaf.metal",
            "javax.swing.plaf.multi",
            "javax.swing.plaf.synth",
            "javax.tools",
            "javax.transaction",
            "javax.transaction.xa",
            "javax.xml.parsers",
            "javax.xml.bind",
            "javax.xml.bind.annotation",
            "javax.xml.bind.annotation.adapters",
            "javax.xml.bind.attachment",
            "javax.xml.bind.helpers",
            "javax.xml.bind.util",
            "javax.xml.soap",
            "javax.xml.ws",
            "javax.xml.ws.handler",
            "javax.xml.ws.handler.soap",
            "javax.xml.ws.http",
            "javax.xml.ws.soap",
            "javax.xml.ws.spi",
            "javax.xml.transform",
            "javax.xml.transform.sax",
            "javax.xml.transform.dom",
            "javax.xml.transform.stax",
            "javax.xml.transform.stream",
            "javax.xml",
            "javax.xml.crypto",
            "javax.xml.crypto.dom",
            "javax.xml.crypto.dsig",
            "javax.xml.crypto.dsig.dom",
            "javax.xml.crypto.dsig.keyinfo",
            "javax.xml.crypto.dsig.spec",
            "javax.xml.datatype",
            "javax.xml.validation",
            "javax.xml.namespace",
            "javax.xml.xpath",
            "javax.xml.stream",
            "javax.xml.stream.events",
            "javax.xml.stream.util",
            "org.ietf.jgss",
            "org.omg.CORBA",
            "org.omg.CORBA.DynAnyPackage",
            "org.omg.CORBA.ORBPackage",
            "org.omg.CORBA.TypeCodePackage",
            "org.omg.stub.java.rmi",
            "org.omg.CORBA.portable",
            "org.omg.CORBA_2_3",
            "org.omg.CORBA_2_3.portable",
            "org.omg.CosNaming",
            "org.omg.CosNaming.NamingContextExtPackage",
            "org.omg.CosNaming.NamingContextPackage",
            "org.omg.SendingContext",
            "org.omg.PortableServer",
            "org.omg.PortableServer.CurrentPackage",
            "org.omg.PortableServer.POAPackage",
            "org.omg.PortableServer.POAManagerPackage",
            "org.omg.PortableServer.ServantLocatorPackage",
            "org.omg.PortableServer.portable",
            "org.omg.PortableInterceptor",
            "org.omg.PortableInterceptor.ORBInitInfoPackage",
            "org.omg.Messaging",
            "org.omg.IOP",
            "org.omg.IOP.CodecFactoryPackage",
            "org.omg.IOP.CodecPackage",
            "org.omg.Dynamic",
            "org.omg.DynamicAny",
            "org.omg.DynamicAny.DynAnyPackage",
            "org.omg.DynamicAny.DynAnyFactoryPackage",
            "org.w3c.dom",
            "org.w3c.dom.events",
            "org.w3c.dom.bootstrap",
            "org.w3c.dom.ls",
            "org.xml.sax",
            "org.xml.sax.ext",
            "org.xml.sax.helpers",
            "com.sun.java.browser.dom",
            "org.w3c.dom",
            "org.w3c.dom.bootstrap",
            "org.w3c.dom.ls",
            "org.w3c.dom.ranges",
            "org.w3c.dom.traversal",
            "org.w3c.dom.html",
            "org.w3c.dom.stylesheets",
            "org.w3c.dom.css",
            "org.w3c.dom.events",
            "org.w3c.dom.views",
            "com.sun.management",
            "com.sun.security.auth",
            "com.sun.security.auth.callback",
            "com.sun.security.auth.login",
            "com.sun.security.auth.module",
            "com.sun.security.jgss",
            "com.sun.net.httpserver",
            "com.sun.net.httpserver.spi",
            "javax.smartcardio"
            // </editor-fold>
        };
        com.sun.tools.javac.Main.compile(args);
    }

}
