package mil.nga.giat.mage.sdk.event;

/**
 * Part of a small event framework. Used to pass events to different parts of
 * the mdk. When locations are saved, when tokens expire, etc...
 * 
 * @author wiedemannse
 * 
 * @param <T>
 */
public interface IEventDispatcher<T> {

	/**
	 * Adds a listener
	 * 
	 * @param listener
	 * @return
	 * @throws Exception
	 */
	public boolean addListener(final IEventListener<T> listener) throws Exception;

	/**
	 * Removes the listener
	 * 
	 * @param listener
	 * @return
	 * @throws Exception
	 */
	public boolean removeListener(final IEventListener<T> listener) throws Exception;
}
