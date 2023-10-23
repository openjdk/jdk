/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor9;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.lang.model.util.SimpleTypeVisitor9;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.source.doctree.CommentTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocRootTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTree.Kind;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.EscapeTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.doctree.InlineTagTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.SimpleDocTreeVisitor;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.Head;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlDocument;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Links;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.Script;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.formats.html.markup.TextBuilder;
import jdk.javadoc.internal.doclets.formats.html.taglets.Taglet;
import jdk.javadoc.internal.doclets.formats.html.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.Comparators;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.Utils.DeclarationPreviewLanguageFeatures;
import jdk.javadoc.internal.doclets.toolkit.util.Utils.ElementFlag;
import jdk.javadoc.internal.doclets.toolkit.util.Utils.PreviewSummary;
import jdk.javadoc.internal.doclint.HtmlTag;

import static com.sun.source.doctree.DocTree.Kind.COMMENT;
import static com.sun.source.doctree.DocTree.Kind.TEXT;


/**
 * The base class for classes that write complete HTML pages to be included in the overall API documentation.
 * The primary method is {@link #buildPage()}.
 */
public abstract class HtmlDocletWriter {

    /**
     * Relative path from the file getting generated to the destination
     * directory. For example, if the file getting generated is
     * "java/lang/Object.html", then the path to the root is "../..".
     * This string can be empty if the file getting generated is in
     * the destination directory.
     */
    public final DocPath pathToRoot;

    /**
     * Platform-independent path from the current or the
     * destination directory to the file getting generated.
     * Used when creating the file.
     */
    public final DocPath path;

    /**
     * The global configuration information for this run.
     */
    public final HtmlConfiguration configuration;

    protected final HtmlOptions options;

    protected final Utils utils;

    protected final Contents contents;

    public final Messages messages;

    protected final Resources resources;

    public final Links links;

    protected final DocPaths docPaths;

    protected final Comparators comparators;

    protected final HtmlIds htmlIds;

    private final Set<String> headingIds = new HashSet<>();

    /**
     * To check whether the repeated annotations is documented or not.
     */
    private boolean isAnnotationDocumented = false;

    /**
     * To check whether the container annotations is documented or not.
     */
    private boolean isContainerDocumented = false;

    /**
     * The window title of this file.
     */
    protected String winTitle;

    protected Script mainBodyScript;

    /**
     * A table of the anchors used for at-index and related tags,
     * so that they can be made unique by appending a suitable suffix.
     * (Ideally, javadoc should be tracking all id's generated in a file
     * to avoid generating duplicates.)
     */
    public final Map<String, Integer> indexAnchorTable = new HashMap<>();

    /**
     * Creates an {@code HtmlDocletWriter}.
     *
     * @param configuration the configuration for this doclet
     * @param path the file to be generated.
     */
    public HtmlDocletWriter(HtmlConfiguration configuration, DocPath path) {
        this(configuration, path, true);
    }
    /**
     * Creates an {@code HtmlDocletWriter}.
     *
     * @param configuration the configuration for this doclet
     * @param path the file to be generated.
     * @param generating whether to write a "Geneterating ..." message to the console
     */
    protected HtmlDocletWriter(HtmlConfiguration configuration, DocPath path, boolean generating) {
        this.configuration = configuration;
        this.options = configuration.getOptions();
        this.contents = configuration.getContents();
        this.messages = configuration.messages;
        this.resources = configuration.docResources;
        this.links = new Links(path);
        this.utils = configuration.utils;
        this.comparators = utils.comparators;
        this.htmlIds = configuration.htmlIds;
        this.path = path;
        this.pathToRoot = path.parent().invert();
        this.docPaths = configuration.docPaths;
        this.mainBodyScript = new Script();

        if (generating) {
            writeGenerating();
        }
    }

    /**
     * The top-level method to generate and write the page represented by this writer.
     *
     * @throws DocletException if a problem occurs while building or writing the page
     */
    public abstract void buildPage() throws DocletException;

    /**
     * Writes a "Generating _file_" message to the console
     */
    protected final void writeGenerating() {
        messages.notice("doclet.Generating_0",
                DocFile.createFileForOutput(configuration, path).getPath());
    }

    /**
     * Replace {&#064;docRoot} tag used in options that accept HTML text, such
     * as -header, -footer, -top and -bottom, and when converting a relative
     * HREF where commentTagsToString inserts a {&#064;docRoot} where one was
     * missing.  (Also see DocRootTaglet for {&#064;docRoot} tags in doc
     * comments.)
     * <p>
     * Replace {&#064;docRoot} tag in htmlstr with the relative path to the
     * destination directory from the directory where the file is being
     * written, looping to handle all such tags in htmlstr.
     * <p>
     * For example, for "-d docs" and -header containing {&#064;docRoot}, when
     * the HTML page for source file p/C1.java is being generated, the
     * {&#064;docRoot} tag would be inserted into the header as "../",
     * the relative path from docs/p/ to docs/ (the document root).
     * <p>
     * Note: This doc comment was written with '&amp;#064;' representing '@'
     * to prevent the inline tag from being interpreted.
     */
    public String replaceDocRootDir(String htmlstr) {
        // Return if no inline tags exist
        int index = htmlstr.indexOf("{@");
        if (index < 0) {
            return htmlstr;
        }
        Matcher docrootMatcher = docrootPattern.matcher(htmlstr);
        if (!docrootMatcher.find()) {
            return htmlstr;
        }
        StringBuilder buf = new StringBuilder();
        int prevEnd = 0;
        do {
            int match = docrootMatcher.start();
            // append htmlstr up to start of next {@docroot}
            buf.append(htmlstr, prevEnd, match);
            prevEnd = docrootMatcher.end();
            if (options.docrootParent().length() > 0 && htmlstr.startsWith("/..", prevEnd)) {
                // Insert the absolute link if {@docRoot} is followed by "/..".
                buf.append(options.docrootParent());
                prevEnd += 3;
            } else {
                // Insert relative path where {@docRoot} was located
                buf.append(pathToRoot.isEmpty() ? "." : pathToRoot.getPath());
            }
            // Append slash if next character is not a slash
            if (prevEnd < htmlstr.length() && htmlstr.charAt(prevEnd) != '/') {
                buf.append('/');
            }
        } while (docrootMatcher.find());
        buf.append(htmlstr.substring(prevEnd));
        return buf.toString();
    }
    //where:
        // Note: {@docRoot} is not case-sensitive when passed in with a command-line option:
        private static final Pattern docrootPattern =
                Pattern.compile(Pattern.quote("{@docroot}"), Pattern.CASE_INSENSITIVE);


    /**
     * Add method information.
     *
     * @param method the method to be documented
     * @param dl the content to which the method information will be added
     */
    private void addMethodInfo(ExecutableElement method, Content dl) {
        var enclosing = (TypeElement) method.getEnclosingElement();
        var overrideInfo = utils.overriddenMethod(method);
        var enclosingVmt = configuration.getVisibleMemberTable(enclosing);
        var implementedMethods = enclosingVmt.getImplementedMethods(method);
        if ((!enclosing.getInterfaces().isEmpty()
                && !implementedMethods.isEmpty())
                || overrideInfo != null) {
            // TODO note that if there are any overridden interface methods throughout the
            //   hierarchy, !enclosingVmt.getImplementedMethods(method).isEmpty(), their information
            //   will be printed if *any* of the below is true:
            //     * the enclosing has _directly_ implemented interfaces
            //     * the overridden method is not null
            //   If both are false, the information will not be printed: there will be no
            //   "Specified by" documentation. The examples of that can be seen in documentation
            //   for these methods:
            //     * ForkJoinPool.execute(java.lang.Runnable)
            //  This is a long-standing bug, which must be fixed separately: JDK-8302316
            MethodWriter.addImplementsInfo(this, method, implementedMethods, dl);
        }
        if (overrideInfo != null) {
            MethodWriter.addOverridden(this,
                    overrideInfo.overriddenMethodOwner(),
                    overrideInfo.overriddenMethod(),
                    dl);
        }
    }

    /**
     * Adds the tags information.
     *
     * @param e the Element for which the tags will be generated
     * @param content the content to which the tags will be added
     */
    protected void addTagsInfo(Element e, Content content) {
        if (options.noComment()) {
            return;
        }
        var dl = HtmlTree.DL(HtmlStyle.notes);
        if (utils.isMethod(e)) {
            addMethodInfo((ExecutableElement)e, dl);
        }
        Content output = getBlockTagOutput(e);
        dl.add(output);
        content.add(dl);
    }

    /**
     * Returns the content generated from the default supported set of block tags
     * for this element.
     *
     * @param element the element
     *
     * @return the content
     */
    protected Content getBlockTagOutput(Element element) {
        return getBlockTagOutput(element, configuration.tagletManager.getBlockTaglets(element));
    }

    /**
     * Returns the content generated from a specified set of block tags
     * for this element.
     *
     * @param element the element
     * @param taglets the taglets to handle the required set of tags
     *
     * @return the content
     */
    protected Content getBlockTagOutput(Element element, List<Taglet> taglets) {
        return getTagletWriterInstance(false)
                .getBlockTagOutput(configuration.tagletManager, element, taglets);
    }

    /**
     * Returns whether there are any tags in a field for the Serialization Overview
     * section to be generated.
     *
     * @param field the field to check
     * @return {@code true} if and only if there are tags to be included
     */
    protected boolean hasSerializationOverviewTags(VariableElement field) {
        Content output = getBlockTagOutput(field);
        return !output.isEmpty();
    }

