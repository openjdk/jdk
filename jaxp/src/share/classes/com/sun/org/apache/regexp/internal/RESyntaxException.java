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
 * Exception thrown to indicate a syntax error in a regular expression.
 * This is a non-checked exception because you should only have problems compiling
 * a regular expression during development.
 * If you are making regular expresion programs dynamically then you can catch it
 * if you wish. But should not be forced to.
 *
 * @author <a href="mailto:jonl@muppetlabs.com">Jonathan Locke</a>
 * @author <a href="mailto:gholam@xtra.co.nz>Michael McCallum</a>
 */
public class RESyntaxException extends RuntimeException
{
    /**
     * Constructor.
     * @param s Further description of the syntax error
     */
    public RESyntaxException(String s)
    {
        super("Syntax error: " + s);
    }
}
