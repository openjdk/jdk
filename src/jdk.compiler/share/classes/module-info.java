/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.tools.JavaCompiler;
import javax.tools.StandardLocation;

/**
 * Defines the implementation of the
 * {@linkplain javax.tools.ToolProvider#getSystemJavaCompiler system Java compiler}
 * and its command line equivalent, <em>{@index javac javac tool}</em>.
 *
 * <p>The {@code com.sun.source.*} packages provide the {@index "Compiler Tree API"}:
 * an API for accessing the abstract trees (ASTs) representing Java source code
 * and documentation comments, used by <em>javac</em>, <em>javadoc</em> and related tools.
 *
 * <h2 style="font-family:'DejaVu Sans Mono', monospace; font-style:italic">javac</h2>
 *
 * <p>
 * This module provides the equivalent of command-line access to <em>javac</em>
 * via the {@link java.util.spi.ToolProvider ToolProvider} and
 * {@link javax.tools.Tool} service provider interfaces (SPIs),
 * and more flexible access via the {@link javax.tools.JavaCompiler JavaCompiler}
 * SPI.</p>
 *
 * <p> Instances of the tools can be obtained by calling
 * {@link java.util.spi.ToolProvider#findFirst ToolProvider.findFirst}
 * or the {@linkplain java.util.ServiceLoader service loader} with the name
 * {@code "javac"}.
 *
 * <p>
 * In addition, instances of {@link javax.tools.JavaCompiler.CompilationTask}
 * obtained from {@linkplain javax.tools.JavaCompiler JavaCompiler} can be
 * downcast to {@link com.sun.source.util.JavacTask JavacTask} for access to
 * lower level aspects of <em>javac</em>, such as the
 * {@link com.sun.source.tree Abstract Syntax Tree} (AST).</p>
 *
 * <p>This module uses the {@link java.nio.file.spi.FileSystemProvider
 * FileSystemProvider} API to locate file system providers. In particular,
 * this means that a jar file system provider, such as that in the
 * {@code jdk.zipfs} module, must be available if the compiler is to be able
 * to read JAR files.
 *
 * <h3>Options and Environment Variables</h3>
 *
 * The full set of options and environment variables supported by <em>javac</em>
 * is given in the <a href="../../specs/man/javac.html"><em>javac Tool Guide</em></a>.
 * However, there are some restrictions when the compiler is invoked through
 * its API.
 *
 * <ul>
 *     <li><p>The {@code -J} option is not supported.
 *          Any necessary VM options must be set in the VM used to invoke the API.
 *          {@code IllegalArgumentException} will be thrown if the option
 *          is used when invoking the tool through the {@code JavaCompiler} API;
 *          an error will be reported if the option is used when invoking
 *          <em>javac</em> through the {@link java.util.spi.ToolProvider ToolProvider}
 *          or legacy {@link com.sun.tools.javac.Main Main} API.
 *
 *     <li><p>The "classpath wildcard" feature is not supported.
 *          The feature is only supported by the native launcher.
 *          When invoking the tool through its API, all necessary jar
 *          files should be included directly in the {@code --class-path}
 *          option, or the {@code CLASSPATH} environment variable.
 *          When invoking the tool through its API, all components of the
 *          class path will be taken literally, and will be ignored if there
 *          is no matching directory or file. The {@code -Xlint:paths}
 *          option can be used to generate warnings about missing components.
 *
 * </ul>
 *
 * The following restrictions apply when invoking the compiler through
 * the {@link JavaCompiler} interface.
 *
 * <ul>
 *     <li><p>Argument files (so-called @-files) are not supported.
 *          The content of any such files should be included directly
 *          in the list of options provided when invoking the tool
 *          though this API.
 *          {@code IllegalArgumentException} will be thrown if
 *          the option is used when invoking the tool through this API.
 *
 *     <li><p>The environment variable {@code JDK_JAVAC_OPTIONS} is not supported.
 *          Any options defined in the environment variable should be included
 *          directly in the list of options provided when invoking the
 *          API; any values in the environment variable will be ignored.
 *
 *     <li><p>Options that are just used to obtain information (such as
 *          {@code --help}, {@code --help-extended}, {@code --version} and
 *          {@code --full-version}) are not supported.
 *          {@link IllegalArgumentException} will be thrown if any of
 *          these options are used when invoking the tool through this API.
 *
 *      <li>Path-related options depend on the file manager being used
 *          when calling {@link JavaCompiler#getTask}. The "standard"
 *          options, such as {@code --class-path}, {@code --module-path},
 *          and so on are available when using the default file manager,
 *          or one derived from it. These options may not be available
 *          and different options may be available, when using a different
 *          file manager.
 *          {@link IllegalArgumentException} will be thrown if any option
 *          that is unknown to the tool or the file manager is used when
 *          invoking the tool through this API.
 * </ul>
 *
 * Note that the {@code CLASSPATH} environment variable <em>is</em> honored
 * when invoking the compiler through its API, although such use is discouraged.
 * An environment variable cannot be unset once a VM has been started,
 * and so it is recommended to ensure that the environment variable is not set
 * when starting a VM that will be used to invoke the compiler.
 * However, if a value has been set, any such value can be overridden by
 * using the {@code --class-path} option when invoking the compiler,
 * or setting {@link StandardLocation#CLASS_PATH} in the file manager
 * when invoking the compiler through the {@link JavaCompiler} interface.
 *
 * <h3>SuppressWarnings</h3>
 *
 * JLS {@jls 9.6.4.5} specifies a number of strings that can be used to
 * suppress warnings that may be generated by a Java compiler.
 *
 * In addition, <em>javac</em> also supports other strings that can be used
 * to suppress other kinds of warnings. The following table lists all the
 * strings that can be used with {@code @SuppressWarnings}.
 *
 * <table class="striped">
 *     <caption>Strings supported by {@code SuppressWarnings}</caption>
 * <thead>
 * <tr><th>String<th>Suppress Warnings About ...
 * </thead>
 * <tbody>
 * <tr><th scope="row">{@code auxiliaryclass}       <td>an auxiliary class that is hidden in a source file, and is used
 *                                                      from other files
 * <tr><th scope="row">{@code cast}                 <td>use of unnecessary casts
 * <tr><th scope="row">{@code classfile}            <td>issues related to classfile contents
 * <tr><th scope="row">{@code deprecation}          <td>use of deprecated items
 * <tr><th scope="row">{@code dep-ann}              <td>items marked as deprecated in a documentation comment but not
 *                                                      using the {@code @Deprecated} annotation
 * <tr><th scope="row">{@code divzero}              <td>division by constant integer {@code 0}
 * <tr><th scope="row">{@code empty}                <td>empty statement after {@code if}
 * <tr><th scope="row">{@code exports}              <td>issues regarding module exports
 * <tr><th scope="row">{@code fallthrough}          <td>falling through from one case of a {@code switch} statement to
 *                                                      the next
 * <tr><th scope="row">{@code finally}              <td>{@code finally} clauses that do not terminate normally
 * <tr><th scope="row">{@code lossy-conversions}    <td>possible lossy conversions in compound assignment
 * <tr><th scope="row">{@code missing-explicit-ctor} <td>missing explicit constructors in public and protected classes
 *                                                      in exported packages
 * <tr><th scope="row">{@code module}               <td>module system related issues
 * <tr><th scope="row">{@code opens}                <td>issues regarding module opens
 * <tr><th scope="row">{@code overloads}            <td>issues regarding method overloads
 * <tr><th scope="row">{@code overrides}            <td>issues regarding method overrides
 * <tr><th scope="row">{@code path}                 <td>invalid path elements on the command line
 * <tr><th scope="row">{@code preview}              <td>use of preview language features
 * <tr><th scope="row">{@code rawtypes}             <td>use of raw types
 * <tr><th scope="row">{@code removal}              <td>use of API that has been marked for removal
 * <tr><th scope="row">{@code requires-automatic}   <td>use of automatic modules in the {@code requires} clauses
 * <tr><th scope="row">{@code requires-transitive-automatic} <td>automatic modules in {@code requires transitive}
 * <tr><th scope="row">{@code serial}               <td>{@link java.base/java.io.Serializable Serializable} classes
 *                                                      that do not have a {@code serialVersionUID} field, or other
 *                                                      suspect declarations in {@code Serializable} and
 *                                                      {@link java.base/java.io.Externalizable Externalizable} classes
 *                                                      and interfaces
 * <tr><th scope="row">{@code static}               <td>accessing a static member using an instance
 * <tr><th scope="row">{@code strictfp}             <td>unnecessary use of the {@code strictfp} modifier
 * <tr><th scope="row">{@code synchronization}      <td>synchronization attempts on instances of value-based classes
 * <tr><th scope="row">{@code text-blocks}          <td>inconsistent white space characters in text block indentation
 * <tr><th scope="row">{@code try}                  <td>issues relating to use of {@code try} blocks
 *                                                      (that is, try-with-resources)
 * <tr><th scope="row">{@code unchecked}            <td>unchecked operations
 * <tr><th scope="row">{@code varargs}              <td>potentially unsafe vararg methods
 * <tr><th scope="row">{@code doclint:accessibility} <td>accessibility issues found in documentation comments
 * <tr><th scope="row">{@code doclint:all}          <td>all issues found in documentation comments
 * <tr><th scope="row">{@code doclint:html}         <td>HTML issues found in documentation comments
 * <tr><th scope="row">{@code doclint:missing}      <td>missing items in documentation comments
 * <tr><th scope="row">{@code doclint:reference}    <td>reference issues found in documentation comments
 * <tr><th scope="row">{@code doclint:syntax}       <td>syntax issues found in documentation comments
 * </tbody>
 * </table>
 *
 * @toolGuide javac
 *
 * @provides java.util.spi.ToolProvider
 *     Use {@link java.util.spi.ToolProvider#findFirst ToolProvider.findFirst("javac")}
 *     to obtain an instance of a {@code ToolProvider} that provides the equivalent
 *     of command-line access to the {@code javac} tool.
 * @provides com.sun.tools.javac.platform.PlatformProvider
 * @provides javax.tools.JavaCompiler
 * @provides javax.tools.Tool
 *
 * @uses javax.annotation.processing.Processor
 * @uses com.sun.source.util.Plugin
 * @uses com.sun.tools.javac.platform.PlatformProvider
 *
 * @moduleGraph
 * @since 9
 */
