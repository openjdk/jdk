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

    // AppContext must be in new thread group, otherwise dispose() throws
    Thread thread = new Thread(new ThreadGroup("Test"), "Test") {
      public void run() {
        appContext = SunToolkit.createNewAppContext();
        new SwingWorker<Void, Void>() {
          protected Void doInBackground() {
            return null;
          }
        }.execute(); // calls SwingWorker.getWorkersExecutorService()
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
