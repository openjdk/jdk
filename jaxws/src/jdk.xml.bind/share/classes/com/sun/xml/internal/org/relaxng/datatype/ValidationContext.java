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

package com.sun.xml.internal.org.relaxng.datatype;

/**
 * An interface that must be implemented by caller to
 * provide context information that is necessary to
 * perform validation of some Datatypes.
 *
 * @author <a href="mailto:jjc@jclark.com">James Clark</a>
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public interface ValidationContext {

        /**
         * Resolves a namespace prefix to the corresponding namespace URI.
         *
         * This method is used for validating the QName type, for example.
         *
         * <p>
         * If the prefix is "" (empty string), it indicates
         * an unprefixed value. The callee
         * should resolve it as for an unprefixed
         * element, rather than for an unprefixed attribute.
         *
         * <p>
         * If the prefix is "xml", then the callee must resolve
         * this prefix into "http://www.w3.org/XML/1998/namespace",
         * as defined in the XML Namespaces Recommendation.
         *
         * @return
         *              namespace URI of this prefix.
         *              If the specified prefix is not declared,
         *              the implementation must return null.
         */
        String resolveNamespacePrefix( String prefix );

        /**
         * Returns the base URI of the context.  The null string may be returned
         * if no base URI is known.
         */
        String getBaseUri();

        /**
         * Checks if an unparsed entity is declared with the
         * specified name.
         *
         * @return
         *  true
         *              if the DTD has an unparsed entity declaration for
         *              the specified name.
         *  false
         *              otherwise.
         */
        boolean isUnparsedEntity( String entityName );

        /**
         * Checks if a notation is declared with the
         * specified name.
         *
         * @return
         *  true
         *              if the DTD has a notation declaration for the specified name.
         *  false
         *              otherwise.
         */
        boolean isNotation( String notationName );
}
