/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2002-2004 The Apache Software Foundation.
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
 * $Id: NodeSequence.java,v 1.6 2007/01/12 19:26:42 spericas Exp $
 */
package com.sun.org.apache.xpath.internal.axes;

import java.util.Vector;

import com.sun.org.apache.xml.internal.dtm.DTM;
import com.sun.org.apache.xml.internal.dtm.DTMFilter;
import com.sun.org.apache.xml.internal.dtm.DTMIterator;
import com.sun.org.apache.xml.internal.dtm.DTMManager;
import com.sun.org.apache.xml.internal.utils.NodeVector;
import com.sun.org.apache.xpath.internal.NodeSetDTM;
import com.sun.org.apache.xpath.internal.XPathContext;
import com.sun.org.apache.xpath.internal.objects.XObject;

/**
 * This class is the dynamic wrapper for a Xalan DTMIterator instance, and
 * provides random access capabilities.
 */
public class NodeSequence extends XObject
  implements DTMIterator, Cloneable, PathComponent
{
    static final long serialVersionUID = 3866261934726581044L;
  /** The index of the last node in the iteration. */
  protected int m_last = -1;

  /**
   * The index of the next node to be fetched.  Useful if this
   * is a cached iterator, and is being used as random access
   * NodeList.
   */
  protected int m_next = 0;

  /**
   * If this iterator needs to cache nodes that are fetched, they
   * are stored in the Vector in the generic object.
   */
  protected NodeVector getVector()
  {
        return (NodeVector)m_obj;
  }

  /**
   * Set the vector where nodes will be stored.
   */
  protected void SetVector(NodeVector v)
  {
        m_obj = v;
  }


  /**
   * If this iterator needs to cache nodes that are fetched, they
   * are stored here.
   */
  public boolean hasCache()
  {
        return (m_obj != null);
  }


  /**
   * The functional iterator that fetches nodes.
   */
  protected DTMIterator m_iter;

  /**
   * Set the functional iterator that fetches nodes.
   * @param iter The iterator that is to be contained.
   */
  public final void setIter(DTMIterator iter)
  {
        m_iter = iter;
  }

  /**
   * Get the functional iterator that fetches nodes.
   * @return The contained iterator.
   */
  public final DTMIterator getContainedIter()
  {
        return m_iter;
  }

  /**
   * The DTMManager to use if we're using a NodeVector only.
   * We may well want to do away with this, and store it in the NodeVector.
   */
  protected DTMManager m_dtmMgr;

  // ==== Constructors ====

  /**
   * Create a new NodeSequence from a (already cloned) iterator.
   *
   * @param iter Cloned (not static) DTMIterator.
   * @param context The initial context node.
   * @param xctxt The execution context.
   * @param shouldCacheNodes True if this sequence can random access.
   */
  public NodeSequence(DTMIterator iter, int context, XPathContext xctxt, boolean shouldCacheNodes)
  {
        setIter(iter);
        setRoot(context, xctxt);
        setShouldCacheNodes(shouldCacheNodes);
  }

  /**
   * Create a new NodeSequence from a (already cloned) iterator.
   *
   * @param nodeVector
   */
  public NodeSequence(Object nodeVector)
  {
        super(nodeVector);
        if(null != nodeVector)
        {
                assertion(nodeVector instanceof NodeVector,
                        "Must have a NodeVector as the object for NodeSequence!");
                if(nodeVector instanceof DTMIterator)
                {
                        setIter((DTMIterator)nodeVector);
                        m_last = ((DTMIterator)nodeVector).getLength();
                }

        }
  }

  /**
   * Construct an empty XNodeSet object.  This is used to create a mutable
   * nodeset to which random nodes may be added.
   */
  public NodeSequence(DTMManager dtmMgr)
  {
    super(new NodeVector());
    m_last = 0;
    m_dtmMgr = dtmMgr;
  }


  /**
   * Create a new NodeSequence in an invalid (null) state.
   */
  public NodeSequence()
  {
  }


  /**
   * @see DTMIterator#getDTM(int)
   */
  public DTM getDTM(int nodeHandle)
  {
        DTMManager mgr = getDTMManager();
        if(null != mgr)
        return getDTMManager().getDTM(nodeHandle);
    else
    {
        assertion(false, "Can not get a DTM Unless a DTMManager has been set!");
        return null;
    }
  }

  /**
   * @see DTMIterator#getDTMManager()
   */
  public DTMManager getDTMManager()
  {
    return m_dtmMgr;
  }

  /**
   * @see DTMIterator#getRoot()
   */
  public int getRoot()
  {
        if(null != m_iter)
        return m_iter.getRoot();
        else
        {
                // NodeSetDTM will call this, and so it's not a good thing to throw
                // an assertion here.
                // assertion(false, "Can not get the root from a non-iterated NodeSequence!");
                return DTM.NULL;
        }
  }

  /**
   * @see DTMIterator#setRoot(int, Object)
   */
  public void setRoot(int nodeHandle, Object environment)
  {
        // If root is DTM.NULL, then something's wrong with the context
        if (nodeHandle == DTM.NULL)
        {
            throw new RuntimeException("Unable to evaluate expression using " +
                    "this context");
        }

        if(null != m_iter)
        {
                XPathContext xctxt = (XPathContext)environment;
                m_dtmMgr = xctxt.getDTMManager();
                m_iter.setRoot(nodeHandle, environment);
                if(!m_iter.isDocOrdered())
                {
                        if(!hasCache())
                                setShouldCacheNodes(true);
                        runTo(-1);
                        m_next=0;
                }
        }
        else
                assertion(false, "Can not setRoot on a non-iterated NodeSequence!");
  }

  /**
   * @see DTMIterator#reset()
   */
  public void reset()
  {
        m_next = 0;
        // not resetting the iterator on purpose!!!
  }

  /**
   * @see DTMIterator#getWhatToShow()
   */
  public int getWhatToShow()
  {
    return hasCache() ? (DTMFilter.SHOW_ALL & ~DTMFilter.SHOW_ENTITY_REFERENCE)
        : m_iter.getWhatToShow();
  }

  /**
   * @see DTMIterator#getExpandEntityReferences()
   */
  public boolean getExpandEntityReferences()
  {
        if(null != m_iter)
                return m_iter.getExpandEntityReferences();
        else
        return true;
  }

  /**
   * @see DTMIterator#nextNode()
   */
  public int nextNode()
  {
    // If the cache is on, and the node has already been found, then
    // just return from the list.
    NodeVector vec = getVector();
    if (null != vec)
    {
        if(m_next < vec.size())
        {
                        int next = vec.elementAt(m_next);
                m_next++;
                return next;
        }
        else if((-1 != m_last) || (null == m_iter))
        {
                m_next++;
                return DTM.NULL;
        }
    }

  if (null == m_iter)
    return DTM.NULL;

        int next = m_iter.nextNode();
    if(DTM.NULL != next)
    {
        if(hasCache())
        {
                if(m_iter.isDocOrdered())
            {
                        getVector().addElement(next);
                        m_next++;
                }
                else
                {
                        int insertIndex = addNodeInDocOrder(next);
                        if(insertIndex >= 0)
                                m_next++;
                }
        }
        else
                m_next++;
    }
    else
    {
        m_last = m_next;
        m_next++;
    }

    return next;
  }

  /**
   * @see DTMIterator#previousNode()
   */
  public int previousNode()
  {
        if(hasCache())
        {
                if(m_next <= 0)
                        return DTM.NULL;
                else
                {
                        m_next--;
                        return item(m_next);
                }
        }
        else
        {
            int n = m_iter.previousNode();
            m_next = m_iter.getCurrentPos();
            return m_next;
        }
  }

  /**
   * @see DTMIterator#detach()
   */
  public void detach()
  {
        if(null != m_iter)
                m_iter.detach();
        super.detach();
  }

  /**
   * Calling this with a value of false will cause the nodeset
   * to be cached.
   * @see DTMIterator#allowDetachToRelease(boolean)
   */
  public void allowDetachToRelease(boolean allowRelease)
  {
        if((false == allowRelease) && !hasCache())
        {
                setShouldCacheNodes(true);
        }

        if(null != m_iter)
                m_iter.allowDetachToRelease(allowRelease);
        super.allowDetachToRelease(allowRelease);
  }

  /**
   * @see DTMIterator#getCurrentNode()
   */
  public int getCurrentNode()
  {
        if(hasCache())
        {
                int currentIndex = m_next-1;
                NodeVector vec = getVector();
                if((currentIndex >= 0) && (currentIndex < vec.size()))
                        return vec.elementAt(currentIndex);
                else
                        return DTM.NULL;
        }

        if(null != m_iter)
        {
        return m_iter.getCurrentNode();
        }
        else
                return DTM.NULL;
  }

  /**
   * @see DTMIterator#isFresh()
   */
  public boolean isFresh()
  {
    return (0 == m_next);
  }

  /**
   * @see DTMIterator#setShouldCacheNodes(boolean)
   */
  public void setShouldCacheNodes(boolean b)
  {
    if (b)
    {
      if(!hasCache())
      {
        SetVector(new NodeVector());
      }
//        else
//          getVector().RemoveAllNoClear();  // Is this good?
    }
    else
      SetVector(null);
  }

  /**
   * @see DTMIterator#isMutable()
   */
  public boolean isMutable()
  {
    return hasCache(); // though may be surprising if it also has an iterator!
  }

  /**
   * @see DTMIterator#getCurrentPos()
   */
  public int getCurrentPos()
  {
    return m_next;
  }

  /**
   * @see DTMIterator#runTo(int)
   */
  public void runTo(int index)
  {
    int n;

    if (-1 == index)
    {
      int pos = m_next;
      while (DTM.NULL != (n = nextNode()));
      m_next = pos;
    }
    else if(m_next == index)
    {
      return;
    }
    else if(hasCache() && m_next < getVector().size())
    {
      m_next = index;
    }
    else if((null == getVector()) && (index < m_next))
    {
      while ((m_next >= index) && DTM.NULL != (n = previousNode()));
    }
    else
    {
      while ((m_next < index) && DTM.NULL != (n = nextNode()));
    }

  }

  /**
   * @see DTMIterator#setCurrentPos(int)
   */
  public void setCurrentPos(int i)
  {
        runTo(i);
  }

  /**
   * @see DTMIterator#item(int)
   */
  public int item(int index)
  {
        setCurrentPos(index);
        int n = nextNode();
        m_next = index;
        return n;
  }

  /**
   * @see DTMIterator#setItem(int, int)
   */
  public void setItem(int node, int index)
  {
        NodeVector vec = getVector();
        if(null != vec)
        {
                vec.setElementAt(node, index);
                m_last = vec.size();
        }
        else
                m_iter.setItem(node, index);
  }

  /**
   * @see DTMIterator#getLength()
   */
  public int getLength()
  {
        if(hasCache())
        {
        // If this NodeSequence wraps a mutable nodeset, then
        // m_last will not reflect the size of the nodeset if
        // it has been mutated...
        if (m_iter instanceof NodeSetDTM)
        {
            return m_iter.getLength();
        }

                if(-1 == m_last)
                {
                        int pos = m_next;
                        runTo(-1);
                        m_next = pos;
                }
            return m_last;
        }
        else
        {
                return (-1 == m_last) ? (m_last = m_iter.getLength()) : m_last;
        }
  }

  /**
   * Note: Not a deep clone.
   * @see DTMIterator#cloneWithReset()
   */
  public DTMIterator cloneWithReset() throws CloneNotSupportedException
  {
        NodeSequence seq = (NodeSequence)super.clone();
    seq.m_next = 0;
    return seq;
  }

  /**
   * Get a clone of this iterator, but don't reset the iteration in the
   * process, so that it may be used from the current position.
   * Note: Not a deep clone.
   *
   * @return A clone of this object.
   *
   * @throws CloneNotSupportedException
   */
  public Object clone() throws CloneNotSupportedException
  {
          NodeSequence clone = (NodeSequence) super.clone();
          if (null != m_iter) clone.m_iter = (DTMIterator) m_iter.clone();
          return clone;
  }


  /**
   * @see DTMIterator#isDocOrdered()
   */
  public boolean isDocOrdered()
  {
        if(null != m_iter)
                return m_iter.isDocOrdered();
        else
        return true; // can't be sure?
  }

  /**
   * @see DTMIterator#getAxis()
   */
  public int getAxis()
  {
        if(null != m_iter)
        return m_iter.getAxis();
    else
    {
        assertion(false, "Can not getAxis from a non-iterated node sequence!");
        return 0;
    }
  }

  /**
   * @see PathComponent#getAnalysisBits()
   */
  public int getAnalysisBits()
  {
        if((null != m_iter) && (m_iter instanceof PathComponent))
        return ((PathComponent)m_iter).getAnalysisBits();
    else
        return 0;
  }

  /**
   * @see com.sun.org.apache.xpath.internal.Expression#fixupVariables(Vector, int)
   */
  public void fixupVariables(Vector vars, int globalsSize)
  {
        super.fixupVariables(vars, globalsSize);
  }

  /**
   * Add the node into a vector of nodes where it should occur in
   * document order.
   * @param node The node to be added.
   * @return insertIndex.
   * @throws RuntimeException thrown if this NodeSetDTM is not of
   * a mutable type.
   */
   protected int addNodeInDocOrder(int node)
   {
      assertion(hasCache(), "addNodeInDocOrder must be done on a mutable sequence!");

      int insertIndex = -1;

      NodeVector vec = getVector();

      // This needs to do a binary search, but a binary search
      // is somewhat tough because the sequence test involves
      // two nodes.
      int size = vec.size(), i;

      for (i = size - 1; i >= 0; i--)
      {
        int child = vec.elementAt(i);

        if (child == node)
        {
          i = -2; // Duplicate, suppress insert

          break;
        }

        DTM dtm = m_dtmMgr.getDTM(node);
        if (!dtm.isNodeAfter(node, child))
        {
          break;
        }
      }

      if (i != -2)
      {
        insertIndex = i + 1;

        vec.insertElementAt(node, insertIndex);
      }

      // checkDups();
      return insertIndex;
    } // end addNodeInDocOrder(Vector v, Object obj)
}
