/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor9;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.lang.model.util.SimpleTypeVisitor9;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.source.doctree.CommentTree;
import com.sun.source.doctree.DocRootTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTree.Kind;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.SummaryTree;
import com.sun.source.doctree.SystemPropertyTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.SimpleDocTreeVisitor;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.FixedStringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.Head;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlDocument;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Links;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.Script;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeWriter;
import jdk.javadoc.internal.doclets.toolkit.ClassWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.PackageSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.taglets.DocRootTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

import static com.sun.source.doctree.DocTree.Kind.CODE;
import static com.sun.source.doctree.DocTree.Kind.COMMENT;
import static com.sun.source.doctree.DocTree.Kind.LINK;
import static com.sun.source.doctree.DocTree.Kind.LINK_PLAIN;
import static com.sun.source.doctree.DocTree.Kind.SEE;
import static com.sun.source.doctree.DocTree.Kind.TEXT;
import static jdk.javadoc.internal.doclets.toolkit.util.CommentHelper.SPACER;


/**
 * Class for the Html Format Code Generation specific to JavaDoc.
 * This Class contains methods related to the Html Code Generation which
 * are used extensively while generating the entire documentation.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class HtmlDocletWriter {

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
     * Name of the file getting generated. If the file getting generated is
     * "java/lang/Object.html", then the filename is "Object.html".
     */
    public final DocPath filename;

    /**
     * The global configuration information for this run.
     */
    public final HtmlConfiguration configuration;

    protected final HtmlOptions options;

    protected final Utils utils;

    protected final Contents contents;

    protected final Messages messages;

    protected final Resources resources;

    protected final Links links;

    protected final DocPaths docPaths;

    /**
     * To check whether annotation heading is printed or not.
     */
    protected boolean printedAnnotationHeading = false;

    /**
     * To check whether annotation field heading is printed or not.
     */
    protected boolean printedAnnotationFieldHeading = false;

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
    Map<String, Integer> indexAnchorTable = new HashMap<>();

    /**
     * Creates an {@code HtmlDocletWriter}.
     *
     * @param configuration the configuration for this doclet
     * @param path the file to be generated.
     */
    public HtmlDocletWriter(HtmlConfiguration configuration, DocPath path) {
        this.configuration = configuration;
        this.options = configuration.getOptions();
        this.contents = configuration.contents;
        this.messages = configuration.messages;
        this.resources = configuration.docResources;
        this.links = new Links(path);
        this.utils = configuration.utils;
        this.path = path;
        this.pathToRoot = path.parent().invert();
        this.filename = path.basename();
        this.docPaths = configuration.docPaths;
        this.mainBodyScript = new Script();

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
            buf.append(htmlstr.substring(prevEnd, match));
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
        // Note: {@docRoot} is not case sensitive when passed in with a command-line option:
        private static final Pattern docrootPattern =
                Pattern.compile(Pattern.quote("{@docroot}"), Pattern.CASE_INSENSITIVE);

    /**
     * Get the script to show or hide the All classes link.
     *
     * @param id id of the element to show or hide
     * @return a content tree for the script
     */
    public Content getAllClassesLinkScript(String id) {
        Script script = new Script("<!--\n" +
                "  allClassesLink = document.getElementById(")
                .appendStringLiteral(id)
                .append(");\n" +
                "  if(window==top) {\n" +
                "    allClassesLink.style.display = \"block\";\n" +
                "  }\n" +
                "  else {\n" +
                "    allClassesLink.style.display = \"none\";\n" +
                "  }\n" +
                "  //-->\n");
        Content div = HtmlTree.DIV(script.asContent());
        Content div_noscript = HtmlTree.DIV(contents.noScriptMessage);
        Content noScript = HtmlTree.NOSCRIPT(div_noscript);
        div.add(noScript);
        return div;
    }

    /**
     * Add method information.
     *
     * @param method the method to be documented
     * @param dl the content tree to which the method information will be added
     */
    private void addMethodInfo(ExecutableElement method, Content dl) {
        TypeElement enclosing = utils.getEnclosingTypeElement(method);
        List<? extends TypeMirror> intfacs = enclosing.getInterfaces();
        ExecutableElement overriddenMethod = utils.overriddenMethod(method);
        VisibleMemberTable vmt = configuration.getVisibleMemberTable(enclosing);
        // Check whether there is any implementation or overridden info to be
        // printed. If no overridden or implementation info needs to be
        // printed, do not print this section.
        if ((!intfacs.isEmpty()
                && vmt.getImplementedMethods(method).isEmpty() == false)
                || overriddenMethod != null) {
            MethodWriterImpl.addImplementsInfo(this, method, dl);
            if (overriddenMethod != null) {
                MethodWriterImpl.addOverridden(this,
                        utils.overriddenType(method),
                        overriddenMethod,
                        dl);
            }
        }
    }

    /**
     * Adds the tags information.
     *
     * @param e the Element for which the tags will be generated
     * @param htmltree the documentation tree to which the tags will be added
     */
    protected void addTagsInfo(Element e, Content htmltree) {
        if (options.noComment()) {
            return;
        }
        Content dl = new HtmlTree(HtmlTag.DL);
        if (utils.isExecutableElement(e) && !utils.isConstructor(e)) {
            addMethodInfo((ExecutableElement)e, dl);
        }
        Content output = new ContentBuilder();
        TagletWriter.genTagOutput(configuration.tagletManager, e,
            configuration.tagletManager.getBlockTaglets(e),
                getTagletWriterInstance(false), output);
        dl.add(output);
        htmltree.add(dl);
    }

    /**
     * Check whether there are any tags for Serialization Overview
     * section to be printed.
     *
     * @param field the VariableElement object to check for tags.
     * @return true if there are tags to be printed else return false.
     */
    protected boolean hasSerializationOverviewTags(VariableElement field) {
        Content output = new ContentBuilder();
        TagletWriter.genTagOutput(configuration.tagletManager, field,
                configuration.tagletManager.getBlockTaglets(field),
                getTagletWriterInstance(false), output);
        return !output.isEmpty();
    }

    /**
     * Returns a TagletWriter that knows how to write HTML.
     *
     * @param isFirstSentence  true if we want to write the first sentence
     * @return a TagletWriter that knows how to write HTML.
     */
    public TagletWriter getTagletWriterInstance(boolean isFirstSentence) {
        return new TagletWriterImpl(this, isFirstSentence);
    }

    /**
     * Returns a TagletWriter that knows how to write HTML.
     *
     * @param isFirstSentence  true if we want to write the first sentence
     * @param inSummary  true if tags are to be added in a summary section
     * @return a TagletWriter
     */
    public TagletWriter getTagletWriterInstance(boolean isFirstSentence, boolean inSummary) {
        return new TagletWriterImpl(this, isFirstSentence, inSummary);
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
        printHtmlDocument(metakeywords, description, new ContentBuilder(), Collections.emptyList(), body);
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
        Content htmlComment = contents.newPage;
        List<DocPath> additionalStylesheets = configuration.getAdditionalStylesheets();
        additionalStylesheets.addAll(localStylesheets);
        Head head = new Head(path, configuration.docletVersion, configuration.startTime)
                .setTimestamp(!options.noTimestamp())
                .setDescription(description)
                .setGenerator(getGenerator(getClass()))
                .setTitle(winTitle)
                .setCharset(options.charset())
                .addKeywords(metakeywords)
                .setStylesheets(configuration.getMainStylesheet(), additionalStylesheets)
                .setIndex(options.createIndex(), mainBodyScript)
                .addContent(extraHeadContent);

        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(), head.toContent(), body);
        HtmlDocument htmlDocument = new HtmlDocument(htmlComment, htmlTree);
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
     * Get user specified header and the footer.
     *
     * @param header if true print the user provided header else print the
     * user provided footer.
     */
    public Content getUserHeaderFooter(boolean header) {
        String content;
        if (header) {
            content = replaceDocRootDir(options.header());
        } else {
            if (options.footer().length() != 0) {
                content = replaceDocRootDir(options.footer());
            } else {
                content = replaceDocRootDir(options.header());
            }
        }
        Content rawContent = new RawHtml(content);
        return rawContent;
    }

    /**
     * Adds the user specified top.
     *
     * @param htmlTree the content tree to which user specified top will be added
     */
    public void addTop(Content htmlTree) {
        Content top = new RawHtml(replaceDocRootDir(options.top()));
        htmlTree.add(top);
    }

    /**
     * Adds the user specified bottom.
     *
     * @param htmlTree the content tree to which user specified bottom will be added
     */
    public void addBottom(Content htmlTree) {
        Content bottom = new RawHtml(replaceDocRootDir(options.bottom()));
        Content small = HtmlTree.SMALL(bottom);
        Content p = HtmlTree.P(HtmlStyle.legalCopy, small);
        htmlTree.add(p);
    }

    /**
     * Get the overview tree link for the main tree.
     *
     * @param label the label for the link
     * @return a content tree for the link
     */
    protected Content getNavLinkMainTree(String label) {
        Content mainTreeContent = links.createLink(pathToRoot.resolve(DocPaths.OVERVIEW_TREE),
                new StringContent(label));
        Content li = HtmlTree.LI(mainTreeContent);
        return li;
    }

    /**
     * Get table caption.
     *
     * @param title the content for the caption
     * @return a content tree for the caption
     */
    public Content getTableCaption(Content title) {
        Content captionSpan = HtmlTree.SPAN(title);
        Content space = Entity.NO_BREAK_SPACE;
        Content tabSpan = HtmlTree.SPAN(HtmlStyle.tabEnd, space);
        Content caption = HtmlTree.CAPTION(captionSpan);
        caption.add(tabSpan);
        return caption;
    }

    /**
     * Returns a packagename content.
     *
     * @param packageElement the package to check
     * @return package name content
     */
    public Content getPackageName(PackageElement packageElement) {
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
        return new StringContent(packageName);
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
     * Given a package, return the name to be used in HTML anchor tag.
     * @param packageElement the package.
     * @return the name to be used in HTML anchor tag.
     */
    public String getPackageAnchorName(PackageElement packageElement) {
        return packageElement == null || packageElement.isUnnamed()
                ? SectionName.UNNAMED_PACKAGE_ANCHOR.getName()
                : utils.getPackageName(packageElement);
    }

    /**
     * Return the link to the given package.
     *
     * @param packageElement the package to link to.
     * @param label the label for the link.
     * @return a content tree for the package link.
     */
    public Content getPackageLink(PackageElement packageElement, CharSequence label) {
        return getPackageLink(packageElement, new StringContent(label));
    }

    public Content getPackageLink(PackageElement packageElement) {
        StringContent content =  packageElement.isUnnamed()
                ? new StringContent()
                : new StringContent(utils.getPackageName(packageElement));
        return getPackageLink(packageElement, content);
    }

    /**
     * Return the link to the given package.
     *
     * @param packageElement the package to link to.
     * @param label the label for the link.
     * @return a content tree for the package link.
     */
    public Content getPackageLink(PackageElement packageElement, Content label) {
        boolean included = packageElement != null && utils.isIncluded(packageElement);
        if (!included) {
            for (PackageElement p : configuration.packages) {
                if (p.equals(packageElement)) {
                    included = true;
                    break;
                }
            }
        }
        if (included || packageElement == null) {
            return links.createLink(pathString(packageElement, DocPaths.PACKAGE_SUMMARY),
                    label);
        } else {
            DocLink crossPkgLink = getCrossPackageLink(packageElement);
            if (crossPkgLink != null) {
                return links.createLink(crossPkgLink, label);
            } else {
                return label;
            }
        }
    }

    /**
     * Get Module link.
     *
     * @param mdle the module being documented
     * @param label tag for the link
     * @return a content for the module link
     */
    public Content getModuleLink(ModuleElement mdle, Content label) {
        boolean included = utils.isIncluded(mdle);
        return (included)
                ? links.createLink(pathToRoot.resolve(docPaths.moduleSummary(mdle)), label, "", "")
                : label;
    }

    public Content interfaceName(TypeElement typeElement, boolean qual) {
        Content name = new StringContent((qual)
                ? typeElement.getQualifiedName()
                : utils.getSimpleName(typeElement));
        return (utils.isInterface(typeElement)) ?  HtmlTree.SPAN(HtmlStyle.interfaceName, name) : name;
    }

    /**
     * Add the link to the content tree.
     *
     * @param element program element for which the link will be added
     * @param label label for the link
     * @param htmltree the content tree to which the link will be added
     */
    public void addSrcLink(Element element, Content label, Content htmltree) {
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
                    .fragment(SourceToHTMLConverter.getAnchorName(utils, element)), label, "", "");
            htmltree.add(content);
        } else {
            htmltree.add(label);
        }
    }

    /**
     * Return the link to the given class.
     *
     * @param linkInfo the information about the link.
     *
     * @return the link for the given class.
     */
    public Content getLink(LinkInfoImpl linkInfo) {
        LinkFactoryImpl factory = new LinkFactoryImpl(this);
        return factory.getLink(linkInfo);
    }

    /**
     * Return the type parameters for the given class.
     *
     * @param linkInfo the information about the link.
     * @return the type for the given class.
     */
    public Content getTypeParameterLinks(LinkInfoImpl linkInfo) {
        LinkFactoryImpl factory = new LinkFactoryImpl(this);
        return factory.getTypeParameterLinks(linkInfo, false);
    }

    /*************************************************************
     * Return a class cross link to external class documentation.
     * The -link option does not allow users to
     * link to external classes in the "default" package.
     *
     * @param classElement the class element
     * @param refMemName the name of the member being referenced.  This should
     * be null or empty string if no member is being referenced.
     * @param label the label for the external link.
     * @param strong true if the link should be strong.
     * @param code true if the label should be code font.
     * @return the link
     */
    public Content getCrossClassLink(TypeElement classElement, String refMemName,
                                    Content label, boolean strong, boolean code) {
        if (classElement != null) {
            String className = utils.getSimpleName(classElement);
            PackageElement packageElement = utils.containingPackage(classElement);
            Content defaultLabel = new StringContent(className);
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
                    (label == null) || label.isEmpty() ? defaultLabel : label,
                    strong,
                    resources.getText("doclet.Href_Class_Or_Interface_Title",
                        utils.getPackageName(packageElement)), "", true);
            }
        }
        return null;
    }

    public boolean isClassLinkable(TypeElement typeElement) {
        if (utils.isIncluded(typeElement)) {
            return configuration.isGeneratedDoc(typeElement);
        }
        return configuration.extern.isExternal(typeElement);
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
     * Get the class link.
     *
     * @param context the id of the context where the link will be added
     * @param element to link to
     * @return a content tree for the link
     */
    public Content getQualifiedClassLink(LinkInfoImpl.Kind context, Element element) {
        LinkInfoImpl linkInfoImpl = new LinkInfoImpl(configuration, context, (TypeElement)element);
        return getLink(linkInfoImpl.label(utils.getFullyQualifiedName(element)));
    }

    /**
     * Add the class link.
     *
     * @param context the id of the context where the link will be added
     * @param typeElement to link to
     * @param contentTree the content tree to which the link will be added
     */
    public void addPreQualifiedClassLink(LinkInfoImpl.Kind context, TypeElement typeElement, Content contentTree) {
        addPreQualifiedClassLink(context, typeElement, false, contentTree);
    }

    /**
     * Retrieve the class link with the package portion of the label in
     * plain text.  If the qualifier is excluded, it will not be included in the
     * link label.
     *
     * @param typeElement the class to link to.
     * @param isStrong true if the link should be strong.
     * @return the link with the package portion of the label in plain text.
     */
    public Content getPreQualifiedClassLink(LinkInfoImpl.Kind context,
            TypeElement typeElement, boolean isStrong) {
        ContentBuilder classlink = new ContentBuilder();
        PackageElement pkg = utils.containingPackage(typeElement);
        if (pkg != null && ! configuration.shouldExcludeQualifier(pkg.getSimpleName().toString())) {
            classlink.add(getEnclosingPackageName(typeElement));
        }
        classlink.add(getLink(new LinkInfoImpl(configuration,
                context, typeElement).label(utils.getSimpleName(typeElement)).strong(isStrong)));
        return classlink;
    }

    /**
     * Add the class link with the package portion of the label in
     * plain text. If the qualifier is excluded, it will not be included in the
     * link label.
     *
     * @param context the id of the context where the link will be added
     * @param typeElement the class to link to
     * @param isStrong true if the link should be strong
     * @param contentTree the content tree to which the link with be added
     */
    public void addPreQualifiedClassLink(LinkInfoImpl.Kind context,
            TypeElement typeElement, boolean isStrong, Content contentTree) {
        PackageElement pkg = utils.containingPackage(typeElement);
        if(pkg != null && ! configuration.shouldExcludeQualifier(pkg.getSimpleName().toString())) {
            contentTree.add(getEnclosingPackageName(typeElement));
        }
        LinkInfoImpl linkinfo = new LinkInfoImpl(configuration, context, typeElement)
                .label(utils.getSimpleName(typeElement))
                .strong(isStrong);
        Content link = getLink(linkinfo);
        contentTree.add(link);
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
    protected TypeElement getCurrentPageElement() {
        return null;
    }

    /**
     * Add the class link, with only class name as the strong link and prefixing
     * plain package name.
     *
     * @param context the id of the context where the link will be added
     * @param typeElement the class to link to
     * @param contentTree the content tree to which the link with be added
     */
    public void addPreQualifiedStrongClassLink(LinkInfoImpl.Kind context, TypeElement typeElement, Content contentTree) {
        addPreQualifiedClassLink(context, typeElement, true, contentTree);
    }

    /**
     * Get the link for the given member.
     *
     * @param context the id of the context where the link will be added
     * @param element the member being linked to
     * @param label the label for the link
     * @return a content tree for the element link
     */
    public Content getDocLink(LinkInfoImpl.Kind context, Element element, CharSequence label) {
        return getDocLink(context, utils.getEnclosingTypeElement(element), element,
                new StringContent(label));
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param element the member being linked to.
     * @param label the label for the link.
     * @param strong true if the link should be strong.
     * @return the link for the given member.
     */
    public Content getDocLink(LinkInfoImpl.Kind context, Element element, CharSequence label,
            boolean strong) {
        return getDocLink(context, utils.getEnclosingTypeElement(element), element, label, strong);
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param typeElement the typeElement that we should link to.  This is not
                 necessarily equal to element.containingClass().  We may be
                 inheriting comments.
     * @param element the member being linked to.
     * @param label the label for the link.
     * @param strong true if the link should be strong.
     * @return the link for the given member.
     */
    public Content getDocLink(LinkInfoImpl.Kind context, TypeElement typeElement, Element element,
            CharSequence label, boolean strong) {
        return getDocLink(context, typeElement, element, label, strong, false);
    }

    public Content getDocLink(LinkInfoImpl.Kind context, TypeElement typeElement, Element element,
            Content label, boolean strong) {
        return getDocLink(context, typeElement, element, label, strong, false);
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param typeElement the typeElement that we should link to.  This is not
                 necessarily equal to element.containingClass().  We may be
                 inheriting comments.
     * @param element the member being linked to.
     * @param label the label for the link.
     * @param strong true if the link should be strong.
     * @param isProperty true if the element parameter is a JavaFX property.
     * @return the link for the given member.
     */
    public Content getDocLink(LinkInfoImpl.Kind context, TypeElement typeElement, Element element,
            CharSequence label, boolean strong, boolean isProperty) {
        return getDocLink(context, typeElement, element, new StringContent(label), strong, isProperty);
    }

    public Content getDocLink(LinkInfoImpl.Kind context, TypeElement typeElement, Element element,
            Content label, boolean strong, boolean isProperty) {
        if (!utils.isLinkable(typeElement, element)) {
            return label;
        }

        if (utils.isExecutableElement(element)) {
            ExecutableElement ee = (ExecutableElement)element;
            return getLink(new LinkInfoImpl(configuration, context, typeElement)
                .label(label)
                .where(links.getName(getAnchor(ee, isProperty)))
                .strong(strong));
        }

        if (utils.isVariableElement(element) || utils.isTypeElement(element)) {
            return getLink(new LinkInfoImpl(configuration, context, typeElement)
                .label(label)
                .where(links.getName(element.getSimpleName().toString()))
                .strong(strong));
        }

        return label;
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be added
     * @param typeElement the typeElement that we should link to.  This is not
                 necessarily equal to element.containingClass().  We may be
                 inheriting comments
     * @param element the member being linked to
     * @param label the label for the link
     * @return the link for the given member
     */
    public Content getDocLink(LinkInfoImpl.Kind context, TypeElement typeElement, Element element,
            Content label) {
        if (! (utils.isIncluded(element) || utils.isLinkable(typeElement))) {
            return label;
        } else if (utils.isExecutableElement(element)) {
            ExecutableElement emd = (ExecutableElement) element;
            return getLink(new LinkInfoImpl(configuration, context, typeElement)
                .label(label)
                .where(links.getName(getAnchor(emd))));
        } else if (utils.isVariableElement(element) || utils.isTypeElement(element)) {
            return getLink(new LinkInfoImpl(configuration, context, typeElement)
                .label(label).where(links.getName(element.getSimpleName().toString())));
        } else {
            return label;
        }
    }

    public String getAnchor(ExecutableElement executableElement) {
        return getAnchor(executableElement, false);
    }

    public String getAnchor(ExecutableElement executableElement, boolean isProperty) {
        if (isProperty) {
            return executableElement.getSimpleName().toString();
        }
        String member = anchorName(executableElement);
        String erasedSignature = utils.makeSignature(executableElement, true, true);
        return member + erasedSignature;
    }

    public String anchorName(Element member) {
        if (member.getKind() == ElementKind.CONSTRUCTOR) {
            return "<init>";
        } else {
            return utils.getSimpleName(member);
        }
    }

    public Content seeTagToContent(Element element, DocTree see) {
        Kind kind = see.getKind();
        if (!(kind == LINK || kind == SEE || kind == LINK_PLAIN)) {
            return new ContentBuilder();
        }

        CommentHelper ch = utils.getCommentHelper(element);
        String tagName = ch.getTagName(see);
        String seetext = replaceDocRootDir(utils.normalizeNewlines(ch.getText(see)).toString());
        // Check if @see is an href or "string"
        if (seetext.startsWith("<") || seetext.startsWith("\"")) {
            return new RawHtml(seetext);
        }
        boolean isLinkPlain = kind == LINK_PLAIN;
        Content label = plainOrCode(isLinkPlain, new RawHtml(ch.getLabel(see)));

        //The text from the @see tag.  We will output this text when a label is not specified.
        Content text = plainOrCode(kind == LINK_PLAIN, new RawHtml(seetext));

        TypeElement refClass = ch.getReferencedClass(see);
        String refClassName =  ch.getReferencedClassName(see);
        Element refMem =       ch.getReferencedMember(see);
        String refMemName =    ch.getReferencedMemberName(see);

        if (refMemName == null && refMem != null) {
            refMemName = refMem.toString();
        }
        if (refClass == null) {
            //@see is not referencing an included class
            PackageElement refPackage = ch.getReferencedPackage(see);
            if (refPackage != null && utils.isIncluded(refPackage)) {
                //@see is referencing an included package
                if (label.isEmpty())
                    label = plainOrCode(isLinkPlain,
                            new StringContent(refPackage.getQualifiedName()));
                return getPackageLink(refPackage, label);
            } else {
                // @see is not referencing an included class, module or package. Check for cross links.
                DocLink elementCrossLink = (configuration.extern.isModule(refClassName))
                        ? getCrossModuleLink(utils.elementUtils.getModuleElement(refClassName)) :
                        (refPackage != null) ? getCrossPackageLink(refPackage) : null;
                if (elementCrossLink != null) {
                    // Element cross link found
                    return links.createLink(elementCrossLink,
                            (label.isEmpty() ? text : label), true);
                } else {
                    // No cross link found so print warning
                    messages.warning(ch.getDocTreePath(see),
                            "doclet.see.class_or_package_not_found",
                            "@" + tagName,
                            seetext);
                    return (label.isEmpty() ? text: label);
                }
            }
        } else if (refMemName == null) {
            // Must be a class reference since refClass is not null and refMemName is null.
            if (label.isEmpty()) {
                /*
                 * it seems to me this is the right thing to do, but it causes comparator failures.
                 */
                if (!configuration.backwardCompatibility) {
                    StringContent content = utils.isEnclosingPackageIncluded(refClass)
                            ? new StringContent(utils.getSimpleName(refClass))
                            : new StringContent(utils.getFullyQualifiedName(refClass));
                    label = plainOrCode(isLinkPlain, content);
                } else {
                    label = plainOrCode(isLinkPlain,
                            new StringContent(utils.getSimpleName(refClass)));
                }

            }
            return getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.DEFAULT, refClass)
                    .label(label));
        } else if (refMem == null) {
            // Must be a member reference since refClass is not null and refMemName is not null.
            // However, refMem is null, so this referenced member does not exist.
            return (label.isEmpty() ? text: label);
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
            if (ch.getText(see).trim().startsWith("#") &&
                ! (utils.isPublic(containing) || utils.isLinkable(containing))) {
                // Since the link is relative and the holder is not even being
                // documented, this must be an inherited link.  Redirect it.
                // The current class either overrides the referenced member or
                // inherits it automatically.
                if (this instanceof ClassWriterImpl) {
                    containing = ((ClassWriterImpl) this).getTypeElement();
                } else if (!utils.isPublic(containing)) {
                    messages.warning(
                        ch.getDocTreePath(see), "doclet.see.class_or_package_not_accessible",
                        tagName, utils.getFullyQualifiedName(containing));
                } else {
                    messages.warning(
                        ch.getDocTreePath(see), "doclet.see.class_or_package_not_found",
                        tagName, seetext);
                }
            }
            if (configuration.currentTypeElement != containing) {
                refMemName = (utils.isConstructor(refMem))
                        ? refMemName
                        : utils.getSimpleName(containing) + "." + refMemName;
            }
            if (utils.isExecutableElement(refMem)) {
                if (refMemName.indexOf('(') < 0) {
                    refMemName += utils.makeSignature((ExecutableElement)refMem, true);
                }
                if (overriddenMethod != null) {
                    // The method to actually link.
                    refMem = overriddenMethod;
                }
            }

            text = plainOrCode(kind == LINK_PLAIN, new StringContent(refMemName));

            return getDocLink(LinkInfoImpl.Kind.SEE_TAG, containing,
                    refMem, (label.isEmpty() ? text: label), false);
        }
    }

    private Content plainOrCode(boolean plain, Content body) {
        return (plain || body.isEmpty()) ? body : HtmlTree.CODE(body);
    }

    /**
     * Add the inline comment.
     *
     * @param element the Element for which the inline comment will be added
     * @param tag the inline tag to be added
     * @param htmltree the content tree to which the comment will be added
     */
    public void addInlineComment(Element element, DocTree tag, Content htmltree) {
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DocTree> description = ch.getDescription(tag);
        addCommentTags(element, tag, description, false, false, false, htmltree);
    }

    /**
     * Get the deprecated phrase as content.
     *
     * @param e the Element for which the inline deprecated comment will be added
     * @return a content tree for the deprecated phrase.
     */
    public Content getDeprecatedPhrase(Element e) {
        return (utils.isDeprecatedForRemoval(e))
                ? contents.deprecatedForRemovalPhrase
                : contents.deprecatedPhrase;
    }

    /**
     * Add the inline deprecated comment.
     *
     * @param e the Element for which the inline deprecated comment will be added
     * @param tag the inline tag to be added
     * @param htmltree the content tree to which the comment will be added
     */
    public void addInlineDeprecatedComment(Element e, DocTree tag, Content htmltree) {
        CommentHelper ch = utils.getCommentHelper(e);
        addCommentTags(e, ch.getBody(tag), true, false, false, htmltree);
    }

    /**
     * Adds the summary content.
     *
     * @param element the Element for which the summary will be generated
     * @param htmltree the documentation tree to which the summary will be added
     */
    public void addSummaryComment(Element element, Content htmltree) {
        addSummaryComment(element, utils.getFirstSentenceTrees(element), htmltree);
    }

    /**
     * Adds the summary content.
     *
     * @param element the Element for which the summary will be generated
     * @param firstSentenceTags the first sentence tags for the doc
     * @param htmltree the documentation tree to which the summary will be added
     */
    public void addSummaryComment(Element element, List<? extends DocTree> firstSentenceTags, Content htmltree) {
        addCommentTags(element, firstSentenceTags, false, true, true, htmltree);
    }

    public void addSummaryDeprecatedComment(Element element, DocTree tag, Content htmltree) {
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DocTree> body = ch.getBody(tag);
        addCommentTags(element, ch.getFirstSentenceTrees(body), true, true, true, htmltree);
    }

    /**
     * Adds the inline comment.
     *
     * @param element the Element for which the inline comments will be generated
     * @param htmltree the documentation tree to which the inline comments will be added
     */
    public void addInlineComment(Element element, Content htmltree) {
        addCommentTags(element, utils.getFullBody(element), false, false, false, htmltree);
    }

    /**
     * Adds the comment tags.
     *
     * @param element the Element for which the comment tags will be generated
     * @param tags the first sentence tags for the doc
     * @param depr true if it is deprecated
     * @param first true if the first sentence tags should be added
     * @param inSummary true if the comment tags are added into the summary section
     * @param htmltree the documentation tree to which the comment tags will be added
     */
    private void addCommentTags(Element element, List<? extends DocTree> tags, boolean depr,
            boolean first, boolean inSummary, Content htmltree) {
        addCommentTags(element, null, tags, depr, first, inSummary, htmltree);
    }

    /**
     * Adds the comment tags.
     *
     * @param element for which the comment tags will be generated
     * @param holderTag the block tag context for the inline tags
     * @param tags the first sentence tags for the doc
     * @param depr true if it is deprecated
     * @param first true if the first sentence tags should be added
     * @param inSummary true if the comment tags are added into the summary section
     * @param htmltree the documentation tree to which the comment tags will be added
     */
    private void addCommentTags(Element element, DocTree holderTag, List<? extends DocTree> tags, boolean depr,
            boolean first, boolean inSummary, Content htmltree) {
        if (options.noComment()){
            return;
        }
        Content div;
        Content result = commentTagsToContent(null, element, tags, first, inSummary);
        if (depr) {
            div = HtmlTree.DIV(HtmlStyle.deprecationComment, result);
            htmltree.add(div);
        }
        else {
            div = HtmlTree.DIV(HtmlStyle.block, result);
            htmltree.add(div);
        }
        if (tags.isEmpty()) {
            htmltree.add(Entity.NO_BREAK_SPACE);
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
            com.sun.tools.doclint.HtmlTag htmlTag = com.sun.tools.doclint.HtmlTag.get(name);
            if (htmlTag != null &&
                    htmlTag.blockType != com.sun.tools.doclint.HtmlTag.BlockType.INLINE) {
                return true;
            }
        }
        return false;
    }

    boolean isAllWhiteSpace(String body) {
        for (int i = 0 ; i < body.length(); i++) {
            if (!Character.isWhitespace(body.charAt(i)))
                return false;
        }
        return true;
    }

    // Notify the next DocTree handler to take necessary action
    private boolean commentRemoved = false;

    /**
     * Converts inline tags and text to Content, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to block tags.
     *
     * @param holderTag    specific tag where comment resides
     * @param element    specific element where comment resides
     * @param tags   array of text tags and inline tags (often alternating)
               present in the text of interest for this element
     * @param isFirstSentence  true if text is first sentence
     * @return a Content object
     */
    public Content commentTagsToContent(DocTree holderTag,
                                        Element element,
                                        List<? extends DocTree> tags,
                                        boolean isFirstSentence)
    {
        return commentTagsToContent(holderTag, element, tags, isFirstSentence, false);
    }

    /**
     * Converts inline tags and text to text strings, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to block tags.
     *
     * @param holderTag       specific tag where comment resides
     * @param element         specific element where comment resides
     * @param trees           array of text tags and inline tags (often alternating)
     *                        present in the text of interest for this element
     * @param isFirstSentence true if text is first sentence
     * @param inSummary       if the comment tags are added into the summary section
     * @return a Content object
     */
    public Content commentTagsToContent(DocTree holderTag,
                                        Element element,
                                        List<? extends DocTree> trees,
                                        boolean isFirstSentence,
                                        boolean inSummary)
    {
        final Content result = new ContentBuilder() {
            @Override
            public void add(CharSequence text) {
                super.add(utils.normalizeNewlines(text));
            }
        };
        CommentHelper ch = utils.getCommentHelper(element);
        // Array of all possible inline tags for this javadoc run
        configuration.tagletManager.checkTags(element, trees, true);
        commentRemoved = false;

        for (ListIterator<? extends DocTree> iterator = trees.listIterator(); iterator.hasNext();) {
            boolean isFirstNode = !iterator.hasPrevious();
            DocTree tag = iterator.next();
            boolean isLastNode  = !iterator.hasNext();

            if (isFirstSentence) {
                // Ignore block tags
                if (ignoreNonInlineTag(tag))
                    continue;

                // Ignore any trailing whitespace OR whitespace after removed html comment
                if ((isLastNode || commentRemoved)
                        && tag.getKind() == TEXT
                        && isAllWhiteSpace(ch.getText(tag)))
                    continue;

                // Ignore any leading html comments
                if ((isFirstNode || commentRemoved) && tag.getKind() == COMMENT) {
                    commentRemoved = true;
                    continue;
                }
            }

            boolean allDone = new SimpleDocTreeVisitor<Boolean, Content>() {

                private boolean inAnAtag() {
                    if (utils.isStartElement(tag)) {
                        StartElementTree st = (StartElementTree)tag;
                        Name name = st.getName();
                        if (name != null) {
                            com.sun.tools.doclint.HtmlTag htag =
                                    com.sun.tools.doclint.HtmlTag.get(name);
                            return htag != null && htag.equals(com.sun.tools.doclint.HtmlTag.A);
                        }
                    }
                    return false;
                }

                @Override
                public Boolean visitAttribute(AttributeTree node, Content c) {
                    StringBuilder sb = new StringBuilder(SPACER).append(node.getName());
                    if (node.getValueKind() == ValueKind.EMPTY) {
                        result.add(sb);
                        return false;
                    }
                    sb.append("=");
                    String quote;
                    switch (node.getValueKind()) {
                        case DOUBLE:
                            quote = "\"";
                            break;
                        case SINGLE:
                            quote = "'";
                            break;
                        default:
                            quote = "";
                            break;
                    }
                    sb.append(quote);
                    result.add(sb);
                    Content docRootContent = new ContentBuilder();

                    boolean isHRef = inAnAtag() && node.getName().toString().equalsIgnoreCase("href");
                    for (DocTree dt : node.getValue()) {
                        if (utils.isText(dt) && isHRef) {
                            String text = ((TextTree) dt).getBody();
                            if (text.startsWith("/..") && !options.docrootParent().isEmpty()) {
                                result.add(options.docrootParent());
                                docRootContent = new ContentBuilder();
                                result.add(textCleanup(text.substring(3), isLastNode));
                            } else {
                                if (!docRootContent.isEmpty()) {
                                    docRootContent = copyDocRootContent(docRootContent);
                                } else {
                                    text = redirectRelativeLinks(element, (TextTree) dt);
                                }
                                result.add(textCleanup(text, isLastNode));
                            }
                        } else {
                            docRootContent = copyDocRootContent(docRootContent);
                            dt.accept(this, docRootContent);
                        }
                    }
                    copyDocRootContent(docRootContent);
                    result.add(quote);
                    return false;
                }

                @Override
                public Boolean visitComment(CommentTree node, Content c) {
                    result.add(new RawHtml(node.getBody()));
                    return false;
                }

                private Content copyDocRootContent(Content content) {
                    if (!content.isEmpty()) {
                        result.add(content);
                        return new ContentBuilder();
                    }
                    return content;
                }

                @Override
                public Boolean visitDocRoot(DocRootTree node, Content c) {
                    Content docRootContent = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager,
                            holderTag,
                            node,
                            getTagletWriterInstance(isFirstSentence));
                    if (c != null) {
                        c.add(docRootContent);
                    } else {
                        result.add(docRootContent);
                    }
                    return false;
                }

                @Override
                public Boolean visitEndElement(EndElementTree node, Content c) {
                    RawHtml rawHtml = new RawHtml("</" + node.getName() + ">");
                    result.add(rawHtml);
                    return false;
                }

                @Override
                public Boolean visitEntity(EntityTree node, Content c) {
                    result.add(new RawHtml(node.toString()));
                    return false;
                }

                @Override
                public Boolean visitErroneous(ErroneousTree node, Content c) {
                    messages.warning(ch.getDocTreePath(node),
                            "doclet.tag.invalid_usage", node);
                    result.add(new RawHtml(node.toString()));
                    return false;
                }

                @Override
                public Boolean visitInheritDoc(InheritDocTree node, Content c) {
                    Content output = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager, holderTag,
                            tag, getTagletWriterInstance(isFirstSentence));
                    result.add(output);
                    // if we obtained the first sentence successfully, nothing more to do
                    return (isFirstSentence && !output.isEmpty());
                }

                @Override
                public Boolean visitIndex(IndexTree node, Content p) {
                    Content output = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager, holderTag, tag,
                            getTagletWriterInstance(isFirstSentence, inSummary));
                    if (output != null) {
                        result.add(output);
                    }
                    return false;
                }

                @Override
                public Boolean visitLink(LinkTree node, Content c) {
                    // we need to pass the DocTreeImpl here, so ignore node
                    Content content = seeTagToContent(element, tag);
                    result.add(content);
                    return false;
                }

                @Override
                public Boolean visitLiteral(LiteralTree node, Content c) {
                    String s = node.getBody().getBody();
                    Content content = new StringContent(utils.normalizeNewlines(s));
                    if (node.getKind() == CODE)
                        content = HtmlTree.CODE(content);
                    result.add(content);
                    return false;
                }

                @Override
                public Boolean visitSee(SeeTree node, Content c) {
                    // we need to pass the DocTreeImpl here, so ignore node
                    result.add(seeTagToContent(element, tag));
                    return false;
                }

                @Override
                public Boolean visitStartElement(StartElementTree node, Content c) {
                    String text = "<" + node.getName();
                    RawHtml rawHtml = new RawHtml(utils.normalizeNewlines(text));
                    result.add(rawHtml);

                    for (DocTree dt : node.getAttributes()) {
                        dt.accept(this, null);
                    }
                    result.add(new RawHtml(node.isSelfClosing() ? "/>" : ">"));
                    return false;
                }

                @Override
                public Boolean visitSummary(SummaryTree node, Content c) {
                    Content output = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager, holderTag, tag,
                            getTagletWriterInstance(isFirstSentence));
                    result.add(output);
                    return false;
                }

                @Override
                public Boolean visitSystemProperty(SystemPropertyTree node, Content p) {
                    Content output = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager, holderTag, tag,
                            getTagletWriterInstance(isFirstSentence, inSummary));
                    if (output != null) {
                        result.add(output);
                    }
                    return false;
                }

                private CharSequence textCleanup(String text, boolean isLast) {
                    return textCleanup(text, isLast, false);
                }

                private CharSequence textCleanup(String text, boolean isLast, boolean stripLeading) {
                    boolean stripTrailing = isFirstSentence && isLast;
                    if (stripLeading && stripTrailing) {
                        text = text.strip();
                    } else if (stripLeading) {
                        text = text.stripLeading();
                    } else if (stripTrailing) {
                        text = text.stripTrailing();
                    }
                    text = utils.replaceTabs(text);
                    return utils.normalizeNewlines(text);
                }

                @Override
                public Boolean visitText(TextTree node, Content c) {
                    String text = node.getBody();
                    result.add(new RawHtml(textCleanup(text, isLastNode, commentRemoved)));
                    return false;
                }

                @Override
                protected Boolean defaultAction(DocTree node, Content c) {
                    Content output = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager, holderTag, tag,
                            getTagletWriterInstance(isFirstSentence));
                    if (output != null) {
                        result.add(output);
                    }
                    return false;
                }

            }.visit(tag, null);
            commentRemoved = false;
            if (allDone)
                break;
        }
        return result;
    }

    /**
     * Return true if relative links should not be redirected.
     *
     * @return Return true if a relative link should not be redirected.
     */
    private boolean shouldNotRedirectRelativeLinks() {
        return  this instanceof AnnotationTypeWriter ||
                this instanceof ClassWriter ||
                this instanceof PackageSummaryWriter;
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
    @SuppressWarnings("preview")
    private String redirectRelativeLinks(Element element, TextTree tt) {
        String text = tt.getBody();
        if (element == null || utils.isOverviewElement(element) || shouldNotRedirectRelativeLinks()) {
            return text;
        }

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
            protected DocPath defaultAction(Element e, Void p) {
                return null;
            }
        }.visit(element);
        if (redirectPathFromRoot == null) {
            return text;
        }
        String lower = Utils.toLowerCase(text);
        if (!(lower.startsWith("mailto:")
                || lower.startsWith("http:")
                || lower.startsWith("https:")
                || lower.startsWith("file:"))) {
            text = "{@" + (new DocRootTaglet()).getName() + "}/"
                    + redirectPathFromRoot.resolve(text).getPath();
            text = replaceDocRootDir(text);
        }
        return text;
    }

    /**
     * According to
     * <cite>The Java&trade; Language Specification</cite>,
     * all the outer classes and static nested classes are core classes.
     */
    public boolean isCoreClass(TypeElement typeElement) {
        return utils.getEnclosingTypeElement(typeElement) == null || utils.isStatic(typeElement);
    }

    /**
     * Adds the annotation types for the given packageElement.
     *
     * @param packageElement the package to write annotations for.
     * @param htmltree the documentation tree to which the annotation info will be
     *        added
     */
    public void addAnnotationInfo(PackageElement packageElement, Content htmltree) {
        addAnnotationInfo(packageElement.getAnnotationMirrors(), htmltree);
    }

    /*
     * this is a hack to delay dealing with Annotations in the writers, the assumption
     * is that all necessary checks have been made to get here.
     */
    public void addReceiverAnnotationInfo(ExecutableElement method, TypeMirror rcvrTypeMirror,
            List<? extends AnnotationMirror> annotationMirrors, Content htmltree) {
        TypeMirror rcvrType = method.getReceiverType();
        List<? extends AnnotationMirror> annotationMirrors1 = rcvrType.getAnnotationMirrors();
        htmltree.add(getAnnotationInfo(annotationMirrors1, false));
    }

    /**
     * Adds the annotation types for the given element.
     *
     * @param element the package to write annotations for
     * @param htmltree the content tree to which the annotation types will be added
     */
    public void addAnnotationInfo(Element element, Content htmltree) {
        addAnnotationInfo(element.getAnnotationMirrors(), htmltree);
    }

    /**
     * Add the annotation types for the given element and parameter.
     *
     * @param param the parameter to write annotations for.
     * @param tree the content tree to which the annotation types will be added
     */
    public boolean addAnnotationInfo(VariableElement param, Content tree) {
        Content annotationInfo = getAnnotationInfo(param.getAnnotationMirrors(), false);
        if (annotationInfo.isEmpty()) {
            return false;
        }
        tree.add(annotationInfo);
        return true;
    }

    /**
     * Adds the annotation types for the given Element.
     *
     * @param descList a list of annotation mirrors.
     * @param htmltree the documentation tree to which the annotation info will be
     *        added
     */
    private void addAnnotationInfo(List<? extends AnnotationMirror> descList, Content htmltree) {
        htmltree.add(getAnnotationInfo(descList, true));
    }

    /**
     * Return a content tree containing the annotation types for the given element.
     *
     * @param descList a list of annotation mirrors.
     * @return the documentation tree containing the annotation info.
     */
    Content getAnnotationInfo(List<? extends AnnotationMirror> descList, boolean lineBreak) {
        List<Content> annotations = getAnnotations(descList, lineBreak);
        String sep = "";
        ContentBuilder builder = new ContentBuilder();
        for (Content annotation: annotations) {
            builder.add(sep);
            builder.add(annotation);
            if (!lineBreak) {
                sep = " ";
            }
        }
        return builder;
    }

    /**
     * Return the string representations of the annotation types for
     * the given doc.
     *
     * @param descList a list of annotation mirrors.
     * @param linkBreak if true, add new line between each member value.
     * @return a list of strings representing the annotations being
     *         documented.
     */
    public List<Content> getAnnotations(List<? extends AnnotationMirror> descList, boolean linkBreak) {
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
            LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                                                     LinkInfoImpl.Kind.ANNOTATION, annotationElement);
            Map<? extends ExecutableElement, ? extends AnnotationValue> pairs = aDesc.getElementValues();
            // If the annotation is synthesized, do not print the container.
            if (utils.configuration.workArounds.isSynthesized(aDesc)) {
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
                addAnnotations(annotationElement, linkInfo, annotation, pairs, linkBreak);
            }
            annotation.add(linkBreak ? DocletConstants.NL : "");
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
    private void addAnnotations(TypeElement annotationDoc, LinkInfoImpl linkInfo,
                                ContentBuilder annotation,
                                Map<? extends ExecutableElement, ? extends AnnotationValue> map,
                                boolean linkBreak) {
        linkInfo.label = new StringContent("@");
        linkInfo.label.add(annotationDoc.getSimpleName());
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
                        annotation.add(DocletConstants.NL);
                        int spaces = annotationDoc.getSimpleName().length() + 2;
                        for (int k = 0; k < (spaces); k++) {
                            annotation.add(" ");
                        }
                    }
                }
                String simpleName = element.getSimpleName().toString();
                if (multipleValues || !"value".equals(simpleName)) { // Omit "value=" where unnecessary
                    annotation.add(getDocLink(LinkInfoImpl.Kind.ANNOTATION,
                                                     element, simpleName, false));
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
            public Content visitType(TypeMirror t, Void p) {
                return new SimpleTypeVisitor9<Content, Void>() {
                    @Override
                    public Content visitDeclared(DeclaredType t, Void p) {
                        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                                LinkInfoImpl.Kind.ANNOTATION, t);
                        String name = utils.isIncluded(t.asElement())
                                ? t.asElement().getSimpleName().toString()
                                : utils.getFullyQualifiedName(t.asElement());
                        linkInfo.label = new StringContent(name + utils.getDimension(t) + ".class");
                        return getLink(linkInfo);
                    }
                    @Override
                    protected Content defaultAction(TypeMirror e, Void p) {
                        return new StringContent(t + utils.getDimension(t) + ".class");
                    }
                }.visit(t);
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
                return getDocLink(LinkInfoImpl.Kind.ANNOTATION,
                        c, c.getSimpleName(), false);
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
                return new StringContent(annotationValue.toString());
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
            CharSequence name;
            switch (e.getKind()) {
                case MODULE:
                case PACKAGE:
                    name = ((QualifiedNameable) e).getQualifiedName();
                    if (name.length() == 0) {
                        name = "<unnamed>";
                    }
                    break;

                default:
                    name = e.getSimpleName();
                    break;
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
     * Returns an HtmlTree for the BODY tag.
     *
     * @param title title for the window
     * @return an HtmlTree for the BODY tag
     */
    public HtmlTree getBody(String title) {
        HtmlTree body = new HtmlTree(HtmlTag.BODY);
        body.put(HtmlAttr.CLASS, getBodyClass());

        this.winTitle = title;
        // Don't print windowtitle script for overview-frame, allclasses-frame
        // and package-frame
        body.add(mainBodyScript.asContent());
        Content noScript = HtmlTree.NOSCRIPT(HtmlTree.DIV(contents.noScriptMessage));
        body.add(noScript);
        return body;
    }

    public String getBodyClass() {
        return getClass().getSimpleName()
                .replaceAll("(Writer)?(Impl)?$", "")
                .replaceAll("AnnotationType", "Class")
                .replaceAll("(.)([A-Z])", "$1-$2")
                .replaceAll("(?i)^(module|package|class)$", "$1-declaration")
                .toLowerCase(Locale.US);
    }

    Script getMainBodyScript() {
        return mainBodyScript;
    }

    /**
     * Returns the path of module/package specific stylesheets for the element.
     * @param element module/Package element
     * @return list of path of module/package specific stylesheets
     * @throws DocFileIOException
     */
    List<DocPath> getLocalStylesheets(Element element) throws DocFileIOException {
        List<DocPath> stylesheets = new ArrayList<>();
        DocPath basePath = null;
        if (element instanceof PackageElement) {
            stylesheets.addAll(getModuleStylesheets((PackageElement)element));
            basePath = docPaths.forPackage((PackageElement)element);
        } else if (element instanceof ModuleElement) {
            basePath = DocPaths.forModule((ModuleElement)element);
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
            DocFilesHandlerImpl docFilesHandler = (DocFilesHandlerImpl)configuration
                    .getWriterFactory().getDocFilesHandler(element);
            localStylesheets = docFilesHandler.getStylesheets();
            configuration.localStylesheetMap.put(element, localStylesheets);
        }
        return localStylesheets;
    }

    Content getVerticalSeparator() {
        return HtmlTree.SPAN(HtmlStyle.verticalSeparator, new FixedStringContent("|"));
    }
}
