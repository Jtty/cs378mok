import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;

enum PoleState {
    NORMAL,
    FAILED,
}

public class Pendulum {
    public int pole_id;       // ID of the pole
    public double init_pos;   // initial position
    public double pos, posDot, angle, angleDot;
    public double prevAngle, angleDDot, posDDot;
    public double action = 0.75;
    
    public final double cartMass = 1.;
    public final double poleMass = 0.1;
    public final double poleLength = 1.;

    ;
    public final double forceMag = 30.;
    public final double fricCart = 0.00005;
    public final double fricPole = 0.005;
    public final double totalMass = cartMass + poleMass;
    public final double halfPole = 0.5 * poleLength;

    ;
    public final double poleMassLength = halfPole * poleMass;
    public final double fourthirds = 4. / 3.;

    public final double cartWidth = 0.4;  // for UI and collision detecion
    double tau_sim;
    int tau_phy_ms;
    PoleState poleState = PoleState.NORMAL;

    public Pendulum(int id, double init_pos) {
      this.pole_id = id;
      this.init_pos = init_pos;
      this.pos = init_pos;
    }

    int get_id() {
      return pole_id;
    }

    void update_pos(double pos) {
        synchronized (this) {
            this.pos = pos;
        }
    }

    double get_pos() {
        synchronized (this) {
            return this.pos;
        }
    }

    void update_posDot(double posDot) {
        synchronized (this) {
            this.posDot = posDot;
        }
    }

    double get_posDot() {
        synchronized (this) {
            return this.posDot;
        }
    }

    void update_posDDot(double posDDot) {
        synchronized (this) {
            this.posDDot = posDDot;
        }
    }

    double get_posDDot() {
        synchronized (this) {
            return this.posDDot;
        }
    }

    void update_angle(double angle) {
        synchronized (this) {
            this.angle = angle;
        }
    }

    double get_angle() {
        synchronized (this) {
            return this.angle;
        }
    }

    void update_angleDot(double angleDot) {
        synchronized (this) {
            this.angleDot = angleDot;
        }
    }

    double get_angleDot() {
        synchronized (this) {
            return this.angleDot;
        }
    }

    void update_angleDDot(double angleDDot) {
        synchronized (this) {
            this.angleDDot = angleDDot;
        }
    }

    double get_angleDDot() {
        synchronized (this) {
            return this.angleDDot;
        }
    }

    void update_prevAngle(double prevAngle) {
        synchronized (this) {
            this.prevAngle = prevAngle;
        }
    }

    double get_prevAngle() {
        synchronized (this) {
            return this.prevAngle;
        }
    }

    void update_action(double action) {
        synchronized (this) {
            this.action = action;
        }
    }

    double get_action() {
        synchronized (this) {
            return this.action;
        }
    }

    PoleState get_poleState() {
        synchronized (this) {
          return this.poleState;
        }
    }

    void update_poleState(PoleState state) {
        synchronized (this) {
          this.poleState = state;
        }
    }

    /**
     * This method resets the pole position values.
     */
    public void resetPole() {
        this.update_pos(this.init_pos);
        this.update_posDot(0.);
        this.update_angle(0.);
        this.update_angleDot(0.);
    }
}
