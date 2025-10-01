/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.main.Option;
import static com.sun.tools.javac.main.Option.*;

/** A table of all command-line options.
 *  If an option has an argument, the option name is mapped to the argument.
 *  If a set option has no argument, it is mapped to itself.
 *
 * <p>
 * Instances start in an uninitialized/empty state. They transition to the initialized state once they start
 * being populated from the flags and arguments provided to the compiler, or manually via {@link #initialize}.
 *
 * <p>
 * Because {@link Options} singletons are used to configure many other compiler singletons, depending on how
 * the compiler is invoked, it's possible for some of these singletons to query options before they have been
 * populated. If this happens, null/false is returned, and then if/when listeners are notified (indicating that
 * the population process is complete), if it turns out that the actual option value is different from what was
 * previously returned, then an assertion error is generated (as this would indicate a startup ordering bug).
 * To fix, change the initialization order or have the singleton initialize itself using {@link #whenReady}.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Options {
    private static final long serialVersionUID = 0;

    /** The context key for the options. */
    public static final Context.Key<Options> optionsKey = new Context.Key<>();

    private final LinkedHashMap<String,String> values;
    private boolean initialized;

    /** Get the Options instance for this context. */
    public static Options instance(Context context) {
        Options instance = context.get(optionsKey);
        if (instance == null)
            instance = new Options(context);
        return instance;
    }

    @SuppressWarnings("this-escape")
    protected Options(Context context) {
// DEBUGGING -- Use LinkedHashMap for reproducibility
        values = new LinkedHashMap<>();
        context.put(optionsKey, this);
    }

    /**
     * Mark this instance as ready to accept queries.
     */
    public void initialize() {
        initialized = true;
    }

    /**
     * Get the value for an undocumented option.
     *
     * @param name option name
     */
    public String get(String name) {
        return computeIfReady(() -> values.get(name), null, Option.XD.primaryName + name);
    }

    /**
     * Get the value for an option.
     *
     * @param option option to get
     */
    public String get(Option option) {
        return computeIfReady(() -> values.get(option.primaryName), null, option.primaryName);
    }

    /**
     * Get the boolean value for an undocumented option, patterned after Boolean.getBoolean,
     * essentially will return true, iff the value exists and is set to "true".
     *
     * @param name option name
     */
    public boolean getBoolean(String name) {
        return getBoolean(name, false);
    }

    /**
     * Get the undocumented boolean with a default value if the option is not set.
     *
     * @param name option name
     * @param defaultValue return value if option is not set
     */
    public boolean getBoolean(String name, boolean defaultValue) {
        return computeIfReady(
            () -> Optional.of(name)
                  .map(values::get)
                  .map(Boolean::parseBoolean)
                  .orElse(defaultValue),
            defaultValue,
            Option.XD.primaryName + name);
    }

    /**
     * Check if the value for an undocumented option has been set.
     */
    public boolean isSet(String name) {
        return computeIfReady(() -> values.get(name) != null, false, Option.XD.primaryName + name);
    }

    /**
     * Check if the value for an option has been set.
     */
    public boolean isSet(Option option) {
        return computeIfReady(() -> values.get(option.primaryName) != null, false, option.primaryName);
    }

    /**
     * Check if the value for a choice option has been set to a specific value.
     */
    public boolean isSet(Option option, String value) {
        return computeIfReady(() -> values.get(option.primaryName + value) != null, false, option.primaryName + value);
    }

    /**
     * Check if the value for an undocumented option has not been set.
     */
    public boolean isUnset(String name) {
        return !isSet(name);
    }

    /**
     * Check if the value for an option has not been set.
     */
    public boolean isUnset(Option option) {
        return !isSet(option);
    }

    /**
     * Check if the value for a choice option has not been set to a specific value.
     */
    public boolean isUnset(Option option, String value) {
        return !isSet(option, value);
    }

    /**
     * Determine if a specific {@link LintCategory} is enabled via a custom
     * option flag of the form {@code -Xlint}, {@code -Xlint:all}, or {@code -Xlint:key}.
     *
     * <p>
     * Note: It's possible the category was also disabled; this method does not check that.
     *
     * @param lc the {@link LintCategory} in question
     * @return true if {@code lc} has been enabled
     */
    public boolean isLintEnabled(LintCategory lc) {
        return isLintExplicitlyEnabled(lc) ||
            isSet(Option.XLINT_CUSTOM) ||
            isSet(Option.XLINT_CUSTOM, Option.LINT_CUSTOM_ALL);
    }

    /**
     * Determine if a specific {@link LintCategory} is disabled via a custom
     * option flag of the form {@code -Xlint:none} or {@code -Xlint:-key}.
     *
     * <p>
     * Note: It's possible the category was also enabled; this method does not check that.
     *
     * @param lc the {@link LintCategory} in question
     * @return true if {@code lc} has been disabled
     */
    public boolean isLintDisabled(LintCategory lc) {
        return isLintExplicitlyDisabled(lc) || isSet(Option.XLINT_CUSTOM, Option.LINT_CUSTOM_NONE);
    }

    /**
     * Determine if a specific {@link LintCategory} is explicitly enabled via a custom
     * option flag of the form {@code -Xlint:key}.
     *
     * <p>
     * Note: This does not check for option flags of the form {@code -Xlint} or {@code -Xlint:all}.
     *
     * <p>
     * Note: It's possible the category was also disabled; this method does not check that.
     *
     * @param lc the {@link LintCategory} in question
     * @return true if {@code lc} has been explicitly enabled
     */
    public boolean isLintExplicitlyEnabled(LintCategory lc) {
        return lc.optionList.stream().anyMatch(alias -> isSet(Option.XLINT_CUSTOM, alias));
    }

    /**
     * Determine if a specific {@link LintCategory} is explicitly disabled via a custom
     * option flag of the form {@code -Xlint:-key}.
     *
     * <p>
     * Note: This does not check for an option flag of the form {@code -Xlint:none}.
     *
     * <p>
     * Note: It's possible the category was also enabled; this method does not check that.
     *
     * @param lc the {@link LintCategory} in question
     * @return true if {@code lc} has been explicitly disabled
     */
    public boolean isLintExplicitlyDisabled(LintCategory lc) {
        return lc.optionList.stream().anyMatch(alias -> isSet(Option.XLINT_CUSTOM, "-" + alias));
    }

    public void put(String name, String value) {
        values.put(name, value);
        initialized = true;
    }

    public void put(Option option, String value) {
        values.put(option.primaryName, value);
        initialized = true;
    }

    public void putAll(Options options) {
        values.putAll(options.values);
        initialized = true;
    }

    public void remove(String name) {
        values.remove(name);
        initialized = true;
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    public int size() {
        return values.size();
    }

    // light-weight notification mechanism

    private List<Runnable> listeners = List.nil();

    public void addListener(Runnable listener) {
        listeners = listeners.prepend(listener);
    }

    public void notifyListeners() {
        initialized = true;
        for (Runnable r: listeners)
            r.run();
        listeners = List.nil();
    }

    public void clear() {
        values.clear();
        listeners = List.nil();
        initialized = false;
    }

    /**
     * Perform the given action once this instance is ready for queries,
     * or immediately if it is ready now.
     *
     * @param action action to take; will be given this instance
     */
    public void whenReady(Consumer<? super Options> action) {
        if (initialized)
            action.accept(this);
        else
            addListener(() -> action.accept(this));
    }

    /**
     * Return the computed value if initialized, otherwise return the given default value
     * and add a notify listener that asserts that our assumption was correct.
     */
    private <T> T computeIfReady(Supplier<T> ifReady, T ifNotReady, String flag) {
        //System.out.println("computeIfReady("+initialized+"): \""+flag+"\" -> " + ifReady.get());
        if (initialized)
            return ifReady.get();
        addListener(() -> Assert.check(Objects.equals(ifReady.get(), ifNotReady), () -> "ignored flag: " + flag));
        return ifNotReady;          // hopefully this is correct...
    }
}
