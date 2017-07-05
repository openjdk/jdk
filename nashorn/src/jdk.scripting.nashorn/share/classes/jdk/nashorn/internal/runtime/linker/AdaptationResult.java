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

package jdk.nashorn.internal.runtime.linker;

import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.ECMAException;

/**
 * A result of generating an adapter for a class. A tuple of an outcome and - in case of an error outcome - a list of
 * classes that caused the error.
 */
final class AdaptationResult {
    /**
     * Contains various outcomes for attempting to generate an adapter class. These are stored in AdapterInfo instances.
     * We have a successful outcome (adapter class was generated) and four possible error outcomes: superclass is final,
     * superclass is not public, superclass has no public or protected constructor, more than one superclass was
     * specified. We don't throw exceptions when we try to generate the adapter, but rather just record these error
     * conditions as they are still useful as partial outcomes, as Nashorn's linker can still successfully check whether
     * the class can be autoconverted from a script function even when it is not possible to generate an adapter for it.
     */
    enum Outcome {
        SUCCESS,
        ERROR_FINAL_CLASS,
        ERROR_NON_PUBLIC_CLASS,
        ERROR_NO_ACCESSIBLE_CONSTRUCTOR,
        ERROR_MULTIPLE_SUPERCLASSES,
        ERROR_NO_COMMON_LOADER,
        ERROR_FINAL_FINALIZER,
        ERROR_OTHER
    }

    static final AdaptationResult SUCCESSFUL_RESULT = new AdaptationResult(Outcome.SUCCESS, "");

    private final Outcome outcome;
    private final RuntimeException cause;
    private final String[] messageArgs;

    AdaptationResult(final Outcome outcome, final RuntimeException cause, final String... messageArgs) {
        this.outcome = outcome;
        this.cause = cause;
        this.messageArgs = messageArgs;
    }

    AdaptationResult(final Outcome outcome, final String... messageArgs) {
        this(outcome, null, messageArgs);
    }

    Outcome getOutcome() {
        return outcome;
    }

    ECMAException typeError() {
        return ECMAErrors.typeError(cause, "extend." + outcome, messageArgs);
    }
}
