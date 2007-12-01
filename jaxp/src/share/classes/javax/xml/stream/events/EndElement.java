/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * Copyright (c) 2003 by BEA Systems, Inc. All Rights Reserved.
 */

package javax.xml.stream.events;

import java.util.Iterator;
import javax.xml.namespace.QName;
/**
 * An interface for the end element event.  An EndElement is reported
 * for each End Tag in the document.
 *
 * @author Copyright (c) 2003 by BEA Systems. All Rights Reserved.
 * @see XMLEvent
 * @since 1.6
 */
public interface EndElement extends XMLEvent {

  /**
   * Get the name of this event
   * @return the qualified name of this event
   */
  public QName getName();

  /**
   * Returns an Iterator of namespaces that have gone out
   * of scope.  Returns an empty iterator if no namespaces have gone
   * out of scope.
   * @return an Iterator over Namespace interfaces, or an
   * empty iterator
   */
  public Iterator getNamespaces();

}
