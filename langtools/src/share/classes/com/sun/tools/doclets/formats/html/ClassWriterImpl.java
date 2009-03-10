/*
 * Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.doclets.formats.html;

import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.builders.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;

/**
 * Generate the Class Information Page.
 * @see com.sun.javadoc.ClassDoc
 * @see java.util.Collections
 * @see java.util.List
 * @see java.util.ArrayList
 * @see java.util.HashMap
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 */
public class ClassWriterImpl extends SubWriterHolderWriter
        implements ClassWriter {

    protected ClassDoc classDoc;

    protected ClassTree classtree;

    protected ClassDoc prev;

    protected ClassDoc next;

    /**
     * @param classDoc the class being documented.
     * @param prevClass the previous class that was documented.
     * @param nextClass the next class being documented.
     * @param classTree the class tree for the given class.
     */
    public ClassWriterImpl (ClassDoc classDoc,
            ClassDoc prevClass, ClassDoc nextClass, ClassTree classTree)
    throws Exception {
        super(ConfigurationImpl.getInstance(),
              DirectoryManager.getDirectoryPath(classDoc.containingPackage()),
              classDoc.name() + ".html",
              DirectoryManager.getRelativePath(classDoc.containingPackage().name()));
        this.classDoc = classDoc;
        configuration.currentcd = classDoc;
        this.classtree = classTree;
        this.prev = prevClass;
        this.next = nextClass;
    }

    /**
     * Print this package link
     */
    protected void navLinkPackage() {
        navCellStart();
        printHyperLink("package-summary.html", "",
            configuration.getText("doclet.Package"), true, "NavBarFont1");
        navCellEnd();
    }

    /**
     * Print class page indicator
     */
    protected void navLinkClass() {
        navCellRevStart();
        fontStyle("NavBarFont1Rev");
        strongText("doclet.Class");
        fontEnd();
        navCellEnd();
    }

    /**
     * Print class use link
     */
    protected void navLinkClassUse() {
        navCellStart();
        printHyperLink("class-use/" + filename, "",
                       configuration.getText("doclet.navClassUse"), true, "NavBarFont1");
        navCellEnd();
    }

    /**
     * Print previous package link
     */
    protected void navLinkPrevious() {
        if (prev == null) {
            printText("doclet.Prev_Class");
        } else {
            printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_CLASS, prev, "",
                configuration.getText("doclet.Prev_Class"), true));
        }
    }

    /**
     * Print next package link
     */
    protected void navLinkNext() {
        if (next == null) {
            printText("doclet.Next_Class");
        } else {
            printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_CLASS, next, "",
                configuration.getText("doclet.Next_Class"), true));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writeHeader(String header) {
        String pkgname = (classDoc.containingPackage() != null)?
            classDoc.containingPackage().name(): "";
        String clname = classDoc.name();
        printHtmlHeader(clname,
            configuration.metakeywords.getMetaKeywords(classDoc), true);
        printTop();
        navLinks(true);
        hr();
        println("<!-- ======== START OF CLASS DATA ======== -->");
        h2();
        if (pkgname.length() > 0) {
            font("-1"); print(pkgname); fontEnd(); br();
        }
        LinkInfoImpl linkInfo = new LinkInfoImpl( LinkInfoImpl.CONTEXT_CLASS_HEADER,
            classDoc, false);
        //Let's not link to ourselves in the header.
        linkInfo.linkToSelf = false;
        print(header + getTypeParameterLinks(linkInfo));
        h2End();
    }

    /**
     * {@inheritDoc}
     */
    public void writeFooter() {
        println("<!-- ========= END OF CLASS DATA ========= -->");
        hr();
        navLinks(false);
        printBottom();
        printBodyHtmlEnd();
    }

    /**
     * {@inheritDoc}
     */
    public void writeClassSignature(String modifiers) {
        boolean isInterface = classDoc.isInterface();
        preNoNewLine();
        writeAnnotationInfo(classDoc);
        print(modifiers);
        LinkInfoImpl linkInfo = new LinkInfoImpl(
            LinkInfoImpl.CONTEXT_CLASS_SIGNATURE, classDoc, false);
        //Let's not link to ourselves in the signature.
        linkInfo.linkToSelf = false;
        String name = classDoc.name() +
            getTypeParameterLinks(linkInfo);
        if (configuration().linksource) {
            printSrcLink(classDoc, name);
        } else {
            strong(name);
        }
        if (!isInterface) {
            Type superclass = Util.getFirstVisibleSuperClass(classDoc,
                configuration());
            if (superclass != null) {
                println();
                print("extends ");
                printLink(new LinkInfoImpl(
                    LinkInfoImpl.CONTEXT_CLASS_SIGNATURE_PARENT_NAME,
                    superclass));
            }
        }
        Type[] implIntfacs = classDoc.interfaceTypes();
        if (implIntfacs != null && implIntfacs.length > 0) {
            int counter = 0;
            for (int i = 0; i < implIntfacs.length; i++) {
                ClassDoc classDoc = implIntfacs[i].asClassDoc();
                if (! (classDoc.isPublic() ||
                    Util.isLinkable(classDoc, configuration()))) {
                    continue;
                }
                if (counter == 0) {
                    println();
                    print(isInterface? "extends " : "implements ");
                } else {
                    print(", ");
                }
                printLink(new LinkInfoImpl(
                    LinkInfoImpl.CONTEXT_CLASS_SIGNATURE_PARENT_NAME,
                    implIntfacs[i]));
                counter++;
            }
        }
        preEnd();
        p();
    }

    /**
     * {@inheritDoc}
     */
    public void writeClassDescription() {
        if(!configuration.nocomment) {
            // generate documentation for the class.
            if (classDoc.inlineTags().length > 0) {
                printInlineComment(classDoc);
                p();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writeClassTagInfo() {
        if(!configuration.nocomment) {
            // Print Information about all the tags here
            printTags(classDoc);
            hr();
            p();
        } else {
            hr();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writeClassDeprecationInfo() {
        hr();
        Tag[] deprs = classDoc.tags("deprecated");
        if (Util.isDeprecated(classDoc)) {
            strongText("doclet.Deprecated");
            if (deprs.length > 0) {
                Tag[] commentTags = deprs[0].inlineTags();
                if (commentTags.length > 0) {
                    space();
                    printInlineDeprecatedComment(classDoc, deprs[0]);
                }
            }
            p();
        }
    }

    /**
     * Generate the indent and get the line image for the class tree.
     * For user accessibility, the image includes the alt attribute
     * "extended by".  (This method is not intended for a class
     * implementing an interface, where "implemented by" would be required.)
     *
     * indent  integer indicating the number of spaces to indent
     */
    private void writeStep(int indent) {
        print(spaces(4 * indent - 2));
        print("<IMG SRC=\"" + relativepathNoSlash + "/resources/inherit.gif\" " +
              "ALT=\"" + configuration.getText("doclet.extended_by") + " \">");
    }

    /**
     * Print the class hierarchy tree for the given class.
     * @param type the class to print the hierarchy for.
     * @return return the amount that should be indented in
     * the next level of the tree.
     */
    private int writeTreeForClassHelper(Type type) {
        Type sup = Util.getFirstVisibleSuperClass(
            type instanceof ClassDoc ? (ClassDoc) type : type.asClassDoc(),
            configuration());
        int indent = 0;
        if (sup != null) {
            indent = writeTreeForClassHelper(sup);
            writeStep(indent);
        }

        if (type.equals(classDoc)) {
            String typeParameters = getTypeParameterLinks(
                new LinkInfoImpl(
                    LinkInfoImpl.CONTEXT_TREE,
                    classDoc, false));
            if (configuration.shouldExcludeQualifier(
                    classDoc.containingPackage().name())) {
                strong(type.asClassDoc().name() + typeParameters);
            } else {
                strong(type.asClassDoc().qualifiedName() + typeParameters);
            }
        } else {
            print(getLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_CLASS_TREE_PARENT,
                    type instanceof ClassDoc ? (ClassDoc) type : type,
                    configuration.getClassName(type.asClassDoc()), false)));
        }
        println();
        return indent + 1;
    }

    /**
     * Print the class hierarchy tree for this class only.
     */
    public void writeClassTree() {
        if (! classDoc.isClass()) {
            return;
        }
        pre();
        writeTreeForClassHelper(classDoc);
        preEnd();
    }

    /**
     * Write the type parameter information.
     */
    public void writeTypeParamInfo() {
        if (classDoc.typeParamTags().length > 0) {
            dl();
            dt();
            TagletOutput output = (new ParamTaglet()).getTagletOutput(classDoc,
                getTagletWriterInstance(false));
            print(output.toString());
            dtEnd();
            dlEnd();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writeSubClassInfo() {
        if (classDoc.isClass()) {
            if (classDoc.qualifiedName().equals("java.lang.Object") ||
                classDoc.qualifiedName().equals("org.omg.CORBA.Object")) {
                return;    // Don't generate the list, too huge
            }
            List<ClassDoc> subclasses = classtree.subs(classDoc, false);
            if (subclasses.size() > 0) {
                dl();
                dt();
                strongText("doclet.Subclasses");
                dtEnd();
                writeClassLinks(LinkInfoImpl.CONTEXT_SUBCLASSES,
                    subclasses);
                dlEnd();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writeSubInterfacesInfo() {
        if (classDoc.isInterface()) {
            List<ClassDoc> subInterfaces = classtree.allSubs(classDoc, false);
            if (subInterfaces.size() > 0) {
                dl();
                dt();
                strongText("doclet.Subinterfaces");
                dtEnd();
                writeClassLinks(LinkInfoImpl.CONTEXT_SUBINTERFACES,
                    subInterfaces);
                dlEnd();
            }
        }
    }

    /**
     * If this is the interface which are the classes, that implement this?
     */
    public void writeInterfaceUsageInfo () {
        if (! classDoc.isInterface()) {
            return;
        }
        if (classDoc.qualifiedName().equals("java.lang.Cloneable") ||
            classDoc.qualifiedName().equals("java.io.Serializable")) {
            return;   // Don't generate the list, too big
        }
        List<ClassDoc> implcl = classtree.implementingclasses(classDoc);
        if (implcl.size() > 0) {
            dl();
            dt();
            strongText("doclet.Implementing_Classes");
            dtEnd();
            writeClassLinks(LinkInfoImpl.CONTEXT_IMPLEMENTED_CLASSES,
                implcl);
            dlEnd();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writeImplementedInterfacesInfo() {
        //NOTE:  we really should be using ClassDoc.interfaceTypes() here, but
        //       it doesn't walk up the tree like we want it to.
        List<Type> interfaceArray = Util.getAllInterfaces(classDoc, configuration);
        if (classDoc.isClass() && interfaceArray.size() > 0) {
            dl();
            dt();
            strongText("doclet.All_Implemented_Interfaces");
            dtEnd();
            writeClassLinks(LinkInfoImpl.CONTEXT_IMPLEMENTED_INTERFACES,
                interfaceArray);
            dlEnd();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writeSuperInterfacesInfo() {
        //NOTE:  we really should be using ClassDoc.interfaceTypes() here, but
        //       it doesn't walk up the tree like we want it to.
        List<Type> interfaceArray = Util.getAllInterfaces(classDoc, configuration);
        if (classDoc.isInterface() && interfaceArray.size() > 0) {
            dl();
            dt();
            strongText("doclet.All_Superinterfaces");
            dtEnd();
            writeClassLinks(LinkInfoImpl.CONTEXT_SUPER_INTERFACES,
                interfaceArray);
            dlEnd();
        }
    }

    /**
     * Generate links to the given classes.
     */
    private void writeClassLinks(int context, List<?> list) {
        Object[] typeList = list.toArray();
        //Sort the list to be printed.
        print(' ');
        dd();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                print(", ");
            }
            if (typeList[i] instanceof ClassDoc) {
                printLink(new LinkInfoImpl(context, (ClassDoc)(typeList[i])));

            } else {
                printLink(new LinkInfoImpl(context, (Type)(typeList[i])));
            }
        }
        ddEnd();
    }

    protected void navLinkTree() {
        navCellStart();
        printHyperLink("package-tree.html", "",
            configuration.getText("doclet.Tree"), true, "NavBarFont1");
        navCellEnd();
    }

    protected void printSummaryDetailLinks() {
        try {
            tr();
            tdVAlignClass("top", "NavBarCell3");
            font("-2");
            print("  ");
            navSummaryLinks();
            fontEnd();
            tdEnd();
            tdVAlignClass("top", "NavBarCell3");
            font("-2");
            navDetailLinks();
            fontEnd();
            tdEnd();
            trEnd();
        } catch (Exception e) {
            e.printStackTrace();
            throw new DocletAbortException();
        }
    }

    protected void navSummaryLinks() throws Exception {
        printText("doclet.Summary");
        space();
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
            configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        String[] navLinkLabels =  new String[] {
            "doclet.navNested", "doclet.navEnum", "doclet.navField", "doclet.navConstructor",
                "doclet.navMethod"
        };
        for (int i = 0; i < navLinkLabels.length; i++ ) {
            if (i == VisibleMemberMap.ENUM_CONSTANTS && ! classDoc.isEnum()) {
                continue;
            }
            if (i == VisibleMemberMap.CONSTRUCTORS && classDoc.isEnum()) {
                continue;
            }
            AbstractMemberWriter writer =
                ((AbstractMemberWriter) memberSummaryBuilder.
                    getMemberSummaryWriter(i));
            if (writer == null) {
                printText(navLinkLabels[i]);
            } else {
                writer.navSummaryLink(
                    memberSummaryBuilder.members(i),
                    memberSummaryBuilder.getVisibleMemberMap(i));
            }
            if (i < navLinkLabels.length-1) {
                navGap();
            }
        }
    }

    /**
     * Method navDetailLinks
     *
     * @throws   Exception
     *
     */
    protected void navDetailLinks() throws Exception {
        printText("doclet.Detail");
        space();
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
            configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        String[] navLinkLabels =  new String[] {
            "doclet.navNested", "doclet.navEnum", "doclet.navField", "doclet.navConstructor",
                "doclet.navMethod"
        };
        for (int i = 1; i < navLinkLabels.length; i++ ) {
            AbstractMemberWriter writer =
                ((AbstractMemberWriter) memberSummaryBuilder.
                    getMemberSummaryWriter(i));
            if (i == VisibleMemberMap.ENUM_CONSTANTS && ! classDoc.isEnum()) {
                continue;
            }
            if (i == VisibleMemberMap.CONSTRUCTORS && classDoc.isEnum()) {
                continue;
            }
            if (writer == null) {
                printText(navLinkLabels[i]);
            } else {
                writer.navDetailLink(memberSummaryBuilder.members(i));
            }
            if (i < navLinkLabels.length - 1) {
                navGap();
            }
        }
    }

    protected void navGap() {
        space();
        print('|');
        space();
    }

    /**
     * If this is an inner class or interface, write the enclosing class or
     * interface.
     */
    public void writeNestedClassInfo() {
        ClassDoc outerClass = classDoc.containingClass();
        if (outerClass != null) {
            dl();
            dt();
            if (outerClass.isInterface()) {
                strongText("doclet.Enclosing_Interface");
            } else {
                strongText("doclet.Enclosing_Class");
            }
            dtEnd();
            dd();
            printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_CLASS, outerClass,
                false));
            ddEnd();
            dlEnd();
        }
    }

    /**
     * Return the classDoc being documented.
     *
     * @return the classDoc being documented.
     */
    public ClassDoc getClassDoc() {
        return classDoc;
    }

    /**
     * {@inheritDoc}
     */
    public void completeMemberSummaryBuild() {
        p();
    }
}
