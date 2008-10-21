package com.sun.org.apache.xml.internal.security.utils;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public interface ElementChecker {
         /**
          * Check that the elemnt is the one expect
          *
          * @throws XMLSecurityException
          */
           public void guaranteeThatElementInCorrectSpace(ElementProxy expected, Element actual)
                   throws XMLSecurityException;

           public boolean isNamespaceElement(Node el, String type, String ns);
}
