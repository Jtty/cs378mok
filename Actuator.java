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
        while (true) {
            try {
              // read action data from control server  
              Object obj = in.readObject();
              double[] data = (double[]) (obj);
              //System.out.println("Data[0]: " + data[0]);
              long delay_test = System.nanoTime() - time_old;
              if (delay_test < upper_bound &&
                  delay_test > lower_bound) {    
                delay = delay_test;
              }
              time_old = System.nanoTime();
              assert(data.length == physics.NUM_POLES);
            physics.update_actions(data);

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
