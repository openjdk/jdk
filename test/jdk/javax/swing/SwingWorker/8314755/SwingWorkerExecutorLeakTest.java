import sun.awt.SunToolkit;
import sun.awt.AppContext;

import java.awt.Toolkit;

import javax.swing.SwingWorker;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.beans.PropertyChangeListener;

/**
 * @test
 * @bug 8314755
 * @summary SwingWorker listener should not keep strong reference to executor
 * @run main SwingWorkerExecutorLeakTest
 */
public class SwingWorkerExecutorLeakTest {

  private static AppContext appContext;

  public static void main(String[] args) throws Exception {

    // AppContext must be in different thread group, otherwise dispose() throws
    Thread thread = new Thread(new ThreadGroup("Test"), "Test") {
      public void run() {
        if (true) {
          // AppContext.getAppContext() creates AppContext in root thread group unless
          // (javaplugin.version != null || javawebstart.version != null) && javafx.version != null
          System.setProperty("javaplugin.version", "foo");
          System.setProperty("javawebstart.version", "foo");
          System.setProperty("javafx.version", "foo");
        }
        else {
          // alternative: call SunToolkit.createNewAppContext() directly, uses current thread group
          SunToolkit.createNewAppContext();
        }

        // SwingWorker.execute() calls SwingWorker.getWorkersExecutorService(),
        // which calls AppContext.getAppContext() and stores reference to executor in AppContext
        new SwingWorker<Void, Void>() {
          protected Void doInBackground() {
            return null;
          }
        }.execute();

        // remember AppContext created in this thread group
        appContext = AppContext.getAppContext();
      }
    };
    thread.start();
    thread.join();

    // SwingWorker.getWorkersExecutorService() stored the executor in the AppContext map
    WeakReference<ExecutorService> executor = new WeakReference<>((ExecutorService)appContext.get(SwingWorker.class));

    appContext.dispose();

    // dispose() cleared the AppContext map
    if (appContext.get(SwingWorker.class) != null) throw new AssertionError();

    // dispose() called the listener defined in SwingWorker.getWorkersExecutorService(),
    // which called shutdown() on the executor
    if (! executor.get().isShutdown()) throw new AssertionError();

    // but the listener retains a strong reference to the executor,
    // and dispose() doesn't clear the list of listeners
    // reference chain: appContext -> listener -> executor

    if (false) {
      // this would make the executor unreachable
      appContext = null;
    }

    if (false) {
      // this would make the executor unreachable
      PropertyChangeListener listener = appContext.getPropertyChangeListeners(AppContext.DISPOSED_PROPERTY_NAME)[0];
      appContext.removePropertyChangeListener(AppContext.DISPOSED_PROPERTY_NAME, listener);
      listener = null;
    }

    System.gc();
    if (executor.get() != null) {
      throw new RuntimeException("executor created by SwingWorker.getWorkersExecutorService() is still strongly referenced");
    }
  }
}
