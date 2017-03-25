/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.java;

import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.options.DerivedOptionValue;
import org.graalvm.compiler.options.DerivedOptionValue.OptionSupplier;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.SuitesCreator;

public abstract class SuitesProviderBase implements SuitesCreator {

    protected final DerivedOptionValue<Suites> defaultSuites;
    protected PhaseSuite<HighTierContext> defaultGraphBuilderSuite;
    protected final DerivedOptionValue<LIRSuites> defaultLIRSuites;

    private class SuitesSupplier implements OptionSupplier<Suites> {

        private static final long serialVersionUID = 2677805381215454728L;

        @Override
        public Suites get() {
            Suites suites = createSuites();
            suites.setImmutable();
            return suites;
        }

    }

    private class LIRSuitesSupplier implements OptionSupplier<LIRSuites> {

        private static final long serialVersionUID = 312070237227476252L;

        @Override
        public LIRSuites get() {
            LIRSuites lirSuites = createLIRSuites();
            lirSuites.setImmutable();
            return lirSuites;
        }

    }

    public SuitesProviderBase() {
        this.defaultSuites = new DerivedOptionValue<>(new SuitesSupplier());
        this.defaultLIRSuites = new DerivedOptionValue<>(new LIRSuitesSupplier());
    }

    @Override
    public final Suites getDefaultSuites() {
        return defaultSuites.getValue();
    }

    @Override
    public PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        return defaultGraphBuilderSuite;
    }

    @Override
    public final LIRSuites getDefaultLIRSuites() {
        return defaultLIRSuites.getValue();
    }

    @Override
    public abstract LIRSuites createLIRSuites();

    @Override
    public abstract Suites createSuites();
}
