/*
 * @test /nodynamiccopyright/
 * @bug 8157519
 * @summary Error messages when compiling a malformed module-info.java confusing
 * @compile/fail/ref=moduleinfo.out -XDrawDiagnostics module-info.java
 */

module java.transaction {
  requires java.base;
  resuires javax.interceptor.javax.interceptor.api;
  requires public javax.enterprise.cdi.api;
  requires public java.sql;
  requires public java.rmi;
  export javax.transaction;
}
