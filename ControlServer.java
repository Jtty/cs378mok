/**
 * This program runs as a server and controls the force to be applied to balance the Inverted Pendulum system running on the clients.
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.Math;





public class ControlServer {

    private static ServerSocket serverSocket;
    private static final int port = 25533;
    /**
     * Main method that creates new socket and PoleServer instance and runs it.
     */
    public static void main(String[] args) throws IOException {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException ioe) {
            System.out.println("unable to set up port");
            System.exit(1);
        }
        System.out.println("Waiting for connection");
        do {
            Socket client = serverSocket.accept();
            System.out.println("\nnew client accepted.\n");
            PoleServer_handler handler = new PoleServer_handler(client);
        } while (true);
    }
}

/**
 * This class sends control messages to balance the pendulum on client side.
 */
class PoleServer_handler implements Runnable {
    // Set the number of poles
    private static final int NUM_POLES = 2;

    static ServerSocket providerSocket;
    Socket connection = null;
    ObjectOutputStream out;
    ObjectInputStream in;
    String message = "abc";
    static Socket clientSocket;
    Thread t;

    /**
     * Class Constructor
     */
    public PoleServer_handler(Socket socket) {
        t = new Thread(this);
        clientSocket = socket;

        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(clientSocket.getInputStream());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        t.start();
    }
    /******************
    ** CLASS MEMBERS **
    *******************/
    double angle, angleDot, posDot, action = 0;
    double i = 0;
    double pos = 0;
    double targetPos = 2;
    double delay = 400; //network delay in ms
    long time_unit = 0;
    Physics physics_prog = new Physics(.01, .01/.1);
    double[] prev_action = new double[NUM_POLES];
    double[] cart_pos = new double[NUM_POLES];
    double offset_from_leader = 1; //Pad distance follower <-> leader
    double target_pad = .2; // Defines when cart is 'at target'
    int hold_pos[] = {0,0}; // If unstable, this is set to N frames to hold position to attempt to save cart
    double prevAction[] = {0,0};        // Previous actions taken by carts { 0, 1 }
    double leaderPos[] = {0,0,0,0,0};   // Leader position array
    int leaderPosPtr = 0;               // Ptr for filling the leader position array
    double leaderPosAvg = 0;            // 5 frame average of leaders Position
    int leader = 0;                     // Which cart is in the lead
    boolean leader_not_elected = true;  // Flag used to ensure leader is only elected once

