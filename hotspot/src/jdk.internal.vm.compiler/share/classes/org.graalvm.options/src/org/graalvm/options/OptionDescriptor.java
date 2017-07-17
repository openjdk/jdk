/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.options;

import java.util.Objects;

/**
 * Represents metadata for a single option.
 *
 * @since 1.0
 */
public final class OptionDescriptor {

    private final OptionKey<?> key;
    private final String name;
    private final String help;
    private final OptionCategory kind;
    private final boolean deprecated;

    OptionDescriptor(OptionKey<?> key, String name, String help, OptionCategory kind, boolean deprecated) {
        this.key = key;
        this.name = name;
        this.help = help;
        this.kind = kind;
        this.deprecated = deprecated;
    }

    /**
     * Returns the name of the option that this descriptor represents.
     *
     * @since 1.0
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the key for this option.
     *
     * @since 1.0
     */
    public OptionKey<?> getKey() {
        return key;
    }

    /**
     * Returns <code>true</code> if this option was marked deprecated. This indicates that the
     * option is going to be removed in a future release or its use is not recommended.
     *
     * @since 1.0
     */
    public boolean isDeprecated() {
        return deprecated;
    }

    /**
     * Returns the user category of this option.
     *
     * @since 1.0
     */
    public OptionCategory getCategory() {
        return kind;
    }

    /**
     * Returns a human-readable description on how to use the option.
     *
     * @since 1.0
     */
    public String getHelp() {
        return help;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public String toString() {
        return "OptionDescriptor [key=" + key + ", help=" + help + ", kind=" + kind + ", deprecated=" + deprecated + "]";
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (deprecated ? 1231 : 1237);
        result = prime * result + ((help == null) ? 0 : help.hashCode());
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((kind == null) ? 0 : kind.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        }
        OptionDescriptor other = (OptionDescriptor) obj;
        return Objects.equals(name, other.name) &&
                        Objects.equals(deprecated, other.deprecated) &&
                        Objects.equals(help, other.help) &&
                        Objects.equals(key, other.key) &&
                        Objects.equals(kind, other.kind);
    }

    /**
     * Creates a new option descriptor builder by key. The option group and name is inferred by the
     * key.
     *
     * @since 1.0
     */
    public static <T> Builder newBuilder(OptionKey<T> key, String name) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(name);
        return new Builder(key, name);
    }

    /**
     * Represents an option descriptor builder.
     *
     * @since 1.0
     */
    public static final class Builder {

        private final OptionKey<?> key;
        private final String name;
        private boolean deprecated;
        private OptionCategory category;
        private String help;

        Builder(OptionKey<?> key, String name) {
            this.key = key;
            this.name = name;
        }

        /**
         * Defines the user category for this option. The default value is
         * {@link OptionCategory#DEBUG}.
         *
         * @since 1.0
         */
        public Builder category(@SuppressWarnings("hiding") OptionCategory category) {
            Objects.requireNonNull(category);
            this.category = category;
            return this;
        }

        /**
         * Defines if this option is deprecated. The default value for deprecated is
         * <code>false</code>. This can be used to evolve options between releases.
         *
         * @since 1.0
         */
        public Builder deprecated(@SuppressWarnings("hiding") boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        /**
         * Specifies a human-readable description on how to use the option.
         *
         * @since 1.0
         */
        public Builder help(@SuppressWarnings("hiding") String help) {
            Objects.requireNonNull(help);
            this.help = help;
            return this;
        }

        /**
         * Builds and returns a new option descriptor.
         *
         * @since 1.0
         */
        public OptionDescriptor build() {
            return new OptionDescriptor(key, name, help == null ? "" : help, category == null ? OptionCategory.DEBUG : category, deprecated);
        }

    }

}
