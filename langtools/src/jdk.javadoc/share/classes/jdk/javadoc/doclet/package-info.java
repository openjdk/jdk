/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * libraries, including javadoc comments embedded in the source.
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
 * The {@link jdk.javadoc.doclet.DocletEnvironment} instance holds the environment that the
 * doclet will be initialized with. From this environment all other information can be
 * extracted, in the form of {@link javax.lang.model} elements. One can further use the
 * APIs and utilities described by {@link javax.lang.model} to query Elements and Types.
 * <p>
 *
 * <a name="terminology"></a>
 * <h3>Terminology</h3>
 *
 * <a name="included"></a>
 * When calling javadoc, one can pass in package names and source file names --
 * these are called the <em>specified</em> PackageElements and TypeElements.
 * Javadoc options are also passed in; the <em>access control</em> Javadoc options
 * ({@code -public}, {@code -protected}, {@code -package},
 * and {@code -private}) are used to filter program elements, producing a
 * result set, called the <em>included</em> set, or "selected" set.
 * <p>

 * <a name="qualified"></a>
 * A <em>qualified</em> element name is one that has its package
 * name prepended to it, such as {@code java.lang.String}.  A non-qualified
 * name has no package name, such as {@code String}.
 * <p>
 *
 * <a name="example"></a>
 * <h3>Example</h3>
 *
 * The following is an example doclet that displays information of a class
 * and its members, supporting an option "someoption".
 * <pre>
 * import com.sun.source.doctree.DocCommentTree;
 * import com.sun.source.util.DocTrees;
 * import java.io.IOException;
 * import java.util.Collections;
 * import java.util.Set;
 * import javax.lang.model.SourceVersion;
 * import javax.lang.model.element.Element;
 * import javax.lang.model.element.TypeElement;
 * import jdk.javadoc.doclet.*;
 *
 * public class Example implements Doclet {
 *
 *     &#64;Override
 *     public void init(Locale locale, Reporter reporter) {
 *        return;
 *     }
 *
 *     &#64;Override
 *     public boolean run(RootDoc root) {
 *         // cache the DocTrees utility class to access DocComments
 *         DocTrees docTrees = root.getDocTrees();
 *
 *         // location of an element in the same directory as overview.html
 *         try {
 *             Element barElement = null;
 *             for (Element e : root.getIncludedClasses()) {
 *                 if (e.getSimpleName().toString().equals("FooBar")) {
 *                     barElement = e;
 *                 }
 *             }
 *             DocCommentTree docCommentTree =
 *                     docTrees.getDocCommentTree(barElement, "overview.html");
 *             if (docCommentTree != null) {
 *                 System.out.println("Overview html: " +
 *                         docCommentTree.getFullBody());
 *             }
 *         } catch (IOException missing) {
 *             System.err.println("No overview.html found.");
 *         }
 *
 *         for (TypeElement t : root.getIncludedClasses()) {
 *             System.out.println(t.getKind() + ":" + t);
 *             for (Element e : t.getEnclosedElements()) {
 *                 DocCommentTree docCommentTree = docTrees.getDocCommentTree(e);
 *                 if (docCommentTree != null) {
 *                     System.out.println("Element (" + e.getKind() + ": " +
 *                             e + ") has the following comments:");
 *                     System.out.println("Entire body: " + docCommentTree.getFullBody());
 *                     System.out.println("Block tags: " + docCommentTree.getBlockTags());
 *                 } else {
 *                     System.out.println("no comment.");
 *                 }
 *             }
 *         }
 *         return true;
 *     }
 *
 *     &#64;Override
 *     public String getName() {
 *         return "Example";
 *     }
 *
 *   private String someOption;
 *
 *   &#64;Override
 *   public Set&lt;Option&gt; getSupportedOptions() {
 *       Option[] options = {
 *           new Option() {
 *               public int getArgumentCount() {
 *                   return 1;
 *               }
 *               public String getDescription() {
 *                   return "someoption";
 *               }
 *               public Option.Kind getKind() {
 *                   return Option.Kind.STANDARD;
 *               }
 *               public String getName() {
 *                   return "someoption";
 *               }
 *               public String getParameters() {
 *                   return "url";
 *               }
 *               public boolean matches(String option) {
 *                  String opt = option.startsWith("-") ? option.substring(1) : option;
 *                  return getName().equals(opt);
 *               }
 *               public boolean process(String option, ListIterator&lt;String&gt; arguments) {
 *                  overviewpath = arguments.next();
 *                  return true;
 *               }
 *          }
 *      };
 *      return new HashSet&lt;Option&gt;(Arrays.asList(options));
 *     }
 *
 *     &#64;Override
 *     public SourceVersion getSupportedSourceVersion() {
 *         // support the latest release
 *         return SourceVersion.latest();
 *     }
 * }
 * </pre>
 * <p>
 * This doclet when invoked with a command line, such as:
 * <pre>
 *     javadoc -doclet Example -sourcepath &lt;source-location&gt;
 * </pre>
 * will produce an output, such as:
 * <pre>
 *  Overview.html: overview comments
 *  ...
 *  ...
 *  CLASS: SomeKlass
 *  .....
 *  Element (METHOD: main(java.lang.String...)) has the following comments:
 *  Entire body: The main entry point.
 *  Block tags: @param an array of Strings
 *  ...
 * </pre>
 *
 * <h3><a name="migration">Migration Guide</a></h3>
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
