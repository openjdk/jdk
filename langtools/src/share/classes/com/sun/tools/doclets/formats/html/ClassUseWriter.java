/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.javadoc.*;
import java.io.*;
import java.util.*;

/**
 * Generate class usage information.
 *
 * @author Robert G. Field
 * @author Bhavesh Patel (Modified)
 */
public class ClassUseWriter extends SubWriterHolderWriter {

    final ClassDoc classdoc;
    Set<PackageDoc> pkgToPackageAnnotations = null;
    final Map<String,List<ProgramElementDoc>> pkgToClassTypeParameter;
    final Map<String,List<ProgramElementDoc>> pkgToClassAnnotations;
    final Map<String,List<ProgramElementDoc>> pkgToMethodTypeParameter;
    final Map<String,List<ProgramElementDoc>> pkgToMethodArgTypeParameter;
    final Map<String,List<ProgramElementDoc>> pkgToMethodReturnTypeParameter;
    final Map<String,List<ProgramElementDoc>> pkgToMethodAnnotations;
    final Map<String,List<ProgramElementDoc>> pkgToMethodParameterAnnotations;
    final Map<String,List<ProgramElementDoc>> pkgToFieldTypeParameter;
    final Map<String,List<ProgramElementDoc>> pkgToFieldAnnotations;
    final Map<String,List<ProgramElementDoc>> pkgToSubclass;
    final Map<String,List<ProgramElementDoc>> pkgToSubinterface;
    final Map<String,List<ProgramElementDoc>> pkgToImplementingClass;
    final Map<String,List<ProgramElementDoc>> pkgToField;
    final Map<String,List<ProgramElementDoc>> pkgToMethodReturn;
    final Map<String,List<ProgramElementDoc>> pkgToMethodArgs;
    final Map<String,List<ProgramElementDoc>> pkgToMethodThrows;
    final Map<String,List<ProgramElementDoc>> pkgToConstructorAnnotations;
    final Map<String,List<ProgramElementDoc>> pkgToConstructorParameterAnnotations;
    final Map<String,List<ProgramElementDoc>> pkgToConstructorArgs;
    final Map<String,List<ProgramElementDoc>> pkgToConstructorArgTypeParameter;
    final Map<String,List<ProgramElementDoc>> pkgToConstructorThrows;
    final SortedSet<PackageDoc> pkgSet;
    final MethodWriterImpl methodSubWriter;
    final ConstructorWriterImpl constrSubWriter;
    final FieldWriterImpl fieldSubWriter;
    final NestedClassWriterImpl classSubWriter;
    // Summary for various use tables.
    final String classUseTableSummary;
    final String subclassUseTableSummary;
    final String subinterfaceUseTableSummary;
    final String fieldUseTableSummary;
    final String methodUseTableSummary;
    final String constructorUseTableSummary;


