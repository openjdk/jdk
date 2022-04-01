/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.tools.Diagnostic;
import javax.tools.DocumentationTool.Location;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SnippetTree;
import com.sun.source.doctree.TextTree;
import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.Action;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.ParseException;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.Parser;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.StyledText;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A taglet that represents the {@code @snippet} tag.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SnippetTaglet extends BaseTaglet {

    public enum Language {

        JAVA("java"),
        PROPERTIES("properties");

        private static final Map<String, Language> languages;

        static {
            Map<String, Language> tmp = new HashMap<>();
            for (var language : values()) {
                String id = Objects.requireNonNull(language.identifier);
                if (tmp.put(id, language) != null)
                    throw new IllegalStateException(); // 1-1 correspondence
            }
            languages = Map.copyOf(tmp);
        }

        Language(String id) {
            identifier = id;
        }

        private final String identifier;

        public static Optional<Language> of(String identifier) {
            if (identifier == null)
                return Optional.empty();
            return Optional.ofNullable(languages.get(identifier));
        }

        public String getIdentifier() {return identifier;}
    }

    public SnippetTaglet() {
        super(DocTree.Kind.SNIPPET, true, EnumSet.allOf(Taglet.Location.class));
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
    public Content getInlineTagOutput(Element holder, DocTree tag, TagletWriter writer) {
        try {
            return generateContent(holder, tag, writer);
        } catch (BadSnippetException e) {
            error(writer, holder, e.tag(), e.key(), e.args());
            String details = writer.configuration().getDocResources().getText(e.key(), e.args());
            return badSnippet(writer, Optional.of(details));
        }
    }

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

    private Content generateContent(Element holder, DocTree tag, TagletWriter writer)
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
            var fileManager = writer.configuration().getFileManager();

            // first, look in local snippet-files subdirectory
            Utils utils = writer.configuration().utils;
            PackageElement pkg = getPackageElement(holder, utils);
            JavaFileManager.Location l = utils.getLocationForPackage(pkg);
            String relativeName = "snippet-files/" + v;
            String packageName = packageName(pkg, utils);
            try {
                fileObject = fileManager.getFileForInput(l, packageName, relativeName);

                // if not found in local snippet-files directory, look on snippet path
                if (fileObject == null && fileManager.hasLocation(Location.SNIPPET_PATH)) {
                    fileObject = fileManager.getFileForInput(Location.SNIPPET_PATH, "", v);
                }
            } catch (IOException | IllegalArgumentException e) { // TODO: test this when JDK-8276892 is integrated
                // JavaFileManager.getFileForInput can throw IllegalArgumentException in certain cases
                throw new BadSnippetException(a, "doclet.exception.read.file", v, e.getCause());
            }

            if (fileObject == null) {
                // i.e. the file does not exist
                throw new BadSnippetException(a, "doclet.snippet_file_not_found", v);
            }

            try {
                externalContent = fileObject.getCharContent(true).toString();
            } catch (IOException e) {  // TODO: test this when JDK-8276892 is integrated
                throw new BadSnippetException(a, "doclet.exception.read.file",
                        fileObject.getName(), e.getCause());
            }
        }

        String lang = null;
        AttributeTree langAttr = attributes.get("lang");
        if (langAttr != null) {
            lang = stringValueOf(langAttr);
        } else if (containsClass) {
            lang = "java";
        } else if (containsFile) {
            lang = languageFromFileName(fileObject.getName());
        }

        Optional<Language> language = Language.of(lang);


        // TODO cache parsed external snippet (WeakHashMap)

        StyledText inlineSnippet = null;
        StyledText externalSnippet = null;

        try {
            Diags d = (text, pos) -> {
                var path = writer.configuration().utils.getCommentHelper(holder)
                        .getDocTreePath(snippetTag.getBody());
                writer.configuration().getReporter().print(Diagnostic.Kind.WARNING,
                        path, pos, pos, pos, text);
            };
            if (inlineContent != null) {
                inlineSnippet = parse(writer.configuration().getDocResources(), d, language, inlineContent);
            }
        } catch (ParseException e) {
            var path = writer.configuration().utils.getCommentHelper(holder)
                    .getDocTreePath(snippetTag.getBody());
            // TODO: there should be a method in Messages; that method should mirror Reporter's; use that method instead accessing Reporter.
            String msg = writer.configuration().getDocResources()
                    .getText("doclet.snippet.markup", e.getMessage());
            writer.configuration().getReporter().print(Diagnostic.Kind.ERROR,
                    path, e.getPosition(), e.getPosition(), e.getPosition(), msg);
            return badSnippet(writer, Optional.of(e.getMessage()));
        }

        try {
            var finalFileObject = fileObject;
            Diags d = (text, pos) -> writer.configuration().getMessages().warning(finalFileObject, pos, pos, pos, text);
            if (externalContent != null) {
                externalSnippet = parse(writer.configuration().getDocResources(), d, language, externalContent);
            }
        } catch (ParseException e) {
            assert fileObject != null;
            writer.configuration().getMessages().error(fileObject, e.getPosition(),
                    e.getPosition(), e.getPosition(), "doclet.snippet.markup", e.getMessage());
            return badSnippet(writer, Optional.of(e.getMessage()));
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

        return writer.snippetTagOutput(holder, snippetTag, text, id, lang);
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

    private StyledText parse(Resources resources, Diags diags, Optional<Language> language, String content) throws ParseException {
        Parser.Result result = new Parser(resources).parse(diags, language, content);
        result.actions().forEach(Action::perform);
        return result.text();
    }

    public interface Diags {
        void warn(String text, int pos);
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
        // TODO: find a way to extend/customize the list of recognized file name extensions
        if (fileName.endsWith(".java")) {
            return "java";
        } else if (fileName.endsWith(".properties")) {
            return "properties";
        }
        return null;
    }

    private void error(TagletWriter writer, Element holder, DocTree tag, String key, Object... args) {
        writer.configuration().getMessages().error(
            writer.configuration().utils.getCommentHelper(holder).getDocTreePath(tag), key, args);
    }

    private Content badSnippet(TagletWriter writer, Optional<String> details) {
        Resources resources = writer.configuration().getDocResources();
        return writer.invalidTagOutput(resources.getText("doclet.tag.invalid", "snippet"), details);
    }

    private String packageName(PackageElement pkg, Utils utils) {
        return utils.getPackageName(pkg);
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
}
