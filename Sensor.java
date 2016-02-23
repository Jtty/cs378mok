/**
 * This class simulates the behavior of Sensor. It sends angle and previous angle values to the controller to compute action.
 */
import java.io.*;

enum TriggerType {

    TIMER_TRIGGER,
    EVENT_TRIGGER,
}

class Sensor implements Runnable {

    Physics physics;
    private ObjectOutputStream out;
    private double samplingPeriod_phy;  // delay in physical time (in second)
    private long samplingPeriod_phy_ms;  // delay in physical time (in ms)
    private double samplingPeriod_sim;  // delay in simulation time (in second)
    private TriggerType triggerType;
    private double threshold;      // only applicable in event based sensor (in degrees)

    Sensor(Physics phy, ObjectOutputStream out, TriggerType type, double threshold, double sensorSamplingPeriod_sim, double sensorSamplingPeriod_phy) {
        this.physics = phy;
        this.out = out;
        this.triggerType = type;
        this.samplingPeriod_phy = sensorSamplingPeriod_phy;
        this.samplingPeriod_sim = sensorSamplingPeriod_sim;
        this.samplingPeriod_phy_ms = Math.round(samplingPeriod_phy*1000);
        this.threshold = threshold;
    }


    public synchronized void run() {

        while (true) {
            // Sensor will get four data from each pendulum
            // {angle, angleDot, pos, posDot}
            double sensorData[] = new double[4 * physics.NUM_POLES];
            Pendulum[] pendulums = physics.get_pendulums();

            for (int i = 0; i < pendulums.length; i++) {
                   synchronized(pendulums[i]) {
                   double angle, angleDot, pos, posDot;
                   angle = pendulums[i].get_angle();
                   angleDot = pendulums[i].get_angleDot();
                   pos = pendulums[i].get_pos();
                   posDot = pendulums[i].get_posDot();

                   if (triggerType == TriggerType.EVENT_TRIGGER) {
                     // Do not send if it is in event triggered mode and the angle is small
                     if (angle * 180 / 3.14 < threshold && angle * 180 / 3.14 > -threshold) {
                       try {
                           Thread.sleep(samplingPeriod_phy_ms);
                       } catch (Exception e) {
                           e.printStackTrace();
                       }
                       continue;
                     }
                   }
                   sensorData[i*4+0] = angle;
                   sensorData[i*4+1] = angleDot;
                   sensorData[i*4+2] = pos;
                   sensorData[i*4+3] = posDot;
                }
            }

            sendMessage_doubleArray(sensorData);
            System.out.println("---------------");
            
            try {
                Thread.sleep(samplingPeriod_phy_ms);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * This method sends the Double message on the object output stream.
     */
    void sendMessage_doubleArray(double[] data) {
        try {
            out.writeObject(data);
            out.flush();
            
            System.out.print("client> ");
            for(int i=0; i< data.length; i++){
                System.out.print(data[i] + "  ");
            }
            System.out.println();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

}

