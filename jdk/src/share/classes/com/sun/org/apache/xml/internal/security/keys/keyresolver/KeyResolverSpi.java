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
package com.sun.org.apache.xml.internal.security.keys.keyresolver;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import javax.crypto.SecretKey;

import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import org.w3c.dom.Element;

/**
 * This class is abstract class for a child KeyInfo Elemnet.
 *
 * If you want the your KeyResolver, at firstly you must extand this class, and register
 * as following in config.xml
 * <PRE>
 *  &lt;KeyResolver URI="http://www.w3.org/2000/09/xmldsig#KeyValue"
 *   JAVACLASS="MyPackage.MyKeyValueImpl"//gt;
 * </PRE>
 *
 * @author $Author: mullan $
 * @version $Revision: 1.5 $
 */
public abstract class KeyResolverSpi {
   /**
    * This method helps the {@link com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolver} to decide whether a
    * {@link com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolverSpi} is able to perform the requested action.
    *
    * @param element
    * @param BaseURI
    * @param storage
    * @return
    */
   public boolean engineCanResolve(Element element, String BaseURI,
                                                    StorageResolver storage) {
           throw new UnsupportedOperationException();
   }

   /**
    * Method engineResolvePublicKey
    *
    * @param element
    * @param BaseURI
    * @param storage
    * @return resolved public key from the registered from the element.
    *
    * @throws KeyResolverException
    */
   public PublicKey engineResolvePublicKey(
      Element element, String BaseURI, StorageResolver storage)
         throws KeyResolverException {
           throw new UnsupportedOperationException();
    };

   /**
    * Method engineResolvePublicKey
    *
    * @param element
    * @param BaseURI
    * @param storage
    * @return resolved public key from the registered from the element.
    *
    * @throws KeyResolverException
    */
    public PublicKey engineLookupAndResolvePublicKey(
      Element element, String BaseURI, StorageResolver storage)
         throws KeyResolverException {
        KeyResolverSpi tmp = cloneIfNeeded();
        if (!tmp.engineCanResolve(element, BaseURI, storage))
                return null;
            return tmp.engineResolvePublicKey(element, BaseURI, storage);
    }

    private KeyResolverSpi cloneIfNeeded() throws KeyResolverException {
        KeyResolverSpi tmp=this;
        if (globalResolver) {
                try {
                        tmp = (KeyResolverSpi) getClass().newInstance();
                } catch (InstantiationException e) {
                        throw new KeyResolverException("",e);
                } catch (IllegalAccessException e) {
                        throw new KeyResolverException("",e);
                }
        }
        return tmp;
    }

    /**
     * Method engineResolveCertificate
     *
     * @param element
     * @param BaseURI
     * @param storage
     * @return resolved X509Certificate key from the registered from the elements
     *
     * @throws KeyResolverException
     */
    public X509Certificate engineResolveX509Certificate(
       Element element, String BaseURI, StorageResolver storage)
          throws KeyResolverException{
                   throw new UnsupportedOperationException();
    };

   /**
    * Method engineResolveCertificate
    *
    * @param element
    * @param BaseURI
    * @param storage
    * @return resolved X509Certificate key from the registered from the elements
    *
    * @throws KeyResolverException
    */
    public X509Certificate engineLookupResolveX509Certificate(
      Element element, String BaseURI, StorageResolver storage)
         throws KeyResolverException {
        KeyResolverSpi tmp = cloneIfNeeded();
        if (!tmp.engineCanResolve(element, BaseURI, storage))
                return null;
        return tmp.engineResolveX509Certificate(element, BaseURI, storage);

    }
    /**
     * Method engineResolveSecretKey
     *
     * @param element
     * @param BaseURI
     * @param storage
     * @return resolved SecretKey key from the registered from the elements
     *
     * @throws KeyResolverException
     */
    public SecretKey engineResolveSecretKey(
       Element element, String BaseURI, StorageResolver storage)
          throws KeyResolverException{
                   throw new UnsupportedOperationException();
    };

   /**
    * Method engineResolveSecretKey
    *
    * @param element
    * @param BaseURI
    * @param storage
    * @return resolved SecretKey key from the registered from the elements
    *
    * @throws KeyResolverException
    */
   public SecretKey engineLookupAndResolveSecretKey(
      Element element, String BaseURI, StorageResolver storage)
         throws KeyResolverException {
           KeyResolverSpi tmp = cloneIfNeeded();
           if (!tmp.engineCanResolve(element, BaseURI, storage))
                   return null;
                return tmp.engineResolveSecretKey(element, BaseURI, storage);
   }

   /** Field _properties */
   protected java.util.Map _properties = null;

   protected boolean globalResolver=false;

   /**
    * Method engineSetProperty
    *
    * @param key
    * @param value
    */
   public void engineSetProperty(String key, String value) {
           if (_properties==null)
                   _properties=new HashMap();
      this._properties.put(key, value);
   }

   /**
    * Method engineGetProperty
    *
    * @param key
    * @return obtain the property appointed by key
    */
   public String engineGetProperty(String key) {
           if (_properties==null)
                   return null;

      return (String) this._properties.get(key);
   }

   /**
    * Method understandsProperty
    *
    * @param propertyToTest
    * @return true if understood the property
    */
   public boolean understandsProperty(String propertyToTest) {
           if (_properties==null)
                   return false;

      return  this._properties.get(propertyToTest)!=null;
   }
   public void setGlobalResolver(boolean globalResolver) {
        this.globalResolver = globalResolver;
   }

}
