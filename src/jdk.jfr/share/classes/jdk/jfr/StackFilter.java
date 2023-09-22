/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Event annotation, specifies method names or classes to exclude in the stack
 * trace.
 * <p>
 * The following example illustrates how the {@code StackFilter} annotation can
 * be used to remove the {@code Logger::log} method in a stack trace:
 *
 * {@snippet class="Snippets" region="StackFilterOverview"}
 *
 * @since 22
 */
@MetadataDefinition
@Target({ ElementType.TYPE })
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface StackFilter {
    /**
     * The methods or classes that should not be part of an event stack trace.
     * <p>
     * A filter is formed by using the fully qualified class name concatenated with
     * the method name using {@code "::"} as separator, for example
     * {@code "java.lang.String::toString"}
     * <p>
     * If only the name of a class is specified, for example {@code
     * "java.lang.String"}, all methods in that class are filtered out.
     * <p>
     * Methods can't be qualified using method parameters or return types.
     * <p>
     * Instance methods belonging to an interface can't be filtered out.
     * <p>
     * Wilcards are not permitted.
     *
     * @return the method names, not {@code null}
     */
    public String[] value();
}
