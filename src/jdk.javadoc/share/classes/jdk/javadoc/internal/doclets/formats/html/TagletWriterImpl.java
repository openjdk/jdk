/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor14;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SnippetTree;
import com.sun.source.doctree.SystemPropertyTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.util.DocTreePath;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.builders.SerializedFormBuilder;
import jdk.javadoc.internal.doclets.toolkit.taglets.ParamTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.Style;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.StyledText;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.Utils.PreviewFlagProvider;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

import static com.sun.source.doctree.DocTree.Kind.LINK_PLAIN;

/**
 * The taglet writer that writes HTML.
 */
public class TagletWriterImpl extends TagletWriter {
    /**
     * A class that provides the information about the enclosing context for
     * a series of {@code DocTree} nodes.
     * This context may be used to determine the content that should be generated from the tree nodes.
     */
    static class Context {
        /**
         * Whether or not the trees are appearing in a context of just the first sentence,
         * such as in the summary table of the enclosing element.
         */
        final boolean isFirstSentence;
        /**
         * Whether or not the trees are appearing in the "summary" section of the
         * page for a declaration.
         */
        final boolean inSummary;
        /**
         * The set of enclosing kinds of tags.
         */
        final Set<DocTree.Kind> inTags;

        /**
         * Creates an outermost context, with no enclosing tags.
         *
         * @param isFirstSentence {@code true} if the trees are appearing in a context of just the
         *                        first sentence and {@code false} otherwise
         * @param inSummary       {@code true} if the trees are appearing in the "summary" section
         *                        of the page for a declaration and {@code false} otherwise
         */
        Context(boolean isFirstSentence, boolean inSummary) {
            this(isFirstSentence, inSummary, EnumSet.noneOf(DocTree.Kind.class));
        }

        private Context(boolean isFirstSentence, boolean inSummary, Set<DocTree.Kind> inTags) {
            this.isFirstSentence = isFirstSentence;
            this.inSummary = inSummary;
            this.inTags = inTags;
        }

        /**
         * Creates a new {@code Context} that includes an extra tag kind in the set of enclosing
         * kinds of tags.
         *
         * @param tree the enclosing tree
         *
         * @return the new {@code Context}
         */
        Context within(DocTree tree) {
            var newInTags = EnumSet.copyOf(inTags);
            newInTags.add(tree.getKind());
            return new Context(isFirstSentence, inSummary, newInTags);
        }
    }

    private final HtmlDocletWriter htmlWriter;
    private final HtmlConfiguration configuration;
    private final HtmlOptions options;
    private final Utils utils;
    private final Resources resources;

    private final Messages messages;

    private final Contents contents;
    private final Context context;

    // Threshold for length of @see tag label for switching from inline to block layout.
    private static final int SEE_TAG_MAX_INLINE_LENGTH = 30;

    /**
     * Creates a taglet writer.
     *
     * @param htmlWriter      the {@code HtmlDocletWriter} for the page
     * @param isFirstSentence {@code true} if this taglet writer is being used for a
     *                        "first sentence" summary
     */
    public TagletWriterImpl(HtmlDocletWriter htmlWriter, boolean isFirstSentence) {
        this(htmlWriter, isFirstSentence, false);
    }

    /**
     * Creates a taglet writer.
     *
     * @param htmlWriter      the {@code HtmlDocletWriter} for the page
     * @param isFirstSentence {@code true} if this taglet writer is being used for a
     *                        "first sentence" summary, and {@code false} otherwise
     * @param inSummary       {@code true} if this taglet writer is being used for the content
     *                        of a {@code {@summary ...}} tag, and {@code false} otherwise
     */
    public TagletWriterImpl(HtmlDocletWriter htmlWriter, boolean isFirstSentence, boolean inSummary) {
        this(htmlWriter, new Context(isFirstSentence, inSummary));
    }

