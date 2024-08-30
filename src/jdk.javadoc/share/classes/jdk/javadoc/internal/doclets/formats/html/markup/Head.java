/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.markup;

import java.io.IOException;
import java.io.Writer;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.html.Comment;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlAttr;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Script;

/**
 * An HTML {@code <head>} element.
 *
 * Many methods return the current object, to facilitate fluent builder-style usage.
 */
public class Head extends Content {
    private final Runtime.Version docletVersion;
    private final ZonedDateTime generatedDate;
    private final DocPath pathToRoot;
    private String title;
    private String charset;
    private final List<String> keywords;
    private String description;
    private String generator;
    private boolean showTimestamp;
    private DocPath mainStylesheet;
    private List<DocPath> additionalStylesheets = List.of();
    private List<DocPath> localStylesheets = List.of();
    private boolean index;
    private Script mainBodyScript;
    private final List<Script> scripts;
    // Scripts added via --add-script option
    private List<HtmlConfiguration.JavaScriptFile> additionalScripts = List.of();
    private final List<Content> extraContent;
    private boolean addDefaultScript = true;
    private DocPath canonicalLink;

    /**
     * Creates a {@code Head} object, for a given file and HTML version.
     * The file is used to help determine the relative paths to stylesheet and script files.
     * The HTML version is used to determine the appropriate form of a META element
     * recording the time the file was created.
     * The doclet version should also be provided for recording in the file.
     * @param path the path for the file that will include this HEAD element
     * @param docletVersion the doclet version
     */
    public Head(DocPath path, Runtime.Version docletVersion, ZonedDateTime generatedDate) {
        this.docletVersion = docletVersion;
        this.generatedDate = generatedDate;
        pathToRoot = path.parent().invert();
        keywords = new ArrayList<>();
        scripts = new ArrayList<>();
        extraContent = new ArrayList<>();
    }

    /**
     * Sets the title to appear in the TITLE element.
     *
     * @param title the title
     * @return this object
     */
    public Head setTitle(String title) {
        this.title = title;
        return this;
    }

    /**
     * Sets the charset to be declared in a META {@code Content-TYPE} element.
     *
     * @param charset the charset
     * @return this object
     */
    // For temporary compatibility, this is currently optional.
    // Eventually, this should be a required call.
    public Head setCharset(String charset) {
        this.charset = charset;
        return this;
    }

    /**
     * Sets the content for the description META element.
     */
    public Head setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the content for the generator META element.
     */
    public Head setGenerator(String generator) {
        this.generator = generator;
        return this;
    }

    /**
     * Adds a list of keywords to appear in META {@code keywords} elements.
     *
     * @param keywords the list of keywords, or null if none need to be added
     * @return this object
     */
    public Head addKeywords(List<String> keywords) {
        if (keywords != null) {
            this.keywords.addAll(keywords);
        }
        return this;
    }

    /**
     * Sets whether or not timestamps should be recorded in the HEAD element.
     * The timestamp will be recorded in a comment, and in an appropriate META
     * element, depending on the HTML version specified when this object was created.
     *
     * @param timestamp true if timestamps should be be added.
     * @return this object
     */
    // For temporary backwards compatibility, if this method is not called,
    // no 'Generated by javadoc' comment will be added.
    public Head setTimestamp(boolean timestamp) {
        showTimestamp = timestamp;
        return this;
    }

    /**
     * Sets the main and any additional stylesheets to be listed in the HEAD element.
     * The paths for the stylesheets must be relative to the root of the generated
     * documentation hierarchy.
     *
     * @param main the main stylesheet, or null to use the default
     * @param additional a list of any additional stylesheets to be included
     * @param local a list of module- or package-local stylesheets to be included
     * @return  this object
     */
    public Head setStylesheets(DocPath main, List<DocPath> additional, List<DocPath> local) {
        this.mainStylesheet = main;
        this.additionalStylesheets = additional;
        this.localStylesheets = local;
        return this;
    }

    /**
     * Sets the list of additional script files to be added to the HEAD element.
     * The path for the script files must be relative to the root of the generated
     * documentation hierarchy.
     *
     * @param scripts the list of additional script files
     * @return this object
     */
    public Head setAdditionalScripts(List<HtmlConfiguration.JavaScriptFile> scripts) {
        this.additionalScripts = scripts;
        return this;
    }

    /**
     * Sets whether or not to include the supporting scripts and stylesheets for the
     * "search" feature.
     * If the feature is enabled, a {@code Script} must be provided into which some
     * JavaScript code will be injected, to be executed during page loading. The value
     * will be ignored if the feature is not enabled.
     *
     * @param index true if the supporting files are to be included
     * @param mainBodyScript the {@code Script} object, or null
     * @return this object
     */
    public Head setIndex(boolean index, Script mainBodyScript) {
        this.index = index;
        this.mainBodyScript = mainBodyScript;
        return this;
    }

    /**
     * Adds a script to be included in the HEAD element.
     *
     * @param script the script
     * @return this object
     */
    public Head addScript(Script script) {
        scripts.add(script);
        return this;
    }

