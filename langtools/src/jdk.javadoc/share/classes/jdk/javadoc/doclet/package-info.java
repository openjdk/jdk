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
 * The Doclet API provides an environment which, in conjunction with
 * the Language Model API and Compiler Tree API, allows clients
 * to inspect the source-level structures of programs and
 * libraries, including API comments embedded in the source.
 *
 * <p>
 * The {@link StandardDoclet standard doclet} can be used to
 * generate HTML-formatted documentation. It supports user-defined
 * {@link Taglet taglets}, which can be used to generate customized
 * output for user-defined tags in documentation comments.
 *
 * <p style="font-style: italic">
 * <b>Note:</b> The declarations in this package supersede those
 * in the older package {@code com.sun.javadoc}. For details on the
 * mapping of old types to new types, see the
 * <a href="#migration">Migration Guide</a>.
 * </p>
 *
 * <p>
 * Doclets are invoked by javadoc and this API can be used to write out
 * program information to files.  For example, the standard doclet is
 * invoked by default, to generate HTML documentation.
 * <p>

 * The invocation is defined by the interface {@link jdk.javadoc.doclet.Doclet}
 * -- the {@link jdk.javadoc.doclet.Doclet#run(DocletEnvironment) run} interface
 * method, defines the entry point.
 * <pre>
 *    public boolean <b>run</b>(DocletEnvironment environment)
 * </pre>
 * The {@link jdk.javadoc.doclet.DocletEnvironment} instance holds the
 * environment that the doclet will be initialized with. From this environment
 * all other information can be extracted, in the form of
 * {@link javax.lang.model.element.Element elements}. One can further use the APIs and utilities
 * described by {@link javax.lang.model Language Model API} to query Elements and Types.
 * <p>
 *
 * <a id="terminology"></a>
 * <h3>Terminology</h3>
 *
 * <dl>
 *   <dt><a id="selected"></a>Selected</dt>
 *     <dd>An element is considered to be <em>selected</em>, if the
 *         <em>selection controls</em> <a href="#options">allow</a> it
 *         to be documented. (Note that synthetic elements are never
 *         selected.)
 *    </dd>
 *
 *   <dt><a id="specified"></a>Specified</dt>
 *   <dd>The set of elements specified by the user are considered to be <em>specified
 *       elements</em>. Specified elements provide the starting points
 *       for determining the <em>included elements</em> to be documented.
 *   </dd>
 *
 *   <dt><a id="included"></a>Included</dt>
 *   <dd>An element is considered to be <em>included</em>, if it is
 *       <em>specified</em> if it contains a <em>specified</em> element,
 *       or it is enclosed in a <em>specified</em> element, and is <em>selected</em>.
 *       Included elements will be documented.
 *   </dd>
 *
 * </dl>
 * <p>
 * <a id="options"></a>
 * <h3>Options</h3>
 * Javadoc <em>selection control</em> can be specified with these options
 * as follows:
 * <ul>
 *   <li>{@code --show-members:value} and {@code --show-types:value} can
 *       be used to filter the members, with the following values:
 *   <ul>
 *     <li> public    -- considers only public elements
 *     <li> protected -- considers public and protected elements
 *     <li> package   -- considers public, protected and package private elements
 *     <li> private   -- considers all elements
 *   </ul>
 *
 *   <li>{@code --show-packages:value} "exported" or "all" can be used
 *       to consider only exported packages or all packages within a module.
 *
 *   <li>{@code --show-module-contents:value} can be used to specify the level at
 *       module declarations could be documented. A value of "api" indicates API
 *       level documentation, and "all" indicates detailed documentation.
 * </ul>
 * The following options can be used to specify the elements to be documented:
 * <ul>
 *   <li>{@code --module} documents the specified modules.
 *
 *   <li>{@code --expand-requires:value} expand the set of modules to be documented
 *        by including some or all of the modules dependencies. The value may be
 *        one of:
 *   <ul>
 *     <li> transitive -- each module specified explicitly on the command line is
 *          expanded to include the closure of its transitive dependencies
 *     <li> all    -- each module specified explicitly on the command line
 *          is expanded to include the closure of its transitive dependencies,
 *          and also all of its direct dependencies
 *   </ul>
 *   By default, only the specified modules will be considered, without expansion
 *   of the module dependencies.
 *
 *   <li>{@code packagenames} can be used to specify packages.
 *   <li>{@code -subpackages} can be used to recursively load packages.
 *   <li>{@code -exclude} can be used exclude package directories.
 *   <li>{@code sourcefilenames} can be used to specify source file names.
 * </ul>
 * <p>
 * <a id="legacy-interactions"></a>
 * <h4>Interactions with older options.</h4>
 *
 * The new {@code --show-*} options provide a more detailed replacement
 * for the older options -public, -protected, -package, -private.
 * Alternatively, the older options can continue to be used as shorter
 * forms for combinations of the new options, as described below:
 <table style="font-family: monospace" border=1>
    <caption>Short form options mapping</caption>
    <tr><th>Older option<th colspan="5">Equivalent to these values with the new option
    <tr><th><th>{@code --show-members}<th>{@code --show-types}<th>{@code --show-packages}<th>{@code --show-module-contents}
    <tr><td>{@code -public}<td>public<td>public<td>exported<td>api
    <tr><td>{@code -protected}<td>protected<td>protected<td>exported<td>api
    <tr><td>{@code -package}<td>package<td>package<td>all<td>all
    <tr><td>{@code -private}<td>private<td>private<td>all<td>all
  </table>
 * <p>
 * <a id="qualified"></a>
 * A <em>qualified</em> element name is one that has its package
 * name prepended to it, such as {@code java.lang.String}.  A non-qualified
 * name has no package name, such as {@code String}.
 * <p>
 *
 * <a id="example"></a>
 * <h3>Example</h3>
 *
 * The following is an example doclet that displays information of a class
 * and its members, supporting an option.
 * <pre>
 * // note imports deleted for clarity
 * public class Example implements Doclet {
 *    Reporter reporter;
 *    &#64;Override
 *    public void init(Locale locale, Reporter reporter) {
 *        reporter.print(Kind.NOTE, "Doclet using locale: " + locale);
 *        this.reporter = reporter;
 *    }
 *
 *    public void printElement(DocTrees trees, Element e) {
 *        DocCommentTree docCommentTree = trees.getDocCommentTree(e);
 *        if (docCommentTree != null) {
 *            System.out.println("Element (" + e.getKind() + ": "
 *                    + e + ") has the following comments:");
 *            System.out.println("Entire body: " + docCommentTree.getFullBody());
 *            System.out.println("Block tags: " + docCommentTree.getBlockTags());
 *        }
 *    }
 *
 *    &#64;Override
 *    public boolean run(DocletEnvironment docEnv) {
 *        reporter.print(Kind.NOTE, "overviewfile: " + overviewfile);
 *        // get the DocTrees utility class to access document comments
 *        DocTrees docTrees = docEnv.getDocTrees();
 *
 *        // location of an element in the same directory as overview.html
 *        try {
 *            Element e = ElementFilter.typesIn(docEnv.getSpecifiedElements()).iterator().next();
 *            DocCommentTree docCommentTree
 *                    = docTrees.getDocCommentTree(e, overviewfile);
 *            if (docCommentTree != null) {
 *                System.out.println("Overview html: " + docCommentTree.getFullBody());
 *            }
 *        } catch (IOException missing) {
 *            reporter.print(Kind.ERROR, "No overview.html found.");
 *        }
 *
 *        for (TypeElement t : ElementFilter.typesIn(docEnv.getIncludedElements())) {
 *            System.out.println(t.getKind() + ":" + t);
 *            for (Element e : t.getEnclosedElements()) {
 *                printElement(docTrees, e);
 *            }
 *        }
 *        return true;
 *    }
 *
 *    &#64;Override
 *    public String getName() {
 *        return "Example";
 *    }
 *
 *    private String overviewfile;
 *
 *    &#64;Override
 *    public Set&lt;? extends Option&gt; getSupportedOptions() {
 *        Option[] options = {
 *            new Option() {
 *                private final List&lt;String&gt; someOption = Arrays.asList(
 *                        "-overviewfile",
 *                        "--overview-file",
 *                        "-o"
 *                );
 *
 *                &#64;Override
 *                public int getArgumentCount() {
 *                    return 1;
 *                }
 *
 *                &#64;Override
 *                public String getDescription() {
 *                    return "an option with aliases";
 *                }
 *
 *                &#64;Override
 *                public Option.Kind getKind() {
 *                    return Option.Kind.STANDARD;
 *                }
 *
 *                &#64;Override
 *                public List&lt;String&gt; getNames() {
 *                    return someOption;
 *                }
 *
 *                &#64;Override
 *                public String getParameters() {
 *                    return "file";
 *                }
 *
 *                &#64;Override
 *                public boolean process(String opt, List&lt;String&gt; arguments) {
 *                    overviewfile = arguments.get(0);
 *                    return true;
 *                }
 *            }
 *        };
 *        return new HashSet&lt;&gt;(Arrays.asList(options));
 *    }
 *
 *    &#64;Override
 *    public SourceVersion getSupportedSourceVersion() {
 *        // support the latest release
 *        return SourceVersion.latest();
 *    }
 * }
 * </pre>
 * <p>
 * This doclet can be invoked with a command line, such as:
 * <pre>
 *     javadoc -doclet Example &#92;
 *       -overviewfile overview.html &#92;
 *       -sourcepath source-location &#92;
 *       source-location/Example.java
 * </pre>
 *
 * <h3><a id="migration">Migration Guide</a></h3>
 *
 * <p>Many of the types in the old {@code com.sun.javadoc} API do not have equivalents in this
 * package. Instead, types in the {@code javax.lang.model} and {@code com.sun.source} APIs
 * are used instead.
 *
 * <p>The following table gives a guide to the mapping from old types to their replacements.
 * In some cases, there is no direct equivalent.
 *
 * <table style="font-family: monospace" border=1>
    <caption>Guide for mapping old types to new types</caption>
    <tr><th>Old Type<th>New Type
    <tr><td>AnnotatedType<td>javax.lang.model.type.Type
    <tr><td>AnnotationDesc<td>javax.lang.model.element.AnnotationMirror
    <tr><td>AnnotationDesc.ElementValuePair<td>javax.lang.model.element.AnnotationValue
    <tr><td>AnnotationTypeDoc<td>javax.lang.model.element.TypeElement
    <tr><td>AnnotationTypeElementDoc<td>javax.lang.model.element.ExecutableElement
    <tr><td>AnnotationValue<td>javax.lang.model.element.AnnotationValue
    <tr><td>ClassDoc<td>javax.lang.model.element.TypeElement
    <tr><td>ConstructorDoc<td>javax.lang.model.element.ExecutableElement
    <tr><td>Doc<td>javax.lang.model.element.Element
    <tr><td>DocErrorReporter<td>jdk.javadoc.doclet.Reporter
    <tr><td>Doclet<td>jdk.javadoc.doclet.Doclet
    <tr><td>ExecutableMemberDoc<td>javax.lang.model.element.ExecutableElement
    <tr><td>FieldDoc<td>javax.lang.model.element.VariableElement
    <tr><td>LanguageVersion<td>javax.lang.model.SourceVersion
    <tr><td>MemberDoc<td>javax.lang.model.element.Element
    <tr><td>MethodDoc<td>javax.lang.model.element.ExecutableElement
    <tr><td>PackageDoc<td>javax.lang.model.element.PackageElement
    <tr><td>Parameter<td>javax.lang.model.element.VariableElement
    <tr><td>ParameterizedType<td>javax.lang.model.type.DeclaredType
    <tr><td>ParamTag<td>com.sun.source.doctree.ParamTree
    <tr><td>ProgramElementDoc<td>javax.lang.model.element.Element
    <tr><td>RootDoc<td>jdk.javadoc.doclet.DocletEnvironment
    <tr><td>SeeTag<td>com.sun.source.doctree.LinkTree<br>com.sun.source.doctree.SeeTree
    <tr><td>SerialFieldTag<td>com.sun.source.doctree.SerialFieldTree
    <tr><td>SourcePosition<td>com.sun.source.util.SourcePositions
    <tr><td>Tag<td>com.sun.source.doctree.DocTree
    <tr><td>ThrowsTag<td>com.sun.source.doctree.ThrowsTree
    <tr><td>Type<td>javax.lang.model.type.Type
    <tr><td>TypeVariable<td>javax.lang.model.type.TypeVariable
    <tr><td>WildcardType<td>javax.lang.model.type.WildcardType
 * </table>
 *
 * @see jdk.javadoc.doclet.Doclet
 * @see jdk.javadoc.doclet.DocletEnvironment
 * @since 9
*/

package jdk.javadoc.doclet;
