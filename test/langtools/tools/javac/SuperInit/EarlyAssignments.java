/*
 * @test /nodynamiccopyright/
 * @bug 8325805
 * @summary Permit non-superclass instance field assignments before this/super in constructors
 * @compile/fail/ref=EarlyAssignments.out -XDrawDiagnostics EarlyAssignments.java
 * @enablePreview
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
}
