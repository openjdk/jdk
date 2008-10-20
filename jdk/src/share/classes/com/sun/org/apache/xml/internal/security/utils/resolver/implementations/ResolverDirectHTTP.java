/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright  1999-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.sun.org.apache.xml.internal.security.utils.resolver.implementations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import com.sun.org.apache.xml.internal.utils.URI;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverException;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverSpi;
import org.w3c.dom.Attr;


/**
 * A simple ResourceResolver for HTTP requests. This class handles only 'pure'
 * HTTP URIs which means without a fragment. The Fragment handling is done by the
 * {@link ResolverFragment} class.
 * <BR>
 * If the user has a corporate HTTP proxy which is to be used, the usage can be
 * switched on by setting properties for the resolver:
 * <PRE>
 * resourceResolver.setProperty("http.proxy.host", "proxy.company.com");
 * resourceResolver.setProperty("http.proxy.port", "8080");
 *
 * // if we need a password for the proxy
 * resourceResolver.setProperty("http.proxy.username", "proxyuser3");
 * resourceResolver.setProperty("http.proxy.password", "secretca");
 * </PRE>
 *
 *
 * @author $Author: mullan $
 * @see <A HREF="http://www.javaworld.com/javaworld/javatips/jw-javatip42_p.html">Java Tip 42: Write Java apps that work with proxy-based firewalls</A>
 * @see <A HREF="http://java.sun.com/j2se/1.4/docs/guide/net/properties.html">SUN J2SE docs for network properties</A>
 * @see <A HREF="http://metalab.unc.edu/javafaq/javafaq.html#proxy">The JAVA FAQ Question 9.5: How do I make Java work with a proxy server?</A>
 * $todo$ the proxy behaviour seems not to work; if a on-existing proxy is set, it works ?!?
 */
