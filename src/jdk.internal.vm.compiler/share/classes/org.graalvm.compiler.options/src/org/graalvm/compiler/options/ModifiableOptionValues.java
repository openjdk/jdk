/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.options;

import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.util.EconomicMap;
import org.graalvm.util.Equivalence;
import org.graalvm.util.UnmodifiableEconomicMap;
import org.graalvm.util.UnmodifiableMapCursor;

/**
 * A context for obtaining values for {@link OptionKey}s that allows for key/value pairs to be
 * updated. Updates have atomic copy-on-write semantics which means a thread may see an old value
 * when reading but writers will never loose updates.
 */
public class ModifiableOptionValues extends OptionValues {

    private final AtomicReference<UnmodifiableEconomicMap<OptionKey<?>, Object>> v = new AtomicReference<>();

    private static final EconomicMap<OptionKey<?>, Object> EMPTY_MAP = newOptionMap();

    public ModifiableOptionValues(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        super(EMPTY_MAP);
        EconomicMap<OptionKey<?>, Object> map = newOptionMap();
        initMap(map, values);
        v.set(map);
    }

    /**
     * Value that can be used in {@link #update(UnmodifiableEconomicMap)} and
     * {@link #update(OptionKey, Object)} to remove an explicitly set value for a key such that
     * {@link OptionKey#hasBeenSet(OptionValues)} will return {@code false} for the key.
     */
    public static final Object UNSET_KEY = new Object();

    /**
     * Updates this object with the given key/value pair.
     *
     * @see #UNSET_KEY
     */
    public void update(OptionKey<?> key, Object value) {
        UnmodifiableEconomicMap<OptionKey<?>, Object> expect;
        EconomicMap<OptionKey<?>, Object> newMap;
        do {
            expect = v.get();
            newMap = EconomicMap.create(Equivalence.IDENTITY, expect);
            if (value == UNSET_KEY) {
                newMap.removeKey(key);
            } else {
                key.update(newMap, value);
                // Need to do the null encoding here as `key.update()` doesn't do it
                newMap.put(key, encodeNull(value));
            }
        } while (!v.compareAndSet(expect, newMap));
    }

    /**
     * Updates this object with the key/value pairs in {@code values}.
     *
     * @see #UNSET_KEY
     */
    public void update(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        if (values.isEmpty()) {
            return;
        }
        UnmodifiableEconomicMap<OptionKey<?>, Object> expect;
        EconomicMap<OptionKey<?>, Object> newMap;
        do {
            expect = v.get();
            newMap = EconomicMap.create(Equivalence.IDENTITY, expect);
            UnmodifiableMapCursor<OptionKey<?>, Object> cursor = values.getEntries();
            while (cursor.advance()) {
                OptionKey<?> key = cursor.getKey();
                Object value = cursor.getValue();
                if (value == UNSET_KEY) {
                    newMap.removeKey(key);
                } else {
                    key.update(newMap, value);
                    // Need to do the null encoding here as `key.update()` doesn't do it
                    newMap.put(key, encodeNull(value));
                }
            }
        } while (!v.compareAndSet(expect, newMap));
    }

    @Override
    protected <T> T get(OptionKey<T> key) {
        return OptionValues.get(v.get(), key);
    }

    @Override
    protected boolean containsKey(OptionKey<?> key) {
        return v.get().containsKey(key);
    }

    @Override
    public UnmodifiableEconomicMap<OptionKey<?>, Object> getMap() {
        return v.get();
    }
}
