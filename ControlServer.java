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
    private static final int NUM_POLES = 1;

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
    double targetPos = 1;

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
		int[] cart_state = new int[NUM_POLES];	
                for (int i = 0; i < NUM_POLES; i++) {
                  angle = data[i*4+0];
                  angleDot = data[i*4+1];
                  pos = data[i*4+2];
                  posDot = data[i*4+3];
		  
		  if (Math.abs(angle) > 0.05) {
			cart_state[i] = 0; // If not balanced	
		  } else {
			cart_state[i] = 1;
		  }
                  cart_pos[i] = pos;

                  System.out.println("server < pole["+i+"]: "+angle+"  "
                      +angleDot+"  "+pos+"  "+posDot);
                  actions[i] = calculate_action(angle, angleDot, pos, posDot, i, cart_pos, cart_state);
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
    double calculate_action(double angle, double angleDot, double pos, double posDot, int cart, double[] cart_pos, int[] cart_state) {
      

	double action =  10 / (80 * .0175) * angle + angleDot + posDot;// + pos; //This balances each cart individually
	//double dist_apart = Math.sqrt( Math.abs(Math.pow(cart_pos[0], 2) - Math.pow(cart_pos[1], 2)) );
	//System.out.println("Distance apart: " + dist_apart);
	double push_away = .5;
	double dist_targ = Math.sqrt( Math.abs(Math.pow(pos,2) - Math.pow(targetPos, 2) ) );
	if (dist_targ > .1) {
		if (targetPos < pos) { // Target is to left
		action += .5;
		} else { // Target is to right
		action -= .5;
		}
	} else {
		action += dist_targ;
	}
	
	/*	if (cart == 0) {
			if (dist_apart < 1) {
				if (cart_pos[1] < pos) { //If cart is to left of 0
					action += push_away; // Go left
				} else {
					action -= push_away;
				}
			}
			if (cart_state[cart] == 1) {
				action -= targetPos;
			}
		} else {
			if (dist_apart < 1) {
				//Move away from other cart
				if (cart_pos[0] < pos) { //Leader is left of follower
					action += push_away; //Big action away from left
				} else{
					action -= push_away; //Big action away from right
				}
			}
			if (cart_state[cart] == 1) {
				action -= targetPos + .5;
			}

		} */
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
