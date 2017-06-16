/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 *  A utility class.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */

package jdk.javadoc.internal.doclets.toolkit;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.DocTreeFactory;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

public class CommentUtils {

    final BaseConfiguration configuration;
    final DocTreeFactory treeFactory;
    final HashMap<Element, DocCommentDuo> dcTreesMap = new HashMap<>();
    final DocTrees trees;
    final Elements elementUtils;

    protected CommentUtils(BaseConfiguration configuration) {
        this.configuration = configuration;
        trees = configuration.docEnv.getDocTrees();
        treeFactory = trees.getDocTreeFactory();
        elementUtils = configuration.docEnv.getElementUtils();
    }

    public List<? extends DocTree> makePropertyDescriptionTree(List<? extends DocTree> content) {
        List<DocTree> out = new ArrayList<>();
        Name name = elementUtils.getName("propertyDescription");
        out.add(treeFactory.newUnknownBlockTagTree(name, content));
        return out;
    }

    public List<? extends DocTree> makePropertyDescriptionTree(String content) {
        List<DocTree> inlist = new ArrayList<>();
        inlist.add(treeFactory.newCommentTree(content));
        List<DocTree> out = new ArrayList<>();
        Name name = elementUtils.getName("propertyDescription");
        out.add(treeFactory.newUnknownBlockTagTree(name, inlist));
        return out;
    }

    public List<? extends DocTree> makeFirstSentenceTree(String content) {
        List<DocTree> out = new ArrayList<>();
        out.add(treeFactory.newTextTree(content));
        return out;
    }

    public DocTree makeSeeTree(String sig, Element e) {
        List<DocTree> list = new ArrayList<>();
        list.add(treeFactory.newReferenceTree(sig));
        return treeFactory.newSeeTree(list);
    }

    public DocTree makeTextTree(String content) {
        TextTree text = treeFactory.newTextTree(content);
        return (DocTree) text;
    }

    public void setEnumValuesTree(BaseConfiguration config, Element e) {
        Utils utils = config.utils;
        String klassName = utils.getSimpleName(utils.getEnclosingTypeElement(e));

        List<DocTree> fullBody = new ArrayList<>();
        fullBody.add(treeFactory.newTextTree(config.getText("doclet.enum_values_doc.fullbody", klassName)));

        List<DocTree> descriptions = new ArrayList<>();
        descriptions.add(treeFactory.newTextTree(config.getText("doclet.enum_values_doc.return")));

        List<DocTree> tags = new ArrayList<>();
        tags.add(treeFactory.newReturnTree(descriptions));
        DocCommentTree docTree = treeFactory.newDocCommentTree(fullBody, tags);
        dcTreesMap.put(e, new DocCommentDuo(null, docTree));
    }

    public void setEnumValueOfTree(BaseConfiguration config, Element e) {

        List<DocTree> fullBody = new ArrayList<>();
        fullBody.add(treeFactory.newTextTree(config.getText("doclet.enum_valueof_doc.fullbody")));

        List<DocTree> tags = new ArrayList<>();

        List<DocTree> paramDescs = new ArrayList<>();
        paramDescs.add(treeFactory.newTextTree(config.getText("doclet.enum_valueof_doc.param_name")));
        ExecutableElement ee = (ExecutableElement) e;
        java.util.List<? extends VariableElement> parameters = ee.getParameters();
        VariableElement param = parameters.get(0);
        IdentifierTree id = treeFactory.newIdentifierTree(elementUtils.getName(param.getSimpleName().toString()));
        tags.add(treeFactory.newParamTree(false, id, paramDescs));

        List<DocTree> returnDescs = new ArrayList<>();
        returnDescs.add(treeFactory.newTextTree(config.getText("doclet.enum_valueof_doc.return")));
        tags.add(treeFactory.newReturnTree(returnDescs));

        List<DocTree> throwsDescs = new ArrayList<>();
        throwsDescs.add(treeFactory.newTextTree(config.getText("doclet.enum_valueof_doc.throws_ila")));

        ReferenceTree ref = treeFactory.newReferenceTree("java.lang.IllegalArgumentException");
        tags.add(treeFactory.newThrowsTree(ref, throwsDescs));

        throwsDescs = new ArrayList<>();
        throwsDescs.add(treeFactory.newTextTree(config.getText("doclet.enum_valueof_doc.throws_npe")));

        ref = treeFactory.newReferenceTree("java.lang.NullPointerException");
        tags.add(treeFactory.newThrowsTree(ref, throwsDescs));

        DocCommentTree docTree = treeFactory.newDocCommentTree(fullBody, tags);

        dcTreesMap.put(e, new DocCommentDuo(null, docTree));
    }

    /*
     * Returns the TreePath/DocCommentTree duo for synthesized element.
     */
    public DocCommentDuo getSyntheticCommentDuo(Element e) {
        return dcTreesMap.get(e);
    }

    /*
     * Returns the TreePath/DocCommentTree duo for html sources.
     */
    public DocCommentDuo getHtmlCommentDuo(Element e) {
        FileObject fo = null;
        PackageElement pe = null;
        switch (e.getKind()) {
            case OTHER:
                fo = configuration.getOverviewPath();
                pe = configuration.workArounds.getUnnamedPackage();
                break;
            case PACKAGE:
                fo = configuration.workArounds.getJavaFileObject((PackageElement)e);
                pe = (PackageElement)e;
                break;
            default:
                return null;
        }
        if (fo == null) {
            return null;
        }

        DocCommentTree dcTree = trees.getDocCommentTree(fo);
        if (dcTree == null) {
            return null;
        }
        DocTreePath treePath = trees.getDocTreePath(fo, pe);
        return new DocCommentDuo(treePath.getTreePath(), dcTree);
    }

    public DocCommentTree parse(URI uri, String text) {
        return trees.getDocCommentTree(new SimpleJavaFileObject(
                uri, JavaFileObject.Kind.SOURCE) {
            @Override @DefinedBy(Api.COMPILER)
            public CharSequence getCharContent(boolean ignoreEncoding) {
                return text;
            }
        });
    }

    public void setDocCommentTree(Element element, List<DocTree> fullBody,
            List<DocTree> blockTags, Utils utils) {
        DocCommentTree docTree = treeFactory.newDocCommentTree(fullBody, blockTags);
        dcTreesMap.put(element, new DocCommentDuo(null, docTree));
        // There maybe an entry with the original comments usually null,
        // therefore remove that entry if it exists, and allow a new one
        // to be reestablished.
        utils.removeCommentHelper(element);
    }

    /**
     * A simplistic container to transport a TreePath, DocCommentTree pair.
     * Here is why we need this:
     * a. not desirable to add javac's pair.
     * b. DocTreePath is not a viable  option either, as a null TreePath is required
     * to represent synthetic comments for Enum.values, valuesOf, javafx properties.
     */
    public static class DocCommentDuo {
        public final TreePath treePath;
        public final DocCommentTree dcTree;

        public DocCommentDuo(TreePath treePath, DocCommentTree dcTree) {
            this.treePath = treePath;
            this.dcTree = dcTree;
        }
    }
}
