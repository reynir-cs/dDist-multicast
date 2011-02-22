import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;


public class PollCallbackExample {

	/**
	 * Test stub. 
	 * Call with "server" to start server. 
	 * Call with "client <inet_address>" to start client when server is at <inet_address>.
	 * Call with "" to start as client and server.
	 * 
	 * @param args the command line
	 */
	public static void main(String[] args) {
	        for (int i=0; i<args.length; i++) {
		        System.out.print(args[i] + " ");
	        }
		System.out.println();
		boolean isClient = false;
		PointToPointQueueSenderEndNonRobust<String> s = null; 
		
		// Are we the client?
		if ((args.length == 2 && args[0].equals("client")) || args.length==0) {
			isClient = true;
			System.out.println("XXXXX");
			// Create sender end of a distributed queue of BigIntegers
			s = new PointToPointQueueSenderEndNonRobust<String>();
			// Let the queue listen on port 40499 for incoming BigIntegers
			if (args.length == 2) {
				s.setReceiver(new InetSocketAddress(args[1],40499));
			} else {
				s.setReceiver(new InetSocketAddress("localhost",40499));				
			}

			int i = 0;
			BigInteger twoToI = BigInteger.ONE;
			for (; i<10000; i++) {
				twoToI = twoToI.add(twoToI);
				s.put("MESSAGE: 2^" + i + " = " + twoToI);
			}
		}
		// Are we the server?
		if ((args.length == 1 && args[0].equals("server")) || args.length==0) {
			// Create receiver end of a distributed queue of BigIntegers
			final PointToPointQueueReceiverEndNonRobust<String> r 
				= new PointToPointQueueReceiverEndNonRobust<String>();
			try {
				// Let the queue listen on port 40499 for incoming BigIntegers
				r.listenOnPort(40499);
				// Define a callback which simply prints the received message.
				final class PrintMsg implements Callback<String> {
					private int msgs = 0;
					public void result(String msg) {
						System.out.println(msg);
						msgs++;
						if (msgs==10000) {
							r.shutdown();
						}
					}
				}
				 // Print all messages received on r.
				new PollCallbackAdapter<String>(r, new PrintMsg());
			} catch (IOException e) {
				System.err.println(e);
			}
		}
		if (isClient) {
			while (!s.isEmpty()) {
			}
			s.shutdown();
		}
	}

}
