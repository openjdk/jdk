/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.tools.Diagnostic;
import javax.tools.DocumentationTool;
import javax.tools.FileObject;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SnippetTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.DocTreePath;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.formats.html.taglets.snippet.Action;
import jdk.javadoc.internal.doclets.formats.html.taglets.snippet.ParseException;
import jdk.javadoc.internal.doclets.formats.html.taglets.snippet.Parser;
import jdk.javadoc.internal.doclets.formats.html.taglets.snippet.Style;
import jdk.javadoc.internal.doclets.formats.html.taglets.snippet.StyledText;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlAttr;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

import static jdk.javadoc.internal.doclets.formats.html.taglets.SnippetTaglet.Language.*;

/**
 * A taglet that represents the {@code @snippet} tag.
 */
public class SnippetTaglet extends BaseTaglet {

    public enum Language {
        JAVA,
        PROPERTIES;
    }

    SnippetTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.SNIPPET, true, EnumSet.allOf(Taglet.Location.class));
    }

    /*
     * A snippet can specify content by value (inline), by reference (external)
     * or both (hybrid).
     *
     * To specify content by value, a snippet uses its body; the body of
     * a snippet is the content.
     *
     * To specify content by reference, a snippet uses either the "class"
     * or "file" attribute; the value of that attribute refers to the content.
     *
     * A snippet can specify the "region" attribute. That attribute refines
     * the location of the content. The value of that attribute must match
     * one of the named regions in the snippets content.
     */
    @Override
    public Content getInlineTagOutput(Element holder, DocTree tag, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        try {
            return generateContent(holder, tag);
        } catch (BadSnippetException e) {
            error(tagletWriter, holder, e.tag(), e.key(), e.args());
            String details = config.getDocResources().getText(e.key(), e.args());
            return badSnippet(tagletWriter, Optional.of(details));
        }
    }

    /**
     * Returns the output for a {@code {@snippet ...}} tag.
     *
     * @param element    The element that owns the doc comment
     * @param tag        the snippet tag
     * @param id         the value of the id attribute, or null if not defined
     * @param lang       the value of the lang attribute, or null if not defined
     *
     * @return the output
     */
    private Content snippetTagOutput(Element element, SnippetTree tag, StyledText content,
                                       String id, String lang) {
        var pathToRoot = tagletWriter.htmlWriter.pathToRoot;
        var pre = new HtmlTree(HtmlTag.PRE).setStyle(HtmlStyles.snippet);
        if (id != null && !id.isBlank()) {
            pre.put(HtmlAttr.ID, id);
        } else {
            pre.put(HtmlAttr.ID, config.htmlIds.forSnippet(element, ids).name());
        }
        var code = new HtmlTree(HtmlTag.CODE)
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
                    Utils.PreviewFlagProvider prevPreviewProvider = utils.setPreviewFlagProvider(el -> false);
                    try {
                        var lt = (LinkTaglet) config.tagletManager.getTaglet(DocTree.Kind.LINK);
                        c = lt.linkSeeReferenceOutput(element,
                                null,
                                t,
                                e,
                                false, // TODO: for now
                                Text.of(sequence.toString()),
                                (key, args) -> { /* TODO: report diagnostic */ },
                                tagletWriter);
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
        String copyText = resources.getText("doclet.Copy_to_clipboard");
        String copiedText = resources.getText("doclet.Copied_to_clipboard");
        String copySnippetText = resources.getText("doclet.Copy_snippet_to_clipboard");
        var snippetContainer = HtmlTree.DIV(HtmlStyles.snippetContainer,
                new HtmlTree(HtmlTag.BUTTON)
                        .add(HtmlTree.SPAN(Text.of(copyText))
                                .put(HtmlAttr.DATA_COPIED, copiedText))
                        .add(new HtmlTree(HtmlTag.IMG)
                                .put(HtmlAttr.SRC, pathToRoot.resolve(DocPaths.RESOURCE_FILES)
                                                             .resolve(DocPaths.CLIPBOARD_SVG).getPath())
                                .put(HtmlAttr.ALT, copySnippetText))
                        .addStyle(HtmlStyles.copy)
                        .addStyle(HtmlStyles.snippetCopy)
                        .put(HtmlAttr.ARIA_LABEL, copySnippetText)
                        .put(HtmlAttr.ONCLICK, "copySnippet(this)"));
        return snippetContainer.add(pre.add(code));
    }

    private final Set<String> ids = new HashSet<>();

    private static final class BadSnippetException extends Exception {

        @java.io.Serial
        private static final long serialVersionUID = 1;

        private final transient DocTree tag;
        private final String key;
        private final transient Object[] args;

        BadSnippetException(DocTree tag, String key, Object... args) {
            this.tag = tag;
            this.key = key;
            this.args = args;
        }

        DocTree tag() {
            return tag;
        }

        String key() {
            return key;
        }

        Object[] args() {
            return args;
        }
    }

    private Content generateContent(Element holder, DocTree tag)
            throws BadSnippetException
    {
        SnippetTree snippetTag = (SnippetTree) tag;

        // organize snippet attributes in a map, performing basic checks along the way
        Map<String, AttributeTree> attributes = new HashMap<>();
        for (DocTree d : snippetTag.getAttributes()) {
            if (!(d instanceof AttributeTree a)) {
                continue; // this might be an ErroneousTree
            }
            if (attributes.putIfAbsent(a.getName().toString(), a) == null) {
                continue;
            }
            // two like-named attributes found; although we report on the most
            // recently encountered of the two, the iteration order might differ
            // from the source order (see JDK-8266826)
            throw new BadSnippetException(a, "doclet.tag.attribute.repeated",
                    a.getName().toString());
        }

        final String CLASS = "class";
        final String FILE = "file";

        final boolean containsClass = attributes.containsKey(CLASS);
        final boolean containsFile = attributes.containsKey(FILE);
        final boolean containsBody = snippetTag.getBody() != null;

        if (containsClass && containsFile) {
            throw new BadSnippetException(attributes.get(CLASS),
                    "doclet.snippet.contents.ambiguity.external");
        } else if (!containsClass && !containsFile && !containsBody) {
            throw new BadSnippetException(tag, "doclet.snippet.contents.none");
        }

        String regionName = null;
        AttributeTree region = attributes.get("region");
        if (region != null) {
            regionName = stringValueOf(region);
            if (regionName.isBlank()) {
                throw new BadSnippetException(region, "doclet.tag.attribute.value.illegal",
                        "region", region.getValue());
            }
        }

        String inlineContent = null, externalContent = null;

        if (containsBody) {
            inlineContent = snippetTag.getBody().getBody();
        }

        FileObject fileObject = null;

        if (containsFile || containsClass) {
            AttributeTree a;
            String v = containsFile
                    ? stringValueOf((a = attributes.get(FILE)))
                    : stringValueOf((a = attributes.get(CLASS))).replace(".", "/") + ".java";

            if (v.isBlank()) {
                throw new BadSnippetException(a, "doclet.tag.attribute.value.illegal",
                        containsFile ? FILE : CLASS, v);
            }

            // we didn't create JavaFileManager, so we won't close it; even if an error occurs
            var fileManager = config.getFileManager();

            try {
                // first, look in local snippet-files subdirectory
                var pkg = getPackageElement(holder, utils);
                var pkgLocation = utils.getLocationForPackage(pkg);
                var pkgName = pkg.getQualifiedName().toString(); // note: empty string for unnamed package
                var relativeName = "snippet-files/" + v;
                fileObject = fileManager.getFileForInput(pkgLocation, pkgName, relativeName);

                // if not found in local snippet-files directory, look on snippet path
                if (fileObject == null && fileManager.hasLocation(DocumentationTool.Location.SNIPPET_PATH)) {
                    fileObject = fileManager.getFileForInput(DocumentationTool.Location.SNIPPET_PATH, "", v);
                }
            } catch (IOException | IllegalArgumentException e) { // TODO: test this when JDK-8276892 is integrated
                // JavaFileManager.getFileForInput can throw IllegalArgumentException in certain cases
                throw new BadSnippetException(a, "doclet.exception.read.file", v, e);
            }

            if (fileObject == null) {
                // i.e. the file does not exist
                throw new BadSnippetException(a, "doclet.snippet_file_not_found", v);
            }

            try {
                externalContent = fileObject.getCharContent(true).toString();
            } catch (IOException e) {  // TODO: test this when JDK-8276892 is integrated
                throw new BadSnippetException(a, "doclet.exception.read.file",
                        fileObject.getName(), e);
            }
        }

        String lang;
        AttributeTree langAttr = attributes.get("lang");

        if (langAttr != null) { // the lang attribute overrides everything else
            lang = stringValueOf(langAttr);
        } else if (inlineContent != null && externalContent == null) { // an inline snippet
            lang = "java";
        } else if (externalContent != null) { // an external or a hybrid snippet
            if (containsClass) { // the class attribute means Java
                lang = "java";
            } else {
                var uri = fileObject.toUri();
                var path = uri.getPath() != null ? uri.getPath() : "";
                var fileName = path.substring(path.lastIndexOf('/') + 1);
                lang = languageFromFileName(fileName);
            }
        } else {
            throw new AssertionError();
        }

        var language = switch (lang) {
            case "properties" -> PROPERTIES;
            case null, default -> JAVA;
        };

        // TODO cache parsed external snippet (WeakHashMap)

        StyledText inlineSnippet = null;
        StyledText externalSnippet = null;

        try {
            Diags d = (key, pos) -> {
                var path = utils.getCommentHelper(holder)
                        .getDocTreePath(snippetTag.getBody());
                var text = resources.getText(key);
                config.getReporter().print(Diagnostic.Kind.WARNING,
                        path, pos, pos, pos, text);
            };
            if (inlineContent != null) {
                inlineSnippet = parse(resources, d, language, inlineContent);
            }
        } catch (ParseException e) {
            var path = utils.getCommentHelper(holder)
                    .getDocTreePath(snippetTag.getBody());
            // TODO: there should be a method in Messages; that method should mirror Reporter's; use that method instead accessing Reporter.
            String msg = resources.getText("doclet.snippet.markup", e.getMessage());
            config.getReporter().print(Diagnostic.Kind.ERROR,
                    path, e.getPosition(), e.getPosition(), e.getPosition(), msg);
            return badSnippet(tagletWriter, Optional.of(e.getMessage()));
        }

        try {
            var finalFileObject = fileObject;
            Diags d = (key, pos) -> messages.warning(finalFileObject, pos, pos, pos, key);
            if (externalContent != null) {
                externalSnippet = parse(resources, d, language, externalContent);
            }
        } catch (ParseException e) {
            assert fileObject != null;
            messages.error(fileObject, e.getPosition(),
                    e.getPosition(), e.getPosition(), "doclet.snippet.markup", e.getMessage());
            return badSnippet(tagletWriter, Optional.of(e.getMessage()));
        }

        // the region must be matched at least in one content: it can be matched
        // in both, but never in none
        if (regionName != null) {
            StyledText r1 = null;
            StyledText r2 = null;
            if (inlineSnippet != null) {
                r1 = inlineSnippet.getBookmarkedText(regionName);
                if (r1 != null) {
                    inlineSnippet = r1;
                }
            }
            if (externalSnippet != null) {
                r2 = externalSnippet.getBookmarkedText(regionName);
                if (r2 != null) {
                    externalSnippet = r2;
                }
            }
            if (r1 == null && r2 == null) {
                throw new BadSnippetException(tag, "doclet.snippet.region.not_found", regionName);
            }
        }

        if (inlineSnippet != null) {
            inlineSnippet = toDisplayForm(inlineSnippet);
        }

        if (externalSnippet != null) {
            externalSnippet = toDisplayForm(externalSnippet);
        }

        if (inlineSnippet != null && externalSnippet != null) {
            String inlineStr = inlineSnippet.asCharSequence().toString();
            String externalStr = externalSnippet.asCharSequence().toString();
            if (!Objects.equals(inlineStr, externalStr)) {
                throw new BadSnippetException(tag, "doclet.snippet.contents.mismatch", diff(inlineStr, externalStr));
            }
        }

        assert inlineSnippet != null || externalSnippet != null;
        StyledText text = inlineSnippet != null ? inlineSnippet : externalSnippet;

        AttributeTree idAttr = attributes.get("id");
        String id = idAttr == null
                ? null
                : stringValueOf(idAttr);

        return snippetTagOutput(holder, snippetTag, text, id, lang);
    }

    /*
     * Maybe there's a case for implementing a proper (or at least more helpful)
     * diff view, but for now simply outputting both sides of a hybrid snippet
     * would do. A user could then use a diff tool of their choice to compare
     * those sides.
     *
     * There's a separate issue of mapping discrepancies back to their
     * originating source in the doc comment and the external file. Maybe there
     * is a value in it, or maybe there isn't. In any case, accurate mapping
     * would not be trivial to code.
     */
    private static String diff(String inline, String external) {
        return """
               ----------------- inline -------------------
               %s
               ----------------- external -----------------
               %s
               """.formatted(inline, external);
    }

    private StyledText parse(Resources resources, Diags diags, Language language, String content) throws ParseException {
        Parser.Result result = new Parser(resources).parse(diags, language, content);
        result.actions().forEach(Action::perform);
        return result.text();
    }

    public interface Diags {
        void warn(String key, int pos);
    }

    private static String stringValueOf(AttributeTree at) throws BadSnippetException {
        if (at.getValueKind() == AttributeTree.ValueKind.EMPTY) {
            throw new BadSnippetException(at, "doclet.tag.attribute.value.missing",
                    at.getName().toString());
        }
        return at.getValue().stream()
                // value consists of TextTree or ErroneousTree nodes;
                // ErroneousTree is a subtype of TextTree
                .map(t -> ((TextTree) t).getBody())
                .collect(Collectors.joining());
    }

    private String languageFromFileName(String fileName) {
        // The assumption is simple: a file extension is the language
        // identifier.
        // Was about to use Path.getExtension introduced in 8057113, but then
        // learned that it was removed in 8298303.
        int lastPeriod = fileName.lastIndexOf('.');
        if (lastPeriod <= 0) {
            return null;
        }
        return (lastPeriod == fileName.length() - 1) ? null : fileName.substring(lastPeriod + 1);
    }

    private void error(TagletWriter writer, Element holder, DocTree tag, String key, Object... args) {
        messages.error(utils.getCommentHelper(holder).getDocTreePath(tag), key, args);
    }

    private Content badSnippet(TagletWriter writer, Optional<String> details) {
        var resources = config.getDocResources();
        return writer.invalidTagOutput(resources.getText("doclet.tag.invalid", "snippet"), details);
    }

    private static PackageElement getPackageElement(Element e, Utils utils) {
        if (e instanceof DocletElement de) {
            return de.getPackageElement();
        } else {
            return utils.elementUtils.getPackageOf(e);
        }
    }

    /*
     * Returns a version of styled text that can be rendered into HTML or
     * compared to another such version. The latter is used to decide if inline
     * and external parts of a hybrid snippet match.
     *
     * Use this method to obtain a final version of text. After all
     * transformations on text have been performed, call this method with that
     * text and then use the returned result as described above.
     */
    private static StyledText toDisplayForm(StyledText source) {
        var sourceString = source.asCharSequence().toString();
        var result = new StyledText();
        var originalLines = sourceString.lines().iterator();
        var unindentedLines = sourceString.stripIndent().lines().iterator();
        // done; the rest of the method translates the stripIndent
        // transformation performed on a character sequence to the styled
        // text that this sequence originates from, line by line
        int pos = 0;
        // overcome a "quirk" of String.lines
        boolean endsWithLineFeed = !sourceString.isEmpty() && sourceString.charAt(source.length() - 1) == '\n';
        while (originalLines.hasNext() && unindentedLines.hasNext()) { // [^1]
            String originalLine = originalLines.next();
            String unindentedLine = unindentedLines.next();
            // the search MUST succeed
            int idx = originalLine.indexOf(unindentedLine);
            // assume newlines are always of the \n form
            // append the found fragment
            result.append(source.subText(pos + idx, pos + idx + unindentedLine.length()));
            // append the possibly styled newline, but not if it's the last line
            int eol = pos + originalLine.length();
            if (originalLines.hasNext() || endsWithLineFeed) {
                result.append(source.subText(eol, eol + 1));
            }
            pos = eol + 1;
        }
        return result;
        // [^1]: Checking hasNext() on both iterators might look unnecessary.
        // However, there are strings for which those iterators return different
        // number of lines. That is, there exists a string s, such that
        //
        //     s.lines().count() != s.stripIndent().lines().count()
        //
        // The most trivial example of such a string is " ". In fact, any string
        // with a trailing non-empty blank line would do.
    }

    /*
     * Returns the element that is linked from the context of the referrer using
     * the provided signature; returns null if such element could not be found.
     *
     * This method is to be used when it is the target of the link that is
     * important, not the container of the link (e.g. was it an @see,
     * @link/@linkplain or @snippet tags, etc.)
     */
    private Element getLinkedElement(Element referer, String signature) {
        var factory = utils.docTrees.getDocTreeFactory();
        var docCommentTree = utils.getDocCommentTree(referer);
        var rootPath = new DocTreePath(utils.getTreePath(referer), docCommentTree);
        var reference = factory.newReferenceTree(signature);
        var fabricatedPath = new DocTreePath(rootPath, reference);
        return utils.docTrees.getElement(fabricatedPath);
    }
}
