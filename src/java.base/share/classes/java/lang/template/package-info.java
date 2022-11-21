/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * <em>String templates</em> and <em>template processors</em> provide a comprehensive
 * system for combining literal text with the values from <em>embedded expressions</em>
 * to produce a result. This result is often a {@link String} but is not limited to just
 * {@link String Strings}.
 * <p>
 * Java string templates look like string literals or text blocks except they contain
 * one or more embedded expressions bracketed by <code>\{</code> and <code>}</code>.
 * An embedded expression is usually positioned in the string where the value of that
 * embedded expression might expect to be inserted.
 * <p>
 * <em>String interpolation</em> is the most general use of string templates. The
 * standard {@link java.lang.template.StringTemplate#STR} template processor is statically
 * imported into every Java compilation unit to facilitate the common use of string
 * interpolation.
 * {@snippet :
 * int x = 10;
 * int y = 20;
 * String s = STR."The result of adding \{x} and \{y} is \{x + y}.";
 * }
 * The value {@code s} in the above example will be
 * {@code "The result of adding 10 and 20 is 30."}.
 * <p>
 * The expression {@code STR."The result of adding \{x} and \{y} is \{x + y}."}
 * above is an example of a <em>process template expression</em>. A process template
 * expression consists of a <em>processor expression</em> and a <em>processor
 * argument</em> separated by a dot (period). A proper processor expression evaluates
 * to an instance of type {@link java.lang.template.ValidatingProcessor}. A proper
 * processor argument is a string template that is represented by an instance of
 *{@link java.lang.template.StringTemplate}. The end result of the process template
 * expression is the value that is produced by invoking the processor's
 *{@link java.lang.template.ValidatingProcessor#process(StringTemplate)}
 * method of with the processor argument. Improper processor expressions or
 * improper processor arguments result in compilation errors.
 * <p>
 * In the example, {@code STR."The result of adding \{x} and \{y} is \{x + y}."},
 * {@code STR} is the processor that implements string interpolation with its
 * {@link java.lang.template.ValidatingProcessor#process(StringTemplate)} method.
 * <p>
 * The string template in the example, represented by a
 * {@link java.lang.template.StringTemplate}, contains the string fragments and
 * embedded expression values expressed in
 * {@code "The result of adding \{x} and \{y} is \{x + y}."}.
 * In the example, the fragments are {@code "The result of adding "}, {@code " and "},
 * {@code " is "} and {@code "."}. The values are {@code 10}, {@code 20} and {@code 30},
 * which are the result of evaluating {@code x}, {@code y} and {@code x + y}.
 * See {@link java.lang.template.StringTemplate} for examples and details.
 * <p>
 * String literals and text blocks can be used as proper processor arguments as
 * well. This is automatically facilitated by the Java compiler converted the
 * strings to {@link java.lang.template.StringTemplate StringTemplate} using the
 * {@link java.lang.template.StringTemplate#of(String)} method.
 * <p>
 * Users can create their own template processors by implementing either
 * {@link java.lang.template.ValidatingProcessor},
 * {@link java.lang.template.TemplateProcessor} or
 * {@link java.lang.template.StringProcessor} interfaces.
 * See {@link java.lang.template.ValidatingProcessor} for examples and details.
 *
 * @see java.lang.template.StringTemplate
 * @see java.lang.template.ValidatingProcessor
 * @see java.lang.template.TemplateProcessor
 * @see java.lang.template.StringTemplate
 *
 * @since 20
 *
 * @jls 15.8.6 Process Template Expressions
 */
package java.lang.template;
