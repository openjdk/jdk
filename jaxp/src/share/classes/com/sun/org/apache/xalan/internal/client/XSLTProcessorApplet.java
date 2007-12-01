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
 * $Id: XSLTProcessorApplet.java,v 1.2.4.1 2005/09/15 02:20:05 jeffsuttor Exp $
 */
package com.sun.org.apache.xalan.internal.client;

import java.applet.Applet;
import java.awt.Graphics;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Enumeration;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.sun.org.apache.xalan.internal.res.XSLMessages;
import com.sun.org.apache.xalan.internal.res.XSLTErrorResources;

/**
 * Provides applet host for the XSLT processor. To perform transformations on an HTML client:
 * <ol>
 * <li>Use an &lt;applet&gt; tag to embed this applet in the HTML client.</li>
 * <li>Use the DocumentURL and StyleURL PARAM tags or the {@link #setDocumentURL} and
 * {@link #setStyleURL} methods to specify the XML source document and XSL stylesheet.</li>
 * <li>Call the {@link #getHtmlText} method (or one of the transformToHtml() methods)
 * to perform the transformation and return the result as a String.</li>
 * </ol>
 *
 * This class extends Applet which ultimately causes this class to implement Serializable.
 * This is a serious restriction on this class. All fields that are not transient and not
 * static are written-out/read-in during serialization. So even private fields essentially
 * become part of the API. Developers need to take care when modifying fields.
 * @xsl.usage general
 */
public class XSLTProcessorApplet extends Applet
{

  /**
   * The stylesheet processor.
   * This field is now transient because a
   * javax.xml.transform.TransformerFactory from JAXP
   * makes no claims to be serializable.
   */
  transient TransformerFactory m_tfactory = null;

  /**
   * @serial
   */
  private String m_styleURL;

  /**
   * @serial
   */
  private String m_documentURL;

  // Parameter names.  To change a name of a parameter, you need only make
  // a single change.  Simply modify the value of the parameter string below.
  //--------------------------------------------------------------------------

  /**
   * @serial
   */
  private final String PARAM_styleURL = "styleURL";

  /**
   * @serial
   */
  private final String PARAM_documentURL = "documentURL";


  // We'll keep the DOM trees around, so tell which trees
  // are cached.

  /**
   * @serial
   */
  private String m_styleURLOfCached = null;

  /**
   * @serial
   */
  private String m_documentURLOfCached = null;

  /**
   * Save this for use on the worker thread; may not be necessary.
   * @serial
   */
  private URL m_codeBase = null;

  /**
   * @serial
   */
  private String m_treeURL = null;

  /**
   * DocumentBase URL
   * @serial
   */
  private URL m_documentBase = null;

  /**
   * Thread stuff for the trusted worker thread.
   */
  transient private Thread m_callThread = null;

  /**
   */
  transient private TrustedAgent m_trustedAgent = null;

  /**
   * Thread for running TrustedAgent.
   */
  transient private Thread m_trustedWorker = null;

  /**
   * Where the worker thread puts the HTML text.
   */
  transient private String m_htmlText = null;

  /**
   * Where the worker thread puts the document/stylesheet text.
   */
  transient private String m_sourceText = null;

  /**
   * Stylesheet attribute name and value that the caller can set.
   */
  transient private String m_nameOfIDAttrOfElemToModify = null;

  /**
   */
  transient private String m_elemIdToModify = null;

  /**
   */
  transient private String m_attrNameToSet = null;

  /**
   */
  transient private String m_attrValueToSet = null;

  /**
   * The XSLTProcessorApplet constructor takes no arguments.
   */
  public XSLTProcessorApplet(){}

  /**
   * Get basic information about the applet
   * @return A String with the applet name and author.
   */
  public String getAppletInfo()
  {
    return "Name: XSLTProcessorApplet\r\n" + "Author: Scott Boag";
  }

  /**
   * Get descriptions of the applet parameters.
   * @return A two-dimensional array of Strings with Name, Type, and Description
   * for each parameter.
   */
  public String[][] getParameterInfo()
  {

    String[][] info =
    {
      { PARAM_styleURL, "String", "URL to an XSL stylesheet" },
      { PARAM_documentURL, "String", "URL to an XML document" },
    };

    return info;
  }

  /**
   * Standard applet initialization.
   */
  public void init()
  {

    // PARAMETER SUPPORT
    //          The following code retrieves the value of each parameter
    // specified with the <PARAM> tag and stores it in a member
    // variable.
    //----------------------------------------------------------------------
    String param;

    // styleURL: Parameter description
    //----------------------------------------------------------------------
    param = getParameter(PARAM_styleURL);

    // stylesheet parameters
    m_parameters = new Hashtable();

    if (param != null)
      setStyleURL(param);

    // documentURL: Parameter description
    //----------------------------------------------------------------------
    param = getParameter(PARAM_documentURL);

    if (param != null)
      setDocumentURL(param);

    m_codeBase = this.getCodeBase();
    m_documentBase = this.getDocumentBase();

    // If you use a ResourceWizard-generated "control creator" class to
    // arrange controls in your applet, you may want to call its
    // CreateControls() method from within this method. Remove the following
    // call to resize() before adding the call to CreateControls();
    // CreateControls() does its own resizing.
    //----------------------------------------------------------------------
    resize(320, 240);
  }