    /**
     * Constructor.
     *
     * @param filename the file to be generated.
     * @throws IOException
     * @throws DocletAbortException
     */
    public ClassUseWriter(ConfigurationImpl configuration,
                          ClassUseMapper mapper, String path,
                          String filename, String relpath,
                          ClassDoc classdoc) throws IOException {
        super(configuration, path, filename, relpath);
        this.classdoc = classdoc;
        if (mapper.classToPackageAnnotations.containsKey(classdoc.qualifiedName()))
                pkgToPackageAnnotations = new HashSet<PackageDoc>(mapper.classToPackageAnnotations.get(classdoc.qualifiedName()));
        configuration.currentcd = classdoc;
        this.pkgSet = new TreeSet<PackageDoc>();
        this.pkgToClassTypeParameter = pkgDivide(mapper.classToClassTypeParam);
        this.pkgToClassAnnotations = pkgDivide(mapper.classToClassAnnotations);
        this.pkgToMethodTypeParameter = pkgDivide(mapper.classToExecMemberDocTypeParam);
        this.pkgToMethodArgTypeParameter = pkgDivide(mapper.classToExecMemberDocArgTypeParam);
        this.pkgToFieldTypeParameter = pkgDivide(mapper.classToFieldDocTypeParam);
        this.pkgToFieldAnnotations = pkgDivide(mapper.annotationToFieldDoc);
        this.pkgToMethodReturnTypeParameter = pkgDivide(mapper.classToExecMemberDocReturnTypeParam);
        this.pkgToMethodAnnotations = pkgDivide(mapper.classToExecMemberDocAnnotations);
        this.pkgToMethodParameterAnnotations = pkgDivide(mapper.classToExecMemberDocParamAnnotation);
        this.pkgToSubclass = pkgDivide(mapper.classToSubclass);
        this.pkgToSubinterface = pkgDivide(mapper.classToSubinterface);
        this.pkgToImplementingClass = pkgDivide(mapper.classToImplementingClass);
        this.pkgToField = pkgDivide(mapper.classToField);
        this.pkgToMethodReturn = pkgDivide(mapper.classToMethodReturn);
        this.pkgToMethodArgs = pkgDivide(mapper.classToMethodArgs);
        this.pkgToMethodThrows = pkgDivide(mapper.classToMethodThrows);
        this.pkgToConstructorAnnotations = pkgDivide(mapper.classToConstructorAnnotations);
        this.pkgToConstructorParameterAnnotations = pkgDivide(mapper.classToConstructorParamAnnotation);
        this.pkgToConstructorArgs = pkgDivide(mapper.classToConstructorArgs);
        this.pkgToConstructorArgTypeParameter = pkgDivide(mapper.classToConstructorDocArgTypeParam);
        this.pkgToConstructorThrows = pkgDivide(mapper.classToConstructorThrows);
        //tmp test
        if (pkgSet.size() > 0 &&
            mapper.classToPackage.containsKey(classdoc.qualifiedName()) &&
            !pkgSet.equals(mapper.classToPackage.get(classdoc.qualifiedName()))) {
            configuration.root.printWarning("Internal error: package sets don't match: " + pkgSet + " with: " +
                                   mapper.classToPackage.get(classdoc.qualifiedName()));
        }
        methodSubWriter = new MethodWriterImpl(this);
        constrSubWriter = new ConstructorWriterImpl(this);
        fieldSubWriter = new FieldWriterImpl(this);
        classSubWriter = new NestedClassWriterImpl(this);
        classUseTableSummary = configuration.getText("doclet.Use_Table_Summary",
                configuration.getText("doclet.classes"));
        subclassUseTableSummary = configuration.getText("doclet.Use_Table_Summary",
                configuration.getText("doclet.subclasses"));
        subinterfaceUseTableSummary = configuration.getText("doclet.Use_Table_Summary",
                configuration.getText("doclet.subinterfaces"));
        fieldUseTableSummary = configuration.getText("doclet.Use_Table_Summary",
                configuration.getText("doclet.fields"));
        methodUseTableSummary = configuration.getText("doclet.Use_Table_Summary",
                configuration.getText("doclet.methods"));
        constructorUseTableSummary = configuration.getText("doclet.Use_Table_Summary",
                configuration.getText("doclet.constructors"));
    }

