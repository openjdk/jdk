/*
 * Copyright (c) 2005, 2010, Thai Open Source Software Center Ltd
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *     Neither the name of the Thai Open Source Software Center Ltd nor
 *     the names of its contributors may be used to endorse or promote
 *     products derived from this software without specific prior written
 *     permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.xml.internal.org.relaxng.datatype.helpers;

import com.sun.xml.internal.org.relaxng.datatype.*;

/**
 * Dummy implementation of {@link DatatypeBuilder}.
 *
 * This implementation can be used for Datatypes which have no parameters.
 * Any attempt to add parameters will be rejected.
 *
 * <p>
 * Typical usage would be:
 * <PRE><XMP>
 * class MyDatatypeLibrary implements DatatypeLibrary {
 *     ....
 *     DatatypeBuilder createDatatypeBuilder( String typeName ) {
 *         return new ParameterleessDatatypeBuilder(createDatatype(typeName));
 *     }
 *     ....
 * }
 * </XMP></PRE>
 *
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public final class ParameterlessDatatypeBuilder implements DatatypeBuilder {

        /** This type object is returned for the derive method. */
        private final Datatype baseType;

        public ParameterlessDatatypeBuilder( Datatype baseType ) {
                this.baseType = baseType;
        }

        public void addParameter( String name, String strValue, ValidationContext context )
                        throws DatatypeException {
                throw new DatatypeException();
        }

        public Datatype createDatatype() throws DatatypeException {
                return baseType;
        }
}
