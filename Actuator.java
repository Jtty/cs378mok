/**
 * This class simulates the behavior of Actuator. It receives the action value from the controller and sends it across to the process.
 */
import java.io.*;

class Actuator implements Runnable {

    Physics physics;
    private ObjectInputStream in;

    Actuator(Physics phy, ObjectInputStream in) {
        this.physics = phy;
        this.in = in;
    }

    void init() {
        double init_actions[] = new double[physics.NUM_POLES];
        for (int i = 0; i < physics.NUM_POLES; i++) {
          init_actions[i] = 0.75;
        }
        physics.update_actions(init_actions);
    }

    public synchronized void run() {
        while (true) {
            try {
              // read action data from control server  
              Object obj = in.readObject();
              double[] data = (double[]) (obj);
              assert(data.length == physics.NUM_POLES);
              physics.update_actions(data);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