    /**
     * This method receives the pole positions and calculates the updated value
     * and sends them across to the client.
     /* It also sends the amount of force to be applied to balance the pendulum.
     * @throws ioException
     */
    void control_pendulum(ObjectOutputStream out, ObjectInputStream in) {
        
        try {
            while(true){
                System.out.println("-----------------");
                // read data from client
                Object obj = in.readObject();
               // Do not process string data unless it is "bye", in which case,
                // we close the server
                if(obj instanceof String){
                    System.out.println("STRING RECEIVED: "+(String) obj);
                    if(obj.equals("bye")){
                        break;
                    }
                    continue;
                }
                
                double[] data= (double[])(obj);
                double[] actions = new double[NUM_POLES];
 
                // Get sensor data of each pole and calculate the action to be
                // applied to each inverted pendulum
                // TODO: Current implementation assumes that each pole is
                // controlled independently. This part needs to be changed if
                // the control of one pendulum needs sensing data from other
                // pendulums.
				delay = data[11]/2; // Unpack delay from client message
                for (int i = 0; i < NUM_POLES; i++) {
                  if (prev_action[i] == 0) {
                    prev_action[i] = .75;
                    }
                  angle = data[i*4+0];
                  angleDot = data[i*4+1];
                  pos = data[i*4+2];
                  posDot = data[i*4+3];
                  //LEADER ELECTION 
        		  if (leader_not_elected && NUM_POLES == 2) {
		          //Leader is cart closer to target
			      cart_pos[0] = data[1]; //Populate location of cart 0
			      cart_pos[1] = data[5]; //Populate location of cart 1
			
			        if (targetPos < cart_pos[0]) {
				        leader = 0;
        			} else {
		        		leader = 1;
			        }
			        leader_not_elected = false;
		          } //END LEADER ELECTION
                  // ******************************    
                  // Leader will be elected by here
                  // ******************************
                  //Init the leaderPos array
		          for (int j = 0; j < leaderPos.length && (i == leader); j++) {
                    if (leaderPos[j] == 0) {
                        leaderPos[j] = pos;
                    }   
                  }
                  if (i == leader) {
                    leaderPos[leaderPosPtr%5] = pos;
                    if (leaderPosPtr == 5) {// PosPtr:Idx 0:0 1:1 2:2 3:3 4:4 5:0 1:1 ...
                       leaderPosPtr = 1; 
                    } else {
                       leaderPosPtr++;
                    }
                  }
		        cart_pos[i] = pos;
                System.out.println(String.format("%c[%d;%df server < pole[%d]:\tangle: %.4f\tangleDot: %.4f\tpos: %.4f\tposDot: %.4f\tdelay: %f", 0x1b, i+20, 0, i, angle, angleDot, pos, posDot, delay));
                time_unit = Math.round(delay/50.0);
                actions[i] = calculate_action(i, leader);
                prev_action[i] = actions[i];
        }

                //send message out
                sendMessage_doubleArray(actions);

            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            if (clientSocket != null) {
                System.out.println("closing down connection ...");                
                out.writeObject("bye");
                out.flush();
                in.close();
                out.close();
                clientSocket.close();
            }
        } catch (IOException ioe) {
            System.out.println("unable to disconnect");
        }

        System.out.println("Session closed. Waiting for new connection...");

    }

    /**
     * This method calls the controller method to balance the pendulum.
     * @throws ioException
     */
    public void run() {

        try {
            control_pendulum(out, in);

        } catch (Exception ioException) {
            ioException.printStackTrace();
        } finally {
        }

    }

    
    /**
     * This method will average a double array.
     *
     */
    private double avgDoubleArray(double[] arrayIn) {
        double arrayAvg = 0;
        for (int i=0; i < arrayIn.length; i++)
            arrayAvg+=arrayIn[i];
        return arrayAvg/arrayIn.length;   
    }


    /**
    * This method will redirect cursor location in VT-100 compatible terminals.
    * Used to make ControlServer output readable.
    */
    private void moveCursor() {
        System.out.print( String.format("%c[%d;%df", 0x1B, 40, 0) );
        return;
    }

    
    /**
    * This method checks various conditions to determine if its safe to for a forward movement optimization
    *
    */
    private boolean fwdOptimization(double dist_targ, double followers_target, int cart, int leader) {
        if (delay <= 71 &&      // Runs away too fast for greater latency 
            (  (cart == leader && dist_targ > .8) || //Leader check
                (cart != leader && followers_target > .8) ) && //Follower check 
            prevAction[cart] != 0 &&
            angle > .005 &&
            angle < .05 &&
            angleDot >= 0 &&
            angleDot < .02 &&
            posDot > .02) {
            return true;
        }
        return false;
    }


    /**
    * This method does cart projection using a local instantiation of the Physics class
    *
    */
    private double[] doProjection(double pos, double angle, double angleDot, double posDot, int cart, double followers_target, double dist_targ) {
        double result[] = {0,0,0,0}; //ANGLE, ANGLEDOT, POS, POSDOT
        
        // INSTANTIATE SIMULATED PENDULUM 
        Pendulum future_pend = new Pendulum(NUM_POLES+1, pos);
        future_pend.update_angle(angle);
        future_pend.update_angleDot(angleDot);
        future_pend.update_posDot(posDot);
        future_pend.update_action(prevAction[cart]);
        
        // LOOP (TIME_UNIT) TIMES THROUGH PHYSICS
        System.out.print( String.format("%c[%d;%dfProjecting %d frames into future.", 0x1B, 28, 0, time_unit) );
        moveCursor();

        for (int i = 0;i < time_unit; i++) {
            physics_prog.update_pendulum(future_pend);
            double temp_action =  10 / (80 * .0175) * future_pend.get_angle() + future_pend.get_angleDot() + future_pend.get_posDot();
            temp_action = apply_bump(temp_action, cart, leader, followers_target, dist_targ);
            future_pend.update_action(temp_action);
        }
            
        // PACK RESULTS FOR RETURN
        result[0] = future_pend.get_angle();    //ANGLE
        result[1] = future_pend.get_angleDot(); //ANGLEDOT
        result[2] = future_pend.get_pos();      //POS
        result[3] = future_pend.get_posDot();   //POSDOT

        return result;
    }

    // Calculate the actions to be applied to the inverted pendulum from the
    // sensing data.
    // TODO: Current implementation assumes that each pole is controlled
    // independently. The interface needs to be changed if the control of one
    // pendulum needs sensing data from other pendulums.
    double calculate_action(int cart, int leader) {
        // Set local varaiables
        double followers_target = 0;
        leaderPosAvg = avgDoubleArray(leaderPos);
        double dist_targ = Math.abs(leaderPosAvg - targetPos);
        double action = 0;
        
        if (NUM_POLES == 2) {
            followers_target = Math.abs( leaderPosAvg - cart_pos[1-leader] ) - offset_from_leader;
            System.out.print( String.format("%c[%d;%dfLeaders Average Position: %4f\t0: %4f\t1: %4f\t2: %4f\t3: %4f\t4: %4f", 0x1B, 15, 0, leaderPosAvg, leaderPos[0], leaderPos[1], leaderPos[2], leaderPos[3], leaderPos[4]) );
    	    moveCursor();
        }

        //If conditions are correct skip calculating an action and repeat last action for speed
        if (fwdOptimization(dist_targ, followers_target, cart, leader)) {
            System.out.print( String.format("%c[%d;%dfSPECIAL CONDITION: Pendulum in optimum angle for forward. Cart %2d is holding its action!", 0x1B, 34, 0, cart) );
            moveCursor();
            double temp = prevAction[cart];
            prevAction[cart] = 0;
            return temp;
        } else { //Clear alert
            System.out.print( String.format("%c[%d;%df                                                                                           ", 0x1B, 34, 0) );
        }

        /***************************************
        ** LAGGY TARGET LOCATION OPTIMIZATION **
        ****************************************/
        if ( leader == cart &&
             dist_targ < target_pad ) {
            dist_targ = 0; //Set to current avg spot
            hold_pos[0] = 10; //Give 10 frames to localize to new spot
        } else if (leader != cart && followers_target < target_pad) {
            followers_target = 0;
            hold_pos[1] = 10;
        }
         
        //if follower or leader is not stable set target to current pos
        if (angleDot > 1) {
                        //Follower must always wait if any cart is unstable
            hold_pos[1] = 7;
            followers_target = 0;
            if (cart == leader) {            // Leader is unstable
                hold_pos[0] = 7;
                dist_targ = 0;
            }
            System.out.print( String.format("%c[%d;%dfSPECIAL CONDITION: angleDot greater than 1 setting hold_pos. \n    Holding follow for %d frames\n    Holding lead for %d frames\n", 0x1B, 30, 0, hold_pos[1], hold_pos[0]) );

        }

        // Allow cart to stabilize for a few frames before trying to move again
        if ( (hold_pos[0] + hold_pos[1]) > 0) {
            // FOLLOWER HOLDS POSITION IF THERE ARE ANY UNSTABLE CARTS
            if (cart != leader &&
                hold_pos[1] > 0) {
                //followers_target /= 2;
                followers_target = 0;
                if ( angleDot < 2 ) { // If angleDot is in safe limits
                    hold_pos[1]--;
                    System.out.print( String.format("%c[%d;%df%2d", 0x1B, 31, 23, hold_pos[1]) );
                    System.out.print( String.format("%c[%d;%df                                ", 0x1B, 31, 40, hold_pos[1]) );
                    moveCursor();
                } else {
                    System.out.print( String.format("%c[%d;%df angleDot > 1, Increasing wait", 0x1B, 31, 40, hold_pos[1]) );
                    hold_pos[1]+=2;
                    moveCursor();
                }
            }
            //LEADER HOLDS POSITION IF IT IS UNSTABLE
            if (hold_pos[0] > 0) {
                dist_targ = 0;
                hold_pos[0]--;
                System.out.print( String.format("%c[%d;%df%2d", 0x1B, 32, 21, hold_pos[0]) );
                moveCursor();
            }
            
        }
        
        // DO PHYSICS PROJECTION
        double projected[] = doProjection(pos, angle, angleDot, posDot, cart, followers_target, dist_targ);
        action = 10 / (80 * .0175) * projected[0] + projected[1] + projected[3]; // ANGLE, ANGLEDOT, POSDOT

        action = apply_bump(action, cart, leader, followers_target, dist_targ);
        prevAction[cart] = action;
        
        if (cart == leader) {
            System.out.print( String.format("%c[%d;%df Leader doing action: %5f", 0x1B, 35, 0, action) );
        } else {
            System.out.print( String.format("%c[%d;%df Follower doing action: %5f", 0x1B, 36, 0, action) );
        }
        moveCursor();
        return action;
   }



double apply_bump(double action,int cart,int leader, double followers_target, double dist_targ) {
    double bump = .3;
    //ONLY APPLY BUMP IF STABLE
    if ( (cart == leader && hold_pos[0] > 0) ||
         (cart != leader && hold_pos[1] > 0) ) {
        return action;
    }
        // Leader only drives to map target
    	if (cart == leader) {
    		if (dist_targ > target_pad) { // Far from target
    			if (targetPos < pos) { // Target is to left
    			action += bump;
    			} else { // Target is to right
    			action -= bump;
    		}
    		} else { // Close to target
                action += dist_targ;
    		} 
    	}//END OF LEADER heading to target
    	
    	if (NUM_POLES == 2) {
    		if (cart != leader) {
    			if (followers_target > target_pad) {  //Far from target
    				if ( (cart_pos[leader]+offset_from_leader) < pos) { // Target is left, go left
    					action += bump;
    				} else { // Target is right, go right
    					action -= bump;
    				}
    				
    			} else { //Close to target
                    action += followers_target;
    
    			}
    
    		}
    	} 
    return action;
    }



    /**
     * This method sends the Double message on the object output stream.
     * @throws ioException
     */
    void sendMessage_double(double msg) {
        try {
            out.writeDouble(msg);
            out.flush();
            System.out.println("server>" + msg);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * This method sends the Double message on the object output stream.
     */
    void sendMessage_doubleArray(double[] data) {
        try {
            out.writeObject(data);
            out.flush();
            
            System.out.print("server> ");
            for(int i=0; i< data.length; i++){
                System.out.print(data[i] + "  ");
            }
            System.out.print(System.nanoTime() + " "); //HACK: Fix by pushing timestamp into data[]
            System.out.println();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }


}
