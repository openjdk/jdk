/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.Head;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;

import java.util.*;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor9;
import javax.lang.model.util.SimpleElementVisitor9;
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
import com.sun.source.doctree.TextTree;
import com.sun.source.util.SimpleDocTreeVisitor;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.DocType;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlDocument;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Links;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.Script;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
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

import static com.sun.source.doctree.DocTree.Kind.*;
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
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 * @author Bhavesh Patel (Modified)
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

    HtmlTree fixedNavDiv = new HtmlTree(HtmlTag.DIV);

    /**
     * The window title of this file.
     */
    protected String winTitle;

    protected Script mainBodyScript;

    /**
     * Constructor to construct the HtmlStandardWriter object.
     *
     * @param configuration the configuration for this doclet
     * @param path the file to be generated.
     */
    public HtmlDocletWriter(HtmlConfiguration configuration, DocPath path) {
        this.configuration = configuration;
        this.contents = configuration.contents;
        this.messages = configuration.messages;
        this.resources = configuration.resources;
        this.links = new Links(path, configuration.htmlVersion);
        this.utils = configuration.utils;
        this.path = path;
        this.pathToRoot = path.parent().invert();
        this.filename = path.basename();
        this.docPaths = configuration.docPaths;

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
            if (configuration.docrootparent.length() > 0 && htmlstr.startsWith("/..", prevEnd)) {
                // Insert the absolute link if {@docRoot} is followed by "/..".
                buf.append(configuration.docrootparent);
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
        // Note: {@docRoot} is not case sensitive when passed in w/command line option:
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
        div.addContent(noScript);
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
        if (configuration.nocomment) {
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
        dl.addContent(output);
        htmltree.addContent(dl);
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
     * @return a TagletWriter that knows how to write HTML.
     */
    public TagletWriter getTagletWriterInstance(boolean isFirstSentence) {
        return new TagletWriterImpl(this, isFirstSentence);
    }

    /**
     * Get Package link, with target frame.
     *
     * @param pkg The link will be to the "package-summary.html" page for this package
     * @param target name of the target frame
     * @param label tag for the link
     * @return a content for the target package link
     */
    public Content getTargetPackageLink(PackageElement pkg, String target,
            Content label) {
        return links.createLink(pathString(pkg, DocPaths.PACKAGE_SUMMARY), label, "", target);
    }

    /**
     * Get Module Package link, with target frame.
     *
     * @param pkg the PackageElement
     * @param target name of the target frame
     * @param label tag for the link
     * @param mdle the module being documented
     * @return a content for the target module packages link
     */
    public Content getTargetModulePackageLink(PackageElement pkg, String target,
            Content label, ModuleElement mdle) {
        return links.createLink(pathString(pkg, DocPaths.PACKAGE_SUMMARY),
                label, "", target);
    }

    /**
     * Get Module link, with target frame.
     *
     * @param target name of the target frame
     * @param label tag for the link
     * @param mdle the module being documented
     * @return a content for the target module link
     */
    public Content getTargetModuleLink(String target, Content label, ModuleElement mdle) {
        return links.createLink(pathToRoot.resolve(
                docPaths.moduleSummary(mdle)), label, "", target);
    }

    /**
     * Generates the HTML document tree and prints it out.
     *
     * @param metakeywords Array of String keywords for META tag. Each element
     *                     of the array is assigned to a separate META tag.
     *                     Pass in null for no array
     * @param includeScript true if printing windowtitle script
     *                      false for files that appear in the left-hand frames
     * @param body the body htmltree to be included in the document
     * @throws DocFileIOException if there is a problem writing the file
     */
    public void printHtmlDocument(List<String> metakeywords, boolean includeScript,
            Content body) throws DocFileIOException {
        DocType htmlDocType = DocType.forVersion(configuration.htmlVersion);
        Content htmlComment = contents.newPage;
        Head head = new Head(path, configuration.htmlVersion, configuration.docletVersion)
                .setTimestamp(!configuration.notimestamp)
                .setTitle(winTitle)
                .setCharset(configuration.charset)
                .addKeywords(metakeywords)
                .setStylesheets(configuration.getMainStylesheet(), configuration.getAdditionalStylesheets())
                .setUseModuleDirectories(configuration.useModuleDirectories)
                .setIndex(configuration.createindex, mainBodyScript);

        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(), head.toContent(), body);
        HtmlDocument htmlDocument = new HtmlDocument(htmlDocType, htmlComment, htmlTree);
        htmlDocument.write(DocFile.createFileForOutput(configuration, path));
    }

    /**
     * Get the window title.
     *
     * @param title the title string to construct the complete window title
     * @return the window title string
     */
    public String getWindowTitle(String title) {
        if (configuration.windowtitle.length() > 0) {
            title += " (" + configuration.windowtitle  + ")";
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
            content = replaceDocRootDir(configuration.header);
        } else {
            if (configuration.footer.length() != 0) {
                content = replaceDocRootDir(configuration.footer);
            } else {
                content = replaceDocRootDir(configuration.header);
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
        Content top = new RawHtml(replaceDocRootDir(configuration.top));
        fixedNavDiv.addContent(top);
    }

    /**
     * Adds the user specified bottom.
     *
     * @param htmlTree the content tree to which user specified bottom will be added
     */
    public void addBottom(Content htmlTree) {
        Content bottom = new RawHtml(replaceDocRootDir(configuration.bottom));
        Content small = HtmlTree.SMALL(bottom);
        Content p = HtmlTree.P(HtmlStyle.legalCopy, small);
        htmlTree.addContent(p);
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
        Content space = Contents.SPACE;
        Content tabSpan = HtmlTree.SPAN(HtmlStyle.tabEnd, space);
        Content caption = HtmlTree.CAPTION(captionSpan);
        caption.addContent(tabSpan);
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
            DocLink crossPkgLink = getCrossPackageLink(utils.getPackageName(packageElement));
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
            htmltree.addContent(content);
        } else {
            htmltree.addContent(label);
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
     * The name must be fully qualified to determine which package
     * the class is in.  The -link option does not allow users to
     * link to external classes in the "default" package.
     *
     * @param qualifiedClassName the qualified name of the external class.
     * @param refMemName the name of the member being referenced.  This should
     * be null or empty string if no member is being referenced.
     * @param label the label for the external link.
     * @param strong true if the link should be strong.
     * @param code true if the label should be code font.
     * @return the link
     */
    public Content getCrossClassLink(String qualifiedClassName, String refMemName,
                                    Content label, boolean strong, boolean code) {
        String className = "";
        String packageName = qualifiedClassName == null ? "" : qualifiedClassName;
        int periodIndex;
        while ((periodIndex = packageName.lastIndexOf('.')) != -1) {
            className = packageName.substring(periodIndex + 1, packageName.length()) +
                (className.length() > 0 ? "." + className : "");
            Content defaultLabel = new StringContent(className);
            if (code)
                defaultLabel = HtmlTree.CODE(defaultLabel);
            packageName = packageName.substring(0, periodIndex);
            if (getCrossPackageLink(packageName) != null) {
                /*
                The package exists in external documentation, so link to the external
                class (assuming that it exists).  This is definitely a limitation of
                the -link option.  There are ways to determine if an external package
                exists, but no way to determine if the external class exists.  We just
                have to assume that it does.
                */
                DocLink link = configuration.extern.getExternalLink(packageName, pathToRoot,
                                className + ".html", refMemName);
                return links.createLink(link,
                    (label == null) || label.isEmpty() ? defaultLabel : label,
                    strong,
                    resources.getText("doclet.Href_Class_Or_Interface_Title", packageName),
                    "", true);
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

    public DocLink getCrossPackageLink(String pkgName) {
        return configuration.extern.getExternalLink(pkgName, pathToRoot,
            DocPaths.PACKAGE_SUMMARY.getPath());
    }

    public DocLink getCrossModuleLink(String mdleName) {
        return configuration.extern.getExternalLink(mdleName, pathToRoot,
            docPaths.moduleSummary(mdleName).getPath());
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
            classlink.addContent(getEnclosingPackageName(typeElement));
        }
        classlink.addContent(getLink(new LinkInfoImpl(configuration,
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
            contentTree.addContent(getEnclosingPackageName(typeElement));
        }
        LinkInfoImpl linkinfo = new LinkInfoImpl(configuration, context, typeElement)
                .label(utils.getSimpleName(typeElement))
                .strong(isStrong);
        Content link = getLink(linkinfo);
        contentTree.addContent(link);
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
        if (! (utils.isIncluded(element) || utils.isLinkable(typeElement))) {
            return label;
        } else if (utils.isExecutableElement(element)) {
            ExecutableElement ee = (ExecutableElement)element;
            return getLink(new LinkInfoImpl(configuration, context, typeElement)
                .label(label)
                .where(links.getName(getAnchor(ee, isProperty)))
                .strong(strong));
        } else if (utils.isVariableElement(element) || utils.isTypeElement(element)) {
            return getLink(new LinkInfoImpl(configuration, context, typeElement)
                .label(label)
                .where(links.getName(element.getSimpleName().toString()))
                .strong(strong));
        } else {
            return label;
        }
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
        if (member.getKind() == ElementKind.CONSTRUCTOR
                && configuration.isOutputHtml5()) {
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
        Content label = plainOrCode(isLinkPlain, new RawHtml(ch.getLabel(configuration, see)));

        //The text from the @see tag.  We will output this text when a label is not specified.
        Content text = plainOrCode(kind == LINK_PLAIN, new RawHtml(seetext));

        TypeElement refClass = ch.getReferencedClass(configuration, see);
        String refClassName =  ch.getReferencedClassName(configuration, see);
        Element refMem =       ch.getReferencedMember(configuration, see);
        String refMemName =    ch.getReferencedMemberName(see);

        if (refMemName == null && refMem != null) {
            refMemName = refMem.toString();
        }
        if (refClass == null) {
            //@see is not referencing an included class
            PackageElement refPackage = ch.getReferencedPackage(configuration, see);
            if (refPackage != null && utils.isIncluded(refPackage)) {
                //@see is referencing an included package
                if (label.isEmpty())
                    label = plainOrCode(isLinkPlain,
                            new StringContent(refPackage.getQualifiedName()));
                return getPackageLink(refPackage, label);
            } else {
                // @see is not referencing an included class, module or package. Check for cross links.
                Content classCrossLink;
                DocLink elementCrossLink = (configuration.extern.isModule(refClassName))
                        ? getCrossModuleLink(refClassName) : getCrossPackageLink(refClassName);
                if (elementCrossLink != null) {
                    // Element cross link found
                    return links.createLink(elementCrossLink,
                            (label.isEmpty() ? text : label), true);
                } else if ((classCrossLink = getCrossClassLink(refClassName,
                        refMemName, label, false, !isLinkPlain)) != null) {
                    // Class cross link found (possibly to a member in the class)
                    return classCrossLink;
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
        List<? extends DocTree> description = ch.getDescription(configuration, tag);
        addCommentTags(element, tag, description, false, false, htmltree);
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
        addCommentTags(e, ch.getBody(configuration, tag), true, false, htmltree);
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
        addCommentTags(element, firstSentenceTags, false, true, htmltree);
    }

    public void addSummaryDeprecatedComment(Element element, DocTree tag, Content htmltree) {
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DocTree> body = ch.getBody(configuration, tag);
        addCommentTags(element, ch.getFirstSentenceTrees(configuration, body), true, true, htmltree);
    }

    /**
     * Adds the inline comment.
     *
     * @param element the Element for which the inline comments will be generated
     * @param htmltree the documentation tree to which the inline comments will be added
     */
    public void addInlineComment(Element element, Content htmltree) {
        addCommentTags(element, utils.getFullBody(element), false, false, htmltree);
    }

    /**
     * Adds the comment tags.
     *
     * @param element the Element for which the comment tags will be generated
     * @param tags the first sentence tags for the doc
     * @param depr true if it is deprecated
     * @param first true if the first sentence tags should be added
     * @param htmltree the documentation tree to which the comment tags will be added
     */
    private void addCommentTags(Element element, List<? extends DocTree> tags, boolean depr,
            boolean first, Content htmltree) {
        addCommentTags(element, null, tags, depr, first, htmltree);
    }

    /**
     * Adds the comment tags.
     *
     * @param element for which the comment tags will be generated
     * @param holderTag the block tag context for the inline tags
     * @param tags the first sentence tags for the doc
     * @param depr true if it is deprecated
     * @param first true if the first sentence tags should be added
     * @param htmltree the documentation tree to which the comment tags will be added
     */
    private void addCommentTags(Element element, DocTree holderTag, List<? extends DocTree> tags, boolean depr,
            boolean first, Content htmltree) {
        if(configuration.nocomment){
            return;
        }
        Content div;
        Content result = commentTagsToContent(null, element, tags, first);
        if (depr) {
            div = HtmlTree.DIV(HtmlStyle.deprecationComment, result);
            htmltree.addContent(div);
        }
        else {
            div = HtmlTree.DIV(HtmlStyle.block, result);
            htmltree.addContent(div);
        }
        if (tags.isEmpty()) {
            htmltree.addContent(Contents.SPACE);
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
     * Converts inline tags and text to text strings, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to non-inline tags.
     *
     * @param holderTag    specific tag where comment resides
     * @param element    specific element where comment resides
     * @param tags   array of text tags and inline tags (often alternating)
               present in the text of interest for this element
     * @param isFirstSentence  true if text is first sentence
     * @return a Content object
     */
    public Content commentTagsToContent(DocTree holderTag, Element element,
            List<? extends DocTree> tags, boolean isFirstSentence) {

        final Content result = new ContentBuilder() {
            @Override
            public void addContent(CharSequence text) {
                super.addContent(utils.normalizeNewlines(text));
            }
        };
        CommentHelper ch = utils.getCommentHelper(element);
        // Array of all possible inline tags for this javadoc run
        configuration.tagletManager.checkTags(element, tags, true);
        commentRemoved = false;

        for (ListIterator<? extends DocTree> iterator = tags.listIterator(); iterator.hasNext();) {
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
                        result.addContent(sb);
                        return false;
                    }
                    sb.append("=");
                    String quote;
                    switch (node.getValueKind()) {
                        case DOUBLE:
                            quote = "\"";
                            break;
                        case SINGLE:
                            quote = "\'";
                            break;
                        default:
                            quote = "";
                            break;
                    }
                    sb.append(quote);
                    result.addContent(sb);
                    Content docRootContent = new ContentBuilder();

                    boolean isHRef = inAnAtag() && node.getName().toString().equalsIgnoreCase("href");
                    for (DocTree dt : node.getValue()) {
                        if (utils.isText(dt) && isHRef) {
                            String text = ((TextTree) dt).getBody();
                            if (text.startsWith("/..") && !configuration.docrootparent.isEmpty()) {
                                result.addContent(configuration.docrootparent);
                                docRootContent = new ContentBuilder();
                                result.addContent(textCleanup(text.substring(3), isLastNode));
                            } else {
                                if (!docRootContent.isEmpty()) {
                                    docRootContent = copyDocRootContent(docRootContent);
                                } else {
                                    text = redirectRelativeLinks(element, (TextTree) dt);
                                }
                                result.addContent(textCleanup(text, isLastNode));
                            }
                        } else {
                            docRootContent = copyDocRootContent(docRootContent);
                            dt.accept(this, docRootContent);
                        }
                    }
                    copyDocRootContent(docRootContent);
                    result.addContent(quote);
                    return false;
                }

                @Override
                public Boolean visitComment(CommentTree node, Content c) {
                    result.addContent(new RawHtml(node.getBody()));
                    return false;
                }

                private Content copyDocRootContent(Content content) {
                    if (!content.isEmpty()) {
                        result.addContent(content);
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
                        c.addContent(docRootContent);
                    } else {
                        result.addContent(docRootContent);
                    }
                    return false;
                }

                @Override
                public Boolean visitEndElement(EndElementTree node, Content c) {
                    RawHtml rawHtml = new RawHtml("</" + node.getName() + ">");
                    result.addContent(rawHtml);
                    return false;
                }

                @Override
                public Boolean visitEntity(EntityTree node, Content c) {
                    result.addContent(new RawHtml(node.toString()));
                    return false;
                }

                @Override
                public Boolean visitErroneous(ErroneousTree node, Content c) {
                    messages.warning(ch.getDocTreePath(node),
                            "doclet.tag.invalid_usage", node);
                    result.addContent(new RawHtml(node.toString()));
                    return false;
                }

                @Override
                public Boolean visitInheritDoc(InheritDocTree node, Content c) {
                    Content output = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager, holderTag,
                            tag, getTagletWriterInstance(isFirstSentence));
                    result.addContent(output);
                    // if we obtained the first sentence successfully, nothing more to do
                    return (isFirstSentence && !output.isEmpty());
                }

                @Override
                public Boolean visitIndex(IndexTree node, Content p) {
                    Content output = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager, holderTag, tag,
                            getTagletWriterInstance(isFirstSentence));
                    if (output != null) {
                        result.addContent(output);
                    }
                    return false;
                }

                @Override
                public Boolean visitLink(LinkTree node, Content c) {
                    // we need to pass the DocTreeImpl here, so ignore node
                    result.addContent(seeTagToContent(element, tag));
                    return false;
                }

                @Override
                public Boolean visitLiteral(LiteralTree node, Content c) {
                    String s = node.getBody().getBody();
                    Content content = new StringContent(utils.normalizeNewlines(s));
                    if (node.getKind() == CODE)
                        content = HtmlTree.CODE(content);
                    result.addContent(content);
                    return false;
                }

                @Override
                public Boolean visitSee(SeeTree node, Content c) {
                    // we need to pass the DocTreeImpl here, so ignore node
                    result.addContent(seeTagToContent(element, tag));
                    return false;
                }

                @Override
                public Boolean visitStartElement(StartElementTree node, Content c) {
                    String text = "<" + node.getName();
                    RawHtml rawHtml = new RawHtml(utils.normalizeNewlines(text));
                    result.addContent(rawHtml);

                    for (DocTree dt : node.getAttributes()) {
                        dt.accept(this, null);
                    }
                    result.addContent(new RawHtml(node.isSelfClosing() ? "/>" : ">"));
                    return false;
                }

                @Override
                public Boolean visitSummary(SummaryTree node, Content c) {
                    Content output = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager, holderTag, tag,
                            getTagletWriterInstance(isFirstSentence));
                    result.addContent(output);
                    return false;
                }

                private CharSequence textCleanup(String text, boolean isLast) {
                    return textCleanup(text, isLast, false);
                }

                private CharSequence textCleanup(String text, boolean isLast, boolean trimLeader) {
                    if (trimLeader) {
                        text = removeLeadingWhitespace(text);
                    }
                    if (isFirstSentence && isLast) {
                        text = removeTrailingWhitespace(text);
                    }
                    text = utils.replaceTabs(text);
                    return utils.normalizeNewlines(text);
                }

                @Override
                public Boolean visitText(TextTree node, Content c) {
                    String text = node.getBody();
                    result.addContent(new RawHtml(textCleanup(text, isLastNode, commentRemoved)));
                    return false;
                }

                @Override
                protected Boolean defaultAction(DocTree node, Content c) {
                    Content output = TagletWriter.getInlineTagOutput(element,
                            configuration.tagletManager, holderTag, tag,
                            getTagletWriterInstance(isFirstSentence));
                    if (output != null) {
                        result.addContent(output);
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

    private String removeTrailingWhitespace(String text) {
        char[] buf = text.toCharArray();
        for (int i = buf.length - 1; i > 0 ; i--) {
            if (!Character.isWhitespace(buf[i]))
                return text.substring(0, i + 1);
        }
        return text;
    }

    private String removeLeadingWhitespace(String text) {
        char[] buf = text.toCharArray();
        for (int i = 0; i < buf.length; i++) {
            if (!Character.isWhitespace(buf[i])) {
                return text.substring(i);
            }
        }
        return text;
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
     * {@literal <a href="./com/sun/javadoc/package-summary.html">The package Page</a>}
     *
     * @param element the Element object whose documentation is being written.
     * @param tt the text being written.
     *
     * @return the text, with all the relative links redirected to work.
     */
    private String redirectRelativeLinks(Element element, TextTree tt) {
        String text = tt.getBody();
        if (element == null || utils.isOverviewElement(element) || shouldNotRedirectRelativeLinks()) {
            return text;
        }

        DocPath redirectPathFromRoot = new SimpleElementVisitor9<DocPath, Void>() {
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
        addAnnotationInfo(packageElement, packageElement.getAnnotationMirrors(), htmltree);
    }

    /**
     * Add the annotation types of the executable receiver.
     *
     * @param method the executable to write the receiver annotations for.
     * @param descList a list of annotation mirrors.
     * @param htmltree the documentation tree to which the annotation info will be
     *        added
     */
    public void addReceiverAnnotationInfo(ExecutableElement method, List<AnnotationMirror> descList,
            Content htmltree) {
        addAnnotationInfo(0, method, descList, false, htmltree);
    }

    /*
     * this is a hack to delay dealing with Annotations in the writers, the assumption
     * is that all necessary checks have been made to get here.
     */
    public void addReceiverAnnotationInfo(ExecutableElement method, TypeMirror rcvrTypeMirror,
            List<? extends AnnotationMirror> annotationMirrors, Content htmltree) {
        TypeMirror rcvrType = method.getReceiverType();
        List<? extends AnnotationMirror> annotationMirrors1 = rcvrType.getAnnotationMirrors();
        addAnnotationInfo(0, method, annotationMirrors1, false, htmltree);
    }

    /**
     * Adds the annotation types for the given element.
     *
     * @param element the package to write annotations for
     * @param htmltree the content tree to which the annotation types will be added
     */
    public void addAnnotationInfo(Element element, Content htmltree) {
        addAnnotationInfo(element, element.getAnnotationMirrors(), htmltree);
    }

    /**
     * Add the annotatation types for the given element and parameter.
     *
     * @param indent the number of spaces to indent the parameters.
     * @param element the element to write annotations for.
     * @param param the parameter to write annotations for.
     * @param tree the content tree to which the annotation types will be added
     */
    public boolean addAnnotationInfo(int indent, Element element, VariableElement param,
            Content tree) {
        return addAnnotationInfo(indent, element, param.getAnnotationMirrors(), false, tree);
    }

    /**
     * Adds the annotatation types for the given Element.
     *
     * @param element the element to write annotations for.
     * @param descList a list of annotation mirrors.
     * @param htmltree the documentation tree to which the annotation info will be
     *        added
     */
    private void addAnnotationInfo(Element element, List<? extends AnnotationMirror> descList,
            Content htmltree) {
        addAnnotationInfo(0, element, descList, true, htmltree);
    }

    /**
     * Adds the annotation types for the given element.
     *
     * @param indent the number of extra spaces to indent the annotations.
     * @param element the element to write annotations for.
     * @param descList a list of annotation mirrors.
     * @param htmltree the documentation tree to which the annotation info will be
     *        added
     */
    private boolean addAnnotationInfo(int indent, Element element,
            List<? extends AnnotationMirror> descList, boolean lineBreak, Content htmltree) {
        List<Content> annotations = getAnnotations(indent, descList, lineBreak);
        String sep = "";
        if (annotations.isEmpty()) {
            return false;
        }
        for (Content annotation: annotations) {
            htmltree.addContent(sep);
            htmltree.addContent(annotation);
            if (!lineBreak) {
                sep = " ";
            }
        }
        return true;
    }

   /**
     * Return the string representations of the annotation types for
     * the given doc.
     *
     * @param indent the number of extra spaces to indent the annotations.
     * @param descList a list of annotation mirrors.
     * @param linkBreak if true, add new line between each member value.
     * @return a list of strings representing the annotations being
     *         documented.
     */
    private List<Content> getAnnotations(int indent, List<? extends AnnotationMirror> descList, boolean linkBreak) {
        return getAnnotations(indent, descList, linkBreak, true);
    }

    private List<Content> getAnnotations(int indent, AnnotationMirror amirror, boolean linkBreak) {
        List<AnnotationMirror> descList = new ArrayList<>();
        descList.add(amirror);
        return getAnnotations(indent, descList, linkBreak, true);
    }

    /**
     * Return the string representations of the annotation types for
     * the given doc.
     *
     * A {@code null} {@code elementType} indicates that all the
     * annotations should be returned without any filtering.
     *
     * @param indent the number of extra spaces to indent the annotations.
     * @param descList a list of annotation mirrors.
     * @param linkBreak if true, add new line between each member value.
     * @param isJava5DeclarationLocation
     * @return a list of strings representing the annotations being
     *         documented.
     */
    public List<Content> getAnnotations(int indent, List<? extends AnnotationMirror> descList,
            boolean linkBreak, boolean isJava5DeclarationLocation) {
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
            /* TODO: check logic here to correctly handle declaration
             * and type annotations.
            if  (utils.isDeclarationAnnotation(annotationElement, isJava5DeclarationLocation)) {
                continue;
            }*/
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
                        annotation.addContent(sep);
                        annotation.addContent(annotationValueToContent(av));
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
                               for (AnnotationValue av : vals) {
                                   annotationTypeValues.add(av);
                               }
                               return null;
                            }
                        }.visit(a, annotationTypeValues);
                    }
                    String sep = "";
                    for (AnnotationValue av : annotationTypeValues) {
                        annotation.addContent(sep);
                        annotation.addContent(annotationValueToContent(av));
                        sep = " ";
                    }
                }
                // If the container has 1 or more value defined and if the
                // repeatable type annotation is not documented, print the container.
                else {
                    addAnnotations(annotationElement, linkInfo, annotation, pairs,
                                   indent, false);
                }
            }
            else {
                addAnnotations(annotationElement, linkInfo, annotation, pairs,
                               indent, linkBreak);
            }
            annotation.addContent(linkBreak ? DocletConstants.NL : "");
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
     * @param indent the number of extra spaces to indent the annotations.
     * @param linkBreak if true, add new line between each member value
     */
    private void addAnnotations(TypeElement annotationDoc, LinkInfoImpl linkInfo,
                                ContentBuilder annotation,
                                Map<? extends ExecutableElement, ? extends AnnotationValue> map,
                                int indent, boolean linkBreak) {
        linkInfo.label = new StringContent("@");
        linkInfo.label.addContent(annotationDoc.getSimpleName());
        annotation.addContent(getLink(linkInfo));
        if (!map.isEmpty()) {
            annotation.addContent("(");
            boolean isFirst = true;
            Set<? extends ExecutableElement> keys = map.keySet();
            boolean multipleValues = keys.size() > 1;
            for (ExecutableElement element : keys) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    annotation.addContent(",");
                    if (linkBreak) {
                        annotation.addContent(DocletConstants.NL);
                        int spaces = annotationDoc.getSimpleName().length() + 2;
                        for (int k = 0; k < (spaces + indent); k++) {
                            annotation.addContent(" ");
                        }
                    }
                }
                String simpleName = element.getSimpleName().toString();
                if (multipleValues || !"value".equals(simpleName)) { // Omit "value=" where unnecessary
                    annotation.addContent(getDocLink(LinkInfoImpl.Kind.ANNOTATION,
                                                     element, simpleName, false));
                    annotation.addContent("=");
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
                annotation.addContent(annotationTypeValues.size() == 1 ? "" : "{");
                String sep = "";
                for (AnnotationValue av : annotationTypeValues) {
                    annotation.addContent(sep);
                    annotation.addContent(annotationValueToContent(av));
                    sep = ",";
                }
                annotation.addContent(annotationTypeValues.size() == 1 ? "" : "}");
                isContainerDocumented = false;
            }
            annotation.addContent(")");
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
                List<Content> list = getAnnotations(0, a, false);
                ContentBuilder buf = new ContentBuilder();
                for (Content c : list) {
                    buf.addContent(c);
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
                    buf.addContent(sep);
                    buf.addContent(visit(av));
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
     * Returns an HtmlTree for the SCRIPT tag.
     *
     * @return an HtmlTree for the SCRIPT tag
     */
    protected Script getWinTitleScript() {
        Script script = new Script();
        if (winTitle != null && winTitle.length() > 0) {
            script.append("<!--\n" +
                    "    try {\n" +
                    "        if (location.href.indexOf('is-external=true') == -1) {\n" +
                    "            parent.document.title=")
                    .appendStringLiteral(winTitle)
                    .append(";\n" +
                    "        }\n" +
                    "    }\n" +
                    "    catch(err) {\n" +
                    "    }\n" +
                    "//-->\n");
        }
        return script;
    }

    /**
     * Returns an HtmlTree for the BODY tag.
     *
     * @param includeScript  set true if printing windowtitle script
     * @param title title for the window
     * @return an HtmlTree for the BODY tag
     */
    public HtmlTree getBody(boolean includeScript, String title) {
        HtmlTree body = new HtmlTree(HtmlTag.BODY);
        // Set window title string which is later printed
        this.winTitle = title;
        // Don't print windowtitle script for overview-frame, allclasses-frame
        // and package-frame
        if (includeScript) {
            this.mainBodyScript = getWinTitleScript();
            body.addContent(mainBodyScript.asContent());
            Content noScript = HtmlTree.NOSCRIPT(HtmlTree.DIV(contents.noScriptMessage));
            body.addContent(noScript);
        }
        return body;
    }

    Script getMainBodyScript() {
        return mainBodyScript;
    }
}
