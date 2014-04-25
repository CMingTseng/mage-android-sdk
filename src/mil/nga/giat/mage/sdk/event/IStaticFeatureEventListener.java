package mil.nga.giat.mage.sdk.event;

import mil.nga.giat.mage.sdk.datastore.layer.Layer;

public interface IStaticFeatureEventListener extends IEventListener {

	/**
	 * The set of layers that the features were added to.
	 * 
	 * @param layers
	 */
	public void onStaticFeaturesCreated(final Layer layer);

}