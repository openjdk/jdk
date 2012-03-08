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
 * $Id: Bool.java,v 1.2.4.1 2005/09/14 21:31:43 jeffsuttor Exp $
 */
package com.sun.org.apache.xpath.internal.operations;

import com.sun.org.apache.xpath.internal.XPathContext;
import com.sun.org.apache.xpath.internal.objects.XBoolean;
import com.sun.org.apache.xpath.internal.objects.XObject;

/**
 * The 'boolean()' operation expression executer.
 */
public class Bool extends UnaryOperation
{
    static final long serialVersionUID = 44705375321914635L;

  /**
   * Apply the operation to two operands, and return the result.
   *
   *
   * @param right non-null reference to the evaluated right operand.
   *
   * @return non-null reference to the XObject that represents the result of the operation.
   *
   * @throws javax.xml.transform.TransformerException
   */
  public XObject operate(XObject right) throws javax.xml.transform.TransformerException
  {

    if (XObject.CLASS_BOOLEAN == right.getType())
      return right;
    else
      return right.bool() ? XBoolean.S_TRUE : XBoolean.S_FALSE;
  }

  /**
   * Evaluate this operation directly to a boolean.
   *
   * @param xctxt The runtime execution context.
   *
   * @return The result of the operation as a boolean.
   *
   * @throws javax.xml.transform.TransformerException
   */
  public boolean bool(XPathContext xctxt)
          throws javax.xml.transform.TransformerException
  {
    return m_right.bool(xctxt);
  }

}