    private Content getInlineTagOutput(Element element, InlineTagTree tree, TagletWriter.Context context) {
        return getTagletWriterInstance(context).getInlineTagOutput(element, tree);
    }

    /**
     * Returns a TagletWriter that knows how to write HTML.
     *
     * @param isFirstSentence  true if we want to write the first sentence
     * @return a TagletWriter that knows how to write HTML.
     */
    public TagletWriter getTagletWriterInstance(boolean isFirstSentence) {
        return new TagletWriter(this, isFirstSentence);
    }

    /**
     * Returns a TagletWriter that knows how to write HTML.
     *
     * @param context  the enclosing context
     * @return a TagletWriter
     */
    public TagletWriter getTagletWriterInstance(TagletWriter.Context context) {
        return new TagletWriter(this, context);
    }

    /**
     * {@return true if the page written by this writer should be indexed,
     * false otherwise}
     *
     * Some pages merely aggregate filtered information available on other pages
     * and, thus, have no indexing value. In fact, if indexed, they would
     * clutter the index and mislead the reader.
     *
     * @implSpec The default implementation returns {@code false}.
     */
    public boolean isIndexable() {
        return false;
    }

    /**
     * Generates the HTML document tree and prints it out.
     *
     * @param metakeywords Array of String keywords for META tag. Each element
     *                     of the array is assigned to a separate META tag.
     *                     Pass in null for no array
     * @param description the content for the description META tag.
     * @param body the body htmltree to be included in the document
     * @throws DocFileIOException if there is a problem writing the file
     */
    public void printHtmlDocument(List<String> metakeywords,
                                  String description,
                                  Content body)
            throws DocFileIOException {
        printHtmlDocument(metakeywords, description, new ContentBuilder(), List.of(), body);
    }

    /**
     * Generates the HTML document tree and prints it out.
     *
     * @param metakeywords Array of String keywords for META tag. Each element
     *                     of the array is assigned to a separate META tag.
     *                     Pass in null for no array
     * @param description the content for the description META tag.
     * @param localStylesheets local stylesheets to be included in the HEAD element
     * @param body the body htmltree to be included in the document
     * @throws DocFileIOException if there is a problem writing the file
     */
    public void printHtmlDocument(List<String> metakeywords,
                                  String description,
                                  List<DocPath> localStylesheets,
                                  Content body)
            throws DocFileIOException {
        printHtmlDocument(metakeywords, description, new ContentBuilder(), localStylesheets, body);
    }

    /**
     * Generates the HTML document tree and prints it out.
     *
     * @param metakeywords Array of String keywords for META tag. Each element
     *                     of the array is assigned to a separate META tag.
     *                     Pass in null for no array
     * @param description the content for the description META tag.
     * @param extraHeadContent any additional content to be included in the HEAD element
     * @param localStylesheets local stylesheets to be included in the HEAD element
     * @param body the body htmltree to be included in the document
     * @throws DocFileIOException if there is a problem writing the file
     */
    public void printHtmlDocument(List<String> metakeywords,
                                  String description,
                                  Content extraHeadContent,
                                  List<DocPath> localStylesheets,
                                  Content body)
            throws DocFileIOException {
        List<DocPath> additionalStylesheets = configuration.getAdditionalStylesheets();
        Head head = new Head(path, configuration.getDocletVersion(), configuration.getBuildDate())
                .setTimestamp(!options.noTimestamp())
                .setDescription(description)
                .setGenerator(getGenerator(getClass()))
                .setTitle(winTitle)
                .setCharset(options.charset())
                .addKeywords(metakeywords)
                .setStylesheets(configuration.getMainStylesheet(), additionalStylesheets, localStylesheets)
                .setAdditionalScripts(configuration.getAdditionalScripts())
                .setIndex(options.createIndex(), mainBodyScript)
                .addContent(extraHeadContent);

        HtmlDocument htmlDocument = new HtmlDocument(
                HtmlTree.HTML(configuration.getLocale().getLanguage(), head, body));
        htmlDocument.write(DocFile.createFileForOutput(configuration, path));
    }

    /**
     * Get the window title.
     *
     * @param title the title string to construct the complete window title
     * @return the window title string
     */
    public String getWindowTitle(String title) {
        if (options.windowTitle().length() > 0) {
            title += " (" + options.windowTitle() + ")";
        }
        return title;
    }

    /**
     * Returns a {@code <header>} element, containing the user "top" text, if any,
     * and the main navigation bar.
     *
     * @param pageMode the pageMode used to configure the navigation bar
     *
     * @return the {@code <header>} element
     */
    protected Content getHeader(Navigation.PageMode pageMode) {
        return getHeader(pageMode, null);
    }

    /**
     * Returns a {@code <header>} element, containing the user "top" text, if any,
     * and the main navigation bar.
     *
     * @param pageMode the page mode used to configure the navigation bar
     * @param element  the element used to configure the navigation bar
     *
     * @return the {@code <header>} element
     */
    protected Content getHeader(Navigation.PageMode pageMode, Element element) {
        return HtmlTree.HEADER()
                        .add(RawHtml.of(replaceDocRootDir(options.top())))
                        .add(getNavBar(pageMode, element).getContent());
    }

    /**
     * Returns a basic navigation bar for a kind of page and element.
     *
     * @apiNote the result may be further configured by overriding this method
     *
     * @param pageMode the page mode
     * @param element  the defining element for the navigation bar, or {@code null} if none
     * @return the basic navigation bar
     */
    protected Navigation getNavBar(Navigation.PageMode pageMode, Element element) {
        return new Navigation(element, configuration, pageMode, path)
                .setUserHeader(RawHtml.of(replaceDocRootDir(options.header())));
    }

    /**
     * Returns a {@code <footer>} element containing the user's "bottom" text,
     * or {@code null} if there is no such text.
     *
     * @return the {@code <footer>} element or {@code null}.
     */
    public HtmlTree getFooter() {
        String bottom = options.bottom();
        return (bottom == null || bottom.isEmpty())
                ? null
                : HtmlTree.FOOTER()
                    .add(new HtmlTree(TagName.HR))
                    .add(HtmlTree.P(HtmlStyle.legalCopy,
                            HtmlTree.SMALL(
                                    RawHtml.of(replaceDocRootDir(bottom)))));
    }

    /**
     * {@return an "overview tree" link for a navigation bar}
     *
     * @param label the label for the link
     */
    protected Content getNavLinkToOverviewTree(String label) {
        Content link = links.createLink(pathToRoot.resolve(DocPaths.OVERVIEW_TREE),
                Text.of(label));
        return HtmlTree.LI(link);
    }

    /**
     * {@return a package name}
     *
     * A localized name is returned for an unnamed package.
     * Use {@link Utils#getPackageName(PackageElement)} to get a static string
     * for the unnamed package instead.
     *
     * @param packageElement the package to get the name for
     */
    public Content getLocalizedPackageName(PackageElement packageElement) {
        return packageElement == null || packageElement.isUnnamed()
                ? contents.defaultPackageLabel
                : getPackageLabel(packageElement.getQualifiedName());
    }

    /**
     * Returns a package name label.
     *
     * @param packageName the package name
     * @return the package name content
     */
    public Content getPackageLabel(CharSequence packageName) {
        return Text.of(packageName);
    }

    /**
     * Return the path to the class page for a typeElement.
     *
     * @param te   TypeElement for which the path is requested.
     * @param name Name of the file(doesn't include path).
     */
    protected DocPath pathString(TypeElement te, DocPath name) {
        return pathString(utils.containingPackage(te), name);
    }

    /**
     * Return path to the given file name in the given package. So if the name
     * passed is "Object.html" and the name of the package is "java.lang", and
     * if the relative path is "../.." then returned string will be
     * "../../java/lang/Object.html"
     *
     * @param packageElement Package in which the file name is assumed to be.
     * @param name File name, to which path string is.
     */
    protected DocPath pathString(PackageElement packageElement, DocPath name) {
        return pathToRoot.resolve(docPaths.forPackage(packageElement).resolve(name));
    }

    /**
     * {@return the link to the given package}
     *
     * @param packageElement the package to link to
     * @param label the label for the link
     */
    public Content getPackageLink(PackageElement packageElement, Content label) {
        return getPackageLink(packageElement, label, null);
    }

    /**
     * {@return the link to the given package}
     *
     * @param packageElement the package to link to
     * @param label the label for the link
     * @param fragment the link fragment
     */
    public Content getPackageLink(PackageElement packageElement, Content label, String fragment) {
        boolean included = packageElement != null && utils.isIncluded(packageElement);
        if (!included) {
            for (PackageElement p : configuration.packages) {
                if (p.equals(packageElement)) {
                    included = true;
                    break;
                }
            }
        }
        Set<ElementFlag> flags;
        if (packageElement != null) {
            flags = utils.elementFlags(packageElement);
        } else {
            flags = EnumSet.noneOf(ElementFlag.class);
        }
        DocLink targetLink;
        if (included || packageElement == null) {
            targetLink = new DocLink(pathString(packageElement, DocPaths.PACKAGE_SUMMARY), fragment);
        } else {
            targetLink = getCrossPackageLink(packageElement);
        }
        if (targetLink != null) {
            if (flags.contains(ElementFlag.PREVIEW)) {
                return new ContentBuilder(
                    links.createLink(targetLink, label),
                    HtmlTree.SUP(links.createLink(targetLink.withFragment(htmlIds.forPreviewSection(packageElement).name()),
                                                  contents.previewMark))
                );
            }
            return links.createLink(targetLink, label);
        } else {
            if (flags.contains(ElementFlag.PREVIEW)) {
                return new ContentBuilder(
                    label,
                    HtmlTree.SUP(contents.previewMark)
                );
            }
            return label;
        }
    }

