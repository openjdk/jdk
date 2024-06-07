/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.TextTree;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.ClassWriter;
import jdk.javadoc.internal.doclets.formats.html.Contents;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter;
import jdk.javadoc.internal.doclets.formats.html.SerializedFormWriter;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.formats.html.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

public class SeeTaglet extends BaseTaglet implements InheritableTaglet {
    SeeTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.SEE, false, EnumSet.allOf(Taglet.Location.class));
        contents = config.contents;
    }

    private final Contents contents;
    private HtmlDocletWriter htmlWriter;


    @Override
    public Output inherit(Element dst, Element src, DocTree tag, boolean isFirstSentence) {
        CommentHelper ch = utils.getCommentHelper(dst);
        var path = ch.getDocTreePath(tag);
        messages.warning(path, "doclet.inheritDocWithinInappropriateTag");
        return new Output(null, null, List.of(), true /* true, otherwise there will be an exception up the stack */);
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        List<? extends SeeTree> tags = utils.getSeeTrees(holder);
        Element e = holder;
        if (utils.isMethod(holder)) {
            var docFinder = utils.docFinder();
            Optional<Documentation> result = docFinder.search((ExecutableElement) holder,
                    m -> DocFinder.Result.fromOptional(extract(utils, m))).toOptional();
            if (result.isPresent()) {
                ExecutableElement m = result.get().method();
                tags = utils.getSeeTrees(m);
                e = m;
            }
        }
        return seeTagOutput(e, tags);
    }

    /**
     * Returns the output for {@code @see} tags.
     *
     * @param holder The element that owns the doc comment
     * @param seeTags the list of tags
     *
     * @return the output
     */
    public Content seeTagOutput(Element holder, List<? extends SeeTree> seeTags) {
        htmlWriter = tagletWriter.htmlWriter;

        List<Content> links = new ArrayList<>();
        for (SeeTree dt : seeTags) {
            links.add(seeTagOutput(holder, dt));
        }
        if (utils.isVariableElement(holder) && ((VariableElement)holder).getConstantValue() != null &&
                htmlWriter instanceof ClassWriter classWriter) {
            //Automatically add link to constant values page for constant fields.
            DocPath constantsPath =
                    htmlWriter.pathToRoot.resolve(DocPaths.CONSTANT_VALUES);
            String whichConstant =
                    classWriter.getTypeElement().getQualifiedName() + "." +
                            utils.getSimpleName(holder);
            DocLink link = constantsPath.fragment(whichConstant);
            links.add(htmlWriter.links.createLink(link,
                    contents.getContent("doclet.Constants_Summary")));
        }
        if (utils.isClass(holder) && utils.isSerializable((TypeElement)holder)) {
            //Automatically add link to serialized form page for serializable classes.
            if (SerializedFormWriter.serialInclude(utils, holder) &&
                    SerializedFormWriter.serialInclude(utils, utils.containingPackage(holder))) {
                DocPath serialPath = htmlWriter.pathToRoot.resolve(DocPaths.SERIALIZED_FORM);
                DocLink link = serialPath.fragment(utils.getFullyQualifiedName(holder));
                links.add(htmlWriter.links.createLink(link,
                        contents.getContent("doclet.Serialized_Form")));
            }
        }
        if (links.isEmpty()) {
            return Text.EMPTY;
        }

        var seeList = tagletWriter.tagList(links);
        return new ContentBuilder(
                HtmlTree.DT(contents.seeAlso),
                HtmlTree.DD(seeList));
    }

    private record Documentation(List<? extends SeeTree> seeTrees, ExecutableElement method) { }

    private static Optional<Documentation> extract(Utils utils, ExecutableElement method) {
        List<? extends SeeTree> tags = utils.getSeeTrees(method);
        return tags.isEmpty() ? Optional.empty() : Optional.of(new Documentation(tags, method));
    }

    /**
     * {@return the output for a single {@code @see} tag}
     *
     * @param element the element that has the documentation comment containing this tag
     * @param seeTag  the tag
     */
    private Content seeTagOutput(Element element, SeeTree seeTag) {

        List<? extends DocTree> ref = seeTag.getReference();
        assert !ref.isEmpty();
        DocTree ref0 = ref.get(0);
        switch (ref0.getKind()) {
            case TEXT, MARKDOWN, START_ELEMENT -> {
                // @see "Reference"
                // @see <a href="...">...</a>
                return htmlWriter.commentTagsToContent(element, ref, false, false);
            }

            case REFERENCE -> {
                // @see reference label...
                CommentHelper ch = utils.getCommentHelper(element);
                String refSignature = ch.getReferencedSignature(ref0);
                List<? extends DocTree> label = ref.subList(1, ref.size());

                var lt = (LinkTaglet) config.tagletManager.getTaglet(DocTree.Kind.LINK);
                return lt.linkSeeReferenceOutput(element,
                        seeTag,
                        refSignature,
                        ch.getReferencedElement(seeTag),
                        isPlain(refSignature, label),
                        htmlWriter.commentTagsToContent(element, label, tagletWriter.getContext().within(seeTag)),
                        (key, args) -> messages.warning(ch.getDocTreePath(seeTag), key, args),
                        tagletWriter
                );
            }

            case ERRONEOUS -> {
                return tagletWriter.invalidTagOutput(resources.getText("doclet.tag.invalid_input",
                                ref0.toString()),
                        Optional.empty());
            }

            default -> throw new IllegalStateException(ref0.getKind().toString());
        }
    }

    /**
     * {@return {@code true} if the label should be rendered in plain font}
     *
     * The method uses a heuristic, to see if the string form of the label
     * is a substring of the reference. Thus, for example:
     *
     * <ul>
     * <li>{@code @see MyClass.MY_CONSTANT MY_CONSTANT}  returns {@code true}
     * <li>{@code @see MyClass.MY_CONSTANT a constant}  returns {@code false}
     * </ul>
     *
     * The result will be {@code true} (meaning, displayed in plain font) if
     * any of the following are true about the label:
     *
     * <ul>
     * <li>There is more than a single item in the list of nodes,
     *     suggesting there may be formatting nodes.
     * <li>There is whitespace outside any parentheses,
     *     suggesting the label is a phrase
     * <li>There are nested parentheses, or content after the parentheses,
     *     which cannot occur in a standalone signature
     * <li>The simple name inferred from the reference does not match
     *     any simple name inferred from the label
     * </ul>
     *
     * @param refSignature the signature of the target of the reference
     * @param label the label
     */
    private boolean isPlain(String refSignature, List<? extends DocTree> label) {
        if (label.isEmpty()) {
            return false;
        } else if (label.size() > 1) {
            return true;
        }

        var l0 = label.get(0);
        String s;
        if (l0 instanceof TextTree t) {
            s = t.getBody().trim();
        } else {
            return true;
        }

        // look for whitespace outside any parens, nested parens, or characters after parens:
        // all of which will not be found in a simple signature
        var inParens = false;
        var ids = new ArrayList<String>();
        var sb = new StringBuilder();
        for (var i = 0; i < s.length(); i++) {
             var ch = s.charAt(i);
             if (!sb.isEmpty() && !Character.isJavaIdentifierPart(ch)) {
                 ids.add(sb.toString());
                 sb.setLength(0);
             }

             switch (ch) {
                 case '(' -> {
                     if (inParens) {
                         return true;
                     } else {
                         inParens = true;
                     }
                 }
                 case ')' -> {
                     if (inParens && i < s.length() - 1) {
                         return true;
                     } else {
                         inParens = false;
                     }
                 }
                 default -> {
                     if (!inParens) {
                         if (Character.isJavaIdentifierStart(ch)
                                 || (!sb.isEmpty() && Character.isJavaIdentifierPart(ch))) {
                             sb.append(ch);
                         } else if (Character.isWhitespace(ch)) {
                             return true;
                         }
                     }
                 }
             }
        }

        if (!sb.isEmpty()) {
            ids.add(sb.toString());
        }

        if (ids.isEmpty()) {
            return true;
        }

        // final check: does the simple name inferred from the label
        // match the simple name inferred from the reference
        var labelSimpleName = ids.get(ids.size() - 1);
        var refSimpleName = getSimpleName(refSignature);
        return !labelSimpleName.equals((refSimpleName));
    }

    /**
     * {@return the simple name from a signature}
     *
     * If there is a member part in the signature, the simple name is the
     * identifier after the {@code #} character.
     * Otherwise, the simple name is the last identifier in the signature.
     *
     * @param sig the signature
     */
    private String getSimpleName(String sig) {
        int hash = sig.indexOf('#');
        if (hash == -1 ) {
            int lastDot = sig.lastIndexOf(".");
            return lastDot == -1 ? sig : sig.substring(lastDot + 1);
        } else {
            int parens = sig.indexOf("(", hash);
            return parens == -1 ? sig.substring(hash + 1) : sig.substring(hash + 1, parens);
        }
    }

}
