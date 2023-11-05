/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

package resources;

import java.util.ListResourceBundle;

public class TableRowTest_ja extends ListResourceBundle {

  public Object[][] getContents() {
      return contents;
  }

  static Object[][] data = {
      {"\uff2d\uff41\uff52\uff4b", "\uff21\uff4e\uff44\uff52\uff45\uff57\uff53", "\uff32\uff45\uff44", Integer.valueOf(2), Boolean.valueOf(true)},
      {"\uff34\uff4f\uff4d", "\uff22\uff41\uff4c\uff4c", "\uff22\uff4c\uff55\uff45", Integer.valueOf(99), Boolean.valueOf(false)},
      {"\uff21\uff4c\uff41\uff4e", "\uff23\uff48\uff55\uff4e\uff47", "\uff27\uff52\uff45\uff45\uff4e", Integer.valueOf(838), Boolean.valueOf(false)},
      {"\uff2a\uff45\uff46\uff46", "\uff24\uff49\uff4e\uff4b\uff49\uff4e\uff53", "\uff34\uff55\uff52\uff51\uff55\uff4f\uff49\uff53", Integer.valueOf(8), Boolean.valueOf(true)},
      {"\uff21\uff4d\uff59", "\uff26\uff4f\uff57\uff4c\uff45\uff52", "\uff39\uff45\uff4c\uff4c\uff4f\uff57", Integer.valueOf(3), Boolean.valueOf(false)},
      {"\uff22\uff52\uff49\uff41\uff4e", "\uff27\uff45\uff52\uff48\uff4f\uff4c\uff44", "\uff27\uff52\uff45\uff45\uff4e", Integer.valueOf(0), Boolean.valueOf(false)},
      {"\uff2a\uff41\uff4d\uff45\uff53", "\uff27\uff4f\uff53\uff4c\uff49\uff4e\uff47", "\uff30\uff49\uff4e\uff4b", Integer.valueOf(21), Boolean.valueOf(false)},
      {"\uff24\uff41\uff56\uff49\uff44", "\uff2b\uff41\uff52\uff4c\uff54\uff4f\uff4e", "\uff32\uff45\uff44", Integer.valueOf(1), Boolean.valueOf(false)},
      {"\uff24\uff41\uff56\uff45", "\uff2b\uff4c\uff4f\uff42\uff41", "\uff39\uff45\uff4c\uff4c\uff4f\uff57", Integer.valueOf(14), Boolean.valueOf(false)},
      {"\uff30\uff45\uff54\uff45\uff52", "\uff2b\uff4f\uff52\uff4e", "\uff30\uff55\uff52\uff50\uff4c\uff45", Integer.valueOf(12), Boolean.valueOf(false)},
      {"\uff30\uff48\uff49\uff4c", "\uff2d\uff49\uff4c\uff4e\uff45", "\uff30\uff55\uff52\uff50\uff4c\uff45", Integer.valueOf(3), Boolean.valueOf(false)},
      {"\uff24\uff41\uff56\uff45", "\uff2d\uff4f\uff4f\uff52\uff45", "\uff27\uff52\uff45\uff45\uff4e", Integer.valueOf(88), Boolean.valueOf(false)},
      {"\uff28\uff41\uff4e\uff53", "\uff2d\uff55\uff4c\uff4c\uff45\uff52", "\uff2d\uff41\uff52\uff4f\uff4f\uff4e", Integer.valueOf(5), Boolean.valueOf(false)},
      {"\uff32\uff49\uff43\uff4b", "\uff2c\uff45\uff56\uff45\uff4e\uff53\uff4f\uff4e", "\uff22\uff4c\uff55\uff45", Integer.valueOf(2), Boolean.valueOf(false)},
      {"\uff34\uff49\uff4d", "\uff30\uff52\uff49\uff4e\uff5a\uff49\uff4e\uff47", "\uff22\uff4c\uff55\uff45", Integer.valueOf(22), Boolean.valueOf(false)},
      {"\uff23\uff48\uff45\uff53\uff54\uff45\uff52", "\uff32\uff4f\uff53\uff45", "\uff22\uff4c\uff41\uff43\uff4b", Integer.valueOf(0), Boolean.valueOf(false)},
      {"\uff32\uff41\uff59", "\uff32\uff59\uff41\uff4e", "\uff27\uff52\uff41\uff59", Integer.valueOf(77), Boolean.valueOf(false)},
      {"\uff27\uff45\uff4f\uff52\uff47\uff45\uff53", "\uff33\uff41\uff41\uff42", "\uff32\uff45\uff44", Integer.valueOf(4), Boolean.valueOf(false)},
      {"\uff37\uff49\uff4c\uff4c\uff49\uff45", "\uff37\uff41\uff4c\uff4b\uff45\uff52", "\uff30\uff48\uff54\uff48\uff41\uff4c\uff4f\u3000\uff42\uff4c\uff55\uff45", Integer.valueOf(4), Boolean.valueOf(false)},
      {"\uff2b\uff41\uff54\uff48\uff59", "\uff37\uff41\uff4c\uff52\uff41\uff54\uff48", "\uff22\uff4c\uff55\uff45", Integer.valueOf(8), Boolean.valueOf(false)},
      {"\uff21\uff52\uff4e\uff41\uff55\uff44", "\uff37\uff45\uff42\uff45\uff52", "\uff27\uff52\uff45\uff45\uff4e", Integer.valueOf(44), Boolean.valueOf(false)},
      {"\uff2d\uff41\uff52\uff4b", "\uff21\uff4e\uff44\uff52\uff45\uff57\uff53", "\uff32\uff45\uff44", Integer.valueOf(2), Boolean.valueOf(true)},
      {"\uff34\uff4f\uff4d", "\uff22\uff41\uff4c\uff4c", "\uff22\uff4c\uff55\uff45", Integer.valueOf(99), Boolean.valueOf(false)},
      {"\uff21\uff4c\uff41\uff4e", "\uff23\uff48\uff55\uff4e\uff47", "\uff27\uff52\uff45\uff45\uff4e", Integer.valueOf(838), Boolean.valueOf(false)},
      {"\uff2a\uff45\uff46\uff46", "\uff24\uff49\uff4e\uff4b\uff49\uff4e\uff53", "\uff34\uff55\uff52\uff51\uff55\uff4f\uff49\uff53", Integer.valueOf(8), Boolean.valueOf(true)},
      {"\uff21\uff4d\uff59", "\uff26\uff4f\uff57\uff4c\uff45\uff52", "\uff39\uff45\uff4c\uff4c\uff4f\uff57", Integer.valueOf(3), Boolean.valueOf(false)},
      {"\uff22\uff52\uff49\uff41\uff4e", "\uff27\uff45\uff52\uff48\uff4f\uff4c\uff44", "\uff27\uff52\uff45\uff45\uff4e", Integer.valueOf(0), Boolean.valueOf(false)},
      {"\uff2a\uff41\uff4d\uff45\uff53", "\uff27\uff4f\uff53\uff4c\uff49\uff4e\uff47", "\uff30\uff49\uff4e\uff4b", Integer.valueOf(21), Boolean.valueOf(false)},
      {"\uff24\uff41\uff56\uff49\uff44", "\uff2b\uff41\uff52\uff4c\uff54\uff4f\uff4e", "\uff32\uff45\uff44", Integer.valueOf(1), Boolean.valueOf(false)},
      {"\uff24\uff41\uff56\uff45", "\uff2b\uff4c\uff4f\uff42\uff41", "\uff39\uff45\uff4c\uff4c\uff4f\uff57", Integer.valueOf(14), Boolean.valueOf(false)},
      {"\uff30\uff45\uff54\uff45\uff52", "\uff2b\uff4f\uff52\uff4e", "\uff30\uff55\uff52\uff50\uff4c\uff45", Integer.valueOf(12), Boolean.valueOf(false)},
      {"\uff30\uff48\uff49\uff4c", "\uff2d\uff49\uff4c\uff4e\uff45", "\uff30\uff55\uff52\uff50\uff4c\uff45", Integer.valueOf(3), Boolean.valueOf(false)},
      {"\uff24\uff41\uff56\uff45", "\uff2d\uff4f\uff4f\uff52\uff45", "\uff27\uff52\uff45\uff45\uff4e", Integer.valueOf(88), Boolean.valueOf(false)},
      {"\uff28\uff41\uff4e\uff53", "\uff2d\uff55\uff4c\uff4c\uff45\uff52", "\uff2d\uff41\uff52\uff4f\uff4f\uff4e", Integer.valueOf(5), Boolean.valueOf(false)},
      {"\uff32\uff49\uff43\uff4b", "\uff2c\uff45\uff56\uff45\uff4e\uff53\uff4f\uff4e", "\uff22\uff4c\uff55\uff45", Integer.valueOf(2), Boolean.valueOf(false)},
      {"\uff34\uff49\uff4d", "\uff30\uff52\uff49\uff4e\uff5a\uff49\uff4e\uff47", "\uff22\uff4c\uff55\uff45", Integer.valueOf(22), Boolean.valueOf(false)},
      {"\uff23\uff48\uff45\uff53\uff54\uff45\uff52", "\uff32\uff4f\uff53\uff45", "\uff22\uff4c\uff41\uff43\uff4b", Integer.valueOf(0), Boolean.valueOf(false)},
      {"\uff32\uff41\uff59", "\uff32\uff59\uff41\uff4e", "\uff27\uff52\uff41\uff59", Integer.valueOf(77), Boolean.valueOf(false)},
      {"\uff27\uff45\uff4f\uff52\uff47\uff45\uff53", "\uff33\uff41\uff41\uff42", "\uff32\uff45\uff44", Integer.valueOf(4), Boolean.valueOf(false)},
      {"\uff37\uff49\uff4c\uff4c\uff49\uff45", "\uff37\uff41\uff4c\uff4b\uff45\uff52", "\uff30\uff48\uff54\uff48\uff41\uff4c\uff4f\u3000\uff42\uff4c\uff55\uff45", Integer.valueOf(4), Boolean.valueOf(false)},
      {"\uff2b\uff41\uff54\uff48\uff59", "\uff37\uff41\uff4c\uff52\uff41\uff54\uff48", "\uff22\uff4c\uff55\uff45", Integer.valueOf(8), Boolean.valueOf(false)},
      {"\uff21\uff52\uff4e\uff41\uff55\uff44", "\uff37\uff45\uff42\uff45\uff52", "\uff27\uff52\uff45\uff45\uff4e", Integer.valueOf(44), Boolean.valueOf(false)}
  };

  static final Object[][] contents = {
      {"TableData", data }        // array for table data
  };
}
