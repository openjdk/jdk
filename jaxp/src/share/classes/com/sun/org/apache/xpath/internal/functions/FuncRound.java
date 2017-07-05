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
 * $Id: FuncRound.java,v 1.2.4.1 2005/09/14 20:18:42 jeffsuttor Exp $
 */
package com.sun.org.apache.xpath.internal.functions;

import com.sun.org.apache.xpath.internal.XPathContext;
import com.sun.org.apache.xpath.internal.objects.XNumber;
import com.sun.org.apache.xpath.internal.objects.XObject;

/**
 * Execute the round() function.
 * @xsl.usage advanced
 */
public class FuncRound extends FunctionOneArg
{
    static final long serialVersionUID = -7970583902573826611L;

  /**
   * Execute the function.  The function must return
   * a valid object.
   * @param xctxt The current execution context.
   * @return A valid XObject.
   *
   * @throws javax.xml.transform.TransformerException
   */
  public XObject execute(XPathContext xctxt) throws javax.xml.transform.TransformerException
  {
          final XObject obj = m_arg0.execute(xctxt);
          final double val= obj.num();
          if (val >= -0.5 && val < 0) return new XNumber(-0.0);
          if (val == 0.0) return new XNumber(val);
          return new XNumber(java.lang.Math.floor(val
                                            + 0.5));
  }
}
