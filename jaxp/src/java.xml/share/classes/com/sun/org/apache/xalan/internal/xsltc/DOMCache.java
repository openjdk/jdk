/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: DOMCache.java,v 1.2.4.1 2005/08/31 10:23:55 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc;


/**
 * @author Morten Jorgensen
 */
public interface DOMCache {

    /**
     * This method is responsible for:
     *
     * (1) building the DOMImpl tree
     *
     *      Parser  _parser = new Parser();
     *      DOMImpl _dom = new DOMImpl();
     *      _parser.setDocumentHandler(_dom.getBuilder());
     *      _parser.setDTDHandler(_dom.getBuilder());
     *      _parser.parse(uri);
     *
     * (2) giving the translet an early opportunity to extract anything from
     *     the DOMImpl that it would like
     *
     *      translet.documentPrepass(_dom);
     *
     * (3) setting the document URI:
     *
     *      _dom.setDocumentURI(uri);
     *
     * @param baseURI The base URI used by the document call.
     * @param href The href argument passed to the document function.
     * @param translet A reference to the translet requesting the document
     */
    public DOM retrieveDocument(String baseURI, String href, Translet translet);

}