    /**
     * Creates a taglet writer.
     *
     * @param htmlWriter the {@code HtmlDocletWriter} for the page
     * @param context    the enclosing context for any tags
     */
    public TagletWriterImpl(HtmlDocletWriter htmlWriter, Context context) {
        super(context.isFirstSentence);
        this.htmlWriter = htmlWriter;
        this.context = context;
        configuration = htmlWriter.configuration;
        options = configuration.getOptions();
        utils = configuration.utils;
        messages = configuration.messages;
        resources = configuration.getDocResources();
        contents = configuration.getContents();
    }

    @Override
    public Content getOutputInstance() {
        return new ContentBuilder();
    }

    @Override
    protected Content codeTagOutput(Element element, LiteralTree tag) {
        return HtmlTree.CODE(Text.of(Text.normalizeNewlines(tag.getBody().getBody())));
    }

    @Override
    protected Content indexTagOutput(Element element, IndexTree tag) {
        CommentHelper ch = utils.getCommentHelper(element);

        DocTree searchTerm = tag.getSearchTerm();
        String tagText = (searchTerm instanceof TextTree tt) ? tt.getBody() : "";
        if (tagText.charAt(0) == '"' && tagText.charAt(tagText.length() - 1) == '"') {
            tagText = tagText.substring(1, tagText.length() - 1)
                             .replaceAll("\\s+", " ");
        }

        Content desc = htmlWriter.commentTagsToContent(element, tag.getDescription(), context.within(tag));
        String descText = extractText(desc);

        return createAnchorAndSearchIndex(element, tagText, descText, tag);
    }

    // ugly but simple;
    // alternatives would be to walk the Content's tree structure, or to add new functionality to Content
    private String extractText(Content c) {
        return c.toString().replaceAll("<[^>]+>", "");
    }

    @Override
    public Content getDocRootOutput() {
        String path;
        if (htmlWriter.pathToRoot.isEmpty())
            path = ".";
        else
            path = htmlWriter.pathToRoot.getPath();
        return Text.of(path);
    }

    @Override
    public Content deprecatedTagOutput(Element element) {
        ContentBuilder result = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DeprecatedTree> deprs = utils.getDeprecatedTrees(element);
        if (utils.isTypeElement(element)) {
            if (utils.isDeprecated(element)) {
                result.add(HtmlTree.SPAN(HtmlStyle.deprecatedLabel,
                        htmlWriter.getDeprecatedPhrase(element)));
                if (!deprs.isEmpty()) {
                    List<? extends DocTree> commentTrees = ch.getDescription(deprs.get(0));
                    if (!commentTrees.isEmpty()) {
                        result.add(commentTagsToOutput(element, null, commentTrees, false));
                    }
                }
            }
        } else {
            if (utils.isDeprecated(element)) {
                result.add(HtmlTree.SPAN(HtmlStyle.deprecatedLabel,
                        htmlWriter.getDeprecatedPhrase(element)));
                if (!deprs.isEmpty()) {
                    List<? extends DocTree> bodyTrees = ch.getBody(deprs.get(0));
                    Content body = commentTagsToOutput(element, null, bodyTrees, false);
                    if (!body.isEmpty())
                        result.add(HtmlTree.DIV(HtmlStyle.deprecationComment, body));
                }
            } else {
                Element ee = utils.getEnclosingTypeElement(element);
                if (utils.isDeprecated(ee)) {
                    result.add(HtmlTree.SPAN(HtmlStyle.deprecatedLabel,
                        htmlWriter.getDeprecatedPhrase(ee)));
                }
            }
        }
        return result;
    }

    @Override
    public Content linkTagOutput(Element element, LinkTree tag) {
        CommentHelper ch = utils.getCommentHelper(element);

        var linkRef = tag.getReference();
        if (linkRef == null) {
            messages.warning(ch.getDocTreePath(tag), "doclet.link.no_reference");
            return invalidTagOutput(resources.getText("doclet.tag.invalid_input", tag.toString()),
                    Optional.empty());
        }

        DocTree.Kind kind = tag.getKind();
        String tagName = ch.getTagName(tag);
        String refSignature = ch.getReferencedSignature(linkRef);

        return linkSeeReferenceOutput(element,
                tag,
                refSignature,
                ch.getReferencedElement(tag),
                tagName,
                (kind == LINK_PLAIN),
                htmlWriter.commentTagsToContent(element, tag.getLabel(), context),
                (key, args) -> messages.warning(ch.getDocTreePath(tag), key, args)
        );
    }