    /**
   *  Automatically called when the HTML client containing the applet loads.
   *  This method starts execution of the applet thread.
   */
  public void start()
  {
      //check if user code's on the stack before invoking the worker thread
     boolean passed = false;
     try {
       java.security.AccessController.checkPermission(new java.security.AllPermission());
     } catch (SecurityException se) {
         //expected
         passed = true;
     }
     if (!passed) {
         throw new SecurityException("The XSLTProcessorApplet class must be extended and its method start() overridden.");
     }

    m_trustedAgent = new TrustedAgent();
    Thread currentThread = Thread.currentThread();
    m_trustedWorker = new Thread(currentThread.getThreadGroup(),
                                 m_trustedAgent);
    m_trustedWorker.start();
    try
    {
      m_tfactory = TransformerFactory.newInstance();
      this.showStatus("Causing Transformer and Parser to Load and JIT...");

      // Prime the pump so that subsequent transforms are faster.
      StringReader xmlbuf = new StringReader("<?xml version='1.0'?><foo/>");
      StringReader xslbuf = new StringReader(
        "<?xml version='1.0'?><xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'><xsl:template match='foo'><out/></xsl:template></xsl:stylesheet>");
      PrintWriter pw = new PrintWriter(new StringWriter());

      synchronized (m_tfactory)
      {
        Templates templates = m_tfactory.newTemplates(new StreamSource(xslbuf));
        Transformer transformer = templates.newTransformer();
        transformer.transform(new StreamSource(xmlbuf), new StreamResult(pw));
      }
      System.out.println("Primed the pump!");
      this.showStatus("Ready to go!");
    }
    catch (Exception e)
    {
      this.showStatus("Could not prime the pump!");
      System.out.println("Could not prime the pump!");
      e.printStackTrace();
    }
  }

  /**
   * Do not call; this applet contains no UI or visual components.
   *
   */
  public void paint(Graphics g){}

  /**
   * Automatically called when the HTML page containing the applet is no longer
   * on the screen. Stops execution of the applet thread.
   */
  public void stop()
  {
    if (null != m_trustedWorker)
    {
      m_trustedWorker.stop();

      // m_trustedWorker.destroy();
      m_trustedWorker = null;
    }

    m_styleURLOfCached = null;
    m_documentURLOfCached = null;
  }

  /**
   * Cleanup; called when applet is terminated and unloaded.
   */
  public void destroy()
  {
    if (null != m_trustedWorker)
    {
      m_trustedWorker.stop();

      // m_trustedWorker.destroy();
      m_trustedWorker = null;
    }
    m_styleURLOfCached = null;
    m_documentURLOfCached = null;
  }

  /**
   * Set the URL to the XSL stylesheet that will be used
   * to transform the input XML.  No processing is done yet.
   * @param urlString valid URL string for XSL stylesheet.
   */
  public void setStyleURL(String urlString)
  {
    m_styleURL = urlString;
  }

  /**
   * Set the URL to the XML document that will be transformed
   * with the XSL stylesheet.  No processing is done yet.
   * @param urlString valid URL string for XML document.
   */
  public void setDocumentURL(String urlString)
  {
    m_documentURL = urlString;
  }

  /**
   * The processor keeps a cache of the source and
   * style trees, so call this method if they have changed
   * or you want to do garbage collection.
   */
  public void freeCache()
  {
    m_styleURLOfCached = null;
    m_documentURLOfCached = null;
  }

  /**
   * Set an attribute in the stylesheet, which gives the ability
   * to have some dynamic selection control.
   * @param nameOfIDAttrOfElemToModify The name of an attribute to search for a unique id.
   * @param elemId The unique ID to look for.
   * @param attrName Once the element is found, the name of the attribute to set.
   * @param value The value to set the attribute to.
   */
  public void setStyleSheetAttribute(String nameOfIDAttrOfElemToModify,
                                     String elemId, String attrName,
                                     String value)
  {
    m_nameOfIDAttrOfElemToModify = nameOfIDAttrOfElemToModify;
    m_elemIdToModify = elemId;
    m_attrNameToSet = attrName;
    m_attrValueToSet = value;
  }


  /**
   * Stylesheet parameter key/value pair stored in a hashtable
   */
  transient Hashtable m_parameters;

