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
 * $Id: InternalRuntimeError.java,v 1.0 2009-11-25 04:34:28 joehw Exp $
 */
package com.sun.org.apache.xalan.internal.xsltc.runtime;

/**
 * Class to express failed assertions and similar for the xsltc runtime.
 * As java.lang.AssertionError was introduced in JDK 1.4 we can't use that yet.
 */
public class InternalRuntimeError extends Error {

    public InternalRuntimeError(String message) {
        super(message);
    }

}
