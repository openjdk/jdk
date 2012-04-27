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
 * $Id: SourceLoader.java,v 1.2.4.1 2005/09/05 09:02:30 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.compiler;

import org.xml.sax.InputSource;

/**
 * @author Morten Jorgensen
 */
public interface SourceLoader {

    /**
     * This interface is used to plug external document loaders into XSLTC
     * (used with the <xsl:include> and <xsl:import> elements.
     *
     * @param href The URI of the document to load
     * @param context The URI of the currently loaded document
     * @param xsltc The compiler that resuests the document
     * @return An InputSource with the loaded document
     */
    public InputSource loadSource(String href, String context, XSLTC xsltc);

}
