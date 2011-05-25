  /*
   * Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
   * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
   */

  package java.io;

  /**
   * Context during upcalls from object stream to class-defined
   * readObject/writeObject methods.
   * Holds object currently being deserialized and descriptor for current class.
   *
   * This context keeps track of the thread it was constructed on, and allows
   * only a single call of defaultReadObject, readFields, defaultWriteObject
   * or writeFields which must be invoked on the same thread before the class's
   * readObject/writeObject method has returned.
   * If not set to the current thread, the getObj method throws NotActiveException.
   */
  final class SerialCallbackContext {
      private final Object obj;
      private final ObjectStreamClass desc;
      /**
       * Thread this context is in use by.
       * As this only works in one thread, we do not need to worry about thread-safety.
       */
      private Thread thread;

      public SerialCallbackContext(Object obj, ObjectStreamClass desc) {
          this.obj = obj;
          this.desc = desc;
          this.thread = Thread.currentThread();
      }

      public Object getObj() throws NotActiveException {
          checkAndSetUsed();
          return obj;
      }

      public ObjectStreamClass getDesc() {
          return desc;
      }

      private void checkAndSetUsed() throws NotActiveException {
          if (thread != Thread.currentThread()) {
               throw new NotActiveException(
                "not in readObject invocation or fields already read");
          }
          thread = null;
      }

      public void setUsed() {
          thread = null;
      }
  }
