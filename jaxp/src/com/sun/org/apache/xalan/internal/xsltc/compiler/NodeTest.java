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
 * $Id: NodeTest.java,v 1.2.4.1 2005/09/02 10:31:14 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.compiler;

import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xml.internal.dtm.DTM;

/**
 * @author Jacek Ambroziak
 * @author Santiago Pericas-Geertsen
 */
public interface NodeTest {
    public static final int TEXT      = DTM.TEXT_NODE;
    public static final int COMMENT   = DTM.COMMENT_NODE;
    public static final int PI        = DTM.PROCESSING_INSTRUCTION_NODE;
    public static final int ROOT      = DTM.DOCUMENT_NODE;
    public static final int ELEMENT   = DTM.ELEMENT_NODE;
    public static final int ATTRIBUTE = DTM.ATTRIBUTE_NODE;

    // generalized type
    public static final int GTYPE     = DTM.NTYPES;

    public static final int ANODE     = DOM.FIRST_TYPE - 1;
}
