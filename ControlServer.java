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

    /**
     * This method receives the pole positions and calculates the updated value
     * and sends them across to the client.
     * It also sends the amount of force to be applied to balance the pendulum.
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
                assert(data.length == NUM_POLES * 4);
                double[] actions = new double[NUM_POLES];
 
                // Get sensor data of each pole and calculate the action to be
                // applied to each inverted pendulum
                // TODO: Current implementation assumes that each pole is
                // controlled independently. This part needs to be changed if
                // the control of one pendulum needs sensing data from other
                // pendulums.
		double[] cart_pos = new double[NUM_POLES];
		boolean leader_not_elected = true;
		int leader = 0;
		for (int i = 0; i < NUM_POLES; i++) {
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
                  actions[i] = calculate_action(angle, angleDot, pos, posDot, i, cart_pos, leader);
                }

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
    double calculate_action(double angle, double angleDot, double pos, double posDot, int cart, double[] cart_pos, int leader) {
      
		
	double action =  10 / (80 * .0175) * angle + angleDot + posDot;// + pos; //This balances each cart individually
	double followers_target = 0;
	double offset_from_leader = 1;

	if (NUM_POLES == 2) {
			followers_target = Math.abs( cart_pos[leader] - cart_pos[1-leader] ) - offset_from_leader;
			
	}
	double dist_targ = Math.abs(cart_pos[leader] - targetPos); // Only cart 0 gets a dist_targ
	
	// Leader only drives to map target
	if (cart == leader) {
		if (dist_targ > .1) {
			if (targetPos < pos) { // Target is to left
			action += .5;
			} else { // Target is to right
			action -= .5;
		}
		} else {
			action += dist_targ;
		} 
	}//END OF LEADER heading to target
	
	if (NUM_POLES == 2) {
		if (cart != leader) {
			if (followers_target > .1) {  //Far from target
				if ( (cart_pos[leader]+offset_from_leader) < pos) { // Target is left, go left
					action += .5;
				} else { // Target is right, go right
					action -= .5;
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
            System.out.println();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }


}
