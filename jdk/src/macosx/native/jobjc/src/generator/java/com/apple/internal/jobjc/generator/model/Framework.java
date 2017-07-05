/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.apple.internal.jobjc.generator.ClassGenerator;
import com.apple.internal.jobjc.generator.Utils;
import com.apple.internal.jobjc.generator.classes.FrameworkClassFile;
import com.apple.internal.jobjc.generator.classes.OutputFile;
import com.apple.internal.jobjc.generator.utils.Fp;
import com.apple.internal.jobjc.generator.utils.Fp.Map1;
import com.apple.jobjc.MacOSXFramework;
import com.apple.jobjc.UnsafeRuntimeAccess;

public class Framework extends Element<Element<?>> implements OutputFileGenerator {
    public final String path;
    public final String pkg;
    public final List<File> binaries;
    public MacOSXFramework nativeFramework;

    public MacOSXFramework load(){
        if(nativeFramework == null){
            String[] bins = new String[binaries.size()];
            for(int i = 0; i < binaries.size(); ++i)
                bins[i] = Utils.getCanonicalPath(binaries.get(i));
            nativeFramework = UnsafeRuntimeAccess.getFramework(bins);
        }
        return nativeFramework;
    }

    public File getMainFrameworkBinary(){ return binaries.get(0); }

    final Node rootNode;

    public Set<Clazz> classes;
    public List<Struct> structs;
    public List<CFType> cfTypes;
    public List<Opaque> opaques;
    public List<Constant> constants;
    public List<StringConstant> stringConstants;
    public List<NativeEnum> enums;
    public List<Function> functions;
    public List<FunctionAlias> functionAliases;
    public List<InformalProtocol> informalProtocols;
    public List<Protocol> protocols;
    public List<Category> categories;
    public List<FrameworkDependency> dependencies;

    public static class FrameworkDependency extends Element<Framework>{
        final String path;
        public Framework object = null;

        public FrameworkDependency(final Node node, final Framework parent) {
            super(getAttr(node, "path").replaceFirst("^.*/([^/]+)\\.framework$", "$1"), parent);
            this.path = getAttr(node, "path");
        }
    }

    public static final XPath XPATH = XPathFactory.newInstance().newXPath();
    public Framework(final String name, final File bsFile) {
        super(name, null);
        try {
            final File pathf = bsFile.getCanonicalFile().getParentFile().getParentFile().getParentFile();
            path = pathf.getParentFile().getParentFile().getCanonicalPath();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
        binaries = findBinaries(path, name);

        pkg = ClassGenerator.JOBJC_PACKAGE + "." + name.toLowerCase();
        try {
            rootNode = (Node)XPATH.evaluate("signatures", new InputSource(bsFile.getAbsolutePath()), XPathConstants.NODE);
        } catch (final XPathExpressionException e) { throw new RuntimeException(e); }
        protocols = new ArrayList<Protocol>();
        categories = new ArrayList<Category>();
    }

    private static List<File> findBinaries(final String rootPath, final String name){
        List<File> bins = new ArrayList<File>(2);

        File mainBin = new File(rootPath, name);
        if(mainBin.exists()) bins.add(mainBin);

        File bsBin = new File(rootPath, "Resources/BridgeSupport/" + name + ".dylib");
        if(bsBin.exists()) bins.add(bsBin);

        return bins;
    }

    public void parseDependencies(final Collection<Framework> frameworks) {
        // Parse
        dependencies = getNodesFor(rootNode, "depends_on", FrameworkDependency.class, this);
        // Resolve
        for(final FrameworkDependency dep : dependencies)
            dep.object = Fp.find(new Map1<Framework,Boolean>(){
                public Boolean apply(Framework f) {
                    return f.path.equals(dep.path);
                }}, frameworks);
    }

    public void parseStructs() {
        structs = getNodesFor(rootNode, "struct", Struct.class, this);

        // HACK BS bug #6100313
        if(Utils.isSnowLeopard && name.equals("IOBluetooth"))
            structs.remove(getStructByName("BluetoothHCIRequestNotificationInfo"));

        // GLIFunctionDispatch is frequently out of sync in BS / system
        if(name.equals("OpenGL"))
            structs.remove(getStructByName("GLIFunctionDispatch"));
    }

    public void parseCFTypes() {
        cfTypes = getNodesFor(rootNode, "cftype", CFType.class, this);
    }

    public void parseOpaques() {
        opaques = getNodesFor(rootNode, "opaque", Opaque.class, this);
    }

    public void parseConstants() {
        constants = getNodesFor(rootNode, "constant", Constant.class, this);
        stringConstants = getNodesFor(rootNode, "string_constant", StringConstant.class, this);
        enums = getNodesFor(rootNode, "enum", NativeEnum.class, this);
    }

    public void parseFunctions() {
        functions = getNodesFor(rootNode, "function", Function.class, this);
        functionAliases = getNodesFor(rootNode, "function_alias", FunctionAlias.class, this);
    }

    public void parseClasses() {
        classes = new HashSet<Clazz>(getNodesFor(rootNode, "class", Clazz.class, this));
        classes = Fp.filterSet(new Map1<Clazz,Boolean>(){
            public Boolean apply(Clazz a) {
                if(a.doesActuallyExist())
                    return true;
                else{
                    System.out.println("Could not find class " + name + ":" + a.name + " in runtime. Discarding.");
                    return false;
                }
            }}, classes);
        informalProtocols = getNodesFor(rootNode, "informal_protocol", InformalProtocol.class, this);
    }

    public void resolveSuperClasses(final Map<String, Clazz> allClasses) throws Throwable {
        load();
        for (final Clazz clazz : classes)
            clazz.resolveSuperClass(nativeFramework, allClasses);
    }

    public void generateClasses(final List<OutputFile> generatedClassFiles) {
        generatedClassFiles.add(new FrameworkClassFile(this));

        final List<List<OutputFileGenerator>> generatorLists =
                  Utils.list(new ArrayList<Clazz>(classes), structs, cfTypes, opaques, categories);
        for (final List<OutputFileGenerator> generators : generatorLists) {
            for (final OutputFileGenerator generator : generators)
                generator.generateClasses(generatedClassFiles);
        }
    }

    @Override public String toString() { return reflectOnMySelf(); }

    public Struct getStructByName(final String stname) {
        return Fp.find(new Fp.Map1<Struct,Boolean>(){
            public Boolean apply(Struct a) {
                return stname.equals(a.name);
            }}, structs);
    }
}
