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
    double angle, angleDot, posDot, action = 0;
    double i = 0;
    double pos = 0;
    double targetPos = 2;
    double delay = 400; //network delay in ms
    long time_unit = 0;
    Physics physics_prog = new Physics(.01, .01/.1);
    double[] prev_action = new double[NUM_POLES];
    double[] cart_pos = new double[NUM_POLES];
    double offset_from_leader = 1.3; //Pad distance follower <-> leader
    double target_pad = .1; // Defines when cart is 'at target'

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
				boolean leader_not_elected = true;
		int leader = 0;
		delay = data[11]/2;
        for (int i = 0; i < NUM_POLES; i++) {
                  if (prev_action[i] == 0) {
                    prev_action[i] = .75;
                    }
                  angle = data[i*4+0];
                  angleDot = data[i*4+1];
                  pos = data[i*4+2];
                  posDot = data[i*4+3];
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
		  }
		  
		  cart_pos[i] = pos;
                System.out.println("server < pole["+i+"]: "+angle+"  "+angleDot+"  "+pos+"  "+posDot);
                time_unit = Math.round(delay/100.0);
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

    // Calculate the actions to be applied to the inverted pendulum from the
    // sensing data.
    // TODO: Current implementation assumes that each pole is controlled
    // independently. The interface needs to be changed if the control of one
    // pendulum needs sensing data from other pendulums.
    double calculate_action(int cart, int leader) {
        double followers_target = 0;
        double dist_targ = Math.abs(cart_pos[leader] - targetPos); // Only cart 0 gets a dist_targ
    	
    	if (NUM_POLES == 2) {
			followers_target = Math.abs( cart_pos[leader] - cart_pos[1-leader] ) - offset_from_leader;		
    	}

        /****************************
         ** Make SIMULATED PENDULUM *
        *****************************/
        Pendulum future_pend = new Pendulum(NUM_POLES+1, pos);
        future_pend.update_angle(angle);
        future_pend.update_angleDot(angleDot);
        future_pend.update_posDot(posDot);
        future_pend.update_action(prev_action[cart]);
        /***************************************
        *** Loop (time_unit) times using phys **
        ****************************************/
        for (int i = 0;i < time_unit; i++) {
            physics_prog.update_pendulum(future_pend);
            double temp_action =  10 / (80 * .0175) * future_pend.get_angle() + future_pend.get_angleDot() + future_pend.get_posDot();
            temp_action = apply_bump(temp_action, cart, leader, followers_target, dist_targ);
            future_pend.update_action(temp_action);
        }
        
        
        angle = future_pend.get_angle();
        angleDot = future_pend.get_angleDot();
        pos = future_pend.get_pos();
        posDot = future_pend.get_posDot();
        

        double action =  10 / (80 * .0175) * angle + angleDot + posDot;
        action = apply_bump(action, cart, leader, followers_target, dist_targ);
       	
        return action;
   }



double apply_bump(double action,int cart,int leader, double followers_target, double dist_targ) {
    double bump = .2;
        	// Leader only drives to map target
    	if (cart == leader) {
    		if (dist_targ > target_pad) {
    			if (targetPos < pos) { // Target is to left
    			action += bump;
    			} else { // Target is to right
    			action -= bump;
    		}
    		} else {
    			action += (dist_targ/2);
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
    					action += (followers_target/2);
    
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