    /**
     * Specifies whether or not to add a reference to a default script to be included
     * in the HEAD element.
     * The default script will normally be included; this method may be used to prevent that.
     *
     * @param addDefaultScript whether or not a default script will be included
     * @return this object
     */
    public Head addDefaultScript(boolean addDefaultScript) {
        this.addDefaultScript = addDefaultScript;
        return this;
    }

    /**
     * Specifies a value for a
     * <a href="https://en.wikipedia.org/wiki/Canonical_link_element">canonical link</a>
     * in the {@code <head>} element.
     * @param link the value for the canonical link
     */
    public void setCanonicalLink(DocPath link) {
        this.canonicalLink = link;
    }

    /**
     * Adds additional content to be included in the HEAD element.
     *
     * @param contents the content
     * @return this object
     */
    public Head addContent(Content... contents) {
        extraContent.addAll(Arrays.asList(contents));
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation always returns {@code false}.
     *
     * @return {@code false}
     */
    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean write(Writer out, String newline, boolean atNewline) throws IOException {
        return toContent().write(out, newline, atNewline);
    }

    /**
     * Returns the HTML for the HEAD element.
     *
     * @return the HTML
     */
    private Content toContent() {
        var head = new HtmlTree(HtmlTag.HEAD);
        head.add(getGeneratedBy(showTimestamp, generatedDate));
        head.add(HtmlTree.TITLE(title));

        head.add(HtmlTree.META("viewport", "width=device-width, initial-scale=1"));

        if (charset != null) { // compatibility; should this be allowed?
            head.add(HtmlTree.META("Content-Type", "text/html", charset));
        }

        if (showTimestamp) {
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            head.add(HtmlTree.META("dc.created", generatedDate.format(dateFormat)));
        }

        if (description != null) {
            head.add(HtmlTree.META("description", description));
        }

        if (generator != null) {
            head.add(HtmlTree.META("generator", generator));
        }

        for (String k : keywords) {
            head.add(HtmlTree.META("keywords", k));
        }

        if (canonicalLink != null) {
            var link = new HtmlTree(HtmlTag.LINK);
            link.put(HtmlAttr.REL, "canonical");
            link.put(HtmlAttr.HREF, canonicalLink.getPath());
            head.add(link);
        }

        addStylesheets(head);
        addScripts(head);
        extraContent.forEach(head::add);

        return head;
    }

    private Comment getGeneratedBy(boolean timestamp, ZonedDateTime buildDate) {
        String text = "Generated by javadoc"; // marker string, deliberately not localized
        text += " (" + docletVersion.feature() + ")";
        if (timestamp) {
            DateTimeFormatter fmt =
                    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy").withLocale(Locale.US);
            text += " on " + buildDate.format(fmt);
        }
        return new Comment(text);
    }

    private void addStylesheets(HtmlTree head) {
        if (index) {
            // Add JQuery-UI stylesheet first so its rules can be overridden.
            addStylesheet(head, DocPaths.RESOURCE_FILES.resolve(DocPaths.JQUERY_UI_CSS));
        }

        if (mainStylesheet == null) {
            mainStylesheet = DocPaths.STYLESHEET;
        }
        addStylesheet(head, DocPaths.RESOURCE_FILES.resolve(mainStylesheet));

        for (DocPath path : additionalStylesheets) {
            addStylesheet(head, DocPaths.RESOURCE_FILES.resolve(path));
        }

        for (DocPath path : localStylesheets) {
            // Local stylesheets are contained in doc-files, so omit resource-files prefix
            addStylesheet(head, path);
        }
    }

    private void addStylesheet(HtmlTree head, DocPath stylesheet) {
        head.add(HtmlTree.LINK("stylesheet", "text/css",
                pathToRoot.resolve(stylesheet).getPath(), "Style"));
    }

    private void addScripts(HtmlTree head) {
        if (addDefaultScript) {
            addScriptElement(head, DocPaths.SCRIPT_JS);
        }
        if (index) {
            if (pathToRoot != null && mainBodyScript != null) {
                String ptrPath = pathToRoot.isEmpty() ? "." : pathToRoot.getPath();
                mainBodyScript.append("const pathtoroot = ")
                        .appendStringLiteral(ptrPath + "/")
                        .append(";\n")
                        .append("loadScripts(document, 'script');");
            }
            addScriptElement(head, DocPaths.JQUERY_JS);
            addScriptElement(head, DocPaths.JQUERY_UI_JS);
        }
        for (HtmlConfiguration.JavaScriptFile javaScriptFile : additionalScripts) {
            addScriptElement(head, javaScriptFile);
        }
        for (Script script : scripts) {
            head.add(script.asContent());
        }
    }

    private void addScriptElement(HtmlTree head, DocPath filePath) {
        DocPath scriptFile = pathToRoot.resolve(DocPaths.SCRIPT_FILES).resolve(filePath);
        head.add(HtmlTree.SCRIPT(scriptFile.getPath()));
    }

    private void addScriptElement(HtmlTree head, HtmlConfiguration.JavaScriptFile script) {
        DocPath scriptFile = pathToRoot.resolve(DocPaths.SCRIPT_FILES).resolve(script.path());
        HtmlTree scriptTag = HtmlTree.SCRIPT(scriptFile.getPath());
        head.add(script.isModule() ? scriptTag.put(HtmlAttr.TYPE, "module") : scriptTag);
    }
}
