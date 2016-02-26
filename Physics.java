import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;

public class Physics implements Runnable {
    public final double trackLimit = 4.8; // Track is available from -5.0 to 5.0
    long startTime;         // simulation start time (physical time)
    // long totalTime;         // total simulation time elapsed (physical time)
    double simulationTime;  // simulation time
    
    double tau_sim;
    int tau_phy_ms;

    public boolean pole_in_good_state = true;    // whether the poles are in good states
    Pendulum pendulums[];
    
    // Set the number of poles
    public final int NUM_POLES = 2;
    // Set the initial position of the poles
    //set initial position of car
    public final double[] pole_init_pos = {-2.0, 0.0};
    //public final double[] pole_init_pos = {-2.0};
    public Physics(double tau_sim, double tau_phy) {
        this.tau_sim = tau_sim;
        this.tau_phy_ms = (int) (1000 * tau_phy);
        pendulums = new Pendulum[NUM_POLES];
        assert(NUM_POLES == pole_init_pos.length);
        for (int i = 0; i < NUM_POLES; i++) {
          pendulums[i] = new Pendulum(i, pole_init_pos[i]);
        }
    }

    // total simulation time (in seconds) elapsed since simulation started
    double get_simTime() {
        return this.simulationTime;
    }

    // total physical time (in ms) elapsed since simulation started
    long get_phyTime() {
        return System.currentTimeMillis() - startTime;
    }

    // Update actions for all the poles
    void update_actions(double[] actions) {
        assert(actions.length == NUM_POLES);
        for (int i = 0; i < NUM_POLES; i++) {
          pendulums[i].update_action(actions[i]);
        }
    }

    Pendulum[] get_pendulums() {
      return pendulums;
    }

    /**
     * This method runs the applet by first connecting to the server socket
     * and runs the animation loop.
     * It also calculates the derivatives of the state variables and updates
     * the state of the pole. It sends across the position values, updates
     * them and the value of action.
     */
    public void run() {
        

        //Remember the starting time.
        startTime = System.currentTimeMillis();
        long nextTime = startTime;
        simulationTime = 0;

        //This is the animation loop.
        while (true) {
          if (pole_in_good_state) {
            for (int i = 0; i < pendulums.length; i++) {
              pole_in_good_state = update_pendulum(pendulums[i]);
              if (!pole_in_good_state) {
                break;
              }
            }
          }
          
          // advance simulation time
          simulationTime += tau_sim;

          //Delay depending on how far we are behind.
          try {
              nextTime += tau_phy_ms;
              Thread.sleep(Math.max(0,
                      nextTime - System.currentTimeMillis()));
          } catch (InterruptedException e) {
            break;
          }
        }
        // this.sendMessage("bye");

    }
    
    /** Update the state of one pendulum
     *  Return whether the pendulum is in NORMAL state after the update
     */
    boolean update_pendulum(Pendulum p) {
      if (p.get_poleState() != PoleState.NORMAL) {
        return false;
      }

      // Update the state of the pole;
      // First calc derivatives of state variables
      synchronized(p) {
        System.out.println("Pendulum["+p.get_id()+"]: applied action = " + p.get_action());
        double force = p.forceMag * p.get_action();
        double sinangle = Math.sin(p.get_angle());
        double cosangle = Math.cos(p.get_angle());
        double angleDotSq = p.get_angleDot() * p.get_angleDot();
        double common = (force + p.poleMassLength * angleDotSq * sinangle
                - p.fricCart * (p.get_posDot() < 0 ? -1 : 0)) / p.totalMass;
        p.update_angleDDot((9.8 * sinangle - cosangle * common
                - p.fricPole * p.get_angleDot() / p.poleMassLength)
                / (p.halfPole * (p.fourthirds - p.poleMass * cosangle * cosangle
                / p.totalMass)));
        p.update_posDDot(common - p.poleMassLength * p.get_angleDDot() * cosangle
                / p.totalMass);
  
        { // update status
            double x = 0.;
            x = p.get_pos();
            x += p.get_posDot() * this.tau_sim;
            p.update_pos(x);
  
            x = p.get_posDot();
            x += p.get_posDDot() * this.tau_sim;
            p.update_posDot(x);
  
            p.update_prevAngle(p.get_angle());
  
            x = p.get_angle();
            x += p.get_angleDot() * this.tau_sim;
            p.update_angle(x);
  
            x = p.get_angleDot();
            x += p.get_angleDDot() * this.tau_sim;
            p.update_angleDot(x);
        }
  
         // If the pole has fallen down
         if (p.get_angle() * 180 / Math.PI > 90.0 || p.get_angle() * 180 / Math.PI < -90.0) {
             if (p.get_angle() > 0) {
                 p.update_angle(Math.PI / 2);
             } else {
                 p.update_angle(-Math.PI / 2);
             }
             p.update_poleState(PoleState.FAILED);
         }
  
         // If the pole has hit the right boundary
         if (p.get_pos() + p.cartWidth / 2 > trackLimit) {
             p.update_pos(trackLimit - p.cartWidth / 2);
             p.update_poleState(PoleState.FAILED);
         }
  
         // If the pole has hit the left boundary
         if (p.get_pos() - p.cartWidth / 2 < -trackLimit) {
             p.update_pos(-trackLimit + p.cartWidth / 2);
             p.update_poleState(PoleState.FAILED);
         }
  
         // Check if the pendulum collide with others
         for (int i = 0; i<pendulums.length; i++) {
           if (p == pendulums[i]) {
             continue;
           }
           if (Math.abs(p.get_pos() - pendulums[i].get_pos()) 
               < (p.cartWidth + pendulums[i].cartWidth)/2) {
              // collision detected
              p.update_poleState(PoleState.FAILED);
              break;
           }
         }
       }

       try {
           Thread.sleep(10);
       } catch (Exception e) {
           e.printStackTrace();
       }
       return p.get_poleState() == PoleState.NORMAL;
    }

}
