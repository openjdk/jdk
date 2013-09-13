/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Generate class usage information.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
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
                          ClassUseMapper mapper, DocPath filename,
                          ClassDoc classdoc) throws IOException {
        super(configuration, filename);
        this.classdoc = classdoc;
        if (mapper.classToPackageAnnotations.containsKey(classdoc.qualifiedName()))
                pkgToPackageAnnotations = new TreeSet<PackageDoc>(mapper.classToPackageAnnotations.get(classdoc.qualifiedName()));
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
            // If -nodeprecated option is set and the containing package is marked
            // as deprecated, do not generate the class-use page. We will still generate
            // the class-use page if the class is marked as deprecated but the containing
            // package is not since it could still be linked from that package-use page.
            if (!(configuration.nodeprecated &&
                    Util.isDeprecated(classes[i].containingPackage())))
                ClassUseWriter.generate(configuration, mapper, classes[i]);
        }
        PackageDoc[] pkgs = configuration.packages;
        for (int i = 0; i < pkgs.length; i++) {
            // If -nodeprecated option is set and the package is marked
            // as deprecated, do not generate the package-use page.
            if (!(configuration.nodeprecated && Util.isDeprecated(pkgs[i])))
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
        DocPath path = DocPath.forPackage(classdoc)
                .resolve(DocPaths.CLASS_USE)
                .resolve(DocPath.forName(classdoc));
        try {
            clsgen = new ClassUseWriter(configuration,
                                        mapper, path,
                                        classdoc);
            clsgen.generateClassUseFile();
            clsgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.
                error("doclet.exception_encountered",
                      exc.toString(), path.getPath());
            throw new DocletAbortException(exc);
        }
    }

    /**
     * Generate the class use list.
     */
    protected void generateClassUseFile() throws IOException {
        Content body = getClassUseHeader();
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.classUseContainer);
        if (pkgSet.size() > 0) {
            addClassUse(div);
        } else {
            div.addContent(getResource("doclet.ClassUse_No.usage.of.0",
                    classdoc.qualifiedName()));
        }
        body.addContent(div);
        addNavLinks(false, body);
        addBottom(body);
        printHtmlDocument(null, true, body);
    }

    /**
     * Add the class use documentation.
     *
     * @param contentTree the content tree to which the class use information will be added
     */
    protected void addClassUse(Content contentTree) throws IOException {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        if (configuration.packages.length > 1) {
            addPackageList(ul);
            addPackageAnnotationList(ul);
        }
        addClassList(ul);
        contentTree.addContent(ul);
    }

    /**
     * Add the packages list that use the given class.
     *
     * @param contentTree the content tree to which the packages list will be added
     */
    protected void addPackageList(Content contentTree) throws IOException {
        Content table = HtmlTree.TABLE(0, 3, 0, useTableSummary,
                getTableCaption(configuration.getResource(
                "doclet.ClassUse_Packages.that.use.0",
                getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.CLASS_USE_HEADER, classdoc
                )))));
        table.addContent(getSummaryTableHeader(packageTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        Iterator<PackageDoc> it = pkgSet.iterator();
        for (int i = 0; it.hasNext(); i++) {
            PackageDoc pkg = it.next();
            HtmlTree tr = new HtmlTree(HtmlTag.TR);
            if (i % 2 == 0) {
                tr.addStyle(HtmlStyle.altColor);
            } else {
                tr.addStyle(HtmlStyle.rowColor);
            }
            addPackageUse(pkg, tr);
            tbody.addContent(tr);
        }
        table.addContent(tbody);
        Content li = HtmlTree.LI(HtmlStyle.blockList, table);
        contentTree.addContent(li);
    }

    /**
     * Add the package annotation list.
     *
     * @param contentTree the content tree to which the package annotation list will be added
     */
    protected void addPackageAnnotationList(Content contentTree) throws IOException {
        if ((!classdoc.isAnnotationType()) ||
                pkgToPackageAnnotations == null ||
                pkgToPackageAnnotations.isEmpty()) {
            return;
        }
        Content table = HtmlTree.TABLE(0, 3, 0, useTableSummary,
                getTableCaption(configuration.getResource(
                "doclet.ClassUse_PackageAnnotation",
                getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.CLASS_USE_HEADER, classdoc)))));
        table.addContent(getSummaryTableHeader(packageTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        Iterator<PackageDoc> it = pkgToPackageAnnotations.iterator();
        for (int i = 0; it.hasNext(); i++) {
            PackageDoc pkg = it.next();
            HtmlTree tr = new HtmlTree(HtmlTag.TR);
            if (i % 2 == 0) {
                tr.addStyle(HtmlStyle.altColor);
            } else {
                tr.addStyle(HtmlStyle.rowColor);
            }
            Content tdFirst = HtmlTree.TD(HtmlStyle.colFirst,
                    getPackageLink(pkg, new StringContent(pkg.name())));
            tr.addContent(tdFirst);
            HtmlTree tdLast = new HtmlTree(HtmlTag.TD);
            tdLast.addStyle(HtmlStyle.colLast);
            addSummaryComment(pkg, tdLast);
            tr.addContent(tdLast);
            tbody.addContent(tr);
        }
        table.addContent(tbody);
        Content li = HtmlTree.LI(HtmlStyle.blockList, table);
        contentTree.addContent(li);
    }

    /**
     * Add the class list that use the given class.
     *
     * @param contentTree the content tree to which the class list will be added
     */
    protected void addClassList(Content contentTree) throws IOException {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        for (Iterator<PackageDoc> it = pkgSet.iterator(); it.hasNext();) {
            PackageDoc pkg = it.next();
            Content li = HtmlTree.LI(HtmlStyle.blockList, getMarkerAnchor(pkg.name()));
            Content link = getResource("doclet.ClassUse_Uses.of.0.in.1",
                    getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.CLASS_USE_HEADER,
                    classdoc)),
                    getPackageLink(pkg, Util.getPackageName(pkg)));
            Content heading = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING, link);
            li.addContent(heading);
            addClassUse(pkg, li);
            ul.addContent(li);
        }
        Content li = HtmlTree.LI(HtmlStyle.blockList, ul);
        contentTree.addContent(li);
    }

    /**
     * Add the package use information.
     *
     * @param pkg the package that uses the given class
     * @param contentTree the content tree to which the package use information will be added
     */
    protected void addPackageUse(PackageDoc pkg, Content contentTree) throws IOException {
        Content tdFirst = HtmlTree.TD(HtmlStyle.colFirst,
                getHyperLink(pkg.name(), new StringContent(Util.getPackageName(pkg))));
        contentTree.addContent(tdFirst);
        HtmlTree tdLast = new HtmlTree(HtmlTag.TD);
        tdLast.addStyle(HtmlStyle.colLast);
        addSummaryComment(pkg, tdLast);
        contentTree.addContent(tdLast);
    }

    /**
     * Add the class use information.
     *
     * @param pkg the package that uses the given class
     * @param contentTree the content tree to which the class use information will be added
     */
    protected void addClassUse(PackageDoc pkg, Content contentTree) throws IOException {
        Content classLink = getLink(new LinkInfoImpl(configuration,
            LinkInfoImpl.Kind.CLASS_USE_HEADER, classdoc));
        Content pkgLink = getPackageLink(pkg, Util.getPackageName(pkg));
        classSubWriter.addUseInfo(pkgToClassAnnotations.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_Annotation", classLink,
                pkgLink), classUseTableSummary, contentTree);
        classSubWriter.addUseInfo(pkgToClassTypeParameter.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_TypeParameter", classLink,
                pkgLink), classUseTableSummary, contentTree);
        classSubWriter.addUseInfo(pkgToSubclass.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_Subclass", classLink,
                pkgLink), subclassUseTableSummary, contentTree);
        classSubWriter.addUseInfo(pkgToSubinterface.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_Subinterface", classLink,
                pkgLink), subinterfaceUseTableSummary, contentTree);
        classSubWriter.addUseInfo(pkgToImplementingClass.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_ImplementingClass", classLink,
                pkgLink), classUseTableSummary, contentTree);
        fieldSubWriter.addUseInfo(pkgToField.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_Field", classLink,
                pkgLink), fieldUseTableSummary, contentTree);
        fieldSubWriter.addUseInfo(pkgToFieldAnnotations.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_FieldAnnotations", classLink,
                pkgLink), fieldUseTableSummary, contentTree);
        fieldSubWriter.addUseInfo(pkgToFieldTypeParameter.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_FieldTypeParameter", classLink,
                pkgLink), fieldUseTableSummary, contentTree);
        methodSubWriter.addUseInfo(pkgToMethodAnnotations.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_MethodAnnotations", classLink,
                pkgLink), methodUseTableSummary, contentTree);
        methodSubWriter.addUseInfo(pkgToMethodParameterAnnotations.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_MethodParameterAnnotations", classLink,
                pkgLink), methodUseTableSummary, contentTree);
        methodSubWriter.addUseInfo(pkgToMethodTypeParameter.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_MethodTypeParameter", classLink,
                pkgLink), methodUseTableSummary, contentTree);
        methodSubWriter.addUseInfo(pkgToMethodReturn.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_MethodReturn", classLink,
                pkgLink), methodUseTableSummary, contentTree);
        methodSubWriter.addUseInfo(pkgToMethodReturnTypeParameter.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_MethodReturnTypeParameter", classLink,
                pkgLink), methodUseTableSummary, contentTree);
        methodSubWriter.addUseInfo(pkgToMethodArgs.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_MethodArgs", classLink,
                pkgLink), methodUseTableSummary, contentTree);
        methodSubWriter.addUseInfo(pkgToMethodArgTypeParameter.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_MethodArgsTypeParameters", classLink,
                pkgLink), methodUseTableSummary, contentTree);
        methodSubWriter.addUseInfo(pkgToMethodThrows.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_MethodThrows", classLink,
                pkgLink), methodUseTableSummary, contentTree);
        constrSubWriter.addUseInfo(pkgToConstructorAnnotations.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_ConstructorAnnotations", classLink,
                pkgLink), constructorUseTableSummary, contentTree);
        constrSubWriter.addUseInfo(pkgToConstructorParameterAnnotations.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_ConstructorParameterAnnotations", classLink,
                pkgLink), constructorUseTableSummary, contentTree);
        constrSubWriter.addUseInfo(pkgToConstructorArgs.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_ConstructorArgs", classLink,
                pkgLink), constructorUseTableSummary, contentTree);
        constrSubWriter.addUseInfo(pkgToConstructorArgTypeParameter.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_ConstructorArgsTypeParameters", classLink,
                pkgLink), constructorUseTableSummary, contentTree);
        constrSubWriter.addUseInfo(pkgToConstructorThrows.get(pkg.name()),
                configuration.getResource("doclet.ClassUse_ConstructorThrows", classLink,
                pkgLink), constructorUseTableSummary, contentTree);
    }

    /**
     * Get the header for the class use Listing.
     *
     * @return a content tree representing the class use header
     */
    protected Content getClassUseHeader() {
        String cltype = configuration.getText(classdoc.isInterface()?
            "doclet.Interface":"doclet.Class");
        String clname = classdoc.qualifiedName();
        String title = configuration.getText("doclet.Window_ClassUse_Header",
                cltype, clname);
        Content bodyTree = getBody(true, getWindowTitle(title));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        ContentBuilder headContent = new ContentBuilder();
        headContent.addContent(getResource("doclet.ClassUse_Title", cltype));
        headContent.addContent(new HtmlTree(HtmlTag.BR));
        headContent.addContent(clname);
        Content heading = HtmlTree.HEADING(HtmlConstants.CLASS_PAGE_HEADING,
                true, HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        bodyTree.addContent(div);
        return bodyTree;
    }

    /**
     * Get this package link.
     *
     * @return a content tree for the package link
     */
    protected Content getNavLinkPackage() {
        Content linkContent =
                getHyperLink(DocPath.parent.resolve(DocPaths.PACKAGE_SUMMARY), packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Get class page link.
     *
     * @return a content tree for the class page link
     */
    protected Content getNavLinkClass() {
        Content linkContent = getLink(new LinkInfoImpl(
                configuration, LinkInfoImpl.Kind.CLASS_USE_HEADER, classdoc)
                .label(configuration.getText("doclet.Class")));
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Get the use link.
     *
     * @return a content tree for the use link
     */
    protected Content getNavLinkClassUse() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, useLabel);
        return li;
    }

    /**
     * Get the tree link.
     *
     * @return a content tree for the tree link
     */
    protected Content getNavLinkTree() {
        Content linkContent = classdoc.containingPackage().isIncluded() ?
            getHyperLink(DocPath.parent.resolve(DocPaths.PACKAGE_TREE), treeLabel) :
            getHyperLink(pathToRoot.resolve(DocPaths.OVERVIEW_TREE), treeLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }
}