  /**
   * Submit a stylesheet parameter.
   *
   * @param key stylesheet parameter key
   * @param expr the parameter expression to be submitted.
   * @see javax.xml.transform.Transformer#setParameter(String,Object)
   */
  public void setStylesheetParam(String key, String expr)
  {
    m_parameters.put(key, expr);
  }

  /**
   * Given a String containing markup, escape the markup so it
   * can be displayed in the browser.
   *
   * @param s String to escape
   *
   * The escaped string.
   */
  public String escapeString(String s)
  {
    StringBuffer sb = new StringBuffer();
    int length = s.length();

    for (int i = 0; i < length; i++)
    {
      char ch = s.charAt(i);

      if ('<' == ch)
      {
        sb.append("&lt;");
      }
      else if ('>' == ch)
      {
        sb.append("&gt;");
      }
      else if ('&' == ch)
      {
        sb.append("&amp;");
      }
      else if (0xd800 <= ch && ch < 0xdc00)
      {
        // UTF-16 surrogate
        int next;

        if (i + 1 >= length)
        {
          throw new RuntimeException(
            XSLMessages.createMessage(
              XSLTErrorResources.ER_INVALID_UTF16_SURROGATE,
              new Object[]{ Integer.toHexString(ch) }));  //"Invalid UTF-16 surrogate detected: "

          //+Integer.toHexString(ch)+ " ?");
        }
        else
        {
          next = s.charAt(++i);

          if (!(0xdc00 <= next && next < 0xe000))
            throw new RuntimeException(
              XSLMessages.createMessage(
                XSLTErrorResources.ER_INVALID_UTF16_SURROGATE,
                new Object[]{
                  Integer.toHexString(ch) + " "
                  + Integer.toHexString(next) }));  //"Invalid UTF-16 surrogate detected: "

          //+Integer.toHexString(ch)+" "+Integer.toHexString(next));
          next = ((ch - 0xd800) << 10) + next - 0xdc00 + 0x00010000;
        }
        sb.append("&#x");
        sb.append(Integer.toHexString(next));
        sb.append(";");
      }
      else
      {
        sb.append(ch);
      }
    }
    return sb.toString();
  }

  /**
   * Assuming the stylesheet URL and the input XML URL have been set,
   * perform the transformation and return the result as a String.
   *
   * @return A string that contains the contents pointed to by the URL.
   *
   */
  public String getHtmlText()
  {
    m_trustedAgent.m_getData = true;
    m_callThread = Thread.currentThread();
    try
    {
      synchronized (m_callThread)
      {
        m_callThread.wait();
      }
    }
    catch (InterruptedException ie)
    {
      System.out.println(ie.getMessage());
    }
    return m_htmlText;
  }

  /**
   * Get an XML document (or stylesheet)
   *
   * @param treeURL valid URL string for the document.
   *
   * @return document
   *
   * @throws IOException
   */
  public String getTreeAsText(String treeURL) throws IOException
  {
    m_treeURL = treeURL;
    m_trustedAgent.m_getData = true;
    m_trustedAgent.m_getSource = true;
    m_callThread = Thread.currentThread();
    try
    {
      synchronized (m_callThread)
      {
        m_callThread.wait();
      }
    }
    catch (InterruptedException ie)
    {
      System.out.println(ie.getMessage());
    }
    return m_sourceText;
  }

  /**
   * Use a Transformer to copy the source document
   * to a StreamResult.
   *
   * @return the document as a string
   */
  private String getSource() throws TransformerException
  {
    StringWriter osw = new StringWriter();
    PrintWriter pw = new PrintWriter(osw, false);
    String text = "";
    try
    {
      URL docURL = new URL(m_documentBase, m_treeURL);
      synchronized (m_tfactory)
      {
        Transformer transformer = m_tfactory.newTransformer();
        StreamSource source = new StreamSource(docURL.toString());
        StreamResult result = new StreamResult(pw);
        transformer.transform(source, result);
        text = osw.toString();
      }
    }
    catch (MalformedURLException e)
    {
      e.printStackTrace();
      throw new RuntimeException(e.getMessage());
    }
    catch (Exception any_error)
    {
      any_error.printStackTrace();
    }
    return text;
  }

  /**
   * Get the XML source Tree as a text string suitable
   * for display in a browser.  Note that this is for display of the
   * XML itself, not for rendering of HTML by the browser.
   *
   * @return XML source document as a string.
   * @throws Exception thrown if tree can not be converted.
   */
  public String getSourceTreeAsText() throws Exception
  {
    return getTreeAsText(m_documentURL);
  }

  /**
   * Get the XSL style Tree as a text string suitable
   * for display in a browser.  Note that this is for display of the
   * XML itself, not for rendering of HTML by the browser.
   *
   * @return The XSL stylesheet as a string.
   * @throws Exception thrown if tree can not be converted.
   */
  public String getStyleTreeAsText() throws Exception
  {
    return getTreeAsText(m_styleURL);
  }

