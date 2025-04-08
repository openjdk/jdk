/*
 * @test /nodynamiccopyright/
 * @bug 8325805
 * @summary Permit non-superclass instance field assignments before this/super in constructors
 * @compile/fail/ref=EarlyAssignments.out -XDrawDiagnostics EarlyAssignments.java
 */
public class EarlyAssignments {

    public static class Inner1 {
        public int x;

        public Inner1() {
            x = 123;                        // OK - "x" belongs to this class
            this.x = 123;                   // OK - "x" belongs to this class
            Inner1.this.x = 123;            // OK - "x" belongs to this class
            super();
        }

        public Inner1(int y) {
            y = x;                          // FAIL - early 'this' reference
            y = this.x;                     // FAIL - early 'this' reference
            y = Inner1.this.x;              // FAIL - early 'this' reference
            super();
        }

        public class Inner1a extends Inner1 {
            public int z;
            public Inner1a(byte value) {
                Inner1.this.x = value;      // OK - "x" belongs to outer class
                z = super.x;                // FAIL - "x" belongs to superclass
                z = x;                      // FAIL - "x" belongs to superclass
                this.z = x;                 // FAIL - "x" belongs to superclass
                Inner1a.this.z = x;         // FAIL - "x" belongs to superclass
                Object o1 = Inner1.this;    // OK - Inner1 is an outer class
                Object o2 = Inner1a.this;   // FAIL - Inner1a is this class
                super();
            }
            public Inner1a(short value) {
                x = value;                  // FAIL - "x" belongs to superclass
                super();
            }
            public Inner1a(char value) {
                this.x = value;             // FAIL - "x" belongs to superclass
                super();
            }
            public Inner1a(int value) {
                super.x = value;            // FAIL - "x" belongs to superclass
                super();
            }
        }

        public class Inner1b {
            public Inner1b(int value) {
                Inner1.this.x = value;      // OK - "x" belongs to outer class
                super();
            }
        }
    }

    public static class Inner2 extends Inner1 {
        int y;
        public Inner2(int value) {
            y = value;                      // OK  - "y" belongs to this class
            this.y = value;                 // OK  - "y" belongs to this class
            x = value;                      // FAIL - "x" belongs to superclass
            this.x = value;                 // FAIL - "x" belongs to superclass
            Object o1 = this;               // FAIL - can't acces 'this' yet
            Object o2 = Inner2.this;        // FAIL - can't acces 'this' yet
            super();
        }
    }

    public static class Inner3 {

        public int e;

        public class Inner3a {

            public static int x;

            public Inner3a(int val) {
                x = val;                    // OK - "x" is a static field
                val = x;                    // OK - "x" is a static field
                e = val;                    // OK - "e" belongs to outer class
                val = e;                    // OK - "e" belongs to outer class
                Inner3.this.e = val;        // OK - "e" belongs to outer class
                super();
            }
        }
    }

    public static class Inner4 {
        public int x;

        public Inner4() {
            x = 0;                              // OK
            x = x + 1;                          // FAIL - illegal early access
            super();
        }

        public Inner4(int a) {
            this.x = 0;                         // OK
            this.x = this.x + 1;                // FAIL - illegal early access
            super();
        }

        public Inner4(char a) {
            Inner4.this.x = 0;                  // OK
            Inner4.this.x = Inner4.this.x + 1;  // FAIL - illegal early access
            super();
        }
    }

    public static class Inner5 extends Inner4 {
        public int y;

        public Inner5() {
            y = x + 1;                          // FAIL - illegal early access
            super();
        }

        public Inner5(int a) {
            this.y = x + 1;                     // FAIL - illegal early access
            super();
        }

        public Inner5(char a) {
            Inner5.this.y = x + 1;              // FAIL - illegal early access
            super();
        }

        public Inner5(short a) {
            y = super.x + 1;                    // FAIL - illegal early access
            super();
        }

        public Inner5(float a) {
            y = Inner5.this.x + 1;              // FAIL - illegal early access
            super();
        }
    }

    public static class Inner6 {
        public int x = 1;

        public Inner6() {
            x = 2;                              // FAIL - illegal early access
            super();
        }
    }

    public static class Inner7 {
        public final int x = 1;

        public Inner7() {
            x = 2;                              // FAIL - illegal early access
            super();
        }
    }

    public static class Inner8 {
        class Inner8a {
            int x;
        }

        public Inner8() {
            this.new Inner8a().x = 1;           // FAIL - illegal early access
            super();
        }
    }
}
