//  from ResourceScopeCloseMin.java
//  https://bugs.openjdk.org/browse/JDK-8267532
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Example3 {

    Runnable dummy = () -> {};

    static class ConfinedScope {
        final Thread owner;
        boolean closed;
        final List<Runnable> resources = new ArrayList<>();
        //int [] weight = new int[1024];
        //final Runnable[] resources = new Runnable[1];

        private void checkState() {
            if (closed) {
                throw new AssertionError("Closed");
            } else if (owner != Thread.currentThread()) {
                throw new AssertionError("Wrong thread");
            }
        }

        ConfinedScope() {
            this.owner = Thread.currentThread();
        }

        void addCloseAction(Runnable runnable) {
            checkState();
            resources.add(runnable);
            //resources[0] = runnable;
        }

        public void close() {
            checkState();
            closed = true;
            for (Runnable r : resources) {
                r.run();
            }
        }
    }

    public void confined_close() {
        ConfinedScope scope = new ConfinedScope();
        try { // simulate TWR
            scope.addCloseAction(dummy);
            scope.close();
        } catch (RuntimeException ex) {
            scope.close();
            throw ex;
        }
    }

    public static void main(String[] args) {
	    var kase = new Example3();
	
        while (true) {
            kase.confined_close();
        }
    }
}
