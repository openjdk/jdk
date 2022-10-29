/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

/**
 * Indicates the warnings to be suppressed at compile time in the
 * annotated element, and in all elements contained in the annotated
 * element.
 *
 * <p>The {@code SuppressWarnings} annotation interface is applicable
 * in all declaration contexts, so an {@code @SuppressWarnings}
 * annotation can be used on any element.  As a matter of style,
 * programmers should always use this annotation on the most deeply
 * nested element where it is effective. For example, if you want to
 * suppress a warning in a particular method, you should annotate that
 * method rather than its class.
 *
 * <p>The set of warnings suppressed in a given element is a union of
 * the warnings suppressed in all containing elements.  For example,
 * if you annotate a class to suppress one warning and annotate a
 * method in the class to suppress another, both warnings will be
 * suppressed in the method.  However, note that if a warning is
 * suppressed in a {@code module-info} file, the suppression applies
 * to elements within the file and <em>not</em> to types contained
 * within the module.  Likewise, if a warning is suppressed in a
 * {@code package-info} file, the suppression applies to elements
 * within the file and <em>not</em> to types contained within the
 * package.
 *
 * <p>Java compilers must recognize all the kinds of warnings defined
 * in the <cite>Java Language Specification</cite> (JLS section {@jls
 * 9.6.4.5}) which include:
 *
 * <ul>
 * <li> Unchecked warnings, specified by the string {@code "unchecked"}.
 * <li> Deprecation warnings, specified by the string {@code "deprecation"}.
 * <li> Removal warnings, specified by the string {@code "removal"}.
 * <li> Preview warnings, specified by the string {@code "preview"}.
 * </ul>
 *
 * Whether or not a Java compiler recognizes other strings is a
 * quality of implementation concern.  Compiler vendors should
 * document the additional warning names they support.  Vendors are
 * encouraged to cooperate to ensure that the same names work across
 * multiple compilers.
 *
 * @implNote
 * In addition to the mandated suppression strings, the {@code javac}
 * reference implementation recognizes compilation-related warning
 * names documented in its {@code --help-lint} output.
 *
 * @author Josh Bloch
 * @since 1.5
 * @jls 4.8 Raw Types
 * @jls 4.12.2 Variables of Reference Type
 * @jls 5.1.9 Unchecked Conversion
 * @jls 5.5 Casting Contexts
 * @jls 9.6.4.5 @SuppressWarnings
 */
// Implicitly target all declaration contexts by omitting a @Target annotation
@Retention(RetentionPolicy.SOURCE)
public @interface SuppressWarnings {
    /**
     * The set of warnings that are to be suppressed by the compiler in the
     * annotated element.  Duplicate names are permitted.  The second and
     * successive occurrences of a name are ignored.  The presence of
     * unrecognized warning names is <i>not</i> an error: Compilers must
     * ignore any warning names they do not recognize.  They are, however,
     * free to emit a warning if an annotation contains an unrecognized
     * warning name.
     * @return the set of warnings to be suppressed
     */
    String[] value();
}
