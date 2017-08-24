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

package com.sun.org.apache.bcel.internal.generic;

import java.io.IOException;
import com.sun.org.apache.bcel.internal.util.ByteSequence;

/**
 * LDC_W - Push item from constant pool (wide index)
 *
 * <PRE>Stack: ... -&gt; ..., item.word1, item.word2</PRE>
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class LDC_W extends LDC {
  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  LDC_W() {}

  public LDC_W(int index) {
    super(index);
  }

  /**
   * Read needed data (i.e., index) from file.
   */
  protected void initFromFile(ByteSequence bytes, boolean wide)
       throws IOException
  {
    setIndex(bytes.readUnsignedShort());
    // Override just in case it has been changed
    opcode = com.sun.org.apache.bcel.internal.Constants.LDC_W;
  }
}
