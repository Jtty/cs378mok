import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;

public class UpdatingUIThread implements Runnable{
    Applet applet;
    Physics physics;
    int updatingPeriod;

    // for double-buffering
    Dimension offDimension;
    // for double-buffering
    Image offImage;
    //for double-buffering
    Graphics offGraphics;

    boolean prev_pole_in_good_state = true;
    long failAt_phy;
    double failAt_sim;
    String[] simConfigInfo;

    double phy_width = 10.0;
    double phy_height = 5.0;

    public UpdatingUIThread(Applet applet, Physics physics,
                            int updatingPeriod, String[] simConfigInfo){
        this.applet = applet;
        this.physics = physics;
        this.updatingPeriod = updatingPeriod;
        this.simConfigInfo = simConfigInfo;
    }

    public void run(){
        long startTime = System.currentTimeMillis();

        while(true){
            //Display it.
            applet.repaint();

            //Delay depending on how far we are behind.
            try {
                startTime += updatingPeriod;
                Thread.sleep(Math.max(0,
                        startTime - System.currentTimeMillis()));
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }


    /**
     * This method draws the track, pole, cart, action arrow and also erases previous image.
     */
    public void update(Graphics gr) {
        Color bg = applet.getBackground();
        Color fg = applet.getForeground();
        Dimension d = applet.getSize();
        Color cartColor = new Color(255, 69, 0);
        Color arrowColor = new Color(255, 255, 0);
        Color trackColor = new Color(100, 100, 50);

        //Create the offscreen graphics context, if no good one exists.
        if ((offGraphics == null)
                || (d.width != offDimension.width)
                || (d.height != offDimension.height)) {
            offDimension = d;
            offImage = applet.createImage(d.width, d.height);
            offGraphics = offImage.getGraphics();
        }

        //Erase the previous image.
        offGraphics.setColor(applet.getBackground());
        offGraphics.fillRect(0, 0, d.width, d.height);

        //Draw Track.
        double xs[] = {-phy_width/2, phy_width/2, phy_width/2, phy_width/2-0.2,
                       phy_width/2-0.2, -(phy_width/2-0.2), -(phy_width/2-0.2),
                           -phy_width/2};
        double ys[] = {-0.4, -0.4, 0., 0., -0.2, -0.2, 0, 0};
        int pixxs[] = new int[8], pixys[] = new int[8];
        for (int i = 0; i < 8; i++) {
            pixxs[i] = pixX(d, xs[i]);
            pixys[i] = pixY(d, ys[i]);
        }
        offGraphics.setColor(trackColor);
        offGraphics.fillPolygon(pixxs, pixys, 8);

        //Draw message
        String msg = "CS378 CPS: Inverted Pendulum Project";
        offGraphics.drawString(msg, 20, d.height - 20);

        //msg = "Angle in Degrees = " + physics.get_angle() * 180 / 3.14;
        //offGraphics.drawString(msg, 20, d.height - 40);

        msg = "Phyical Timer = " + String.format("%.03f", (double) (physics.get_phyTime()/1000.0))
                + " secs   Sim. Timer = "+ String.format("%.03f", physics.get_simTime())+" secs" ;
        offGraphics.drawString(msg, 20, d.height - 60);

        // msg = "pos = " + (physics.get_pos()) + " posD = "+ physics.get_posDot()+" posDD = "+physics.get_posDDot() ;
        // offGraphics.drawString(msg, 20, d.height - 100);

        // msg = "ang = " + (physics.get_angle()) + " angD = "+ physics.get_angleDot()+" angDD = "+physics.get_angleDDot() ;
        // offGraphics.drawString(msg, 20, d.height - 120);

        boolean cur_pole_in_good_state = physics.pole_in_good_state;
        if (!cur_pole_in_good_state) {
          // found that the pole has fallen down at this moment
          if(prev_pole_in_good_state){
              this.failAt_phy = physics.get_phyTime();
              this.failAt_sim = physics.get_simTime();
          }
          msg = "Failed at time = " + String.format("%.03f", (double) (this.failAt_phy/1000.0)) +
                  "  secs  sim. time = "+ String.format("%.03f", this.failAt_sim) +" secs";
          offGraphics.drawString(msg, 20, d.height - 80);
        }
        prev_pole_in_good_state = cur_pole_in_good_state;
    
    
        // Display Simulation Configuration Information
        offGraphics.setColor(trackColor);
        msg = simConfigInfo[0];
        offGraphics.drawString(msg, 20, d.height - 120);
        msg = simConfigInfo[1];
        offGraphics.drawString(msg, 20, d.height - 100);


        // Draw each pendulums
        Pendulum[] pendulums = physics.get_pendulums();
        for(int i = 0; i < pendulums.length; i++) {
            Pendulum p = pendulums[i];

               
            // physics.update_ppos(physics.get_pos() % (2.5));
    
            //Draw cart.
            offGraphics.setColor(cartColor);
            offGraphics.fillRect(pixX(d, p.get_pos() - p.cartWidth/2), pixY(d, 0),
                                 pixDX(d, p.cartWidth), pixDY(d, -0.2));
    
            //Draw pole.
            //    offGraphics.setColor(cartColor);
            offGraphics.drawLine(pixX(d, p.get_pos()), pixY(d, 0),
                    pixX(d, p.get_pos() + Math.sin(p.get_angle()) * p.poleLength),
                    pixY(d, p.poleLength * Math.cos(p.get_angle())));
    
            //Draw action arrow.
            if (p.get_action() != 0) {
                int signAction = (p.get_action() > 0 ? 1 : (p.get_action() < 0) ? -1 : 0);
                int tipx = pixX(d, p.get_pos() + 0.2 * signAction);
                int tipy = pixY(d, -0.1);
                offGraphics.setColor(arrowColor);
                offGraphics.drawLine(pixX(d, p.get_pos()), pixY(d, -0.1), tipx, tipy);
                offGraphics.drawLine(tipx, tipy, tipx - 4 * signAction, tipy + 4);
                offGraphics.drawLine(tipx, tipy, tipx - 4 * signAction, tipy - 4);
            }
        }
        //Last thing: Paint the image onto the screen.
        gr.drawImage(offImage, 0, 0, applet);
    }

    public int pixX(Dimension d, double v) {

        return (int) Math.round((v + phy_width/2) / phy_width * d.width);
    }

    public int pixY(Dimension d, double v) {
        return (int) Math.round(d.height - (v + phy_height/2) / phy_height * d.height);
    }

    public int pixDX(Dimension d, double v) {
        return (int) Math.round(v / phy_width * d.width);
    }

    public int pixDY(Dimension d, double v) {
        return (int) Math.round(-v / phy_height * d.height);
    }

    public void stop() {
        //Get rid of the objects necessary for double buffering.
        offGraphics = null;
        offImage = null;
    }
}
