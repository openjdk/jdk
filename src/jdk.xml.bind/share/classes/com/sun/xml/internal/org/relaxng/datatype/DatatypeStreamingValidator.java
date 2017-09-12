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
 * Datatype streaming validator.
 *
 * <p>
 * The streaming validator is an optional feature that is useful for
 * certain Datatypes. It allows the caller to incrementally provide
 * the literal.
 *
 * @author <a href="mailto:jjc@jclark.com">James Clark</a>
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public interface DatatypeStreamingValidator {

        /**
         * Passes an additional fragment of the literal.
         *
         * <p>
         * The application can call this method several times, then call
         * the isValid method (or the checkValid method) to check the validity
         * of the accumulated characters.
         */
        void addCharacters( char[] buf, int start, int len );

        /**
         * Tells if the accumulated literal is valid with respect to
         * the underlying Datatype.
         *
         * @return
         *              True if it is valid. False if otherwise.
         */
        boolean isValid();

        /**
         * Similar to the isValid method, but this method throws
         * Exception (with possibly diagnostic information), instead of
         * returning false.
         *
         * @exception DatatypeException
         *              If the callee supports the diagnosis and the accumulated
         *              literal is invalid, then this exception that possibly
         *              contains diagnosis information is thrown.
         */
        void checkValid() throws DatatypeException;
}
