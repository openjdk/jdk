/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.options;

import java.util.Locale;
import java.util.TimeZone;
import jdk.nashorn.internal.runtime.QuotedStringTokenizer;

/**
 * This describes the valid input for an option, as read from the resource
 * bundle file. Metainfo such as parameters and description is here as well
 * for context sensitive help generation.
 */
public final class OptionTemplate implements Comparable<OptionTemplate> {
    /** Resource, e.g. "nashorn" for this option */
    private final String resource;

    /** Key in the resource bundle */
    private final String key;

    /** Is this option a help option? */
    private final boolean isHelp;

    /** Is this option a extended help option? */
    private final boolean isXHelp;

    /** Name - for example --dump-on-error (usually prefixed with --) */
    private String name;

    /** Short name - for example -doe (usually prefixed with -) */
    private String shortName;

    /** Params - a parameter template string */
    private String params;

    /** Type - e.g. "boolean". */
    private String type;

    /** Does this option have a default value? */
    private String defaultValue;

    /** Does this option activate another option when set? */
    private String dependency;

    /** Does this option conflict with another? */
    private String conflict;

    /** Is this a documented option that should show up in help? */
    private boolean isUndocumented;

    /** A longer description of what this option does */
    private String description;

    /** is the option value specified as next argument? */
    private boolean valueNextArg;

    /**
     * Can this option be repeated in command line?
     *
     * For a repeatable option, multiple values will be merged as comma
     * separated values rather than the last value overriding previous ones.
     */
    private boolean repeated;

    OptionTemplate(final String resource, final String key, final String value, final boolean isHelp, final boolean isXHelp) {
        this.resource = resource;
        this.key = key;
        this.isHelp = isHelp;
        this.isXHelp = isXHelp;
        parse(value);
    }

    /**
     * Is this the special help option, used to generate help for
     * all the others
     *
     * @return true if this is the help option
     */
    public boolean isHelp() {
        return this.isHelp;
    }

    /**
     * Is this the special extended help option, used to generate extended help for
     * all the others
     *
     * @return true if this is the extended help option
     */
    public boolean isXHelp() {
        return this.isXHelp;
    }

    /**
     * Get the resource name used to prefix this option set, e.g. "nashorn"
     *
     * @return the name of the resource
     */
    public String getResource() {
        return this.resource;
    }

    /**
     * Get the type of this option
     *
     * @return the type of the option
     */
    public String getType() {
        return this.type;
    }

    /**
     * Get the key of this option
     *
     * @return the key
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Get the default value for this option
     *
     * @return the default value as a string
     */
    public String getDefaultValue() {
        switch (getType()) {
        case "boolean":
            if (this.defaultValue == null) {
                this.defaultValue = "false";
            }
            break;
        case "integer":
            if (this.defaultValue == null) {
                this.defaultValue = "0";
            }
            break;
        case "timezone":
            this.defaultValue = TimeZone.getDefault().getID();
            break;
        case "locale":
            this.defaultValue = Locale.getDefault().toLanguageTag();
            break;
        default:
            break;
        }
        return this.defaultValue;
    }

    /**
     * Does this option automatically enable another option, i.e. a dependency.
     * @return the dependency or null if none exists
     */
    public String getDependency() {
        return this.dependency;
    }

    /**
     * Is this option in conflict with another option so that both can't be enabled
     * at the same time
     *
     * @return the conflicting option or null if none exists
     */
    public String getConflict() {
        return this.conflict;
    }

    /**
     * Is this option undocumented, i.e. should not show up in the standard help output
     *
     * @return true if option is undocumented
     */
    public boolean isUndocumented() {
        return this.isUndocumented;
    }

    /**
     * Get the short version of this option name if one exists, e.g. "-co" for "--compile-only"
     *
     * @return the short name
     */
    public String getShortName() {
        return this.shortName;
    }

    /**
     * Get the name of this option, e.g. "--compile-only". A name always exists
     *
     * @return the name of the option
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the description of this option.
     *
     * @return the description
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Is value of this option passed as next argument?
     * @return boolean
     */
    public boolean isValueNextArg() {
        return valueNextArg;
    }

