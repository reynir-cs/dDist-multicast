import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;

/**
 * Turns a Pollable receiving point into a Callback-based receiving point.
 * Will poll for ready messages and do a callback for each of them.
 * 
 * @author Jesper Buus Nielsen, Aarhus University, 2011.
 *
 */
public class PollCallbackAdapter<E> implements Runnable {

	/**
	 *  
	 * @param pollable The pollable to make callback-based.
	 * @param callback The callback which processes received objects.
	 */
	public PollCallbackAdapter(Pollable<E> pollable, Callback<E> callback) {
		if (pollable == null || callback == null) {
			throw new NullPointerException();
		}
		this.pollable = pollable;
		this.callback = callback;
		new Thread(this).start();
	}

	private final Pollable<E> pollable;
	private final Callback<E> callback;

	public void run() {
		E object;
		while ((object = pollable.poll())!=null) {
			callback.result(object);
		}
	}
	
}
