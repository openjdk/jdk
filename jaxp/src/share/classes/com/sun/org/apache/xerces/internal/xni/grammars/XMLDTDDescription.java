/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2002,2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xerces.internal.xni.grammars;

/**
 * All information specific to DTD grammars.
 *
 * @author Sandy Gao, IBM
 */
public interface XMLDTDDescription extends XMLGrammarDescription {

    /**
     * Return the root name of this DTD.
     *
     * @return  the root name. null if the name is unknown.
     */
    public String getRootName();

} // interface XMLDTDDescription
