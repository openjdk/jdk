/*
 * Copyright (c) 2001, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import java.util.*;
import com.sun.tools.javac.main.OptionName;
import static com.sun.tools.javac.main.OptionName.*;

/** A table of all command-line options.
 *  If an option has an argument, the option name is mapped to the argument.
 *  If a set option has no argument, it is mapped to itself.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Options {
    private static final long serialVersionUID = 0;

    /** The context key for the options. */
    public static final Context.Key<Options> optionsKey =
        new Context.Key<Options>();

    private LinkedHashMap<String,String> values;

    /** Get the Options instance for this context. */
    public static Options instance(Context context) {
        Options instance = context.get(optionsKey);
        if (instance == null)
            instance = new Options(context);
        return instance;
    }

    protected Options(Context context) {
// DEBUGGING -- Use LinkedHashMap for reproducability
        values = new LinkedHashMap<String,String>();
        context.put(optionsKey, this);
    }

    /**
     * Get the value for an undocumented option.
     */
    public String get(String name) {
        return values.get(name);
    }

    /**
     * Get the value for an option.
     */
    public String get(OptionName name) {
        return values.get(name.optionName);
    }

    /**
     * Check if the value for an undocumented option has been set.
     */
    public boolean isSet(String name) {
        return (values.get(name) != null);
    }

    /**
     * Check if the value for an option has been set.
     */
    public boolean isSet(OptionName name) {
        return (values.get(name.optionName) != null);
    }

    /**
     * Check if the value for a choice option has been set to a specific value.
     */
    public boolean isSet(OptionName name, String value) {
        return (values.get(name.optionName + value) != null);
    }

    /**
     * Check if the value for an undocumented option has not been set.
     */
    public boolean isUnset(String name) {
        return (values.get(name) == null);
    }

    /**
     * Check if the value for an option has not been set.
     */
    public boolean isUnset(OptionName name) {
        return (values.get(name.optionName) == null);
    }

    /**
     * Check if the value for a choice option has not been set to a specific value.
     */
    public boolean isUnset(OptionName name, String value) {
        return (values.get(name.optionName + value) == null);
    }

    public void put(String name, String value) {
        values.put(name, value);
    }

    public void put(OptionName name, String value) {
        values.put(name.optionName, value);
    }

    public void putAll(Options options) {
        values.putAll(options.values);
    }

    public void remove(String name) {
        values.remove(name);
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    public int size() {
        return values.size();
    }

    /** Check for a lint suboption. */
    public boolean lint(String s) {
        // return true if either the specific option is enabled, or
        // they are all enabled without the specific one being
        // disabled
        return
            isSet(XLINT_CUSTOM, s) ||
            (isSet(XLINT) || isSet(XLINT_CUSTOM, "all")) &&
                isUnset(XLINT_CUSTOM, "-" + s);
    }
}
