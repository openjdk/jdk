/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
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
 * $Id: CustomStringPool.java,v 1.2.4.1 2005/09/15 08:14:59 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.dtm.ref;
import java.util.Hashtable;

/** <p>CustomStringPool is an example of appliction provided data structure
 * for a DTM implementation to hold symbol references, e.g. elelment names.
 * It will follow the DTMDStringPool interface and use two simple methods
 * indexToString(int i) and stringToIndex(Sring s) to map between a set of
 * string values and a set of integer index values.  Therefore, an application
 * may improve DTM processing speed by substituting the DTM symbol resolution
 * tables with application specific quick symbol resolution tables.</p>
 *
 * %REVIEW% The only difference between this an DTMStringPool seems to be that
 * it uses a java.lang.Hashtable full of Integers rather than implementing its
 * own hashing. Joe deliberately avoided that approach when writing
 * DTMStringPool, since it is both much more memory-hungry and probably slower
 * -- especially in JDK 1.1.x, where Hashtable is synchronized. We need to
 * either justify this implementation or discard it.
 *
 * <p>Status: In progress, under discussion.</p>
 * */
public class CustomStringPool extends DTMStringPool {
        //final Vector m_intToString;
        //static final int HASHPRIME=101;
        //int[] m_hashStart=new int[HASHPRIME];
        final Hashtable m_stringToInt = new Hashtable();
        public static final int NULL=-1;

        public CustomStringPool()
        {
                super();
                /*m_intToString=new Vector();
                System.out.println("In constructor m_intToString is " +
                                                                                         ((null == m_intToString) ? "null" : "not null"));*/
                //m_stringToInt=new Hashtable();
                //removeAllElements();
        }

        public void removeAllElements()
        {
                m_intToString.removeAllElements();
                if (m_stringToInt != null)
                        m_stringToInt.clear();
        }

        /** @return string whose value is uniquely identified by this integer index.
         * @throws java.lang.ArrayIndexOutOfBoundsException
         *  if index doesn't map to a string.
         * */
        public String indexToString(int i)
        throws java.lang.ArrayIndexOutOfBoundsException
        {
                return(String) m_intToString.elementAt(i);
        }

        /** @return integer index uniquely identifying the value of this string. */
        public int stringToIndex(String s)
        {
                if (s==null) return NULL;
                Integer iobj=(Integer)m_stringToInt.get(s);
                if (iobj==null) {
                        m_intToString.addElement(s);
                        iobj=new Integer(m_intToString.size());
                        m_stringToInt.put(s,iobj);
                }
                return iobj.intValue();
        }
}
