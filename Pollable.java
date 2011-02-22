
public interface Pollable<E> {
	
	/**
	 * 
	 * Models a point where objects of the type E can be received. 
	 * If objects are ready, one of them are returned. If none
	 * are ready, the call blocks until more objects are ready.
	 * If the pollable is dead, i.e., no more objects can ever 
	 * be ready, then null is returned.
	 * 
	 * @return The received object, null if the pollable is dead.
	 */
	public E poll();

}
