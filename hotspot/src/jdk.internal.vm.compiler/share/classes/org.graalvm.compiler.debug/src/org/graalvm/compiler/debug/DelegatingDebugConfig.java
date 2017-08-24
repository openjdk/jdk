/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import java.io.PrintStream;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import org.graalvm.compiler.debug.internal.DebugScope;

public class DelegatingDebugConfig implements DebugConfig {

    protected final DebugConfig delegate;

    /**
     * The features of a {@link DelegatingDebugConfig} that can be force
     * {@linkplain DelegatingDebugConfig#enable(Feature) enabled}/
     * {@linkplain DelegatingDebugConfig#disable(Feature) disabled} or
     * {@linkplain DelegatingDebugConfig#delegate(Feature) delegated}.
     */
    public enum Feature {
        /**
         * @see Debug#isLogEnabledForMethod()
         */
        LOG_METHOD,
        /**
         * @see Debug#isDumpEnabledForMethod()
         */
        DUMP_METHOD,
        /**
         * @see Debug#isVerifyEnabled()
         */
        VERIFY,
        /**
         * @see Debug#isVerifyEnabledForMethod()
         */
        VERIFY_METHOD,
        /**
         * @see Debug#isCountEnabled()
         */
        COUNT,
        /**
         * @see Debug#isMethodMeterEnabled()
         */
        METHOD_METRICS,
        /**
         * @see Debug#isMemUseTrackingEnabled()
         */
        TRACK_MEM_USE,
        /**
         * @see Debug#isTimeEnabled()
         */
        TIME,
        /**
         * @see DebugConfig#interceptException(Throwable)
         */
        INTERCEPT
    }

    private final Map<Feature, Boolean> featureState = new EnumMap<>(Feature.class);

    /**
     * The debug levels of a {@link DelegatingDebugConfig} than can be
     * {@linkplain DelegatingDebugConfig#override(Level, int) overridden} or
     * {@linkplain DelegatingDebugConfig#delegate(Level) delegated}.
     */
    public enum Level {
        LOG,
        DUMP
    }

    private final Map<Level, Integer> levelState = new EnumMap<>(Level.class);

    /**
     * Creates a config that delegates to the {@link DebugScope#getConfig() current config}.
     */
    public DelegatingDebugConfig() {
        this(DebugScope.getConfig());
    }

    /**
     * Creates a config that delegates to a given config.
     */
    public DelegatingDebugConfig(DebugConfig delegate) {
        this.delegate = delegate;
    }

    public DelegatingDebugConfig enable(Feature feature) {
        featureState.put(feature, Boolean.TRUE);
        return this;
    }

    public DelegatingDebugConfig disable(Feature feature) {
        featureState.put(feature, Boolean.FALSE);
        return this;
    }

    public DelegatingDebugConfig override(Level level, int newLevel) {
        levelState.put(level, newLevel);
        return this;
    }

    public DelegatingDebugConfig delegate(Feature feature) {
        featureState.put(feature, null);
        return this;
    }

    public DelegatingDebugConfig delegate(Level level) {
        levelState.put(level, null);
        return this;
    }

    @Override
    public int getLogLevel() {
        Integer ls = levelState.get(Level.LOG);
        if (ls == null) {
            return delegate.getLogLevel();
        }
        return ls.intValue();
    }

    @Override
    public boolean isLogEnabledForMethod() {
        Boolean fs = featureState.get(Feature.LOG_METHOD);
        if (fs == null) {
            return delegate.isLogEnabledForMethod();
        }
        return fs.booleanValue();
    }

    @Override
    public boolean isCountEnabled() {
        Boolean fs = featureState.get(Feature.COUNT);
        if (fs == null) {
            return delegate.isCountEnabled();
        }
        return fs.booleanValue();
    }

    @Override
    public boolean isMemUseTrackingEnabled() {
        Boolean fs = featureState.get(Feature.TRACK_MEM_USE);
        if (fs == null) {
            return delegate.isMemUseTrackingEnabled();
        }
        return fs.booleanValue();
    }

    @Override
    public int getDumpLevel() {
        Integer ls = levelState.get(Level.DUMP);
        if (ls == null) {
            return delegate.getDumpLevel();
        }
        return ls.intValue();
    }

    @Override
    public boolean isDumpEnabledForMethod() {
        Boolean fs = featureState.get(Feature.DUMP_METHOD);
        if (fs == null) {
            return delegate.isDumpEnabledForMethod();
        }
        return fs.booleanValue();
    }

    @Override
    public boolean isVerifyEnabled() {
        Boolean fs = featureState.get(Feature.VERIFY);
        if (fs == null) {
            return delegate.isVerifyEnabled();
        }
        return fs.booleanValue();
    }

    @Override
    public boolean isVerifyEnabledForMethod() {
        Boolean fs = featureState.get(Feature.VERIFY_METHOD);
        if (fs == null) {
            return delegate.isVerifyEnabledForMethod();
        }
        return fs.booleanValue();
    }

    @Override
    public boolean isTimeEnabled() {
        Boolean fs = featureState.get(Feature.TIME);
        if (fs == null) {
            return delegate.isTimeEnabled();
        }
        return fs.booleanValue();
    }

    @Override
    public boolean isMethodMeterEnabled() {
        Boolean fs = featureState.get(Feature.METHOD_METRICS);
        if (fs == null) {
            return delegate.isMethodMeterEnabled();
        }
        return fs.booleanValue();
    }

    @Override
    public RuntimeException interceptException(Throwable e) {
        Boolean fs = featureState.get(Feature.INTERCEPT);
        if (fs == null || fs) {
            return delegate.interceptException(e);
        }
        return null;
    }

    @Override
    public Collection<DebugDumpHandler> dumpHandlers() {
        return delegate.dumpHandlers();
    }

    @Override
    public Collection<DebugVerifyHandler> verifyHandlers() {
        return delegate.verifyHandlers();
    }

    @Override
    public PrintStream output() {
        return delegate.output();
    }

    @Override
    public void addToContext(Object o) {
        delegate.addToContext(o);
    }

    @Override
    public void removeFromContext(Object o) {
        delegate.removeFromContext(o);
    }

}