module jdk.compiler {
    requires transitive java.compiler;
    requires jdk.zipfs;

    exports com.sun.source.doctree;
    exports com.sun.source.tree;
    exports com.sun.source.util;
    exports com.sun.tools.javac;

    exports com.sun.tools.doclint to
        jdk.javadoc;
    exports com.sun.tools.javac.api to
        jdk.javadoc,
        jdk.jshell;
    exports com.sun.tools.javac.resources to
        jdk.jshell;
    exports com.sun.tools.javac.code to
        jdk.javadoc,
        jdk.jshell;
    exports com.sun.tools.javac.comp to
        jdk.javadoc,
        jdk.jshell;
    exports com.sun.tools.javac.file to
        jdk.jdeps,
        jdk.javadoc;
    exports com.sun.tools.javac.jvm to
        jdk.javadoc;
    exports com.sun.tools.javac.main to
        jdk.javadoc,
        jdk.jshell;
    exports com.sun.tools.javac.model to
        jdk.javadoc;
    exports com.sun.tools.javac.parser to
        jdk.jshell;
    exports com.sun.tools.javac.platform to
        jdk.jdeps,
        jdk.javadoc;
    exports com.sun.tools.javac.tree to
        jdk.javadoc,
        jdk.jshell;
    exports com.sun.tools.javac.util to
        jdk.jdeps,
        jdk.javadoc,
        jdk.jshell;
    exports jdk.internal.shellsupport.doc to
        jdk.jshell;

    uses javax.annotation.processing.Processor;
    uses com.sun.source.util.Plugin;
    uses com.sun.tools.doclint.DocLint;
    uses com.sun.tools.javac.platform.PlatformProvider;

    provides java.util.spi.ToolProvider with
        com.sun.tools.javac.main.JavacToolProvider;

    provides com.sun.tools.javac.platform.PlatformProvider with
        com.sun.tools.javac.platform.JDKPlatformProvider;

    provides javax.tools.JavaCompiler with
        com.sun.tools.javac.api.JavacTool;

    provides javax.tools.Tool with
        com.sun.tools.javac.api.JavacTool;
}

