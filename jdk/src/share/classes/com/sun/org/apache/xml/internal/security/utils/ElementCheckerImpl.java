package com.sun.org.apache.xml.internal.security.utils;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public abstract class ElementCheckerImpl implements ElementChecker {
        public boolean isNamespaceElement(Node el, String type, String ns) {
                if ((el == null) ||
                   ns!=el.getNamespaceURI() || !el.getLocalName().equals(type)){
                   return false;
                }

                return true;
        }
        /** A checker for DOM that interns NS */
        public static class InternedNsChecker extends ElementCheckerImpl{
                public void guaranteeThatElementInCorrectSpace(ElementProxy expected,
                                Element actual) throws XMLSecurityException {

                      String localnameSHOULDBE = expected.getBaseLocalName();
                      String namespaceSHOULDBE = expected.getBaseNamespace();

                      String localnameIS = actual.getLocalName();
                      String namespaceIS = actual.getNamespaceURI();
                      if ((namespaceSHOULDBE!=namespaceIS) ||
                       !localnameSHOULDBE.equals(localnameIS) ) {
                         Object exArgs[] = { namespaceIS +":"+ localnameIS,
                           namespaceSHOULDBE +":"+ localnameSHOULDBE};
                         throw new XMLSecurityException("xml.WrongElement", exArgs);
                      }
                }
        }

        /** A checker for DOM that interns NS */
        public static class FullChecker extends ElementCheckerImpl {
                public void guaranteeThatElementInCorrectSpace(ElementProxy expected,
                                Element actual) throws XMLSecurityException {

                      String localnameSHOULDBE = expected.getBaseLocalName();
                      String namespaceSHOULDBE = expected.getBaseNamespace();

                      String localnameIS = actual.getLocalName();
                      String namespaceIS = actual.getNamespaceURI();
                      if ((!namespaceSHOULDBE.equals(namespaceIS)) ||
                       !localnameSHOULDBE.equals(localnameIS) ) {
                         Object exArgs[] = { namespaceIS +":"+ localnameIS,
                           namespaceSHOULDBE +":"+ localnameSHOULDBE};
                         throw new XMLSecurityException("xml.WrongElement", exArgs);
                      }
                }
        }

        /** An empty checker if schema checking is used */
        public static class EmptyChecker extends ElementCheckerImpl {
                public void guaranteeThatElementInCorrectSpace(ElementProxy expected,
                                Element actual) throws XMLSecurityException {
                }
        }
}