    /**
     * Write out class use pages.
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration,
                                ClassTree classtree)  {
        ClassUseMapper mapper = new ClassUseMapper(configuration.root, classtree);
        ClassDoc[] classes = configuration.root.classes();
        for (int i = 0; i < classes.length; i++) {
            ClassUseWriter.generate(configuration, mapper, classes[i]);
        }
        PackageDoc[] pkgs = configuration.packages;
        for (int i = 0; i < pkgs.length; i++) {
            PackageUseWriter.generate(configuration, mapper, pkgs[i]);
        }
    }

    private Map<String,List<ProgramElementDoc>> pkgDivide(Map<String,? extends List<? extends ProgramElementDoc>> classMap) {
        Map<String,List<ProgramElementDoc>> map = new HashMap<String,List<ProgramElementDoc>>();
        List<? extends ProgramElementDoc> list= classMap.get(classdoc.qualifiedName());
        if (list != null) {
            Collections.sort(list);
            Iterator<? extends ProgramElementDoc> it = list.iterator();
            while (it.hasNext()) {
                ProgramElementDoc doc = it.next();
                PackageDoc pkg = doc.containingPackage();
                pkgSet.add(pkg);
                List<ProgramElementDoc> inPkg = map.get(pkg.name());
                if (inPkg == null) {
                    inPkg = new ArrayList<ProgramElementDoc>();
                    map.put(pkg.name(), inPkg);
                }
                inPkg.add(doc);
            }
        }
        return map;
    }

    /**
     * Generate a class page.
     */
    public static void generate(ConfigurationImpl configuration,
                                ClassUseMapper mapper, ClassDoc classdoc) {
        ClassUseWriter clsgen;
        String path = DirectoryManager.getDirectoryPath(classdoc.
                                                            containingPackage());
        if (path.length() > 0) {
            path += File.separator;
        }
        path += "class-use";
        String filename = classdoc.name() + ".html";
        String pkgname = classdoc.containingPackage().name();
        pkgname += (pkgname.length() > 0)? ".class-use": "class-use";
        String relpath = DirectoryManager.getRelativePath(pkgname);
        try {
            clsgen = new ClassUseWriter(configuration,
                                        mapper, path, filename,
                                        relpath, classdoc);
            clsgen.generateClassUseFile();
            clsgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.
                error("doclet.exception_encountered",
                      exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Print the class use list.
     */
    protected void generateClassUseFile() throws IOException {

        printClassUseHeader();

        if (pkgSet.size() > 0) {
            generateClassUse();
        } else {
            printText("doclet.ClassUse_No.usage.of.0",
                      classdoc.qualifiedName());
            p();
        }

        printClassUseFooter();
    }

    protected void generateClassUse() throws IOException {
        if (configuration.packages.length > 1) {
            generatePackageList();
            generatePackageAnnotationList();
        }
        generateClassList();
    }

    protected void generatePackageList() throws IOException {
        tableIndexSummary(useTableSummary);
        tableCaptionStart();
        printText("doclet.ClassUse_Packages.that.use.0",
            getLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_CLASS_USE_HEADER, classdoc,
                false)));
        tableCaptionEnd();
        summaryTableHeader(packageTableHeader, "col");

        for (Iterator<PackageDoc> it = pkgSet.iterator(); it.hasNext();) {
            PackageDoc pkg = it.next();
            generatePackageUse(pkg);
        }
        tableEnd();
        space();
        p();
    }

    protected void generatePackageAnnotationList() throws IOException {
        if ((! classdoc.isAnnotationType()) ||
               pkgToPackageAnnotations == null ||
               pkgToPackageAnnotations.size() == 0)
            return;
        tableIndexSummary(useTableSummary);
        tableCaptionStart();
        printText("doclet.ClassUse_PackageAnnotation",
            getLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_CLASS_USE_HEADER, classdoc,
                false)));
        tableCaptionEnd();
        summaryTableHeader(packageTableHeader, "col");
        for (Iterator<PackageDoc> it = pkgToPackageAnnotations.iterator(); it.hasNext();) {
            PackageDoc pkg = it.next();
            trBgcolorStyle("white", "TableRowColor");
            summaryRow(0);
            //Just want an anchor here.
            printPackageLink(pkg, pkg.name(), true);
            summaryRowEnd();
            summaryRow(0);
            printSummaryComment(pkg);
            space();
            summaryRowEnd();
            trEnd();
        }
        tableEnd();
        space();
        p();
    }

    protected void generateClassList() throws IOException {
        for (Iterator<PackageDoc> it = pkgSet.iterator(); it.hasNext();) {
            PackageDoc pkg = it.next();
            anchor(pkg.name());
            tableIndexSummary();
            tableHeaderStart("#CCCCFF");
            printText("doclet.ClassUse_Uses.of.0.in.1",
                getLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_CLASS_USE_HEADER,
                    classdoc, false)),
                getPackageLink(pkg, Util.getPackageName(pkg), false));
            tableHeaderEnd();
            tableEnd();
            space();
            p();
            generateClassUse(pkg);
        }
    }

    /**
     * Print the package use list.
     */
    protected void generatePackageUse(PackageDoc pkg) throws IOException {
        trBgcolorStyle("white", "TableRowColor");
        summaryRow(0);
        //Just want an anchor here.
        printHyperLink("", pkg.name(), Util.getPackageName(pkg), true);
        summaryRowEnd();
        summaryRow(0);
        printSummaryComment(pkg);
        space();
        summaryRowEnd();
        trEnd();
    }

    /**
     * Print the class use list.
     */
    protected void generateClassUse(PackageDoc pkg) throws IOException {
        String classLink = getLink(new LinkInfoImpl(
            LinkInfoImpl.CONTEXT_CLASS_USE_HEADER, classdoc, false));
        String pkgLink = getPackageLink(pkg, Util.getPackageName(pkg), false);
        classSubWriter.printUseInfo(pkgToClassAnnotations.get(pkg.name()),
                configuration.getText("doclet.ClassUse_Annotation", classLink,
                pkgLink), classUseTableSummary);
        classSubWriter.printUseInfo(pkgToClassTypeParameter.get(pkg.name()),
                configuration.getText("doclet.ClassUse_TypeParameter", classLink,
                pkgLink), classUseTableSummary);
        classSubWriter.printUseInfo(pkgToSubclass.get(pkg.name()),
                configuration.getText("doclet.ClassUse_Subclass", classLink,
                pkgLink), subclassUseTableSummary);
        classSubWriter.printUseInfo(pkgToSubinterface.get(pkg.name()),
                configuration.getText("doclet.ClassUse_Subinterface", classLink,
                pkgLink), subinterfaceUseTableSummary);
        classSubWriter.printUseInfo(pkgToImplementingClass.get(pkg.name()),
                configuration.getText("doclet.ClassUse_ImplementingClass", classLink,
                pkgLink), classUseTableSummary);
        fieldSubWriter.printUseInfo(pkgToField.get(pkg.name()),
                configuration.getText("doclet.ClassUse_Field", classLink,
                pkgLink), fieldUseTableSummary);
        fieldSubWriter.printUseInfo(pkgToFieldAnnotations.get(pkg.name()),
                configuration.getText("doclet.ClassUse_FieldAnnotations", classLink,
                pkgLink), fieldUseTableSummary);
        fieldSubWriter.printUseInfo(pkgToFieldTypeParameter.get(pkg.name()),
                configuration.getText("doclet.ClassUse_FieldTypeParameter", classLink,
                pkgLink), fieldUseTableSummary);
        methodSubWriter.printUseInfo(pkgToMethodAnnotations.get(pkg.name()),
                configuration.getText("doclet.ClassUse_MethodAnnotations", classLink,
                pkgLink), methodUseTableSummary);
        methodSubWriter.printUseInfo(pkgToMethodParameterAnnotations.get(pkg.name()),
                configuration.getText("doclet.ClassUse_MethodParameterAnnotations", classLink,
                pkgLink), methodUseTableSummary);
        methodSubWriter.printUseInfo(pkgToMethodTypeParameter.get(pkg.name()),
                configuration.getText("doclet.ClassUse_MethodTypeParameter", classLink,
                pkgLink), methodUseTableSummary);
        methodSubWriter.printUseInfo(pkgToMethodReturn.get(pkg.name()),
                configuration.getText("doclet.ClassUse_MethodReturn", classLink,
                pkgLink), methodUseTableSummary);
        methodSubWriter.printUseInfo(pkgToMethodReturnTypeParameter.get(pkg.name()),
                configuration.getText("doclet.ClassUse_MethodReturnTypeParameter", classLink,
                pkgLink), methodUseTableSummary);
        methodSubWriter.printUseInfo(pkgToMethodArgs.get(pkg.name()),
                configuration.getText("doclet.ClassUse_MethodArgs", classLink,
                pkgLink), methodUseTableSummary);
        methodSubWriter.printUseInfo(pkgToMethodArgTypeParameter.get(pkg.name()),
                configuration.getText("doclet.ClassUse_MethodArgsTypeParameters", classLink,
                pkgLink), methodUseTableSummary);
        methodSubWriter.printUseInfo(pkgToMethodThrows.get(pkg.name()),
                configuration.getText("doclet.ClassUse_MethodThrows", classLink,
                pkgLink), methodUseTableSummary);
        constrSubWriter.printUseInfo(pkgToConstructorAnnotations.get(pkg.name()),
                configuration.getText("doclet.ClassUse_ConstructorAnnotations", classLink,
                pkgLink), constructorUseTableSummary);
        constrSubWriter.printUseInfo(pkgToConstructorParameterAnnotations.get(pkg.name()),
                configuration.getText("doclet.ClassUse_ConstructorParameterAnnotations", classLink,
                pkgLink), constructorUseTableSummary);
        constrSubWriter.printUseInfo(pkgToConstructorArgs.get(pkg.name()),
                configuration.getText("doclet.ClassUse_ConstructorArgs", classLink,
                pkgLink), constructorUseTableSummary);
        constrSubWriter.printUseInfo(pkgToConstructorArgTypeParameter.get(pkg.name()),
                configuration.getText("doclet.ClassUse_ConstructorArgsTypeParameters", classLink,
                pkgLink), constructorUseTableSummary);
        constrSubWriter.printUseInfo(pkgToConstructorThrows.get(pkg.name()),
                configuration.getText("doclet.ClassUse_ConstructorThrows", classLink,
                pkgLink), constructorUseTableSummary);
    }

    /**
     * Print the header for the class use Listing.
     */
    protected void printClassUseHeader() {
        String cltype = configuration.getText(classdoc.isInterface()?
                                    "doclet.Interface":
                                    "doclet.Class");
        String clname = classdoc.qualifiedName();
        printHtmlHeader(configuration.getText("doclet.Window_ClassUse_Header",
                            cltype, clname), null, true);
        printTop();
        navLinks(true);
        hr();
        center();
        h2();
        strongText("doclet.ClassUse_Title", cltype, clname);
        h2End();
        centerEnd();
    }

    /**
     * Print the footer for the class use Listing.
     */
    protected void printClassUseFooter() {
        hr();
        navLinks(false);
        printBottom();
        printBodyHtmlEnd();
    }


    /**
     * Print this package link
     */
    protected void navLinkPackage() {
        navCellStart();
        printHyperLink("../package-summary.html", "",
                       configuration.getText("doclet.Package"), true, "NavBarFont1");
        navCellEnd();
    }

    /**
     * Print class page indicator
     */
    protected void navLinkClass() {
        navCellStart();
        printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_CLASS_USE_HEADER, classdoc, "",
            configuration.getText("doclet.Class"), true, "NavBarFont1"));
        navCellEnd();
    }

    /**
     * Print class use link
     */
    protected void navLinkClassUse() {
        navCellRevStart();
        fontStyle("NavBarFont1Rev");
        strongText("doclet.navClassUse");
        fontEnd();
        navCellEnd();
    }

    protected void navLinkTree() {
        navCellStart();
        if (classdoc.containingPackage().isIncluded()) {
            printHyperLink("../package-tree.html", "",
                configuration.getText("doclet.Tree"), true, "NavBarFont1");
        } else {
            printHyperLink(relativePath + "overview-tree.html", "",
                configuration.getText("doclet.Tree"), true, "NavBarFont1");
        }
        navCellEnd();
    }

}
