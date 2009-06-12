/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @bug     6464451
 * @summary javac in 5.0ux can not compile try-finally block which has a lot of "return"
 * @author  Wei Tao
 * @compile -source 5 -target 5 DeepNestedFinally.java
 * @clean DeepNestedFinally
 * @compile/fail DeepNestedFinally.java
 */

public class DeepNestedFinally {
   static public int func(int i) {
    try {
        if(i == 1) return 1;
    } finally {
        try {
            if(i == 2) return 2;
        } finally {
            try {
                if(i == 3) return 3;
            } finally {
                try {
                    if(i == 4) return 4;
                } finally {
                    try {
                        if(i == 5) return 5;
                    } finally {
                        try {
                            if(i == 6) return 6;
                        } finally {
                            try {
                                if (i == 7) return 7;
                            } finally {
                                int x = 0;
                                x += 1;
                                x += 2;
                                x += 3;
                                x += 4;
                                x += 5;
                                x += 6;
                                x += 7;
                                x += 8;
                                x += 9;
                            }
                        }
                    }
                }
             }
         }
      }
      return 0;
   }
}
