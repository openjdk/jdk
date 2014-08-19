/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.developer;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.Headers;

import javax.xml.transform.Source;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents additional data to be added to EPRs
 * created from {@link StatefulWebServiceManager} (for advanced users).
 *
 * <p>
 * Occasionally it is convenient to be able to control the data to be
 * present on {@link EndpointReference}s created by {@link StatefulWebServiceManager}.
 * You can do so by using this class like this:
 *
 * <pre>
 * statefulWebServiceManager.export({@link W3CEndpointReference}.class,myObject,
 *   new EPRRecipe().addReferenceParameter({@link Headers}.create(...))
 *                  .addReferenceParameter({@link Headers}.create(...)));
 * </pre>
 *
 * <p>
 * The methods on this class follows <a href="http://www.martinfowler.com/bliki/FluentInterface.html">
 * the fluent interface design</a> to allow construction without using a variable.
 *
 *
 * <p>
 * See <a href="http://www.w3.org/TR/2006/REC-ws-addr-core-20060509/#eprinfomodel">
 * WS-Addressing EPR information model</a> for more details.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.1.1
 * @see StatefulWebServiceManager
 * @see Headers
 */
public final class EPRRecipe {
    private final List<Header> referenceParameters = new ArrayList<Header>();
    private final List<Source> metadata = new ArrayList<Source>();

    /**
     * Gets all the reference parameters added so far.
     */
    public @NotNull List<Header> getReferenceParameters() {
        return referenceParameters;
    }

    /**
     * Gets all the metadata added so far.
     */
    public @NotNull List<Source> getMetadata() {
        return metadata;
    }

    /**
     * Adds a new reference parameter.
     */
    public EPRRecipe addReferenceParameter(Header h) {
        if(h==null) throw new IllegalArgumentException();
        referenceParameters.add(h);
        return this;
    }

    /**
     * Adds all the headers as reference parameters.
     */
    public EPRRecipe addReferenceParameters(Header... headers) {
        for (Header h : headers)
            addReferenceParameter(h);
        return this;
    }

    /**
     * Adds all the headers as reference parameters.
     */
    public EPRRecipe addReferenceParameters(Iterable<? extends Header> headers) {
        for (Header h : headers)
            addReferenceParameter(h);
        return this;
    }

    /**
     * Adds a new metadata.
     */
    public EPRRecipe addMetadata(Source source) {
        if(source==null)    throw new IllegalArgumentException();
        metadata.add(source);
        return this;
    }

    public EPRRecipe addMetadata(Source... sources) {
        for (Source s : sources)
            addMetadata(s);
        return this;
    }

    public EPRRecipe addMetadata(Iterable<? extends Source> sources) {
        for (Source s : sources)
            addMetadata(s);
        return this;
    }
}
