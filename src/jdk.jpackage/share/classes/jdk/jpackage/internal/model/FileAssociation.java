/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public interface FileAssociation {

    String description();

    Path icon();
    
    default boolean hasIcon() {
        return Objects.nonNull(icon());
    }
    
    default boolean hasNonEmptyDescription() {
        return Optional.ofNullable(description()).filter(Predicate.not(String::isEmpty)).isPresent();
    }

    String mimeType();

    String extension();

    record Stub(String description, Path icon, String mimeType, String extension) implements FileAssociation {
    }

    class Proxy<T extends FileAssociation> extends ProxyBase<T> implements FileAssociation {

        protected Proxy(T target) {
            super(target);
        }

        @Override
        final public String description() {
            return target.description();
        }

        @Override
        final public Path icon() {
            return target.icon();
        }

        @Override
        final public String mimeType() {
            return target.mimeType();
        }

        @Override
        final public String extension() {
            return target.extension();
        }
    }
}
