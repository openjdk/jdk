/*
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 * @test
 * @bug 6646019
 * @summary array subscript expressions become top() with -d64
 * @run main/othervm -Xcomp -XX:CompileOnly=Test.test Test
*/


public class Test  {
  final static int i = 2076285318;
  long l = 2;
  short s;

  public static void main(String[] args) {
    Test t = new Test();
    try { t.test(); }
    catch (Throwable e) {
      if (t.l != 5) {
        System.out.println("Fails: " + t.l + " != 5");
      }
    }
  }

  private void test() {
    l = 5;
    l = (new short[(byte)-2])[(byte)(l = i)];
  }
}
