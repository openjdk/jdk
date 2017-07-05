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

package com.sun.xml.internal.ws.api.model;

/**
 * Denotes the binding of a parameter.
 *
 * <p>
 * This is somewhat like an enumeration (but it is <b>NOT</b> an enumeration.)
 *
 * <p>
 * The possible values are
 * BODY, HEADER, UNBOUND, and ATTACHMENT. BODY, HEADER, and UNBOUND
 * has a singleton semantics, but there are multiple ATTACHMENT instances
 * as it carries additional MIME type parameter.
 *
 * <p>
 * So don't use '==' for testing the equality.
 */
public final class ParameterBinding {
    /**
     * Singleton instance that represents 'BODY'
     */
    public static final ParameterBinding BODY = new ParameterBinding(Kind.BODY,null);
    /**
     * Singleton instance that represents 'HEADER'
     */
    public static final ParameterBinding HEADER = new ParameterBinding(Kind.HEADER,null);
    /**
     * Singleton instance that represents 'UNBOUND',
     * meaning the parameter doesn't have a representation in a SOAP message.
     */
    public static final ParameterBinding UNBOUND = new ParameterBinding(Kind.UNBOUND,null);
    /**
     * Creates an instance that represents the attachment
     * with a given MIME type.
     *
     * <p>
     * TODO: shall we consider givint the singleton semantics by using
     * a cache? It's more elegant to do so, but
     * no where in JAX-WS RI two {@link ParameterBinding}s are compared today,
     */
    public static ParameterBinding createAttachment(String mimeType) {
        return new ParameterBinding(Kind.ATTACHMENT,mimeType);
    }

    /**
     * Represents 4 kinds of binding.
     */
    public static enum Kind {
        BODY, HEADER, UNBOUND, ATTACHMENT;
    }


    /**
     * Represents the kind of {@link ParameterBinding}.
     * Always non-null.
     */
    public final Kind kind;

    /**
     * Only used with attachment binding.
     */
    private String mimeType;

    private ParameterBinding(Kind kind,String mimeType) {
        this.kind = kind;
        this.mimeType = mimeType;
    }



    public String toString() {
        return kind.toString();
    }

    /**
     * Returns the MIME type associated with this binding.
     *
     * @throws IllegalStateException
     *      if this binding doesn't represent an attachment.
     *      IOW, if {@link #isAttachment()} returns false.
     * @return
     *      Can be null, if the MIME type is not known.
     */
    public String getMimeType() {
        if(!isAttachment())
            throw new IllegalStateException();
        return mimeType;
    }

    public boolean isBody(){
        return this==BODY;
    }

    public boolean isHeader(){
        return this==HEADER;
    }

    public boolean isUnbound(){
        return this==UNBOUND;
    }

    public boolean isAttachment(){
        return kind==Kind.ATTACHMENT;
    }
}
