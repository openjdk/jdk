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
package com.sun.org.apache.xml.internal.security.utils.resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import org.w3c.dom.Attr;

/**
 * During reference validation, we have to retrieve resources from somewhere.
 * This is done by retrieving a Resolver. The resolver needs two arguments: The
 * URI in which the link to the new resource is defined and the BaseURI of the
 * file/entity in which the URI occurs (the BaseURI is the same as the SystemId.
 *
 * <UL xml:lang="DE" LANG="DE">
 * <LI> Verschiedene Implementierungen k??nnen sich als Resolver registrieren.
 * <LI> Standardm????ig werden erste Implementierungen auf dem XML config file registrirt.
 * <LI> Der Benutzer kann bei Bedarf Implementierungen voranstellen oder anf??gen.
 * <LI> Implementierungen k??nnen mittels Features customized werden ??
 *      (z.B. um Proxy-Passworter ??bergeben zu k??nnen).
 * <LI> Jede Implementierung bekommt das URI Attribut und den Base URI
 *      ??bergeben und muss antworten, ob sie aufl??sen kann.
 * <LI> Die erste Implementierung, die die Aufgabe erf??llt, f??hrt die Aufl??sung durch.
 * </UL>
 *
 * @author $Author: mullan $
 */
public class ResourceResolver {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(ResourceResolver.class.getName());

   /** Field _alreadyInitialized */
   static boolean _alreadyInitialized = false;

   /** these are the system-wide resolvers */
   static List _resolverVector = null;

   static boolean allThreadSafeInList=true;

   /** Field transformSpi */
   protected ResourceResolverSpi _resolverSpi = null;

   /**
    * Constructor ResourceResolver
    *
    * @param className
    * @throws ClassNotFoundException
    * @throws IllegalAccessException
    * @throws InstantiationException
    */
   private ResourceResolver(String className)
           throws ClassNotFoundException, IllegalAccessException,
                  InstantiationException {
      this._resolverSpi =
         (ResourceResolverSpi) Class.forName(className).newInstance();
   }

   /**
    * Constructor ResourceResolver
    *
    * @param resourceResolver
    */
   public ResourceResolver(ResourceResolverSpi resourceResolver) {
      this._resolverSpi = resourceResolver;
   }


   /**
    * Method getInstance
    *
    * @param uri
    * @param BaseURI
    * @return the instnace
    *
    * @throws ResourceResolverException
    */
   public static final ResourceResolver getInstance(Attr uri, String BaseURI)
           throws ResourceResolverException {
      int length=ResourceResolver._resolverVector.size();
      for (int i = 0; i < length; i++) {
                  ResourceResolver resolver =
            (ResourceResolver) ResourceResolver._resolverVector.get(i);
                  ResourceResolver resolverTmp=null;
                  try {
                        resolverTmp =  allThreadSafeInList || resolver._resolverSpi.engineIsThreadSafe() ? resolver :
                                        new ResourceResolver((ResourceResolverSpi)resolver._resolverSpi.getClass().newInstance());
                  } catch (InstantiationException e) {
                          throw new ResourceResolverException("",e,uri,BaseURI);
                  } catch (IllegalAccessException e) {
                          throw new ResourceResolverException("",e,uri,BaseURI);
                  }

         if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "check resolvability by class " + resolver._resolverSpi.getClass().getName());

         if ((resolver != null) && resolverTmp.canResolve(uri, BaseURI)) {
                 if (i!=0) {
                 //update resolver.
                         //System.out.println("Swaping");
                         List resolverVector=(List)((ArrayList)_resolverVector).clone();
                         resolverVector.remove(i);
                         resolverVector.add(0,resolver);
                         _resolverVector=resolverVector;
                 } else {
                         //System.out.println("hitting");
                 }

            return resolverTmp;
         }
      }

      Object exArgs[] = { ((uri != null)
                           ? uri.getNodeValue()
                           : "null"), BaseURI };

