/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal;


/**
 * Exception constants.
 *
 * @author  <A HREF="http://www.inf.fu-berlin.de/~ehaase">E. Haase</A>
 */
public interface ExceptionConstants {
  /** The mother of all exceptions
   */
  public static final Class THROWABLE = Throwable.class;

  /** Super class of any run-time exception
   */
  public static final Class RUNTIME_EXCEPTION = RuntimeException.class;

  /** Super class of any linking exception (aka Linkage Error)
   */
  public static final Class LINKING_EXCEPTION = LinkageError.class;

  /** Linking Exceptions
   */
  public static final Class CLASS_CIRCULARITY_ERROR         = ClassCircularityError.class;
  public static final Class CLASS_FORMAT_ERROR              = ClassFormatError.class;
  public static final Class EXCEPTION_IN_INITIALIZER_ERROR  = ExceptionInInitializerError.class;
  public static final Class INCOMPATIBLE_CLASS_CHANGE_ERROR = IncompatibleClassChangeError.class;
  public static final Class ABSTRACT_METHOD_ERROR           = AbstractMethodError.class;
  public static final Class ILLEGAL_ACCESS_ERROR            = IllegalAccessError.class;
  public static final Class INSTANTIATION_ERROR             = InstantiationError.class;
  public static final Class NO_SUCH_FIELD_ERROR             = NoSuchFieldError.class;
  public static final Class NO_SUCH_METHOD_ERROR            = NoSuchMethodError.class;
  public static final Class NO_CLASS_DEF_FOUND_ERROR        = NoClassDefFoundError.class;
  public static final Class UNSATISFIED_LINK_ERROR          = UnsatisfiedLinkError.class;
  public static final Class VERIFY_ERROR                    = VerifyError.class;

  /* UnsupportedClassVersionError is new in JDK 1.2 */
  //public static final Class UnsupportedClassVersionError = UnsupportedClassVersionError.class;

  /** Run-Time Exceptions
   */
  public static final Class NULL_POINTER_EXCEPTION              = NullPointerException.class;
  public static final Class ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION = ArrayIndexOutOfBoundsException.class;
  public static final Class ARITHMETIC_EXCEPTION                = ArithmeticException.class;
  public static final Class NEGATIVE_ARRAY_SIZE_EXCEPTION       = NegativeArraySizeException.class;
  public static final Class CLASS_CAST_EXCEPTION                = ClassCastException.class;
  public static final Class ILLEGAL_MONITOR_STATE               = IllegalMonitorStateException.class;

  /** Pre-defined exception arrays according to chapters 5.1-5.4 of the Java Virtual
   * Machine Specification
   */
  public static final Class[] EXCS_CLASS_AND_INTERFACE_RESOLUTION = {
    NO_CLASS_DEF_FOUND_ERROR, CLASS_FORMAT_ERROR, VERIFY_ERROR, ABSTRACT_METHOD_ERROR,
    EXCEPTION_IN_INITIALIZER_ERROR, ILLEGAL_ACCESS_ERROR
  }; // Chapter 5.1

  public static final Class[] EXCS_FIELD_AND_METHOD_RESOLUTION = {
    NO_SUCH_FIELD_ERROR, ILLEGAL_ACCESS_ERROR, NO_SUCH_METHOD_ERROR
  }; // Chapter 5.2

  public static final Class[] EXCS_INTERFACE_METHOD_RESOLUTION = new Class[0]; // Chapter 5.3 (as below)
  public static final Class[] EXCS_STRING_RESOLUTION           = new Class[0];
  // Chapter 5.4 (no errors but the ones that _always_ could happen! How stupid.)

  public static final Class[] EXCS_ARRAY_EXCEPTION = {
    NULL_POINTER_EXCEPTION, ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION
  };

}