    @Override
    protected Content literalTagOutput(Element element, LiteralTree tag) {
        return Text.of(Text.normalizeNewlines(tag.getBody().getBody()));
    }

    @Override
    public Content getParamHeader(ParamTaglet.ParamKind kind) {
        Content header = switch (kind) {
            case PARAMETER -> contents.parameters;
            case TYPE_PARAMETER -> contents.typeParameters;
            case RECORD_COMPONENT -> contents.recordComponents;
            default -> throw new IllegalArgumentException(kind.toString());
        };
        return HtmlTree.DT(header);
    }

    @Override
    public Content paramTagOutput(Element element, ParamTree paramTag, String paramName) {
        ContentBuilder body = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        // define id attributes for state components so that generated descriptions may refer to them
        boolean defineID = (element.getKind() == ElementKind.RECORD)
                && !paramTag.isTypeParameter();
        Content nameContent = Text.of(paramName);
        body.add(HtmlTree.CODE(defineID ? HtmlTree.SPAN_ID(HtmlIds.forParam(paramName), nameContent) : nameContent));
        body.add(" - ");
        List<? extends DocTree> description = ch.getDescription(paramTag);
        body.add(htmlWriter.commentTagsToContent(element, description, context.within(paramTag)));
        return HtmlTree.DD(body);
    }

    @Override
    public Content returnTagOutput(Element element, ReturnTree returnTag, boolean inline) {
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DocTree> desc = ch.getDescription(returnTag);
        Content content = htmlWriter.commentTagsToContent(element, desc, context.within(returnTag));
        return inline
                ? new ContentBuilder(contents.getContent("doclet.Returns_0", content))
                : new ContentBuilder(HtmlTree.DT(contents.returns), HtmlTree.DD(content));
    }

    @Override
    public Content seeTagOutput(Element holder, List<? extends SeeTree> seeTags) {
        List<Content> links = new ArrayList<>();
        for (SeeTree dt : seeTags) {
            TagletWriterImpl t = new TagletWriterImpl(htmlWriter, context.within(dt));
            links.add(t.seeTagOutput(holder, dt));
        }
        if (utils.isVariableElement(holder) && ((VariableElement)holder).getConstantValue() != null &&
                htmlWriter instanceof ClassWriterImpl writer) {
            //Automatically add link to constant values page for constant fields.
            DocPath constantsPath =
                    htmlWriter.pathToRoot.resolve(DocPaths.CONSTANT_VALUES);
            String whichConstant =
                    writer.getTypeElement().getQualifiedName() + "." +
                    utils.getSimpleName(holder);
            DocLink link = constantsPath.fragment(whichConstant);
            links.add(htmlWriter.links.createLink(link,
                    contents.getContent("doclet.Constants_Summary")));
        }
        if (utils.isClass(holder) && utils.isSerializable((TypeElement)holder)) {
            //Automatically add link to serialized form page for serializable classes.
            if (SerializedFormBuilder.serialInclude(utils, holder) &&
                      SerializedFormBuilder.serialInclude(utils, utils.containingPackage(holder))) {
                DocPath serialPath = htmlWriter.pathToRoot.resolve(DocPaths.SERIALIZED_FORM);
                DocLink link = serialPath.fragment(utils.getFullyQualifiedName(holder));
                links.add(htmlWriter.links.createLink(link,
                        contents.getContent("doclet.Serialized_Form")));
            }
        }
        if (links.isEmpty()) {
            return Text.EMPTY;
        }
        // Use a different style if any link label is longer than 30 chars or contains commas.
        boolean hasLongLabels = links.stream().anyMatch(this::isLongOrHasComma);
        var seeList = HtmlTree.UL(hasLongLabels ? HtmlStyle.seeListLong : HtmlStyle.seeList);
        links.stream()
                .filter(Predicate.not(Content::isEmpty))
                .forEach(item -> seeList.add(HtmlTree.LI(item)));

        return new ContentBuilder(
                HtmlTree.DT(contents.seeAlso),
                HtmlTree.DD(seeList));
    }

