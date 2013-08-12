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

package jdk.nashorn.internal.runtime.arrays;

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Helper class for the various map/apply functions in {@link jdk.nashorn.internal.objects.NativeArray}.
 * @param <T> element type of results from application callback
 */
public abstract class IteratorAction<T> {
    /** Self object */
    protected final Object self;

    /** This for the callback invocation */
    protected Object thisArg;

    /** Callback function to be applied to elements */
    protected final Object callbackfn;

    /** Result of array iteration */
    protected T result;

    /** Current array index of iterator */
    protected long index;

    /** Iterator object */
    private final ArrayLikeIterator<Object> iter;

    /**
     * Constructor
     *
     * @param self          self reference to array object
     * @param callbackfn    callback function for each element
     * @param thisArg       the reference
     * @param initialResult result accumulator initialization
     */
    public IteratorAction(final Object self, final Object callbackfn, final Object thisArg, final T initialResult) {
        this(self, callbackfn, thisArg, initialResult, ArrayLikeIterator.arrayLikeIterator(self));
    }

    /**
     * Constructor
     *
     * @param self          self reference to array object
     * @param callbackfn    callback function for each element
     * @param thisArg       the reference
     * @param initialResult result accumulator initialization
     * @param iter          custom element iterator
     */
    public IteratorAction(final Object self, final Object callbackfn, final Object thisArg, final T initialResult, final ArrayLikeIterator<Object> iter) {
        this.self       = self;
        this.callbackfn = callbackfn;
        this.result     = initialResult;
        this.iter       = iter;
        this.thisArg    = thisArg;
    }

    /**
     * An action to be performed once at the start of the apply loop
     * @param iterator array element iterator
     */
    protected void applyLoopBegin(final ArrayLikeIterator<Object> iterator) {
        //empty
    }

    /**
     * Apply action main loop.
     * @return result of apply
     */
    public final T apply() {
        final boolean strict;
        if (callbackfn instanceof ScriptFunction) {
            strict = ((ScriptFunction)callbackfn).isStrict();
        } else if (callbackfn instanceof ScriptObjectMirror &&
            ((ScriptObjectMirror)callbackfn).isFunction()) {
            strict = ((ScriptObjectMirror)callbackfn).isStrictFunction();
        } else if (Bootstrap.isDynamicMethod(callbackfn) || Bootstrap.isFunctionalInterfaceObject(callbackfn)) {
            strict = false;
        } else {
            throw typeError("not.a.function", ScriptRuntime.safeToString(callbackfn));
        }

        // for non-strict callback, need to translate undefined thisArg to be global object
        thisArg = (thisArg == ScriptRuntime.UNDEFINED && !strict)? Context.getGlobal() : thisArg;

        applyLoopBegin(iter);
        final boolean reverse = iter.isReverse();
        while (iter.hasNext()) {

            final Object val = iter.next();
            index = iter.nextIndex() + (reverse ? 1 : -1);

            try {
                if (!forEach(val, index)) {
                    return result;
                }
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return result;
    }

    /**
     * For each callback
     *
     * @param val value
     * @param i   position of value
     *
     * @return true if callback invocation return true
     *
     * @throws Throwable if invocation throws an exception/error
     */
    protected abstract boolean forEach(final Object val, final long i) throws Throwable;

}
