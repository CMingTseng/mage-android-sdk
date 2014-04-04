package mil.nga.giat.mage.sdk.connectivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import mil.nga.giat.mage.sdk.event.IEventDispatcher;
import mil.nga.giat.mage.sdk.event.IEventListener;
import mil.nga.giat.mage.sdk.event.connectivity.IConnectivityEventListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkChangeReceiver extends BroadcastReceiver implements IEventDispatcher<Void> {

	private static final int sleepDelay = 10; // in seconds
	
	private static final String LOG_NAME = NetworkChangeReceiver.class.getName();

	private static Collection<IConnectivityEventListener> listeners = new ArrayList<IConnectivityEventListener>();

	private static ScheduledExecutorService connectionFutureWorker = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledFuture<?> connectionDataFuture = null;
	private static Boolean oldConnectionAvailabilityState = null;	
	
	private static ScheduledExecutorService wifiFutureWorker = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledFuture<?> wifiFuture = null;
	private static Boolean oldWifiAvailabilityState = null;

	private static ScheduledExecutorService mobileFutureWorker = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledFuture<?> mobileDataFuture = null;
	private static Boolean oldMobileDataAvailabilityState = null;
	
	@Override
	public void onReceive(final Context context, final Intent intent) {
		final ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		final NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		final NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		
		final boolean newWifiAvailabilityState = wifi.isAvailable();
		final boolean newMobileDataAvailabilityState = mobile.isAvailable();
		final boolean newConnectionAvailabilityState = newWifiAvailabilityState || newMobileDataAvailabilityState;
		
		// set the old state if it's the first time through!
		if(oldWifiAvailabilityState == null) {
			oldWifiAvailabilityState = !newWifiAvailabilityState;
		}
		
		if(oldMobileDataAvailabilityState == null) {
			oldMobileDataAvailabilityState = !newMobileDataAvailabilityState;
		}
		
		if(oldConnectionAvailabilityState == null) {
			oldConnectionAvailabilityState = !newConnectionAvailabilityState;
		}
		
		// was there a change in wifi?
		if (oldWifiAvailabilityState ^ newWifiAvailabilityState) {
			// is wifi now on?
			if(newWifiAvailabilityState) {
				Runnable task = new Runnable() {
					public void run() {
						for (IConnectivityEventListener listener : listeners) {
							listener.onWifiConnected();
						}
						Log.d(LOG_NAME, "WIFI IS ON");
					}
				};
				wifiFuture = wifiFutureWorker.schedule(task, sleepDelay, TimeUnit.SECONDS);	
			} else {
				if(wifiFuture != null) {
					wifiFuture.cancel(false);
					wifiFuture = null;
				}
				for (IConnectivityEventListener listener : listeners) {
					listener.onWifiDisconnected();
				}
				Log.d(LOG_NAME, "WIFI IS OFF");
			}
		}
		
		// was there a change in mobile data?
		if (oldMobileDataAvailabilityState ^ newMobileDataAvailabilityState) {
			// is mobile data now on?
			if(newMobileDataAvailabilityState) {
				Runnable task = new Runnable() {
					public void run() {
						for (IConnectivityEventListener listener : listeners) {
							listener.onMobileDataConnected();
						}
						Log.d(LOG_NAME, "MOBILE DATA IS ON");
					}
				};
				mobileDataFuture = mobileFutureWorker.schedule(task, sleepDelay, TimeUnit.SECONDS);	
			} else {
				if(mobileDataFuture != null) {
					mobileDataFuture.cancel(false);
					mobileDataFuture = null;
				}
				for (IConnectivityEventListener listener : listeners) {
					listener.onMobileDataDisconnected();
				}
				Log.d(LOG_NAME, "MOBILE DATA IS OFF");
			}
		}
		
		// was there a change in general connectivity?
		if (oldConnectionAvailabilityState ^ newConnectionAvailabilityState) {
			// is mobile data now on?
			if(newConnectionAvailabilityState) {
				Runnable task = new Runnable() {
					public void run() {
						for (IConnectivityEventListener listener : listeners) {
							listener.onAnyConnected();
						}
						Log.d(LOG_NAME, "CONNECTIVITY IS ON");
					}
				};
				connectionDataFuture = connectionFutureWorker.schedule(task, sleepDelay, TimeUnit.SECONDS);	
			} else {
				if(connectionDataFuture != null) {
					connectionDataFuture.cancel(false);
					connectionDataFuture = null;
				}
				for (IConnectivityEventListener listener : listeners) {
					listener.onAllDisconnected();
				}
				Log.d(LOG_NAME, "CONNECTIVITY IS OFF");
			}
		}
		
		// set the old states!
		oldWifiAvailabilityState = newWifiAvailabilityState;
		oldMobileDataAvailabilityState = newMobileDataAvailabilityState;
		oldConnectionAvailabilityState = newConnectionAvailabilityState;
	}

	@Override
	public boolean addListener(IEventListener<Void> listener) {
		return listeners.add((IConnectivityEventListener) listener);
	}

	@Override
	public boolean removeListener(IEventListener<Void> listener) {
		return listeners.remove((IConnectivityEventListener) listener);
	}
}