/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import javax.xml.bind.annotation.DomHandler;

import com.sun.xml.internal.bind.v2.model.core.WildcardMode;

import org.xml.sax.SAXException;

/**
 * Feed incoming events to {@link DomHandler} and builds a DOM tree.
 *
 * <p>
 * Note that the SAXException returned by the ContentHandler is
 * unreported. So we have to catch them and report it, then rethrow
 * it if necessary.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class WildcardLoader extends ProxyLoader {

    private final DomLoader dom;

    private final WildcardMode mode;

    public WildcardLoader(DomHandler dom, WildcardMode mode) {
        this.dom = new DomLoader(dom);
        this.mode = mode;
    }

    protected Loader selectLoader(UnmarshallingContext.State state, TagName tag) throws SAXException {
        UnmarshallingContext context = state.getContext();

        if(mode.allowTypedObject) {
            Loader l = context.selectRootLoader(state,tag);
            if(l!=null)
                return l;
        }
        if(mode.allowDom)
            return dom;

        // simply discard.
        return Discarder.INSTANCE;
    }

}
