/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=CtorAccess.out -XDrawDiagnostics -Xlint:serial CtorAccess.java
 */

import java.io.*;

class CtorAccess {
    public CtorAccess(int i) {}

    // Cannot by accessed by SerialSubclass
    private CtorAccess(){}

    static class SerialSubclass
        extends CtorAccess
        implements Serializable {
        private static final long serialVersionUID = 42;
        SerialSubclass() {
            super(42);
        }
    }

    // *not* static
    class MemberSuper {
        // Implicit this$0 argument
        public MemberSuper() {}
    }

    class SerialMemberSub
        extends MemberSuper
        implements Serializable {

        SerialMemberSub(){super();}
        private static final long serialVersionUID = 42;
    }
}