    private boolean isLongOrHasComma(Content c) {
        String s = c.toString()
                .replaceAll("<.*?>", "")              // ignore HTML
                .replaceAll("&#?[A-Za-z0-9]+;", " ")  // entities count as a single character
                .replaceAll("\\R", "\n");             // normalize newlines
        return s.length() > SEE_TAG_MAX_INLINE_LENGTH || s.contains(",");
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
            case TEXT, START_ELEMENT -> {
                // @see "Reference"
                // @see <a href="...">...</a>
                return htmlWriter.commentTagsToContent(element, ref, false, false);
            }

            case REFERENCE -> {
                // @see reference label...
                CommentHelper ch = utils.getCommentHelper(element);
                String tagName = ch.getTagName(seeTag);
                String refSignature = ch.getReferencedSignature(ref0);
                List<? extends DocTree> label = ref.subList(1, ref.size());

                return linkSeeReferenceOutput(element,
                        seeTag,
                        refSignature,
                        ch.getReferencedElement(seeTag),
                        tagName,
                        false,
                        htmlWriter.commentTagsToContent(element, label, context),
                        (key, args) -> messages.warning(ch.getDocTreePath(seeTag), key, args)
                );
            }

            case ERRONEOUS -> {
                return invalidTagOutput(resources.getText("doclet.tag.invalid_input",
                                ref0.toString()),
                        Optional.empty());
            }

            default -> throw new IllegalStateException(ref0.getKind().toString());
        }

    }

    /**
     * Worker method to generate a link from the information in different kinds of tags,
     * such as {@code {@link ...}} tags, {@code @see ...} tags and the {@code link} markup tag
     * in a {@code {@snippet ...}} tag.
     *
     * @param holder        the element that has the documentation comment containing the information
     * @param refTree       the tree node containing the information, or {@code null} if not available
     * @param refSignature  the normalized signature of the target of the reference
     * @param ref           the target of the reference
     * @param tagName       the name of the tag in the source, to be used in diagnostics
     * @param isLinkPlain   {@code true} if the link should be presented in "plain" font,
     *                      or {@code false} for "code" font
     * @param label         the label for the link,
     *                      or an empty item to use a default label derived from the signature
     * @param reportWarning a function to report warnings about issues found in the reference
     *
     * @return the output containing the generated link, or content indicating an error
     */
    private Content linkSeeReferenceOutput(Element holder,
                                           DocTree refTree,
                                           String refSignature,
                                           Element ref,
                                           String tagName,
                                           boolean isLinkPlain,
                                           Content label,
                                           BiConsumer<String, Object[]> reportWarning) {
        Content labelContent = plainOrCode(isLinkPlain, label);

        // The signature from the @see tag. We will output this text when a label is not specified.
        Content text = plainOrCode(isLinkPlain,
                Text.of(Objects.requireNonNullElse(refSignature, "")));

        CommentHelper ch = utils.getCommentHelper(holder);
        TypeElement refClass = ch.getReferencedClass(ref);
        Element refMem =       ch.getReferencedMember(ref);
        String refMemName =    ch.getReferencedMemberName(refSignature);

        if (refMemName == null && refMem != null) {
            refMemName = refMem.toString();
        }
        if (refClass == null) {
            ModuleElement refModule = ch.getReferencedModule(ref);
            if (refModule != null && utils.isIncluded(refModule)) {
                return htmlWriter.getModuleLink(refModule, labelContent.isEmpty() ? text : labelContent);
            }
            //@see is not referencing an included class
            PackageElement refPackage = ch.getReferencedPackage(ref);
            if (refPackage != null && utils.isIncluded(refPackage)) {
                //@see is referencing an included package
                if (labelContent.isEmpty())
                    labelContent = plainOrCode(isLinkPlain,
                            Text.of(refPackage.getQualifiedName()));
                return htmlWriter.getPackageLink(refPackage, labelContent);
            } else {
                // @see is not referencing an included class, module or package. Check for cross links.
                String refModuleName =  ch.getReferencedModuleName(refSignature);
                DocLink elementCrossLink = (refPackage != null) ? htmlWriter.getCrossPackageLink(refPackage) :
                        (configuration.extern.isModule(refModuleName))
                                ? htmlWriter.getCrossModuleLink(utils.elementUtils.getModuleElement(refModuleName))
                                : null;
                if (elementCrossLink != null) {
                    // Element cross link found
                    return htmlWriter.links.createExternalLink(elementCrossLink,
                            (labelContent.isEmpty() ? text : labelContent));
                } else {
                    // No cross link found so print warning
                    if (!configuration.isDocLintReferenceGroupEnabled()) {
                        reportWarning.accept(
                                "doclet.see.class_or_package_not_found",
                                new Object[] { "@" + tagName, refSignature});
                    }
                    return htmlWriter.invalidTagOutput(resources.getText("doclet.tag.invalid", tagName),
                            Optional.of(labelContent.isEmpty() ? text: labelContent));
                }
            }
        } else if (refMemName == null) {
            // Must be a class reference since refClass is not null and refMemName is null.
            if (labelContent.isEmpty() && refTree != null) {
                TypeMirror referencedType = ch.getReferencedType(refTree);
                if (utils.isGenericType(referencedType)) {
                    // This is a generic type link, use the TypeMirror representation.
                    return plainOrCode(isLinkPlain, htmlWriter.getLink(
                            new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.DEFAULT, referencedType)));
                }
                labelContent = plainOrCode(isLinkPlain, Text.of(utils.getSimpleName(refClass)));
            }
            return htmlWriter.getLink(new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.DEFAULT, refClass)
                    .label(labelContent));
        } else if (refMem == null) {
            // Must be a member reference since refClass is not null and refMemName is not null.
            // However, refMem is null, so this referenced member does not exist.
            return (labelContent.isEmpty() ? text: labelContent);
        } else {
            // Must be a member reference since refClass is not null and refMemName is not null.
            // refMem is not null, so this @see tag must be referencing a valid member.
            TypeElement containing = utils.getEnclosingTypeElement(refMem);

            // Find the enclosing type where the method is actually visible
            // in the inheritance hierarchy.
            ExecutableElement overriddenMethod = null;
            if (refMem.getKind() == ElementKind.METHOD) {
                VisibleMemberTable vmt = configuration.getVisibleMemberTable(containing);
                overriddenMethod = vmt.getOverriddenMethod((ExecutableElement)refMem);

                if (overriddenMethod != null)
                    containing = utils.getEnclosingTypeElement(overriddenMethod);
            }
            if (refSignature.trim().startsWith("#") &&
                    ! (utils.isPublic(containing) || utils.isLinkable(containing))) {
                // Since the link is relative and the holder is not even being
                // documented, this must be an inherited link.  Redirect it.
                // The current class either overrides the referenced member or
                // inherits it automatically.
                if (htmlWriter instanceof ClassWriterImpl writer) {
                    containing = writer.getTypeElement();
                } else if (!utils.isPublic(containing)) {
                    reportWarning.accept("doclet.see.class_or_package_not_accessible",
                            new Object[] { tagName, utils.getFullyQualifiedName(containing)});
                } else {
                    if (!configuration.isDocLintReferenceGroupEnabled()) {
                        reportWarning.accept("doclet.see.class_or_package_not_found",
                                new Object[] { tagName, refSignature });
                    }
                }
            }
            if (configuration.currentTypeElement != containing) {
                refMemName = (utils.isConstructor(refMem))
                        ? refMemName
                        : utils.getSimpleName(containing) + "." + refMemName;
            }
            if (utils.isExecutableElement(refMem)) {
                if (refMemName.indexOf('(') < 0) {
                    refMemName += utils.makeSignature((ExecutableElement) refMem, null, true);
                }
                if (overriddenMethod != null) {
                    // The method to actually link.
                    refMem = overriddenMethod;
                }
            }

            return htmlWriter.getDocLink(HtmlLinkInfo.Kind.SEE_TAG, containing,
                    refMem, (labelContent.isEmpty()
                            ? plainOrCode(isLinkPlain, Text.of(refMemName))
                            : labelContent), null, false);
        }
    }

    private Content plainOrCode(boolean plain, Content body) {
        return (plain || body.isEmpty()) ? body : HtmlTree.CODE(body);
    }

    @Override
    public Content simpleBlockTagOutput(Element element, List<? extends DocTree> simpleTags, String header) {
        CommentHelper ch = utils.getCommentHelper(element);
        ContentBuilder body = new ContentBuilder();
        boolean many = false;
        for (DocTree simpleTag : simpleTags) {
            if (many) {
                body.add(", ");
            }
            List<? extends DocTree> bodyTags = ch.getBody(simpleTag);
            body.add(htmlWriter.commentTagsToContent(element, bodyTags, context.within(simpleTag)));
            many = true;
        }
        return new ContentBuilder(
                HtmlTree.DT(RawHtml.of(header)),
                HtmlTree.DD(body));
    }

    @Override
    protected Content snippetTagOutput(Element element, SnippetTree tag, StyledText content,
                                       String id, String lang) {
        var pre = new HtmlTree(TagName.PRE).setStyle(HtmlStyle.snippet);
        if (id != null && !id.isBlank()) {
            pre.put(HtmlAttr.ID, id);
        }
        var code = new HtmlTree(TagName.CODE)
                .addUnchecked(Text.EMPTY); // Make sure the element is always rendered
        if (lang != null && !lang.isBlank()) {
            code.addStyle("language-" + lang);
        }

        content.consumeBy((styles, sequence) -> {
            CharSequence text = Text.normalizeNewlines(sequence);
            if (styles.isEmpty()) {
                code.add(text);
            } else {
                Element e = null;
                String t = null;
                boolean linkEncountered = false;
                boolean markupEncountered = false;
                Set<String> classes = new HashSet<>();
                for (Style s : styles) {
                    if (s instanceof Style.Name n) {
                        classes.add(n.name());
                    } else if (s instanceof Style.Link l) {
                        assert !linkEncountered; // TODO: do not assert; pick the first link report on subsequent
                        linkEncountered = true;
                        t = l.target();
                        e = getLinkedElement(element, t);
                        if (e == null) {
                            // TODO: diagnostic output
                        }
                    } else if (s instanceof Style.Markup) {
                        markupEncountered = true;
                        break;
                    } else {
                        // TODO: transform this if...else into an exhaustive
                        // switch over the sealed Style hierarchy when "Pattern
                        // Matching for switch" has been implemented (JEP 406
                        // and friends)
                        throw new AssertionError(styles);
                    }
                }
                Content c;
                if (markupEncountered) {
                    return;
                } else if (linkEncountered) {
                    assert e != null;
                    //disable preview tagging inside the snippets:
                    PreviewFlagProvider prevPreviewProvider = utils.setPreviewFlagProvider(el -> false);
                    try {
                        c = linkSeeReferenceOutput(element,
                                null,
                                t,
                                e,
                                "link",
                                false, // TODO: for now
                                Text.of(sequence.toString()),
                                (key, args) -> { /* TODO: report diagnostic */ });
                    } finally {
                        utils.setPreviewFlagProvider(prevPreviewProvider);
                    }
                } else {
                    c = HtmlTree.SPAN(Text.of(text));
                    classes.forEach(((HtmlTree) c)::addStyle);
                }
                code.add(c);
            }
        });
        String copyText = resources.getText("doclet.Copy_snippet_to_clipboard");
        String copiedText = resources.getText("doclet.Copied_snippet_to_clipboard");
        var snippetContainer = HtmlTree.DIV(HtmlStyle.snippetContainer,
                new HtmlTree(TagName.BUTTON)
                        .add(HtmlTree.SPAN(Text.of(copyText))
                                .put(HtmlAttr.DATA_COPIED, copiedText))
                        .add(new HtmlTree(TagName.IMG)
                                .put(HtmlAttr.SRC, htmlWriter.pathToRoot.resolve(DocPaths.CLIPBOARD_SVG).getPath())
                                .put(HtmlAttr.ALT, copyText))
                        .addStyle(HtmlStyle.copy)
                        .addStyle(HtmlStyle.snippetCopy)
                        .put(HtmlAttr.ONCLICK, "copySnippet(this)"));
        return snippetContainer.add(pre.add(code));
    }

    /*
     * Returns the element that is linked from the context of the referrer using
     * the provided signature; returns null if such element could not be found.
     *
     * This method is to be used when it is the target of the link that is
     * important, not the container of the link (e.g. was it an @see,
     * @link/@linkplain or @snippet tags, etc.)
     */
    public Element getLinkedElement(Element referer, String signature) {
        var factory = utils.docTrees.getDocTreeFactory();
        var docCommentTree = utils.getDocCommentTree(referer);
        var rootPath = new DocTreePath(utils.getTreePath(referer), docCommentTree);
        var reference = factory.newReferenceTree(signature);
        var fabricatedPath = new DocTreePath(rootPath, reference);
        return utils.docTrees.getElement(fabricatedPath);
    }

    @Override
    protected Content systemPropertyTagOutput(Element element, SystemPropertyTree tag) {
        String tagText = tag.getPropertyName().toString();
        return HtmlTree.CODE(createAnchorAndSearchIndex(element, tagText,
                resources.getText("doclet.System_Property"), tag));
    }

    @Override
    public Content getThrowsHeader() {
        return HtmlTree.DT(contents.throws_);
    }

    @Override
    public Content throwsTagOutput(Element element, ThrowsTree throwsTag, TypeMirror substituteType) {
        ContentBuilder body = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        Element exception = ch.getException(throwsTag);
        Content excName;
        if (substituteType != null) {
           excName = htmlWriter.getLink(new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.MEMBER,
                   substituteType));
        } else if (exception == null) {
            excName = RawHtml.of(throwsTag.getExceptionName().toString());
        } else if (exception.asType() == null) {
            excName = Text.of(utils.getFullyQualifiedName(exception));
        } else {
            HtmlLinkInfo link = new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.MEMBER,
                                                 exception.asType());
            link.excludeTypeBounds = true;
            excName = htmlWriter.getLink(link);
        }
        body.add(HtmlTree.CODE(excName));
        List<? extends DocTree> description = ch.getDescription(throwsTag);
        Content desc = htmlWriter.commentTagsToContent(element, description, context.within(throwsTag));
        if (desc != null && !desc.isEmpty()) {
            body.add(" - ");
            body.add(desc);
        }
        return HtmlTree.DD(body);
    }

    @Override
    public Content throwsTagOutput(TypeMirror throwsType) {
        return HtmlTree.DD(HtmlTree.CODE(htmlWriter.getLink(
                new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.MEMBER, throwsType))));
    }

    @Override
    public Content valueTagOutput(VariableElement field, String constantVal, boolean includeLink) {
        return includeLink
                ? htmlWriter.getDocLink(HtmlLinkInfo.Kind.VALUE_TAG, field, constantVal)
                : Text.of(constantVal);
    }

    @Override
    protected Content invalidTagOutput(String summary, Optional<String> detail) {
        return htmlWriter.invalidTagOutput(summary,
                detail.isEmpty() || detail.get().isEmpty()
                        ? Optional.empty()
                        : Optional.of(Text.of(Text.normalizeNewlines(detail.get()))));
    }

    @Override
    public Content commentTagsToOutput(DocTree holder, List<? extends DocTree> tags) {
        return commentTagsToOutput(null, holder, tags, false);
    }

    @Override
    public Content commentTagsToOutput(Element element, List<? extends DocTree> tags) {
        return commentTagsToOutput(element, null, tags, false);
    }

    @Override
    public Content commentTagsToOutput(Element holder,
                                       DocTree holderTag,
                                       List<? extends DocTree> tags,
                                       boolean isFirstSentence)
    {
        return htmlWriter.commentTagsToContent(holder,
                tags, holderTag == null ? context : context.within(holderTag));
    }

    @Override
    public BaseConfiguration configuration() {
        return configuration;
    }

    @Override
    protected TypeElement getCurrentPageElement() {
        return htmlWriter.getCurrentPageElement();
    }

    private Content createAnchorAndSearchIndex(Element element, String tagText, String desc, DocTree tree) {
        Content result = null;
        if (context.isFirstSentence && context.inSummary || context.inTags.contains(DocTree.Kind.INDEX)) {
            result = Text.of(tagText);
        } else {
            HtmlId id = HtmlIds.forText(tagText, htmlWriter.indexAnchorTable);
            result = HtmlTree.SPAN(id, HtmlStyle.searchTagResult, Text.of(tagText));
            if (options.createIndex() && !tagText.isEmpty()) {
                String holder = new SimpleElementVisitor14<String, Void>() {

                    @Override
                    public String visitModule(ModuleElement e, Void p) {
                        return resources.getText("doclet.module")
                                + " " + utils.getFullyQualifiedName(e);
                    }

                    @Override
                    public String visitPackage(PackageElement e, Void p) {
                        return resources.getText("doclet.package")
                                + " " + utils.getFullyQualifiedName(e);
                    }

                    @Override
                    public String visitType(TypeElement e, Void p) {
                        return utils.getTypeElementKindName(e, true)
                                + " " + utils.getFullyQualifiedName(e);
                    }

                    @Override
                    public String visitExecutable(ExecutableElement e, Void p) {
                        return utils.getFullyQualifiedName(utils.getEnclosingTypeElement(e))
                                + "." + utils.getSimpleName(e)
                                + utils.flatSignature(e, htmlWriter.getCurrentPageElement());
                    }

                    @Override
                    public String visitVariable(VariableElement e, Void p) {
                        return utils.getFullyQualifiedName(utils.getEnclosingTypeElement(e))
                                + "." + utils.getSimpleName(e);
                    }

                    @Override
                    public String visitUnknown(Element e, Void p) {
                        if (e instanceof DocletElement de) {
                            return switch (de.getSubKind()) {
                                case OVERVIEW -> resources.getText("doclet.Overview");
                                case DOCFILE -> getHolderName(de);
                            };
                        } else {
                            return super.visitUnknown(e, p);
                        }
                    }

                    @Override
                    protected String defaultAction(Element e, Void p) {
                        return utils.getFullyQualifiedName(e);
                    }
                }.visit(element);
                IndexItem item = IndexItem.of(element, tree, tagText, holder, desc,
                        new DocLink(htmlWriter.path, id.name()));
                configuration.mainIndex.add(item);
            }
        }
        return result;
    }

    private String getHolderName(DocletElement de) {
        PackageElement pe = de.getPackageElement();
        if (pe.isUnnamed()) {
            // if package is unnamed use enclosing module only if it is named
            Element ee = pe.getEnclosingElement();
            if (ee instanceof ModuleElement && !((ModuleElement)ee).isUnnamed()) {
                return resources.getText("doclet.module") + " " + utils.getFullyQualifiedName(ee);
            }
            return pe.toString(); // "Unnamed package" or similar
        }
        return resources.getText("doclet.package") + " " + utils.getFullyQualifiedName(pe);
    }
}