    /**
     * Can this option be repeated?
     * @return boolean
     */
    public boolean isRepeated() {
        return repeated;
    }

    private static String strip(final String value, final char start, final char end) {
        final int len = value.length();
        if (len > 1 && value.charAt(0) == start && value.charAt(len - 1) == end) {
            return value.substring(1, len - 1);
        }
        return null;
    }

    private void parse(final String origValue) {
        String value = origValue.trim();

        try {
            value = OptionTemplate.strip(value, '{', '}');
            final QuotedStringTokenizer keyValuePairs = new QuotedStringTokenizer(value, ",");

            while (keyValuePairs.hasMoreTokens()) {
                final String                keyValue = keyValuePairs.nextToken();
                final QuotedStringTokenizer st       = new QuotedStringTokenizer(keyValue, "=");
                final String                keyToken = st.nextToken();
                final String                arg      = st.nextToken();

                switch (keyToken) {
                case "is_undocumented":
                    this.isUndocumented = Boolean.parseBoolean(arg);
                    break;
                case "name":
                    if (!arg.startsWith("-")) {
                        throw new IllegalArgumentException(arg);
                    }
                    this.name = arg;
                    break;
                case "short_name":
                    if (!arg.startsWith("-")) {
                        throw new IllegalArgumentException(arg);
                    }
                    this.shortName = arg;
                    break;
                case "desc":
                    this.description = arg;
                    break;
                case "params":
                    this.params = arg;
                    break;
                case "type":
                    this.type = arg.toLowerCase(Locale.ENGLISH);
                    break;
                case "default":
                    this.defaultValue = arg;
                    break;
                case "dependency":
                    this.dependency = arg;
                    break;
                case "conflict":
                    this.conflict = arg;
                    break;
                case "value_next_arg":
                    this.valueNextArg = Boolean.parseBoolean(arg);
                    break;
                case "repeated":
                    this.repeated = true;
                    break;
                default:
                    throw new IllegalArgumentException(keyToken);
                }
            }

            // default to boolean if no type is given
            if (this.type == null) {
                this.type = "boolean";
            }

            if (this.params == null && "boolean".equals(this.type)) {
                this.params = "[true|false]";
            }

        } catch (final Exception e) {
            throw new IllegalArgumentException(origValue);
        }

        if (name == null && shortName == null) {
            throw new IllegalArgumentException(origValue);
        }

        if (this.repeated && !"string".equals(this.type)) {
            throw new IllegalArgumentException("repeated option should be of type string: " + this.name);
        }
    }

    boolean nameMatches(final String aName) {
        return aName.equals(this.shortName) || aName.equals(this.name);
    }

    private static final int LINE_BREAK = 64;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append('\t');

        if (shortName != null) {
            sb.append(shortName);
            if (name != null) {
                sb.append(", ");
            }
        }

        if (name != null) {
            sb.append(name);
        }

        if (description != null) {
            final int indent = sb.length();
            sb.append(' ');
            sb.append('(');
            int pos = 0;
            for (final char c : description.toCharArray()) {
                sb.append(c);
                pos++;
                if (pos >= LINE_BREAK && Character.isWhitespace(c)) {
                    pos = 0;
                    sb.append("\n\t");
                    for (int i = 0; i < indent; i++) {
                        sb.append(' ');
                    }
                }
            }
            sb.append(')');
        }

        if (params != null) {
            sb.append('\n');
            sb.append('\t');
            sb.append('\t');
            sb.append(Options.getMsg("nashorn.options.param")).append(": ");
            sb.append(params);
            sb.append("   ");
            final Object def = this.getDefaultValue();
            if (def != null) {
                sb.append(Options.getMsg("nashorn.options.default")).append(": ");
                sb.append(this.getDefaultValue());
            }
        }


        return sb.toString();
    }

    @Override
    public int compareTo(final OptionTemplate o) {
        return this.getKey().compareTo(o.getKey());
    }
}
