/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001, 2002,2004 The Apache Software Foundation.
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

package com.sun.org.apache.xerces.internal.impl.dv;

import com.sun.org.apache.xerces.internal.xs.ShortList;

/**
 * Class to get the information back after content is validated. This info
 * would be filled by validate().
 *
 * @xerces.internal
 *
 * @author Neeraj Bajaj, Sun Microsystems, inc.
 *
 */
public class ValidatedInfo {

    /**
     * The normalized value of a string value
     */
    public String normalizedValue;

    /**
     * The actual value from a string value (QName, Boolean, etc.)
     * An array of Objects if the type is a list.
     */
    public Object actualValue;

    /**
     * The type of the actual value. It's one of the _DT constants
     * defined in XSConstants.java. The value is used to indicate
     * the most specific built-in type.
     * (i.e. short instead of decimal or integer).
     */
    public short actualValueType;

    /**
     * If the type is a union type, then the member type which
     * actually validated the string value.
     */
    public XSSimpleType memberType;

    /**
     * If
     * 1. the type is a union type where one of the member types is a list, or
     *    if the type is a list; and
     * 2. the item type of the list is a union type
     * then an array of member types used to validate the values.
     */
    public XSSimpleType[] memberTypes;

    /**
     * In the case the value is a list or a list of unions, this value
     * indicates the type(s) of the items in the list.
     * For a normal list, the length of the array is 1; for list of unions,
     * the length of the array is the same as the length of the list.
     */
    public ShortList itemValueTypes;

    /**
     * reset the state of this object
     */
    public void reset() {
        this.normalizedValue = null;
        this.actualValue = null;
        this.memberType = null;
        this.memberTypes = null;
    }

    /**
     * Return a string representation of the value. If there is an actual
     * value, use toString; otherwise, use the normalized value.
     */
    public String stringValue() {
        if (actualValue == null)
            return normalizedValue;
        else
            return actualValue.toString();
    }
}
