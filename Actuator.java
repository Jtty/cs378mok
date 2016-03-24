/**
 * This class simulates the behavior of Actuator. It receives the action value from the controller and sends it across to the process.
 */
import java.io.*;

class Actuator implements Runnable {
    long upper_bound = 400000000; //400 ms
    long lower_bound = 100000000; //100 ms
    Physics physics;
    private ObjectInputStream in;
    long delay = upper_bound;       //Assume worst case to start
    Actuator(Physics phy, ObjectInputStream in) {
        this.physics = phy;
        this.in = in;
    }

    Actuator getExistingActuator() {
        return this;
    }

    long getDelay() {
        return this.delay;
    }

    void init() {
        double init_actions[] = new double[physics.NUM_POLES];
        for (int i = 0; i < physics.NUM_POLES; i++) {
          init_actions[i] = 0.75;
        }
        physics.update_actions(init_actions);
    }

    public synchronized void run() {
        double[] data_old = null;
        long time_old = System.nanoTime();
        int i = 0;
        while (true) {
            try {
              // read action data from control server  
              Object obj = in.readObject();
              double[] data = (double[]) (obj);

              long delay_test = System.nanoTime() - time_old;
              if (delay_test < upper_bound &&
                  delay_test > lower_bound) {    
                delay = delay_test;
                delay_test /= 1000000;
              }
              time_old = System.nanoTime();
              assert(data.length == physics.NUM_POLES);
              //if (delay_test > 150) { //Use if block to skip 'duplicate' packets from the controller. Doesn't fix issue.
                  System.out.print( String.format("ACTUATOR::Time: %d\tAction: %f\tIter: %d\n", delay_test, data[0], i++) );
                  physics.update_actions(data);
              //}

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
