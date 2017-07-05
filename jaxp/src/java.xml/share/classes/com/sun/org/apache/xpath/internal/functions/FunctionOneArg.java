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
 * $Id: FunctionOneArg.java,v 1.2.4.1 2005/09/14 20:18:45 jeffsuttor Exp $
 */
package com.sun.org.apache.xpath.internal.functions;

import com.sun.org.apache.xalan.internal.res.XSLMessages;
import com.sun.org.apache.xpath.internal.Expression;
import com.sun.org.apache.xpath.internal.ExpressionOwner;
import com.sun.org.apache.xpath.internal.XPathVisitor;

/**
 * Base class for functions that accept one argument.
 * @xsl.usage advanced
 */
public class FunctionOneArg extends Function implements ExpressionOwner
{
    static final long serialVersionUID = -5180174180765609758L;

  /** The first argument passed to the function (at index 0).
   *  @serial  */
  Expression m_arg0;

  /**
   * Return the first argument passed to the function (at index 0).
   *
   * @return An expression that represents the first argument passed to the
   *         function.
   */
  public Expression getArg0()
  {
    return m_arg0;
  }

  /**
   * Set an argument expression for a function.  This method is called by the
   * XPath compiler.
   *
   * @param arg non-null expression that represents the argument.
   * @param argNum The argument number index.
   *
   * @throws WrongNumberArgsException If the argNum parameter is greater than 0.
   */
  public void setArg(Expression arg, int argNum)
          throws WrongNumberArgsException
  {

    if (0 == argNum)
    {
      m_arg0 = arg;
      arg.exprSetParent(this);
    }
    else
      reportWrongNumberArgs();
  }

  /**
   * Check that the number of arguments passed to this function is correct.
   *
   *
   * @param argNum The number of arguments that is being passed to the function.
   *
   * @throws WrongNumberArgsException
   */
  public void checkNumberArgs(int argNum) throws WrongNumberArgsException
  {
    if (argNum != 1)
      reportWrongNumberArgs();
  }

  /**
   * Constructs and throws a WrongNumberArgException with the appropriate
   * message for this function object.
   *
   * @throws WrongNumberArgsException
   */
  protected void reportWrongNumberArgs() throws WrongNumberArgsException {
      throw new WrongNumberArgsException(XSLMessages.createXPATHMessage("one", null));
  }

  /**
   * Tell if this expression or it's subexpressions can traverse outside
   * the current subtree.
   *
   * @return true if traversal outside the context node's subtree can occur.
   */
   public boolean canTraverseOutsideSubtree()
   {
    return m_arg0.canTraverseOutsideSubtree();
   }

  /**
   * This function is used to fixup variables from QNames to stack frame
   * indexes at stylesheet build time.
   * @param vars List of QNames that correspond to variables.  This list
   * should be searched backwards for the first qualified name that
   * corresponds to the variable reference qname.  The position of the
   * QName in the vector from the start of the vector will be its position
   * in the stack frame (but variables above the globalsTop value will need
   * to be offset to the current stack frame).
   */
  public void fixupVariables(java.util.Vector vars, int globalsSize)
  {
    if(null != m_arg0)
      m_arg0.fixupVariables(vars, globalsSize);
  }

  /**
   * @see com.sun.org.apache.xpath.internal.XPathVisitable#callVisitors(ExpressionOwner, XPathVisitor)
   */
  public void callArgVisitors(XPathVisitor visitor)
  {
        if(null != m_arg0)
                m_arg0.callVisitors(this, visitor);
  }


  /**
   * @see ExpressionOwner#getExpression()
   */
  public Expression getExpression()
  {
    return m_arg0;
  }

  /**
   * @see ExpressionOwner#setExpression(Expression)
   */
  public void setExpression(Expression exp)
  {
        exp.exprSetParent(this);
        m_arg0 = exp;
  }

  /**
   * @see Expression#deepEquals(Expression)
   */
  public boolean deepEquals(Expression expr)
  {
        if(!super.deepEquals(expr))
                return false;

        if(null != m_arg0)
        {
                if(null == ((FunctionOneArg)expr).m_arg0)
                        return false;

                if(!m_arg0.deepEquals(((FunctionOneArg)expr).m_arg0))
                        return false;
        }
        else if(null != ((FunctionOneArg)expr).m_arg0)
                return false;

        return true;
  }


}
