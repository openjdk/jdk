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

package com.sun.org.apache.regexp.internal;

/**
 * Encapsulates String as CharacterIterator.
 *
 * @author <a href="mailto:ales.novak@netbeans.com">Ales Novak</a>
 */
public final class StringCharacterIterator implements CharacterIterator
{
    /** encapsulated */
    private final String src;

    /** @param src - encapsulated String */
    public StringCharacterIterator(String src)
    {
        this.src = src;
    }

    /** @return a substring */
    public String substring(int beginIndex, int endIndex)
    {
        return src.substring(beginIndex, endIndex);
    }

    /** @return a substring */
    public String substring(int beginIndex)
    {
        return src.substring(beginIndex);
    }

    /** @return a character at the specified position. */
    public char charAt(int pos)
    {
        return src.charAt(pos);
    }

    /** @return <tt>true</tt> iff if the specified index is after the end of the character stream */
    public boolean isEnd(int pos)
    {
        return (pos >= src.length());
    }
}
