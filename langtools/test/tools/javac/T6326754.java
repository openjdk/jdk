/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug 6326754
 * @summary Compiler will fail to handle -Xmaxerrs with -ve numbers
 *
 * @compile/fail/ref=T6326754.out -XDrawDiagnostics -Xmaxerrs -1 T6326754.java
 * @compile/fail/ref=T6326754.out -XDrawDiagnostics -Xmaxerrs  0 T6326754.java
 * @compile/fail/ref=T6326754.out -XDrawDiagnostics -Xmaxerrs 10 T6326754.java
 * @compile/fail/ref=T6326754.out -XDrawDiagnostics              T6326754.java
 */
class TestConstructor<T,K>{
    T t;
    K k;
    public TestConstructor(T t,K k){
        this.t =t;
    }
    public TestConstructor(K k){
        this.k = k;
        this.t = null;
    }
    public TestConstructor(T t){
        this.t=t;
        this.k=null;
    }
    public void setT(T t){
        this.t=t;
        this.k=null;
    }
    public void setT(K k){
        this.k = k;
        this.t = null;
    }
    public void setT(T t,K k){
        this.t = t;
        this.k = k;
    }
}
class TestC<T>{
    T t;
    public <T>void setT(T t){
        this.t = t;
    }
}
public class T6326754{
    public static void main(String... arg){
        TestC tC =new TestC();
        tC.setT();
        TestConstructor tc = new TestConstructor("saaa");
        tc.setT("sasa");
        TestC<Integer> tC1 = new TestC();
        tC1.setT(545);
    }
}
