/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Generate Index for all the Member Names with Indexing in
 * Unicode Order. This class is a base class for {@link SingleIndexWriter} and
 * {@link SplitIndexWriter}. It uses the functionality from
 * {@link HtmlDocletWriter} to generate the Index Contents.
 *
 * @see    IndexBuilder
 * @author Atul M Dambalkar
 */
public class AbstractIndexWriter extends HtmlDocletWriter {

    /**
     * The index of all the members with unicode character.
     */
    protected IndexBuilder indexbuilder;

    /**
     * This constructor will be used by {@link SplitIndexWriter}. Initialises
     * path to this file and relative path from this file.
     *
     * @param path       Path to the file which is getting generated.
     * @param filename   Name of the file which is getting genrated.
     * @param relpath    Relative path from this file to the current directory.
     * @param indexbuilder Unicode based Index from {@link IndexBuilder}
     */
    protected AbstractIndexWriter(ConfigurationImpl configuration,
                                  String path, String filename,
                                  String relpath, IndexBuilder indexbuilder)
                                  throws IOException {
        super(configuration, path, filename, relpath);
        this.indexbuilder = indexbuilder;
    }

    /**
     * This Constructor will be used by {@link SingleIndexWriter}.
     *
     * @param filename   Name of the file which is getting genrated.
     * @param indexbuilder Unicode based Index form {@link IndexBuilder}
     */
    protected AbstractIndexWriter(ConfigurationImpl configuration,
                                  String filename, IndexBuilder indexbuilder)
                                  throws IOException {
        super(configuration, filename);
        this.indexbuilder = indexbuilder;
    }

    /**
     * Print the text "Index" in strong format in the navigation bar.
     */
    protected void navLinkIndex() {
        navCellRevStart();
        fontStyle("NavBarFont1Rev");
        strongText("doclet.Index");
        fontEnd();
        navCellEnd();
    }

    /**
     * Generate the member information for the unicode character along with the
     * list of the members.
     *
     * @param unicode Unicode for which member list information to be generated.
     * @param memberlist List of members for the unicode character.
     */
    protected void generateContents(Character unicode, List<? extends Doc> memberlist) {
        anchor("_" + unicode + "_");
        h2();
        strong(unicode.toString());
        h2End();
        int memberListSize = memberlist.size();
        // Display the list only if there are elements to be displayed.
        if (memberListSize > 0) {
            dl();
            for (int i = 0; i < memberListSize; i++) {
                Doc element = memberlist.get(i);
                if (element instanceof MemberDoc) {
                    printDescription((MemberDoc)element);
                } else if (element instanceof ClassDoc) {
                    printDescription((ClassDoc)element);
                } else if (element instanceof PackageDoc) {
                    printDescription((PackageDoc)element);
                }
            }
            dlEnd();
        }
        hr();
    }


    /**
     * Print one line summary comment for the package.
     *
     * @param pkg PackageDoc passed.
     */
    protected void printDescription(PackageDoc pkg) {
        dt();
        printPackageLink(pkg, Util.getPackageName(pkg), true);
        print(" - ");
        print(configuration.getText("doclet.package") + " " + pkg.name());
        dtEnd();
        dd();
        printSummaryComment(pkg);
        ddEnd();
    }

    /**
     * Print one line summary comment for the class.
     *
     * @param cd ClassDoc passed.
     */
    protected void printDescription(ClassDoc cd) {
        dt();
        printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_INDEX, cd, true));
        print(" - ");
        printClassInfo(cd);
        dtEnd();
        dd();
        printComment(cd);
        ddEnd();
    }

    /**
     * Print the classkind(class, interface, exception, error of the class
     * passed.
     *
     * @param cd ClassDoc.
     */
    protected void printClassInfo(ClassDoc cd) {
        print(configuration.getText("doclet.in",
            Util.getTypeName(configuration, cd, false),
            getPackageLink(cd.containingPackage(),
                Util.getPackageName(cd.containingPackage()), false)));
    }


    /**
     * Generate Description for Class, Field, Method or Constructor.
     * for Java.* Packages Class Members.
     *
     * @param member MemberDoc for the member of the Class Kind.
     * @see com.sun.javadoc.MemberDoc
     */
    protected void printDescription(MemberDoc member) {
        String name = (member instanceof ExecutableMemberDoc)?
            member.name() + ((ExecutableMemberDoc)member).flatSignature() :
            member.name();
        if (name.indexOf("<") != -1 || name.indexOf(">") != -1) {
                name = Util.escapeHtmlChars(name);
        }
        ClassDoc containing = member.containingClass();
        dt();
        printDocLink(LinkInfoImpl.CONTEXT_INDEX, member, name, true);
        println(" - ");
        printMemberDesc(member);
        println();
        dtEnd();
        dd();
        printComment(member);
        ddEnd();
        println();
    }


    /**
     * Print comment for each element in the index. If the element is deprecated
     * and it has a @deprecated tag, use that comment. Else if the containing
     * class for this element is deprecated, then add the word "Deprecated." at
     * the start and then print the normal comment.
     *
     * @param element Index element.
     */
    protected void printComment(ProgramElementDoc element) {
        Tag[] tags;
        if (Util.isDeprecated(element)) {
            strongText("doclet.Deprecated"); space();
            if ((tags = element.tags("deprecated")).length > 0)
                printInlineDeprecatedComment(element, tags[0]);
        } else {
            ClassDoc cont = element.containingClass();
            while (cont != null) {
                if (Util.isDeprecated(cont)) {
                    strongText("doclet.Deprecated"); space();
                    break;
                }
                cont = cont.containingClass();
            }
            printSummaryComment(element);
        }
    }

    /**
     * Print description about the Static Varible/Method/Constructor for a
     * member.
     *
     * @param member MemberDoc for the member within the Class Kind.
     * @see com.sun.javadoc.MemberDoc
     */
    protected void printMemberDesc(MemberDoc member) {
        ClassDoc containing = member.containingClass();
        String classdesc = Util.getTypeName(configuration, containing, true) + " " +
            getPreQualifiedClassLink(LinkInfoImpl.CONTEXT_INDEX, containing,
                false);
        if (member.isField()) {
            if (member.isStatic()) {
                printText("doclet.Static_variable_in", classdesc);
            } else {
                printText("doclet.Variable_in", classdesc);
            }
        } else if (member.isConstructor()) {
            printText("doclet.Constructor_for", classdesc);
        } else if (member.isMethod()) {
            if (member.isStatic()) {
                printText("doclet.Static_method_in", classdesc);
            } else {
                printText("doclet.Method_in", classdesc);
            }
        }
    }
}
