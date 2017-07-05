/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: ApacheOctetStreamData.java 1197150 2011-11-03 14:34:57Z coheigea $
 */
package org.jcp.xml.dsig.internal.dom;

import java.io.IOException;
import javax.xml.crypto.OctetStreamData;
import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;

public class ApacheOctetStreamData extends OctetStreamData
    implements ApacheData {

    private XMLSignatureInput xi;

    public ApacheOctetStreamData(XMLSignatureInput xi)
        throws CanonicalizationException, IOException
    {
        super(xi.getOctetStream(), xi.getSourceURI(), xi.getMIMEType());
        this.xi = xi;
    }

    public XMLSignatureInput getXMLSignatureInput() {
        return xi;
    }
}
