/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2002,2004 The Apache Software Foundation.
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
package com.sun.org.apache.xerces.internal.dom;


/** Internal class LCount is used to track the number of
    listeners registered for a given event name, as an entry
    in a global hashtable. This should allow us to avoid generating,
    or discard, events for which no listeners are registered.

    ***** There should undoubtedly be methods here to manipulate
    this table. At the moment that code's residing in NodeImpl.
    Move it when we have a chance to do so. Sorry; we were
    rushed.

    ???? CONCERN: Hashtables are known to be "overserialized" in
    current versions of Java. That may impact performance.

    ???? CONCERN: The hashtable should probably be a per-document object.
    Finer granularity would be even better, but would cost more cycles to
    resolve and might not save enough event traffic to be worth the investment.
*/
/**
 * @xerces.internal
 *
 */

class LCount
{
    static java.util.Hashtable lCounts=new java.util.Hashtable();
    public int captures=0,bubbles=0,defaults, total=0;

    static LCount lookup(String evtName)
    {
        LCount lc=(LCount)lCounts.get(evtName);
        if(lc==null)
            lCounts.put(evtName,(lc=new LCount()));
        return lc;
    }
} // class LCount