  /**
   * Get the HTML result Tree as a text string suitable
   * for display in a browser.  Note that this is for display of the
   * XML itself, not for rendering of HTML by the browser.
   *
   * @return Transformation result as unmarked text.
   * @throws Exception thrown if tree can not be converted.
   */
  public String getResultTreeAsText() throws Exception
  {
    return escapeString(getHtmlText());
  }

  /**
   * Process a document and a stylesheet and return
   * the transformation result.  If one of these is null, the
   * existing value (of a previous transformation) is not affected.
   *
   * @param doc URL string to XML document
   * @param style URL string to XSL stylesheet
   *
   * @return HTML transformation result
   */
  public String transformToHtml(String doc, String style)
  {

    if (null != doc)
    {
      m_documentURL = doc;
    }

    if (null != style)
    {
      m_styleURL = style;
    }

    return getHtmlText();
  }

  /**
   * Process a document and a stylesheet and return
   * the transformation result. Use the xsl:stylesheet PI to find the
   * document, if one exists.
   *
   * @param doc  URL string to XML document containing an xsl:stylesheet PI.
   *
   * @return HTML transformation result
   */
  public String transformToHtml(String doc)
  {

    if (null != doc)
    {
      m_documentURL = doc;
    }

    m_styleURL = null;

    return getHtmlText();
  }


  /**
   * Process the transformation.
   *
   * @return The transformation result as a string.
   *
   * @throws TransformerException
   */
  private String processTransformation() throws TransformerException
  {
    String htmlData = null;
    this.showStatus("Waiting for Transformer and Parser to finish loading and JITing...");

    synchronized (m_tfactory)
    {
     URL documentURL = null;
      URL styleURL = null;
      StringWriter osw = new StringWriter();
      PrintWriter pw = new PrintWriter(osw, false);
      StreamResult result = new StreamResult(pw);

      this.showStatus("Begin Transformation...");
      try
      {
        documentURL = new URL(m_codeBase, m_documentURL);
        StreamSource xmlSource = new StreamSource(documentURL.toString());

        styleURL = new URL(m_codeBase, m_styleURL);
        StreamSource xslSource = new StreamSource(styleURL.toString());

        Transformer transformer = m_tfactory.newTransformer(xslSource);


        Enumeration m_keys = m_parameters.keys();
        while (m_keys.hasMoreElements()){
          Object key = m_keys.nextElement();
          Object expression = m_parameters.get(key);
          transformer.setParameter((String) key, expression);
        }
        transformer.transform(xmlSource, result);
      }
      catch (TransformerConfigurationException tfe)
      {
        tfe.printStackTrace();
        throw new RuntimeException(tfe.getMessage());
      }
      catch (MalformedURLException e)
      {
        e.printStackTrace();
        throw new RuntimeException(e.getMessage());
      }

      this.showStatus("Transformation Done!");
      htmlData = osw.toString();
    }
    return htmlData;
  }

  /**
   * This class maintains a worker thread that that is
   * trusted and can do things like access data.  You need
   * this because the thread that is called by the browser
   * is not trusted and can't access data from the URLs.
   */
  class TrustedAgent implements Runnable
  {

    /**
     * Specifies whether the worker thread should perform a transformation.
     */
    public boolean m_getData = false;

    /**
     * Specifies whether the worker thread should get an XML document or XSL stylesheet.
     */
    public boolean m_getSource = false;

    /**
     * The real work is done from here.
     *
     */
    public void run()
    {
      while (true)
      {
        m_trustedWorker.yield();

        if (m_getData)  // Perform a transformation or get a document.
        {
          try
          {
            m_getData = false;
            m_htmlText = null;
            m_sourceText = null;
            if (m_getSource)  // Get a document.
            {
              m_getSource = false;
              m_sourceText = getSource();
            }
            else              // Perform a transformation.
              m_htmlText = processTransformation();
          }
          catch (Exception e)
          {
            e.printStackTrace();
          }
          finally
          {
            synchronized (m_callThread)
            {
              m_callThread.notify();
            }
          }
        }
        else
        {
          try
          {
            m_trustedWorker.sleep(50);
          }
          catch (InterruptedException ie)
          {
            ie.printStackTrace();
          }
        }
      }
    }
  }

  // For compatiblity with old serialized objects
  // We will change non-serialized fields and change methods
  // and not have this break us.
  private static final long serialVersionUID=4618876841979251422L;

  // For compatibility when de-serializing old objects
  private void readObject(java.io.ObjectInputStream inStream) throws IOException, ClassNotFoundException
  {
      inStream.defaultReadObject();

      // Needed assignment of non-serialized fields

      // A TransformerFactory is not guaranteed to be serializable,
      // so we create one here
      m_tfactory = TransformerFactory.newInstance();
  }
}
