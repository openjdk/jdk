/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import jdk.nashorn.api.scripting.AbstractJSObject;
import java.nio.DoubleBuffer;

/**
 * Simple class demonstrating pluggable script object
 * implementation. By implementing jdk.nashorn.api.scripting.JSObject
 * (or extending AbstractJSObject which implements it), you
 * can supply a friendly script object. Nashorn will call
 * 'magic' methods on such a class on 'obj.foo, obj.foo = 33,
 * obj.bar()' etc. from script.
 *
 * In this example, Java nio DoubleBuffer object is wrapped
 * as a friendly script object that provides indexed acces
 * to buffer content and also support array-like "length"
 * readonly property to retrieve buffer's capacity. This class
 * also demonstrates a function valued property called "buf".
 * On 'buf' method, we return the underlying nio buffer object
 * that is being wrapped.
 */
public class BufferArray extends AbstractJSObject {
    // underlying nio buffer
    private final DoubleBuffer buf;

    public BufferArray(int size) {
        buf = DoubleBuffer.allocate(size);
    }

    public BufferArray(DoubleBuffer buf) {
        this.buf = buf;
    }

    // called to check if indexed property exists
    @Override
    public boolean hasSlot(int index) {
        return index > 0 && index < buf.capacity();
    }

    // get the value from that index
    @Override
    public Object getSlot(int index) {
       return buf.get(index);
    }

    // set the value at that index
    @Override
    public void setSlot(int index, Object value) {
       buf.put(index, ((Number)value).doubleValue());
    }

    // do you have a property of that given name?
    @Override
    public boolean hasMember(String name) {
       return "length".equals(name) || "buf".equals(name);
    }

    // get the value of that named property
    @Override
    public Object getMember(String name) {
       switch (name) {
          case "length":
              return buf.capacity();
          case "buf":
              // return a 'function' value for this property
              return new AbstractJSObject() {
                  @Override
                  public Object call(Object thiz, Object... args) {
                      return BufferArray.this.buf;
                  }

                  // yes, I'm a function !
                  @Override
                  public boolean isFunction() {
                      return true;
                  }
              };
       }
       return null;
    }
}