    /**
     * {@return a link to module}
     *
     * @param mdle the module being documented
     * @param label tag for the link
     */
    public Content getModuleLink(ModuleElement mdle, Content label) {
        return getModuleLink(mdle, label, null);
    }

    /**
     * {@return a link to module}
     *
     * @param mdle the module being documented
     * @param label tag for the link
     * @param fragment the link fragment
     */
    public Content getModuleLink(ModuleElement mdle, Content label, String fragment) {
        Set<ElementFlag> flags = mdle != null ? utils.elementFlags(mdle)
                                              : EnumSet.noneOf(ElementFlag.class);
        boolean included = utils.isIncluded(mdle);
        if (included) {
            DocLink targetLink;
            targetLink = new DocLink(pathToRoot.resolve(docPaths.moduleSummary(mdle)), fragment);
            Content link = links.createLink(targetLink, label, "");
            if (flags.contains(ElementFlag.PREVIEW) && label != contents.moduleLabel) {
                link = new ContentBuilder(
                        link,
                        HtmlTree.SUP(links.createLink(targetLink.withFragment(htmlIds.forPreviewSection(mdle).name()),
                                                      contents.previewMark))
                );
            }
            return link;
        }
        if (flags.contains(ElementFlag.PREVIEW)) {
            return new ContentBuilder(
                label,
                HtmlTree.SUP(contents.previewMark)
            );
        }
        return label;
    }

    /**
     * Add the link to the content.
     *
     * @param element program element for which the link will be added
     * @param label label for the link
     * @param target the content to which the link will be added
     */
    public void addSrcLink(Element element, Content label, Content target) {
        if (element == null) {
            return;
        }
        TypeElement te = utils.getEnclosingTypeElement(element);
        if (te == null) {
            // must be a typeElement since in has no containing class.
            te = (TypeElement) element;
        }
        if (utils.isIncluded(te)) {
            DocPath href = pathToRoot
                    .resolve(DocPaths.SOURCE_OUTPUT)
                    .resolve(docPaths.forClass(te));
            Content content = links.createLink(href
                    .fragment(SourceToHTMLConverter.getAnchorName(utils, element).name()), label, "");
            target.add(content);
        } else {
            target.add(label);
        }
    }

    /**
     * Return the link to the given class.
     *
     * @param linkInfo the information about the link.
     *
     * @return the link for the given class.
     */
    public Content getLink(HtmlLinkInfo linkInfo) {
        HtmlLinkFactory factory = new HtmlLinkFactory(this);
        return factory.getLink(linkInfo);
    }

    /**
     * Return the type parameters for the given class.
     *
     * @param linkInfo the information about the link.
     * @return the type for the given class.
     */
    public Content getTypeParameterLinks(HtmlLinkInfo linkInfo) {
        HtmlLinkFactory factory = new HtmlLinkFactory(this);
        return factory.getTypeParameterLinks(linkInfo);
    }

    /*************************************************************
     * Return a class cross-link to external class documentation.
     * The -link option does not allow users to
     * link to external classes in the "default" package.
     *
     * @param classElement the class element
     * @param refMemName the name of the member being referenced.  This should
     * be null or empty string if no member is being referenced.
     * @param label the label for the external link.
     * @param style optional style for the link.
     * @param code true if the label should be code font.
     * @return the link
     */
    public Content getCrossClassLink(TypeElement classElement, String refMemName,
                                    Content label, HtmlStyle style, boolean code) {
        if (classElement != null) {
            String className = utils.getSimpleName(classElement);
            PackageElement packageElement = utils.containingPackage(classElement);
            Content defaultLabel = Text.of(className);
            if (code)
                defaultLabel = HtmlTree.CODE(defaultLabel);
            if (getCrossPackageLink(packageElement) != null) {
                /*
                The package exists in external documentation, so link to the external
                class (assuming that it exists).  This is definitely a limitation of
                the -link option.  There are ways to determine if an external package
                exists, but no way to determine if the external class exists.  We just
                have to assume that it does.
                */
                DocLink link = configuration.extern.getExternalLink(packageElement, pathToRoot,
                                className + ".html", refMemName);
                return links.createLink(link,
                    (label == null) || label.isEmpty() ? defaultLabel : label, style,
                    resources.getText("doclet.Href_Class_Or_Interface_Title",
                        getLocalizedPackageName(packageElement)), true);
            }
        }
        return null;
    }

    public DocLink getCrossPackageLink(PackageElement element) {
        return configuration.extern.getExternalLink(element, pathToRoot,
            DocPaths.PACKAGE_SUMMARY.getPath());
    }

    public DocLink getCrossModuleLink(ModuleElement element) {
        return configuration.extern.getExternalLink(element, pathToRoot,
            docPaths.moduleSummary(utils.getModuleName(element)).getPath());
    }

    /**
     * {@return a link to the given class}
     *
     * @param context the id of the context where the link will be added
     * @param element the class to link to
     */
    public Content getQualifiedClassLink(HtmlLinkInfo.Kind context, Element element) {
        HtmlLinkInfo htmlLinkInfo = new HtmlLinkInfo(configuration, context, (TypeElement)element);
        return getLink(htmlLinkInfo.label(utils.getFullyQualifiedName(element)));
    }

    /**
     * Adds a link to the given class.
     *
     * @param context the id of the context where the link will be added
     * @param typeElement the class to link to
     * @param target the content to which the link will be added
     */
    public void addPreQualifiedClassLink(HtmlLinkInfo.Kind context, TypeElement typeElement, Content target) {
        addPreQualifiedClassLink(context, typeElement, null, target);
    }

    /**
     * Retrieve the class link with the package portion of the label in
     * plain text.  If the qualifier is excluded, it will not be included in the
     * link label.
     *
     * @param typeElement the class to link to.
     * @return the link with the package portion of the label in plain text.
     */
    public Content getPreQualifiedClassLink(HtmlLinkInfo.Kind context, TypeElement typeElement) {
        ContentBuilder classlink = new ContentBuilder();
        PackageElement pkg = utils.containingPackage(typeElement);
        if (pkg != null && ! configuration.shouldExcludeQualifier(pkg.getSimpleName().toString())) {
            classlink.add(getEnclosingPackageName(typeElement));
        }
        classlink.add(getLink(new HtmlLinkInfo(configuration,
                context, typeElement).label(utils.getSimpleName(typeElement))));
        return classlink;
    }

    /**
     * Add the class link with the package portion of the label in
     * plain text. If the qualifier is excluded, it will not be included in the
     * link label.
     *
     * @param context the id of the context where the link will be added
     * @param typeElement the class to link to
     * @param style optional style for the link
     * @param target the content to which the link with be added
     */
    public void addPreQualifiedClassLink(HtmlLinkInfo.Kind context,
                                         TypeElement typeElement, HtmlStyle style, Content target) {
        PackageElement pkg = utils.containingPackage(typeElement);
        if(pkg != null && ! configuration.shouldExcludeQualifier(pkg.getSimpleName().toString())) {
            target.add(getEnclosingPackageName(typeElement));
        }
        HtmlLinkInfo linkinfo = new HtmlLinkInfo(configuration, context, typeElement)
                .label(utils.getSimpleName(typeElement))
                .style(style);
        Content link = getLink(linkinfo);
        target.add(link);
    }

    /**
     * Get the enclosed name of the package
     *
     * @param te  TypeElement
     * @return the name
     */
    public String getEnclosingPackageName(TypeElement te) {

        PackageElement encl = configuration.utils.containingPackage(te);
        return (encl.isUnnamed()) ? "" : (encl.getQualifiedName() + ".");
    }

    /**
     * Return the main type element of the current page or null for pages that don't have one.
     *
     * @return the type element of the current page.
     */
    public TypeElement getCurrentPageElement() {
        return null;
    }

    /**
     * Add the class link, with only class name as the strong link and prefixing
     * plain package name.
     *
     * @param context the id of the context where the link will be added
     * @param typeElement the class to link to
     * @param content the content to which the link with be added
     */
    public void addPreQualifiedStrongClassLink(HtmlLinkInfo.Kind context, TypeElement typeElement, Content content) {
        addPreQualifiedClassLink(context, typeElement, HtmlStyle.typeNameLink, content);
    }

