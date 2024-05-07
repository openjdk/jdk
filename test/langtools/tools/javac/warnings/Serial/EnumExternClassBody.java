/*
 * @test /nodynamiccopyright/
 * @bug 8312415
 * @compile/ref=ClassBody.out -XDrawDiagnostics -Xlint:serial EnumExternClassBody.java
 * @compile/ref=empty.out     -XDrawDiagnostics               EnumExternClassBody.java
 */

import java.io.*;

/*
 * Verify warnings are generated as appropriate for enum constants
 * with specialized class bodies.
 */
class EnumExternClassBody {

    /*
     * Define externalization methods both in the enum class and a
     * specialized enum constant.
     */
    private static enum ColorExtern1 implements Externalizable {
        RED(  0xFF_00_00),
        GREEN(0x00_FF_00),
        BLUE( 0x00_00_FF) {
           @Override
            public void readExternal(ObjectInput in) {
                throw new RuntimeException();
            }

           @Override
            public void writeExternal(ObjectOutput out) throws IOException {
                throw new RuntimeException();
            }

           // Look for serialization members too
           private static final long serialVersionUID = 42;
           private static final ObjectStreamField[] serialPersistentFields = {};

           private void writeObject(ObjectOutputStream stream) throws IOException {
                throw new RuntimeException();
           }

           private Object writeReplace() throws ObjectStreamException {
               return null;
           }

           private void readObject(ObjectInputStream stream)
               throws IOException, ClassNotFoundException {
                throw new RuntimeException();
           }

           private void readObjectNoData() throws ObjectStreamException {
               return;
           }

           private Object readResolve() throws ObjectStreamException {
               return null;
           }
        };

        int rgb;
        private ColorExtern1(int rgb) {
            this.rgb = rgb;
        }

        @Override
        public void readExternal(ObjectInput in) {
            throw new RuntimeException();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            throw new RuntimeException();
        }
    }

    /*
     * Define externalization methods only on specialized enum
     * constants.
     */
    private static enum ColorExtern2 implements Externalizable {
        CYAN {
           @Override
            public void readExternal(ObjectInput in) {
                throw new RuntimeException();
            }

           @Override
            public void writeExternal(ObjectOutput out) throws IOException {
                throw new RuntimeException();
            }
        };
    }

    /*
     * Define externalization methods only on specialized enum
     * constants.
     */
    private static enum ColorExtern3 implements Externalizable {
        MAGENTA {
           @Override
            public void readExternal(ObjectInput in) {
                throw new RuntimeException();
            }

           @Override
            public void writeExternal(ObjectOutput out) throws IOException {
                throw new RuntimeException();
            }
        };

        // Acceptable to have ineffectual warnings for these abstract methods
        public abstract void readExternal(ObjectInput in);
        public abstract void writeExternal(ObjectOutput out) throws IOException ;
    }
}