public class ResolverDirectHTTP extends ResourceResolverSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                            ResolverDirectHTTP.class.getName());

   /** Field properties[] */
   private static final String properties[] =
        { "http.proxy.host", "http.proxy.port",
          "http.proxy.username",
          "http.proxy.password",
          "http.basic.username",
          "http.basic.password" };

   /** Field HttpProxyHost */
   private static final int HttpProxyHost = 0;

   /** Field HttpProxyPort */
   private static final int HttpProxyPort = 1;

   /** Field HttpProxyUser */
   private static final int HttpProxyUser = 2;

   /** Field HttpProxyPass */
   private static final int HttpProxyPass = 3;

   /** Field HttpProxyUser */
   private static final int HttpBasicUser = 4;

   /** Field HttpProxyPass */
   private static final int HttpBasicPass = 5;

   public boolean engineIsThreadSafe() {
           return true;
   }
   /**
    * Method resolve
    *
    * @param uri
    * @param BaseURI
    *
    * @throws ResourceResolverException
    * @return
    * $todo$ calculate the correct URI from the attribute and the BaseURI
    */
   public XMLSignatureInput engineResolve(Attr uri, String BaseURI)
           throws ResourceResolverException {

      try {
         boolean useProxy = false;
         String proxyHost =
            engineGetProperty(ResolverDirectHTTP
               .properties[ResolverDirectHTTP.HttpProxyHost]);
         String proxyPort =
            engineGetProperty(ResolverDirectHTTP
               .properties[ResolverDirectHTTP.HttpProxyPort]);

         if ((proxyHost != null) && (proxyPort != null)) {
            useProxy = true;
         }

         String oldProxySet = null;
         String oldProxyHost = null;
         String oldProxyPort = null;
         // switch on proxy usage
         if (useProxy) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "Use of HTTP proxy enabled: " + proxyHost + ":"
                      + proxyPort);
            }
            oldProxySet = System.getProperty("http.proxySet");
            oldProxyHost = System.getProperty("http.proxyHost");
            oldProxyPort = System.getProperty("http.proxyPort");
            System.setProperty("http.proxySet", "true");
            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", proxyPort);
         }

         boolean switchBackProxy = ((oldProxySet != null)
                                    && (oldProxyHost != null)
                                    && (oldProxyPort != null));

         // calculate new URI
         URI uriNew = getNewURI(uri.getNodeValue(), BaseURI);

         // if the URI contains a fragment, ignore it
         URI uriNewNoFrag = new URI(uriNew);

         uriNewNoFrag.setFragment(null);

         URL url = new URL(uriNewNoFrag.toString());
         URLConnection urlConnection = url.openConnection();

         {

            // set proxy pass
            String proxyUser =
               engineGetProperty(ResolverDirectHTTP
                  .properties[ResolverDirectHTTP.HttpProxyUser]);
            String proxyPass =
               engineGetProperty(ResolverDirectHTTP
                  .properties[ResolverDirectHTTP.HttpProxyPass]);

            if ((proxyUser != null) && (proxyPass != null)) {
               String password = proxyUser + ":" + proxyPass;
               String encodedPassword = Base64.encode(password.getBytes());

               // or was it Proxy-Authenticate ?
               urlConnection.setRequestProperty("Proxy-Authorization",
                                                encodedPassword);
            }
         }

         {

            // check if Basic authentication is required
            String auth = urlConnection.getHeaderField("WWW-Authenticate");

            if (auth != null) {

               // do http basic authentication
               if (auth.startsWith("Basic")) {
                  String user =
                     engineGetProperty(ResolverDirectHTTP
                        .properties[ResolverDirectHTTP.HttpBasicUser]);
                  String pass =
                     engineGetProperty(ResolverDirectHTTP
                        .properties[ResolverDirectHTTP.HttpBasicPass]);

                  if ((user != null) && (pass != null)) {
                     urlConnection = url.openConnection();

                     String password = user + ":" + pass;
                     String encodedPassword =
                        Base64.encode(password.getBytes());

                     // set authentication property in the http header
                     urlConnection.setRequestProperty("Authorization",
                                                      "Basic "
                                                      + encodedPassword);
                  }
               }
            }
         }

         String mimeType = urlConnection.getHeaderField("Content-Type");
         InputStream inputStream = urlConnection.getInputStream();
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         byte buf[] = new byte[4096];
         int read = 0;
         int summarized = 0;

         while ((read = inputStream.read(buf)) >= 0) {
            baos.write(buf, 0, read);

            summarized += read;
         }

         log.log(java.util.logging.Level.FINE, "Fetched " + summarized + " bytes from URI "
                   + uriNew.toString());

         XMLSignatureInput result = new XMLSignatureInput(baos.toByteArray());

         // XMLSignatureInput result = new XMLSignatureInput(inputStream);
         result.setSourceURI(uriNew.toString());
         result.setMIMEType(mimeType);

         // switch off proxy usage
         if (useProxy && switchBackProxy) {
            System.setProperty("http.proxySet", oldProxySet);
            System.setProperty("http.proxyHost", oldProxyHost);
            System.setProperty("http.proxyPort", oldProxyPort);
         }

         return result;
      } catch (MalformedURLException ex) {
         throw new ResourceResolverException("generic.EmptyMessage", ex, uri,
                                             BaseURI);
      } catch (IOException ex) {
         throw new ResourceResolverException("generic.EmptyMessage", ex, uri,
                                             BaseURI);
      }
   }

   /**
    * We resolve http URIs <I>without</I> fragment...
    *
    * @param uri
    * @param BaseURI
    *  @return true if can be resolved
    */
   public boolean engineCanResolve(Attr uri, String BaseURI) {
      if (uri == null) {
         log.log(java.util.logging.Level.FINE, "quick fail, uri == null");

         return false;
      }

      String uriNodeValue = uri.getNodeValue();

      if (uriNodeValue.equals("") || (uriNodeValue.charAt(0)=='#')) {
         log.log(java.util.logging.Level.FINE, "quick fail for empty URIs and local ones");

         return false;
      }

      if (log.isLoggable(java.util.logging.Level.FINE)) {
         log.log(java.util.logging.Level.FINE, "I was asked whether I can resolve " + uriNodeValue);
      }

      if ( uriNodeValue.startsWith("http:") ||
                                (BaseURI!=null && BaseURI.startsWith("http:") )) {
         if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "I state that I can resolve " + uriNodeValue);
         }

         return true;
      }

      if (log.isLoggable(java.util.logging.Level.FINE)) {
         log.log(java.util.logging.Level.FINE, "I state that I can't resolve " + uriNodeValue);
      }

      return false;
   }

   /**
    * @inheritDoc
    */
   public String[] engineGetPropertyKeys() {
      return (String[]) ResolverDirectHTTP.properties.clone();
   }

   private URI getNewURI(String uri, String BaseURI)
           throws URI.MalformedURIException {

      if ((BaseURI == null) || "".equals(BaseURI)) {
         return new URI(uri);
      }
      return new URI(new URI(BaseURI), uri);
   }
}