      throw new ResourceResolverException("utils.resolver.noClass", exArgs,
                                          uri, BaseURI);
   }
   /**
    * Method getInstance
    *
    * @param uri
    * @param BaseURI
    * @param individualResolvers
    * @return the instance
    *
    * @throws ResourceResolverException
    */
   public static final ResourceResolver getInstance(
           Attr uri, String BaseURI, List individualResolvers)
              throws ResourceResolverException {
      if (log.isLoggable(java.util.logging.Level.FINE)) {

        log.log(java.util.logging.Level.FINE, "I was asked to create a ResourceResolver and got " + (individualResolvers==null? 0 : individualResolvers.size()) );
        log.log(java.util.logging.Level.FINE, " extra resolvers to my existing " + ResourceResolver._resolverVector.size() + " system-wide resolvers");
      }

      // first check the individual Resolvers
          int size=0;
      if ((individualResolvers != null) && ((size=individualResolvers.size()) > 0)) {
         for (int i = 0; i < size; i++) {
            ResourceResolver resolver =
               (ResourceResolver) individualResolvers.get(i);

            if (resolver != null) {
               String currentClass = resolver._resolverSpi.getClass().getName();
               if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "check resolvability by class " + currentClass);

               if (resolver.canResolve(uri, BaseURI)) {
                  return resolver;
               }
            }
         }
      }

          return getInstance(uri,BaseURI);
   }

   /**
    * The init() function is called by com.sun.org.apache.xml.internal.security.Init.init()
    */
   public static void init() {

      if (!ResourceResolver._alreadyInitialized) {
         ResourceResolver._resolverVector = new ArrayList(10);
         _alreadyInitialized = true;
      }
   }

    /**
     * Registers a ResourceResolverSpi class. This method logs a warning if
     * the class cannot be registered.
     *
     * @param className the name of the ResourceResolverSpi class to be
     *    registered
     */
    public static void register(String className) {
        register(className, false);
    }

    /**
     * Registers a ResourceResolverSpi class at the beginning of the provider
     * list. This method logs a warning if the class cannot be registered.
     *
     * @param className the name of the ResourceResolverSpi class to be
     *    registered
     */
    public static void registerAtStart(String className) {
        register(className, true);
    }

    private static void register(String className, boolean start) {
        try {
            ResourceResolver resolver = new ResourceResolver(className);
            if (start) {
                ResourceResolver._resolverVector.add(0, resolver);
                log.log(java.util.logging.Level.FINE, "registered resolver");
            } else {
                ResourceResolver._resolverVector.add(resolver);
            }
            if (!resolver._resolverSpi.engineIsThreadSafe()) {
                allThreadSafeInList=false;
        }
        } catch (Exception e) {
            log.log(java.util.logging.Level.WARNING, "Error loading resolver " + className +" disabling it");
        } catch (NoClassDefFoundError e) {
            log.log(java.util.logging.Level.WARNING, "Error loading resolver " + className +" disabling it");
        }
    }

   /**
    * Method resolve
    *
    * @param uri
    * @param BaseURI
    * @return the resource
    *
    * @throws ResourceResolverException
    */
   public static XMLSignatureInput resolveStatic(Attr uri, String BaseURI)
           throws ResourceResolverException {

      ResourceResolver myResolver = ResourceResolver.getInstance(uri, BaseURI);

      return myResolver.resolve(uri, BaseURI);
   }

   /**
    * Method resolve
    *
    * @param uri
    * @param BaseURI
    * @return the resource
    *
    * @throws ResourceResolverException
    */
   public XMLSignatureInput resolve(Attr uri, String BaseURI)
           throws ResourceResolverException {
      return this._resolverSpi.engineResolve(uri, BaseURI);
   }

   /**
    * Method setProperty
    *
    * @param key
    * @param value
    */
   public void setProperty(String key, String value) {
      this._resolverSpi.engineSetProperty(key, value);
   }

   /**
    * Method getProperty
    *
    * @param key
    * @return the value of the property
    */
   public String getProperty(String key) {
      return this._resolverSpi.engineGetProperty(key);
   }

   /**
    * Method addProperties
    *
    * @param properties
    */
   public void addProperties(Map properties) {
      this._resolverSpi.engineAddProperies(properties);
   }

   /**
    * Method getPropertyKeys
    *
    * @return all property keys.
    */
   public String[] getPropertyKeys() {
      return this._resolverSpi.engineGetPropertyKeys();
   }

   /**
    * Method understandsProperty
    *
    * @param propertyToTest
    * @return true if the resolver understands the property
    */
   public boolean understandsProperty(String propertyToTest) {
      return this._resolverSpi.understandsProperty(propertyToTest);
   }

   /**
    * Method canResolve
    *
    * @param uri
    * @param BaseURI
    * @return true if it can resolve the uri
    */
   private boolean canResolve(Attr uri, String BaseURI) {
      return this._resolverSpi.engineCanResolve(uri, BaseURI);
   }
}
