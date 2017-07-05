/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package org.relaxng.datatype.helpers;

import org.relaxng.datatype.*;

/**
 * Dummy implementation of {@link DatatypeStreamingValidator}.
 *
 * <p>
 * This implementation can be used as a quick hack when the performance
 * of streaming validation is not important. And this implementation
 * also shows you how to implement the DatatypeStreamingValidator interface.
 *
 * <p>
 * Typical usage would be:
 * <PRE><XMP>
 * class MyDatatype implements Datatype {
 *     ....
 *     public DatatypeStreamingValidator createStreamingValidator( ValidationContext context ) {
 *         return new StreamingValidatorImpl(this,context);
 *     }
 *     ....
 * }
 * </XMP></PRE>
 *
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public final class StreamingValidatorImpl implements DatatypeStreamingValidator {

        /** This buffer accumulates characters. */
        private final StringBuffer buffer = new StringBuffer();

        /** Datatype obejct that creates this streaming validator. */
        private final Datatype baseType;

        /** The current context. */
        private final ValidationContext context;

        public void addCharacters( char[] buf, int start, int len ) {
                // append characters to the current buffer.
                buffer.append(buf,start,len);
        }

        public boolean isValid() {
                return baseType.isValid(buffer.toString(),context);
        }

        public void checkValid() throws DatatypeException {
                baseType.checkValid(buffer.toString(),context);
        }

        public StreamingValidatorImpl( Datatype baseType, ValidationContext context ) {
                this.baseType = baseType;
                this.context = context;
        }
}
