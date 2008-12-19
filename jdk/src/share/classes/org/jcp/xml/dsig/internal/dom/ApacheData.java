/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2005 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
/*
 * Copyright 2005-2008 Sun Microsystems, Inc. All rights reserved.
 */
/*
 * $Id: ApacheData.java,v 1.2 2008/07/24 15:20:32 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.Data;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;

/**
 * XMLSignatureInput Data wrapper.
 *
 * @author Sean Mullan
 */
public interface ApacheData extends Data {

    /**
     * Returns the XMLSignatureInput.
     */
    public XMLSignatureInput getXMLSignatureInput();
}
