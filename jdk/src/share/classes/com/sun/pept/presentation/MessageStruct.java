/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/** Java interface "MessageStruct.java" generated from Poseidon for UML.
 *  Poseidon for UML is developed by <A HREF="http://www.gentleware.com">Gentleware</A>.
 *  Generated with <A HREF="http://jakarta.apache.org/velocity/">velocity</A> template engine.
 */
package com.sun.pept.presentation;

import java.util.*;
import java.lang.reflect.Method;
/**
 * <p>
 *
 * @author Dr. Harold Carr
 * </p>
 */
public interface MessageStruct {

  ///////////////////////////////////////
  //attributes


/**
 * <p>
 * Represents ...
 * </p>
 */
    public static final int NORMAL_RESPONSE = 0;

/**
 * <p>
 * Represents ...
 * </p>
 */
    public static final int CHECKED_EXCEPTION_RESPONSE = 1;

/**
 * <p>
 * Represents ...
 * </p>
 */
    public static final int UNCHECKED_EXCEPTION_RESPONSE = 2;

/**
 * <p>
 * Represents ...
 * </p>
 */
    public static final int REQUEST_RESPONSE_MEP = 1;

/**
 * <p>
 * Represents ...
 * </p>
 */
    public static final int ONE_WAY_MEP = 2;

/**
 * <p>
 * Represents ...
 * </p>
 */
    public static final int ASYNC_POLL_MEP = 3;

/**
 * <p>
 * Represents ...
 * </p>
 */
    public static final int ASYNC_CALLBACK_MEP = 4;

  ///////////////////////////////////////
  // operations

/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @param data ...
 * </p><p>
 *
 * </p>
 */
    public void setData(Object[] data);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Object[] with ...
 * </p>
 */
    public Object[] getData();
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param name ...
 * </p><p>
 * @param value ...
 * </p>
 */
    public void setMetaData(Object name, Object value);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Object with ...
 * </p><p>
 * @param name ...
 * </p>
 */
    public Object getMetaData(Object name);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param messageExchangePattern ...
 * </p>
 */
    public void setMEP(int messageExchangePattern);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a int with ...
 * </p>
 */
    public int getMEP();
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a int with ...
 * </p>
 */
    public int getResponseType();
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param responseType ...
 * </p>
 */
    public void setResponseType(int responseType);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Object with ...
 * </p>
 */
    public Object getResponse();
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param response ...
 * </p>
 */
    public void setResponse(Object response);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param method ...
 * </p>
 */
    public void setMethod(Method method);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Method with ...
 * </p>
 */
    public Method getMethod();

} // end MessageStruct
