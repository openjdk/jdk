/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=SerialMethodThrows.out -XDrawDiagnostics -Xlint:serial SerialMethodThrows.java
 */

import java.io.*;

/*
 * Container class for various serializable classes with different
 * kinds of throws clauses. Canonical serialization method signatures:
 *
 * private void writeObject(ObjectOutputStream stream)
 * throws IOException
 *
 * ANY-ACCESS-MODIFIER Object writeReplace()
 * throws ObjectStreamException
 *
 * private void readObject(ObjectInputStream stream)
 * throws IOException, ClassNotFoundException
 *
 * private void readObjectNoData()
 * throws ObjectStreamException
 *
 * ANY-ACCESS-MODIFIER Object readResolve()
 * throws ObjectStreamException
 */
class SerialMethodThrows {

    // Being declared to throw no exceptions is fine and should not
    // generate any warnings.
    static class NoThrows implements Serializable {
        private static final long serialVersionUID = 42;

        private void writeObject(ObjectOutputStream stream) {
            try {
                stream.defaultWriteObject();
            } catch (IOException e) {
                ;
            }
        }

        private Object writeReplace() {
            return null;
        }

        private void readObject(ObjectInputStream stream) {
            try {
                stream.defaultReadObject();
            } catch (IOException | ClassNotFoundException e) {
                ;
            }
        }

        private void readObjectNoData() {}

        private Object readResolve() {
            return null;
        }
    }

    // Being declared to throw the canonical exceptions is fine and
    // should not generate any warnings.
    static class ErrorThrows implements Serializable {
        private static final long serialVersionUID = 42;

        private void writeObject(ObjectOutputStream stream)
            throws Error {
            try {
                stream.defaultWriteObject();
            } catch (IOException e) {
                ;
            }
        }

        private Object writeReplace()
            throws Error {
            return null;
        }

        private void readObject(ObjectInputStream stream)
            throws Error {
            try {
                stream.defaultReadObject();
            } catch (IOException | ClassNotFoundException e) {
                ;
            }
        }

        private void readObjectNoData()
        throws Error {}

        private Object readResolve()
            throws Error {
            return null;
        }
    }

    // Being declared to throw the canonical exceptions is fine and
    // should not generate any warnings.
    static class ExactThrows implements Serializable {
        private static final long serialVersionUID = 42;

        private void writeObject(ObjectOutputStream stream)
            throws IOException {
            stream.defaultWriteObject();
        }

        private Object writeReplace()
            throws ObjectStreamException {
            return null;
        }

        private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
            stream.defaultReadObject();
        }

        private void readObjectNoData()
        throws ObjectStreamException {}

        private Object readResolve()
            throws ObjectStreamException {
            return null;
        }
    }

    // Being declared to throw subclasses of the canonical exceptions
    // is fine and should not generate any warnings.
    static class SubclassThrows implements Serializable {
        private static final long serialVersionUID = 42;

        private void writeObject(ObjectOutputStream stream)
            throws CustomIOException {
            try {
                stream.defaultWriteObject();
            } catch (IOException e) {
                ;
            }
        }

        private Object writeReplace()
            throws CustomObjectStreamException {
            return null;
        }

        private void readObject(ObjectInputStream stream)
            throws CustomIOException, CustomClassNotFoundException {
            try {
                stream.defaultReadObject();
            } catch (IOException | ClassNotFoundException e) {
                ;
            }
        }

        private void readObjectNoData()
        throws CustomObjectStreamException {}

        private Object readResolve()
            throws CustomObjectStreamException {
            return null;
        }
    }

    private static class CustomIOException extends IOException{
        private static final long serialVersionUID = 1;
    }

    private static class CustomObjectStreamException extends ObjectStreamException {
        private static final long serialVersionUID = 2;
    }

    private static class CustomClassNotFoundException extends ClassNotFoundException {
        private static final long serialVersionUID = 3;
    }

    // Use to trigger warnings
    private static class CustomException extends Exception {
        private static final long serialVersionUID = 3;
    }

    // Being declared to throw subclasses of the canonical exceptions
    // is fine and should not generate any warnings.
    static class CustomThrows implements Serializable {
        private static final long serialVersionUID = 42;

        private void writeObject(ObjectOutputStream stream)
            throws CustomException {
            try {
                stream.defaultWriteObject();
            } catch (IOException e) {
                ;
            }
        }

        private Object writeReplace()
            throws CustomException {
            return null;
        }

        private void readObject(ObjectInputStream stream)
            throws CustomException {
            try {
                stream.defaultReadObject();
            } catch (IOException | ClassNotFoundException e) {
                ;
            }
        }

        private void readObjectNoData()
        throws CustomException {}

        private Object readResolve()
            throws CustomException {
            return null;
        }
    }
}