    /**
     * {@return a link to the given member}
     *
     * @param context the id of the context where the link will be added
     * @param element the member being linked to
     * @param label the label for the link
     */
    public Content getDocLink(HtmlLinkInfo.Kind context, Element element, CharSequence label) {
        return getDocLink(context, utils.getEnclosingTypeElement(element), element,
                Text.of(label), null, false);
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param typeElement the typeElement that we should link to. This is
     *            not necessarily the type containing element since we may be
     *            inheriting comments.
     * @param element the member being linked to.
     * @param label the label for the link.
     * @return the link for the given member.
     */
    public Content getDocLink(HtmlLinkInfo.Kind context, TypeElement typeElement, Element element,
                              CharSequence label) {
        return getDocLink(context, typeElement, element, Text.of(label), null, false);
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param typeElement the typeElement that we should link to. This is
     *            not necessarily the type containing element since we may be
     *            inheriting comments.
     * @param element the member being linked to.
     * @param label the label for the link.
     * @param style optional style for the link.
     * @return the link for the given member.
     */
    public Content getDocLink(HtmlLinkInfo.Kind context, TypeElement typeElement, Element element,
                              CharSequence label, HtmlStyle style) {
        return getDocLink(context, typeElement, element, Text.of(label), style, false);
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param typeElement the typeElement that we should link to. This is
     *            not necessarily the type containing element since we may be
     *            inheriting comments.
     * @param element the member being linked to.
     * @param label the label for the link.
     * @return the link for the given member.
     */
    public Content getDocLink(HtmlLinkInfo.Kind context, TypeElement typeElement, Element element,
                              CharSequence label, boolean isProperty) {
        return getDocLink(context, typeElement, element, Text.of(label), null, isProperty);
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param typeElement the typeElement that we should link to. This is
     *            not necessarily the type containing element since we may be
     *            inheriting comments.
     * @param element the member being linked to.
     * @param label the label for the link.
     * @param style optional style to use for the link.
     * @param isProperty true if the element parameter is a JavaFX property.
     * @return the link for the given member.
     */
    public Content getDocLink(HtmlLinkInfo.Kind context, TypeElement typeElement, Element element,
                              Content label, HtmlStyle style, boolean isProperty) {
        if (!utils.isLinkable(typeElement, element)) {
            return label;
        }

        if (utils.isExecutableElement(element)) {
            ExecutableElement ee = (ExecutableElement)element;
            HtmlId id = isProperty ? htmlIds.forProperty(ee) : htmlIds.forMember(ee);
            return getLink(new HtmlLinkInfo(configuration, context, typeElement)
                .label(label)
                .fragment(id.name())
                .style(style)
                .targetMember(element));
        }

        if (utils.isVariableElement(element) || utils.isTypeElement(element)) {
            return getLink(new HtmlLinkInfo(configuration, context, typeElement)
                .label(label)
                .fragment(element.getSimpleName().toString())
                .style(style)
                .targetMember(element));
        }

        return label;
    }

    /**
     * Add the inline comment.
     *
     * @param element the Element for which the inline comment will be added
     * @param tag the inline tag to be added
     * @param target the content to which the comment will be added
     */
    public void addInlineComment(Element element, DocTree tag, Content target) {
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DocTree> description = ch.getDescription(tag);
        addCommentTags(element, description, false, false, false, target);
    }

    /**
     * {@return a phrase describing the type of deprecation}
     *
     * @param e the Element for which the inline deprecated comment will be added
     */
    public Content getDeprecatedPhrase(Element e) {
        // TODO e should be checked to being deprecated
        return (utils.isDeprecatedForRemoval(e))
                ? contents.deprecatedForRemovalPhrase
                : contents.deprecatedPhrase;
    }

    /**
     * Add the inline deprecated comment.
     *
     * @param e the Element for which the inline deprecated comment will be added
     * @param tag the inline tag to be added
     * @param target the content to which the comment will be added
     */
    public void addInlineDeprecatedComment(Element e, DeprecatedTree tag, Content target) {
        CommentHelper ch = utils.getCommentHelper(e);
        addCommentTags(e, ch.getBody(tag), true, false, false, target);
    }

    /**
     * Adds the summary content.
     *
     * @param element the Element for which the summary will be generated
     * @param target the content to which the summary will be added
     */
    public void addSummaryComment(Element element, Content target) {
        addSummaryComment(element, utils.getFirstSentenceTrees(element), target);
    }

    /**
     * Adds the preview content.
     *
     * @param element the Element for which the summary will be generated
     * @param firstSentenceTags the first sentence tags for the doc
     * @param target the content to which the summary will be added
     */
    public void addPreviewComment(Element element, List<? extends DocTree> firstSentenceTags, Content target) {
        addCommentTags(element, firstSentenceTags, false, true, true, target);
    }

    /**
     * Adds the summary content.
     *
     * @param element the Element for which the summary will be generated
     * @param firstSentenceTags the first sentence tags for the doc
     * @param target the content to which the summary will be added
     */
    public void addSummaryComment(Element element, List<? extends DocTree> firstSentenceTags, Content target) {
        addCommentTags(element, firstSentenceTags, false, true, true, target);
    }

    public void addSummaryDeprecatedComment(Element element, DeprecatedTree tag, Content target) {
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DocTree> body = ch.getBody(tag);
        addCommentTags(element, ch.getFirstSentenceTrees(body), true, true, true, target);
    }

    /**
     * Adds the full-body content of the given element.
     *
     * @param element the element for which the content will be added
     * @param target the content to which the content will be added
     */
    public void addInlineComment(Element element, Content target) {
        addCommentTags(element, utils.getFullBody(element), false, false, false, target);
    }

    /**
     * Adds the comment tags.
     *
     * @param element the Element for which the comment tags will be generated
     * @param tags the first sentence tags for the doc
     * @param depr true if it is deprecated
     * @param first true if the first sentence tags should be added
     * @param inSummary true if the comment tags are added into the summary section
     * @param target the content to which the comment tags will be added
     */
    private void addCommentTags(Element element, List<? extends DocTree> tags, boolean depr,
            boolean first, boolean inSummary, Content target) {
        if (options.noComment()) {
            return;
        }
        Content div;
        Content result = commentTagsToContent(element, tags, first, inSummary);
        if (!result.isEmpty()) {
            if (depr) {
                div = HtmlTree.DIV(HtmlStyle.deprecationComment, result);
                target.add(div);
            } else {
                div = HtmlTree.DIV(HtmlStyle.block, result);
                target.add(div);
            }
        }
        if (tags.isEmpty()) {
            target.add(Entity.NO_BREAK_SPACE);
        }
    }

    boolean ignoreNonInlineTag(DocTree dtree) {
        Name name = null;
        if (dtree.getKind() == Kind.START_ELEMENT) {
            StartElementTree setree = (StartElementTree)dtree;
            name = setree.getName();
        } else if (dtree.getKind() == Kind.END_ELEMENT) {
            EndElementTree eetree = (EndElementTree)dtree;
            name = eetree.getName();
        }

        if (name != null) {
            HtmlTag htmlTag = HtmlTag.get(name);
            if (htmlTag != null &&
                    htmlTag.blockType != jdk.javadoc.internal.doclint.HtmlTag.BlockType.INLINE) {
                return true;
            }
        }
        return false;
    }

    // Notify the next DocTree handler to take necessary action
    private boolean commentRemoved = false;

    /**
     * Converts inline tags and text to content, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to block tags.
     *
     * @param element         specific element where comment resides
     * @param tags            list of text trees and inline tag trees (often alternating)
     * @param isFirstSentence true if text is first sentence
     * @return a Content object
     */
    public Content commentTagsToContent(Element element,
                                        List<? extends DocTree> tags,
                                        boolean isFirstSentence)
    {
        return commentTagsToContent(element, tags, isFirstSentence, false);
    }

    /**
     * Converts inline tags and text to content, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to block tags.
     *
     * @param element         specific element where comment resides
     * @param trees           list of text trees and inline tag trees (often alternating)
     * @param isFirstSentence true if text is first sentence
     * @param inSummary       if the comment tags are added into the summary section
     * @return a Content object
     */
    public Content commentTagsToContent(Element element,
                                        List<? extends DocTree> trees,
                                        boolean isFirstSentence,
                                        boolean inSummary) {
        return commentTagsToContent(element, trees,
                new TagletWriter.Context(isFirstSentence, inSummary));
    }

    /**
     * Converts inline tags and text to content, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to block tags.
     *
     * @param element   specific element where comment resides
     * @param trees     list of text trees and inline tag trees (often alternating)
     * @param context   the enclosing context for the trees
     *
     * @return a Content object
     */
    public Content commentTagsToContent(Element element,
                                        List<? extends DocTree> trees,
                                        TagletWriter.Context context)
    {
        final Content result = new ContentBuilder() {
            @Override
            public ContentBuilder add(CharSequence text) {
                return super.add(Text.normalizeNewlines(text));
            }
        };
        CommentHelper ch = utils.getCommentHelper(element);
        configuration.tagletManager.checkTags(element, trees);
        commentRemoved = false;

        for (ListIterator<? extends DocTree> iterator = trees.listIterator(); iterator.hasNext();) {
            boolean isFirstNode = !iterator.hasPrevious();
            DocTree tag = iterator.next();
            boolean isLastNode  = !iterator.hasNext();

            if (context.isFirstSentence) {
                // Ignore block tags
                if (ignoreNonInlineTag(tag))
                    continue;

                // Ignore any trailing whitespace OR whitespace after removed html comment
                if ((isLastNode || commentRemoved)
                        && tag.getKind() == TEXT
                        && ((tag instanceof TextTree tt) && tt.getBody().isBlank()))
                    continue;

                // Ignore any leading html comments
                if ((isFirstNode || commentRemoved) && tag.getKind() == COMMENT) {
                    commentRemoved = true;
                    continue;
                }
            }

            var docTreeVisitor = new SimpleDocTreeVisitor<Boolean, Content>() {

                private boolean inAnAtag() {
                    return (tag instanceof StartElementTree st) && equalsIgnoreCase(st.getName(), "a");
                }

                @Override
                public Boolean visitAttribute(AttributeTree node, Content content) {
                    if (!content.isEmpty()) {
                        content.add(" ");
                    }
                    content.add(node.getName());
                    if (node.getValueKind() == ValueKind.EMPTY) {
                        return false;
                    }
                    content.add("=");
                    String quote = switch (node.getValueKind()) {
                        case DOUBLE -> "\"";
                        case SINGLE -> "'";
                        default -> "";
                    };
                    content.add(quote);

                    /* In the following code for an attribute value:
                     * 1. {@docRoot} followed by text beginning "/.." is replaced by the value
                     *    of the docrootParent option, followed by the remainder of the text
                     * 2. in the value of an "href" attribute in a <a> tag, an initial text
                     *    value will have a relative link redirected.
                     * Note that, realistically, it only makes sense to ever use {@docRoot}
                     * at the beginning of a URL in an attribute value, but this is not
                     * required or enforced.
                     */
                    boolean isHRef = inAnAtag() && equalsIgnoreCase(node.getName(), "href");
                    boolean first = true;
                    DocRootTree pendingDocRoot = null;
                    for (DocTree dt : node.getValue()) {
                        if (pendingDocRoot != null) {
                            if (dt instanceof TextTree tt) {
                                String text = tt.getBody();
                                if (text.startsWith("/..") && !options.docrootParent().isEmpty()) {
                                    content.add(options.docrootParent());
                                    content.add(textCleanup(text.substring(3), isLastNode));
                                    pendingDocRoot = null;
                                    continue;
                                }
                            }
                            pendingDocRoot.accept(this, content);
                            pendingDocRoot = null;
                        }

                        if (dt instanceof TextTree tt) {
                            String text = tt.getBody();
                            if (first && isHRef) {
                                text = redirectRelativeLinks(element, tt);
                            }
                            content.add(textCleanup(text, isLastNode));
                        } else if (dt instanceof DocRootTree drt) {
                            // defer until we see what, if anything, follows this node
                            pendingDocRoot = drt;
                        } else {
                            dt.accept(this, content);
                        }
                        first = false;
                    }
                    if (pendingDocRoot != null) {
                        pendingDocRoot.accept(this, content);
                    }

                    content.add(quote);
                    return false;
                }

                @Override
                public Boolean visitComment(CommentTree node, Content content) {
                    content.add(RawHtml.comment(node.getBody()));
                    return false;
                }

                @Override
                public Boolean visitEndElement(EndElementTree node, Content content) {
                    content.add(RawHtml.endElement(node.getName()));
                    return false;
                }

                @Override
                public Boolean visitEntity(EntityTree node, Content content) {
                    content.add(Entity.of(node.getName()));
                    return false;
                }

                @Override
                public Boolean visitErroneous(ErroneousTree node, Content content) {
                    DocTreePath dtp = ch.getDocTreePath(node);
                    if (dtp != null) {
                        String body = node.getBody();
                        Matcher m = Pattern.compile("(?i)\\{@([a-z]+).*").matcher(body);
                        String tagName = m.matches() ? m.group(1) : null;
                        if (tagName == null) {
                            if (!configuration.isDocLintSyntaxGroupEnabled()) {
                                messages.warning(dtp, "doclet.tag.invalid_input", body);
                            }
                            content.add(invalidTagOutput(resources.getText("doclet.tag.invalid_input", body),
                                    Optional.empty()));
                        } else {
                            messages.warning(dtp, "doclet.tag.invalid_usage", body);
                            content.add(invalidTagOutput(resources.getText("doclet.tag.invalid", tagName),
                                    Optional.of(Text.of(body))));
                        }
                    }
                    return false;
                }

                @Override
                public Boolean visitEscape(EscapeTree node, Content content) {
                    result.add(node.getBody());
                    return false;
                }

                @Override
                public Boolean visitInheritDoc(InheritDocTree node, Content content) {
                    Content output = getInlineTagOutput(element, node, context);
                    content.add(output);
                    // if we obtained the first sentence successfully, nothing more to do
                    return (context.isFirstSentence && !output.isEmpty());
                }

                @Override
                public Boolean visitStartElement(StartElementTree node, Content content) {
                    Content attrs = new ContentBuilder();
                    if (node.getName().toString().matches("(?i)h[1-6]")
                            && isIndexable()) {
                        createSectionIdAndIndex(node, trees, attrs, element, context);
                    }
                    for (DocTree dt : node.getAttributes()) {
                        dt.accept(this, attrs);
                    }
                    content.add(RawHtml.startElement(node.getName(), attrs, node.isSelfClosing()));
                    return false;
                }

                private CharSequence textCleanup(String text, boolean isLast) {
                    return textCleanup(text, isLast, false);
                }

                private CharSequence textCleanup(String text, boolean isLast, boolean stripLeading) {
                    boolean stripTrailing = context.isFirstSentence && isLast;
                    if (stripLeading && stripTrailing) {
                        text = text.strip();
                    } else if (stripLeading) {
                        text = text.stripLeading();
                    } else if (stripTrailing) {
                        text = text.stripTrailing();
                    }
                    text = utils.replaceTabs(text);
                    return Text.normalizeNewlines(text);
                }

                @Override
                public Boolean visitText(TextTree node, Content content) {
                    String text = node.getBody();
                    result.add(text.startsWith("<![CDATA[")
                            ? RawHtml.cdata(text)
                            : Text.of(textCleanup(text, isLastNode, commentRemoved)));
                    return false;
                }

                @Override
                protected Boolean defaultAction(DocTree node, Content content) {
                    if (node instanceof InlineTagTree itt) {
                        var output = getInlineTagOutput(element, itt, context);
                        if (output != null) {
                            content.add(output);
                        }
                    }
                    return false;
                }

            };

            boolean allDone = docTreeVisitor.visit(tag, result);
            commentRemoved = false;

            if (allDone)
                break;
        }
        return result;
    }

    private boolean equalsIgnoreCase(Name name, String s) {
        return name != null && name.toString().equalsIgnoreCase(s);
    }

    private Optional<String> getIdAttributeValue(StartElementTree node) {
         return node.getAttributes().stream()
                 .filter(dt -> dt instanceof AttributeTree at && equalsIgnoreCase(at.getName(), "id"))
                 .map(dt -> ((AttributeTree)dt).getValue().toString())
                 .findFirst();
    }

    private void createSectionIdAndIndex(StartElementTree node, List<? extends DocTree> trees, Content attrs,
                                         Element element, TagletWriter.Context context) {
        // Use existing id attribute if available
        String id = getIdAttributeValue(node).orElse(null);
        StringBuilder sb = new StringBuilder();
        String tagName = node.getName().toString().toLowerCase(Locale.ROOT);
        // Go through heading content to collect content and look for existing id
        for (DocTree docTree : trees.subList(trees.indexOf(node) + 1, trees.size())) {
            if (docTree instanceof TextTree text) {
                sb.append(text.getBody());
            } else if (docTree instanceof LiteralTree literal) {
                sb.append(literal.getBody().getBody());
            } else if (docTree instanceof LinkTree link) {
                var label = link.getLabel();
                sb.append(label.isEmpty() ? link.getReference().getSignature() : label.toString());
            } else if (id == null && docTree instanceof StartElementTree nested
                    && equalsIgnoreCase(nested.getName(), "a")) {
                // Use id of embedded anchor element if present
                id = getIdAttributeValue(nested).orElse(null);
            } else if (docTree instanceof EndElementTree endElement
                    && equalsIgnoreCase(endElement.getName(), tagName)) {
                break;
            }
        }
        String headingContent = sb.toString().trim();
        if (id == null) {
            // Generate id attribute
            HtmlId htmlId = htmlIds.forHeading(headingContent, headingIds);
            id = htmlId.name();
            attrs.add("id=\"").add(htmlId.name()).add("\"");
        }
        // Generate index item
        if (!headingContent.isEmpty() && configuration.indexBuilder != null) {
            String tagText = headingContent.replaceAll("\\s+", " ");
            IndexItem item = IndexItem.of(element, node, tagText,
                    getTagletWriterInstance(context).getHolderName(element),
                    resources.getText("doclet.Section"),
                    new DocLink(path, id));
            configuration.indexBuilder.add(item);
        }
    }

    /**
     * Returns true if relative links should be redirected.
     *
     * @return true if a relative link should be redirected.
     */
    private boolean shouldRedirectRelativeLinks(Element element) {
        if (element == null || utils.isOverviewElement(element)) {
            // Can't redirect unless there is a valid source element.
            return false;
        }
        // Retrieve the element of this writer if it is a "primary" writer for an element.
        // Note: It would be nice to have getCurrentPageElement() return package and module elements
        // in their respective writers, but other uses of the method are only interested in TypeElements.
        Element currentPageElement = getCurrentPageElement();
        if (currentPageElement == null) {
            if (this instanceof PackageWriter packageWriter) {
                currentPageElement = packageWriter.packageElement;
            } else if (this instanceof ModuleWriter moduleWriter) {
                currentPageElement = moduleWriter.mdle;
            }
        }
        // Redirect link if the current writer is not the primary writer for the source element.
        return currentPageElement == null
                || (currentPageElement != element
                    &&  currentPageElement != utils.getEnclosingTypeElement(element));
    }

    /**
     * Returns the output for an invalid tag. The returned content uses special styling to
     * highlight the problem. Depending on the presence of the {@code detail} string the method
     * returns a plain text span or an expandable component.
     *
     * @param summary the single-line summary message
     * @param detail the optional detail message which may contain preformatted text
     * @return the output
     */
    public Content invalidTagOutput(String summary, Optional<Content> detail) {
        if (detail.isEmpty() || detail.get().isEmpty()) {
            return HtmlTree.SPAN(HtmlStyle.invalidTag, Text.of(summary));
        }
        return HtmlTree.DETAILS(HtmlStyle.invalidTag)
                .add(HtmlTree.SUMMARY(Text.of(summary)))
                .add(HtmlTree.PRE(detail.get()));
    }

    /**
     * Returns true if element lives in the same package as the type or package
     * element of this writer.
     */
    private boolean inSamePackage(Element element) {
        Element currentPageElement = (this instanceof PackageWriter packageWriter)
                ? packageWriter.packageElement : getCurrentPageElement();
        return currentPageElement != null && !utils.isModule(element)
                && Objects.equals(utils.containingPackage(currentPageElement),
                utils.containingPackage(element));
    }

    /**
     * Suppose a piece of documentation has a relative link.  When you copy
     * that documentation to another place such as the index or class-use page,
     * that relative link will no longer work.  We should redirect those links
     * so that they will work again.
     * <p>
     * Here is the algorithm used to fix the link:
     * <p>
     * {@literal <relative link> => docRoot + <relative path to file> + <relative link> }
     * <p>
     * For example, suppose DocletEnvironment has this link:
     * {@literal <a href="package-summary.html">The package Page</a> }
     * <p>
     * If this link appeared in the index, we would redirect
     * the link like this:
     *
     * {@literal <a href="./jdk/javadoc/doclet/package-summary.html">The package Page</a>}
     *
     * @param element the Element object whose documentation is being written.
     * @param tt the text being written.
     *
     * @return the text, with all the relative links redirected to work.
     */
    private String redirectRelativeLinks(Element element, TextTree tt) {
        String text = tt.getBody();
        if (!shouldRedirectRelativeLinks(element)) {
            return text;
        }
        String lower = Utils.toLowerCase(text);
        if (lower.startsWith("mailto:")
                || lower.startsWith("http:")
                || lower.startsWith("https:")
                || lower.startsWith("file:")
                || lower.startsWith("ftp:")) {
            return text;
        }
        if (text.startsWith("#")) {
            // Redirected fragment link: prepend HTML file name to make it work
            if (utils.isModule(element)) {
                text = "module-summary.html" + text;
            } else if (utils.isPackage(element)) {
                text = DocPaths.PACKAGE_SUMMARY.getPath() + text;
            } else {
                TypeElement typeElement = element instanceof TypeElement
                        ? (TypeElement) element : utils.getEnclosingTypeElement(element);
                text = docPaths.forName(typeElement).getPath() + text;
            }
        }

        if (!inSamePackage(element)) {
            DocPath redirectPathFromRoot = new SimpleElementVisitor14<DocPath, Void>() {
                @Override
                public DocPath visitType(TypeElement e, Void p) {
                    return docPaths.forPackage(utils.containingPackage(e));
                }

                @Override
                public DocPath visitPackage(PackageElement e, Void p) {
                    return docPaths.forPackage(e);
                }

                @Override
                public DocPath visitVariable(VariableElement e, Void p) {
                    return docPaths.forPackage(utils.containingPackage(e));
                }

                @Override
                public DocPath visitExecutable(ExecutableElement e, Void p) {
                    return docPaths.forPackage(utils.containingPackage(e));
                }

                @Override
                public DocPath visitModule(ModuleElement e, Void p) {
                    return DocPaths.forModule(e);
                }

                @Override
                protected DocPath defaultAction(Element e, Void p) {
                    return null;
                }
            }.visit(element);
            if (redirectPathFromRoot != null) {
                text = "{@" + Kind.DOC_ROOT.tagName + "}/"
                        + redirectPathFromRoot.resolve(text).getPath();
                return replaceDocRootDir(text);
            }
        }
        return text;
    }

    /**
     * {@return the annotation types info for the given element}
     *
     * @param element an Element
     * @param lineBreak if true add new line between each member value
     */
    Content getAnnotationInfo(Element element, boolean lineBreak) {
        return getAnnotationInfo(element.getAnnotationMirrors(), lineBreak);
    }

    /**
     * {@return the description for the given annotations}
     *
     * @param descList a list of annotation mirrors
     * @param lineBreak if true add new line between each member value
     */
    Content getAnnotationInfo(List<? extends AnnotationMirror> descList, boolean lineBreak) {
        List<Content> annotations = getAnnotations(descList, lineBreak);
        String sep = "";
        ContentBuilder result = new ContentBuilder();
        for (Content annotation: annotations) {
            result.add(sep);
            result.add(annotation);
            if (!lineBreak) {
                sep = " ";
            }
        }
        return result;
    }

    /**
     * Return the string representations of the annotation types for
     * the given doc.
     *
     * @param descList a list of annotation mirrors.
     * @param lineBreak if true, add new line between each member value.
     * @return a list of strings representing the annotations being
     *         documented.
     */
    public List<Content> getAnnotations(List<? extends AnnotationMirror> descList, boolean lineBreak) {
        List<Content> results = new ArrayList<>();
        ContentBuilder annotation;
        for (AnnotationMirror aDesc : descList) {
            TypeElement annotationElement = (TypeElement)aDesc.getAnnotationType().asElement();
            // If an annotation is not documented, do not add it to the list. If
            // the annotation is of a repeatable type, and if it is not documented
            // and also if its container annotation is not documented, do not add it
            // to the list. If an annotation of a repeatable type is not documented
            // but its container is documented, it will be added to the list.
            if (!utils.isDocumentedAnnotation(annotationElement) &&
                (!isAnnotationDocumented && !isContainerDocumented)) {
                continue;
            }
            annotation = new ContentBuilder();
            isAnnotationDocumented = false;
            HtmlLinkInfo linkInfo = new HtmlLinkInfo(configuration,
                                                     HtmlLinkInfo.Kind.PLAIN, annotationElement);
            Map<? extends ExecutableElement, ? extends AnnotationValue> pairs = aDesc.getElementValues();
            // If the annotation is mandated, do not print the container.
            if (utils.configuration.workArounds.isMandated(aDesc)) {
                for (ExecutableElement ee : pairs.keySet()) {
                    AnnotationValue annotationValue = pairs.get(ee);
                    List<AnnotationValue> annotationTypeValues = new ArrayList<>();

                    new SimpleAnnotationValueVisitor9<Void, List<AnnotationValue>>() {
                        @Override
                        public Void visitArray(List<? extends AnnotationValue> vals, List<AnnotationValue> p) {
                            p.addAll(vals);
                            return null;
                        }

                        @Override
                        protected Void defaultAction(Object o, List<AnnotationValue> p) {
                            p.add(annotationValue);
                            return null;
                        }
                    }.visit(annotationValue, annotationTypeValues);

                    String sep = "";
                    for (AnnotationValue av : annotationTypeValues) {
                        annotation.add(sep);
                        annotation.add(annotationValueToContent(av));
                        sep = " ";
                    }
                }
            } else if (isAnnotationArray(pairs)) {
                // If the container has 1 or more value defined and if the
                // repeatable type annotation is not documented, do not print
                // the container.
                if (pairs.size() == 1 && isAnnotationDocumented) {
                    List<AnnotationValue> annotationTypeValues = new ArrayList<>();
                    for (AnnotationValue a :  pairs.values()) {
                        new SimpleAnnotationValueVisitor9<Void, List<AnnotationValue>>() {
                            @Override
                            public Void visitArray(List<? extends AnnotationValue> vals, List<AnnotationValue> annotationTypeValues) {
                               annotationTypeValues.addAll(vals);
                               return null;
                            }
                        }.visit(a, annotationTypeValues);
                    }
                    String sep = "";
                    for (AnnotationValue av : annotationTypeValues) {
                        annotation.add(sep);
                        annotation.add(annotationValueToContent(av));
                        sep = " ";
                    }
                }
                // If the container has 1 or more value defined and if the
                // repeatable type annotation is not documented, print the container.
                else {
                    addAnnotations(annotationElement, linkInfo, annotation, pairs, false);
                }
            }
            else {
                addAnnotations(annotationElement, linkInfo, annotation, pairs, lineBreak);
            }
            annotation.add(lineBreak ? Text.NL : "");
            results.add(annotation);
        }
        return results;
    }

    /**
     * Add annotation to the annotation string.
     *
     * @param annotationDoc the annotation being documented
     * @param linkInfo the information about the link
     * @param annotation the annotation string to which the annotation will be added
     * @param map annotation type element to annotation value pairs
     * @param linkBreak if true, add new line between each member value
     */
    private void addAnnotations(TypeElement annotationDoc, HtmlLinkInfo linkInfo,
                                ContentBuilder annotation,
                                Map<? extends ExecutableElement, ? extends AnnotationValue> map,
                                boolean linkBreak) {
        linkInfo.label("@" + annotationDoc.getSimpleName());
        annotation.add(getLink(linkInfo));
        if (!map.isEmpty()) {
            annotation.add("(");
            boolean isFirst = true;
            Set<? extends ExecutableElement> keys = map.keySet();
            boolean multipleValues = keys.size() > 1;
            for (ExecutableElement element : keys) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    annotation.add(",");
                    if (linkBreak) {
                        annotation.add(Text.NL);
                        int spaces = annotationDoc.getSimpleName().length() + 2;
                        for (int k = 0; k < (spaces); k++) {
                            annotation.add(" ");
                        }
                    }
                }
                String simpleName = element.getSimpleName().toString();
                if (multipleValues || !"value".equals(simpleName)) { // Omit "value=" where unnecessary
                    annotation.add(getDocLink(HtmlLinkInfo.Kind.PLAIN, element, simpleName));
                    annotation.add("=");
                }
                AnnotationValue annotationValue = map.get(element);
                List<AnnotationValue> annotationTypeValues = new ArrayList<>();
                new SimpleAnnotationValueVisitor9<Void, AnnotationValue>() {
                    @Override
                    public Void visitArray(List<? extends AnnotationValue> vals, AnnotationValue p) {
                        annotationTypeValues.addAll(vals);
                        return null;
                    }
                    @Override
                    protected Void defaultAction(Object o, AnnotationValue p) {
                        annotationTypeValues.add(p);
                        return null;
                    }
                }.visit(annotationValue, annotationValue);
                annotation.add(annotationTypeValues.size() == 1 ? "" : "{");
                String sep = "";
                for (AnnotationValue av : annotationTypeValues) {
                    annotation.add(sep);
                    annotation.add(annotationValueToContent(av));
                    sep = ",";
                }
                annotation.add(annotationTypeValues.size() == 1 ? "" : "}");
                isContainerDocumented = false;
            }
            annotation.add(")");
        }
    }

    /**
     * Check if the annotation contains an array of annotation as a value. This
     * check is to verify if a repeatable type annotation is present or not.
     *
     * @param pairs annotation type element and value pairs
     *
     * @return true if the annotation contains an array of annotation as a value.
     */
    private boolean isAnnotationArray(Map<? extends ExecutableElement, ? extends AnnotationValue> pairs) {
        AnnotationValue annotationValue;
        for (ExecutableElement ee : pairs.keySet()) {
            annotationValue = pairs.get(ee);
            boolean rvalue = new SimpleAnnotationValueVisitor9<Boolean, Void>() {
                @Override
                public Boolean visitArray(List<? extends AnnotationValue> vals, Void p) {
                    if (vals.size() > 1) {
                        if (vals.get(0) instanceof AnnotationMirror) {
                            isContainerDocumented = true;
                            return new SimpleAnnotationValueVisitor9<Boolean, Void>() {
                                @Override
                                public Boolean visitAnnotation(AnnotationMirror a, Void p) {
                                    isContainerDocumented = true;
                                    Element asElement = a.getAnnotationType().asElement();
                                    if (utils.isDocumentedAnnotation((TypeElement)asElement)) {
                                        isAnnotationDocumented = true;
                                    }
                                    return true;
                                }
                                @Override
                                protected Boolean defaultAction(Object o, Void p) {
                                    return false;
                                }
                            }.visit(vals.get(0));
                        }
                    }
                    return false;
                }

                @Override
                protected Boolean defaultAction(Object o, Void p) {
                    return false;
                }
            }.visit(annotationValue);
            if (rvalue) {
                return true;
            }
        }
        return false;
    }

    private Content annotationValueToContent(AnnotationValue annotationValue) {
        return new SimpleAnnotationValueVisitor9<Content, Void>() {

            @Override
            public Content visitType(TypeMirror type, Void p) {
                return new SimpleTypeVisitor9<Content, Void>() {
                    @Override
                    public Content visitDeclared(DeclaredType t, Void p) {
                        HtmlLinkInfo linkInfo = new HtmlLinkInfo(configuration,
                                HtmlLinkInfo.Kind.PLAIN, t);
                        return getLink(linkInfo);
                    }
                    @Override
                    public Content visitArray(ArrayType t, Void p) {
                        // render declared base component type as link
                        return visit(t.getComponentType()).add("[]");
                    }
                    @Override
                    protected Content defaultAction(TypeMirror t, Void p) {
                        return new TextBuilder(t.toString());
                    }
                }.visit(type).add(".class");
            }

            @Override
            public Content visitAnnotation(AnnotationMirror a, Void p) {
                List<Content> list = getAnnotations(List.of(a), false);
                ContentBuilder buf = new ContentBuilder();
                for (Content c : list) {
                    buf.add(c);
                }
                return buf;
            }

            @Override
            public Content visitEnumConstant(VariableElement c, Void p) {
                return getDocLink(HtmlLinkInfo.Kind.PLAIN, c, c.getSimpleName());
            }

            @Override
            public Content visitArray(List<? extends AnnotationValue> vals, Void p) {
                ContentBuilder buf = new ContentBuilder();
                String sep = "";
                for (AnnotationValue av : vals) {
                    buf.add(sep);
                    buf.add(visit(av));
                    sep = " ";
                }
                return buf;
            }

            @Override
            protected Content defaultAction(Object o, Void p) {
                return Text.of(annotationValue.toString());
            }
        }.visit(annotationValue);
    }

    protected TableHeader getPackageTableHeader() {
        return new TableHeader(contents.packageLabel, contents.descriptionLabel);
    }

    /**
     * Generates a string for use in a description meta element,
     * based on an element and its enclosing elements
     * @param prefix a prefix for the string
     * @param elem the element
     * @return the description
     */
    static String getDescription(String prefix, Element elem) {
        LinkedList<Element> chain = new LinkedList<>();
        for (Element e = elem; e != null; e = e.getEnclosingElement()) {
            // ignore unnamed enclosing elements
            if (e.getSimpleName().length() == 0 && e != elem) {
                break;
            }
            chain.addFirst(e);
        }
        StringBuilder sb = new StringBuilder();
        for (Element e: chain) {
            String name;
            switch (e.getKind()) {
                case MODULE, PACKAGE -> {
                    name = ((QualifiedNameable) e).getQualifiedName().toString();
                    if (name.length() == 0) {
                        name = "<unnamed>";
                    }
                }
                default -> name = e.getSimpleName().toString();
            }

            if (sb.length() == 0) {
                sb.append(prefix).append(": ");
            } else {
                sb.append(", ");
            }
            sb.append(e.getKind().toString().toLowerCase(Locale.US).replace("_", " "))
                    .append(": ")
                    .append(name);
        }
        return sb.toString();
    }

    static String getGenerator(Class<?> clazz) {
        return "javadoc/" + clazz.getSimpleName();
    }

    /**
     * Returns an HtmlTree for the BODY element.
     *
     * @param title title for the window
     * @return an HtmlTree for the BODY tag
     */
    public HtmlTree getBody(String title) {
        var body = new HtmlTree(TagName.BODY).setStyle(getBodyStyle());

        this.winTitle = title;
        // Don't print windowtitle script for overview-frame, allclasses-frame
        // and package-frame
        body.add(mainBodyScript.asContent());
        var noScript = HtmlTree.NOSCRIPT(HtmlTree.DIV(contents.noScriptMessage));
        body.add(noScript);
        return body;
    }

    public HtmlStyle getBodyStyle() {
        String kind = getClass().getSimpleName()
                .replaceAll("(Writer)?(Impl)?$", "")
                .replaceAll("AnnotationType", "Class")
                .replaceAll("^(Module|Package|Class)$", "$1Declaration")
                .replace("API", "Api");
        String page = kind.substring(0, 1).toLowerCase(Locale.US) + kind.substring(1) + "Page";
        return HtmlStyle.valueOf(page);
    }

    /**
     * Returns the path of module/package specific stylesheets for the element.
     * @param element module/Package element
     * @return list of path of module/package specific stylesheets
     * @throws DocFileIOException if an issue arises while accessing any stylesheets
     */
    List<DocPath> getLocalStylesheets(Element element) throws DocFileIOException {
        List<DocPath> stylesheets = new ArrayList<>();
        DocPath basePath = null;
        if (element instanceof PackageElement pkg) {
            stylesheets.addAll(getModuleStylesheets(pkg));
            basePath = docPaths.forPackage(pkg);
        } else if (element instanceof ModuleElement mdle) {
            basePath = DocPaths.forModule(mdle);
        }
        for (DocPath stylesheet : getStylesheets(element)) {
            stylesheets.add(basePath.resolve(stylesheet.getPath()));
        }
        return stylesheets;
    }

    private List<DocPath> getModuleStylesheets(PackageElement pkgElement) throws
            DocFileIOException {
        List<DocPath> moduleStylesheets = new ArrayList<>();
        ModuleElement moduleElement = utils.containingModule(pkgElement);
        if (moduleElement != null && !moduleElement.isUnnamed()) {
            List<DocPath> localStylesheets = getStylesheets(moduleElement);
            DocPath basePath = DocPaths.forModule(moduleElement);
            for (DocPath stylesheet : localStylesheets) {
                moduleStylesheets.add(basePath.resolve(stylesheet));
            }
        }
        return moduleStylesheets;
    }

    private List<DocPath> getStylesheets(Element element) throws DocFileIOException {
        List<DocPath> localStylesheets = configuration.localStylesheetMap.get(element);
        if (localStylesheets == null) {
            DocFilesHandler docFilesHandler = configuration.getWriterFactory().newDocFilesHandler(element);
            localStylesheets = docFilesHandler.getStylesheets();
            configuration.localStylesheetMap.put(element, localStylesheets);
        }
        return localStylesheets;
    }

    public void addPreviewSummary(Element forWhat, Content target) {
        if (utils.isPreviewAPI(forWhat)) {
            var div = HtmlTree.DIV(HtmlStyle.block);
            div.add(HtmlTree.SPAN(HtmlStyle.previewLabel, contents.previewPhrase));
            target.add(div);
        }
    }

    public void addRestrictedSummary(Element forWhat, Content target) {
        if (utils.isRestrictedAPI(forWhat)) {
            var div = HtmlTree.DIV(HtmlStyle.block);
            div.add(HtmlTree.SPAN(HtmlStyle.restrictedLabel, contents.restrictedPhrase));
            target.add(div);
        }
    }

    public void addPreviewInfo(Element forWhat, Content target) {
        if (utils.isPreviewAPI(forWhat)) {
            //in Java platform:
            var previewDiv = HtmlTree.DIV(HtmlStyle.previewBlock);
            previewDiv.setId(htmlIds.forPreviewSection(forWhat));
            String name = (switch (forWhat.getKind()) {
                case PACKAGE, MODULE ->
                        ((QualifiedNameable) forWhat).getQualifiedName();
                case CONSTRUCTOR ->
                        forWhat.getEnclosingElement().getSimpleName();
                default -> forWhat.getSimpleName();
            }).toString();
            var nameCode = HtmlTree.CODE(Text.of(name));
            boolean isReflectivePreview = utils.isReflectivePreviewAPI(forWhat);
            String leadingNoteKey =
                    !isReflectivePreview ? "doclet.PreviewPlatformLeadingNote"
                                         : "doclet.ReflectivePreviewPlatformLeadingNote";
            Content leadingNote =
                    contents.getContent(leadingNoteKey, nameCode);
            previewDiv.add(HtmlTree.SPAN(HtmlStyle.previewLabel,
                                         leadingNote));
            if (!isReflectivePreview) {
                Content note1 = contents.getContent("doclet.PreviewTrailingNote1", nameCode);
                previewDiv.add(HtmlTree.DIV(HtmlStyle.previewComment, note1));
            }
            Content note2 = contents.getContent("doclet.PreviewTrailingNote2", nameCode);
            previewDiv.add(HtmlTree.DIV(HtmlStyle.previewComment, note2));
            target.add(previewDiv);
        } else if (forWhat.getKind().isClass() || forWhat.getKind().isInterface()) {
            //in custom code:
            List<Content> previewNotes = getPreviewNotes((TypeElement) forWhat);
            if (!previewNotes.isEmpty()) {
                Name name = forWhat.getSimpleName();
                var nameCode = HtmlTree.CODE(Text.of(name));
                var previewDiv = HtmlTree.DIV(HtmlStyle.previewBlock);
                previewDiv.setId(htmlIds.forPreviewSection(forWhat));
                Content leadingNote = contents.getContent("doclet.PreviewLeadingNote", nameCode);
                previewDiv.add(HtmlTree.SPAN(HtmlStyle.previewLabel,
                                             leadingNote));
                var ul = HtmlTree.UL(HtmlStyle.previewComment);
                for (Content note : previewNotes) {
                    ul.add(HtmlTree.LI(note));
                }
                previewDiv.add(ul);
                Content note1 =
                        contents.getContent("doclet.PreviewTrailingNote1",
                                            nameCode);
                previewDiv.add(HtmlTree.DIV(HtmlStyle.previewComment, note1));
                Content note2 =
                        contents.getContent("doclet.PreviewTrailingNote2",
                                            name);
                previewDiv.add(HtmlTree.DIV(HtmlStyle.previewComment, note2));
                target.add(previewDiv);
            }
        }
    }

    private List<Content> getPreviewNotes(TypeElement el) {
        String className = el.getSimpleName().toString();
        List<Content> result = new ArrayList<>();
        PreviewSummary previewAPITypes = utils.declaredUsingPreviewAPIs(el);
        Set<TypeElement> previewAPI = new HashSet<>(previewAPITypes.previewAPI);
        Set<TypeElement> reflectivePreviewAPI = new HashSet<>(previewAPITypes.reflectivePreviewAPI);
        Set<TypeElement> declaredUsingPreviewFeature = new HashSet<>(previewAPITypes.declaredUsingPreviewFeature);
        Set<DeclarationPreviewLanguageFeatures> previewLanguageFeatures = new HashSet<>();
        for (Element enclosed : el.getEnclosedElements()) {
            if (!utils.isIncluded(enclosed)) {
                continue;
            }
            if (utils.isPreviewAPI(enclosed)) {
                //for class summary, ignore methods that are themselves preview:
                continue;
            }
            if (!enclosed.getKind().isClass() && !enclosed.getKind().isInterface()) {
                PreviewSummary memberAPITypes = utils.declaredUsingPreviewAPIs(enclosed);
                declaredUsingPreviewFeature.addAll(memberAPITypes.declaredUsingPreviewFeature);
                previewAPI.addAll(memberAPITypes.previewAPI);
                reflectivePreviewAPI.addAll(memberAPITypes.reflectivePreviewAPI);
                previewLanguageFeatures.addAll(utils.previewLanguageFeaturesUsed(enclosed));
            } else if (!utils.previewLanguageFeaturesUsed(enclosed).isEmpty()) {
                declaredUsingPreviewFeature.add((TypeElement) enclosed);
            }
        }
        previewLanguageFeatures.addAll(utils.previewLanguageFeaturesUsed(el));
        if (!previewLanguageFeatures.isEmpty()) {
            for (DeclarationPreviewLanguageFeatures feature : previewLanguageFeatures) {
                String featureDisplayName =
                        resources.getText("doclet.Declared_Using_Preview." + feature.name());
                result.add(withPreviewFeatures("doclet.Declared_Using_Preview", className,
                                               featureDisplayName, feature.features));
            }
        }
        if (!declaredUsingPreviewFeature.isEmpty()) {
            result.add(withLinks("doclet.UsesDeclaredUsingPreview", className, declaredUsingPreviewFeature));
        }
        if (!previewAPI.isEmpty()) {
            result.add(withLinks("doclet.PreviewAPI", className, previewAPI));
        }
        if (!reflectivePreviewAPI.isEmpty()) {
            result.add(withLinks("doclet.ReflectivePreviewAPI", className, reflectivePreviewAPI));
        }
        return result;
    }

    private Content withPreviewFeatures(String key, String className, String featureName, List<String> features) {
        String[] sep = new String[] {""};
        ContentBuilder featureCodes = new ContentBuilder();
        features.forEach(c -> {
                    featureCodes.add(sep[0]);
                    featureCodes.add(HtmlTree.CODE(new ContentBuilder().add(c)));
                    sep[0] = ", ";
                });
        return contents.getContent(key,
                                   HtmlTree.CODE(Text.of(className)),
                                   new HtmlTree(TagName.EM).add(featureName),
                                   featureCodes);
    }

    private Content withLinks(String key, String className, Set<TypeElement> elements) {
        String[] sep = new String[] {""};
        ContentBuilder links = new ContentBuilder();
        elements.stream()
                .sorted(Comparator.comparing(te -> te.getSimpleName().toString()))
                .distinct()
                .map(te -> getLink(new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS, te)
                        .label(HtmlTree.CODE(Text.of(te.getSimpleName()))).skipPreview(true)))
                .forEach(c -> {
                    links.add(sep[0]);
                    links.add(c);
                    sep[0] = ", ";
                });
        return contents.getContent(key,
                                   HtmlTree.CODE(Text.of(className)),
                                   links);
    }

    public URI resolveExternalSpecURI(URI specURI) {
        if (!specURI.isAbsolute()) {
            URI baseURI = configuration.getOptions().specBaseURI();
            if (baseURI == null) {
                baseURI = URI.create("../specs/");
            }
            if (!baseURI.isAbsolute() && !pathToRoot.isEmpty()) {
                baseURI = URI.create(pathToRoot.getPath() + "/").resolve(baseURI);
            }
            specURI = baseURI.resolve(specURI);
        }
        return specURI;
    }

    public void addRestrictedInfo(ExecutableElement forWhat, Content target) {
        if (utils.isRestrictedAPI(forWhat)) {
            //in Java platform:
            var restrictedDiv = HtmlTree.DIV(HtmlStyle.restrictedBlock);
            restrictedDiv.setId(htmlIds.forRestrictedSection(forWhat));
            String name = forWhat.getSimpleName().toString();
            var nameCode = HtmlTree.CODE(Text.of(name));
            String leadingNoteKey = "doclet.RestrictedLeadingNote";
            Content leadingNote =
                    contents.getContent(leadingNoteKey, nameCode);
            restrictedDiv.add(HtmlTree.SPAN(HtmlStyle.restrictedLabel,
                    leadingNote));
            Content note1 = contents.getContent("doclet.RestrictedTrailingNote1", nameCode);
            restrictedDiv.add(HtmlTree.DIV(HtmlStyle.restrictedComment, note1));
            Content note2 = contents.getContent("doclet.RestrictedTrailingNote2", nameCode);
            restrictedDiv.add(HtmlTree.DIV(HtmlStyle.restrictedComment, note2));
            target.add(restrictedDiv);
        }
    }

}
