/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.oracle.webservices.internal.api.message;

//TODO Do we want to remove this implementation dependency?
import com.sun.xml.internal.ws.encoding.ContentTypeImpl;

/**
 * A Content-Type transport header that will be returned by {@link MessageContext#write(java.io.OutputStream)}.
 * It will provide the Content-Type header and also take care of SOAP 1.1 SOAPAction header.
 *
 * @author Vivek Pandey
 */
public interface ContentType {

    /**
     * Gives non-null Content-Type header value.
     */
    public String getContentType();

    /**
     * Gives SOAPAction transport header value. It will be non-null only for SOAP 1.1 messages. In other cases
     * it MUST be null. The SOAPAction transport header should be written out only when its non-null.
     *
     * @return It can be null, in that case SOAPAction header should be written.
     */
    public String getSOAPActionHeader();

    /**
     * Controls the Accept transport header, if the transport supports it.
     * Returning null means the transport need not add any new header.
     *
     * <p>
     * We realize that this is not an elegant abstraction, but
     * this would do for now. If another person comes and asks for
     * a similar functionality, we'll define a real abstraction.
     */
    public String getAcceptHeader();

    static public class Builder {
        private String contentType;
        private String soapAction;
        private String accept;
        private String charset;

        public Builder contentType(String s) {contentType = s; return this; }
        public Builder soapAction (String s) {soapAction = s;  return this; }
        public Builder accept     (String s) {accept = s;      return this; }
        public Builder charset    (String s) {charset = s;     return this; }
        public ContentType build() {
            //TODO Do we want to remove this implementation dependency?
            return new ContentTypeImpl(contentType, soapAction, accept, charset);
        }
    }
}
