package mil.nga.giat.mage.sdk.http.post;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Date;

import mil.nga.giat.mage.sdk.R;
import mil.nga.giat.mage.sdk.datastore.DaoStore;
import mil.nga.giat.mage.sdk.datastore.observation.Attachment;
import mil.nga.giat.mage.sdk.datastore.user.Event;
import mil.nga.giat.mage.sdk.datastore.user.User;
import mil.nga.giat.mage.sdk.datastore.user.UserHelper;
import mil.nga.giat.mage.sdk.gson.deserializer.UserDeserializer;
import mil.nga.giat.mage.sdk.http.client.HttpClientManager;
import mil.nga.giat.mage.sdk.jackson.deserializer.AttachmentDeserializer;
import mil.nga.giat.mage.sdk.utils.MediaUtility;

/**
 * A class that contains common POST requests to the MAGE server.
 * 
 * @author wiedemanns
 * 
 */
public class MageServerPostRequests {

	private static final String LOG_NAME = MageServerPostRequests.class.getName();

    private static AttachmentDeserializer attachmentDeserializer = new AttachmentDeserializer();

	/**
	 * POST an {@link Attachment} to the server.
	 * 
	 * @param attachment
	 *            The attachment to post.
	 * @param context
	 */
	// The following code will sometimes fail to post attachments
	public static Attachment postAttachment(Attachment attachment, Context context) {
		DefaultHttpClient httpClient = HttpClientManager.getInstance(context).getHttpClient();
		HttpEntity entity = null;
		try {
			Log.d(LOG_NAME, "Pushing attachment " + attachment.getId() + " to " + attachment.getObservation().getUrl() + "/attachments");
			URL endpoint = new URL(attachment.getObservation().getUrl() + "/attachments");
			
			HttpPost request = new HttpPost(endpoint.toURI());
			String mimeType = MediaUtility.getMimeType(attachment.getLocalPath());
			
			Log.d(LOG_NAME, "Mime type is: " + mimeType);

			if(mimeType == null) {
				throw new Exception("Attachment mimeType is " + String.valueOf(mimeType));
			}

			FileBody fileBody = new FileBody(new File(attachment.getLocalPath()), mimeType);
			FormBodyPart fbp = new FormBodyPart("attachment", fileBody);

			MultipartEntity reqEntity = new MultipartEntity();
			reqEntity.addPart(fbp);

			request.setEntity(reqEntity);
			HttpResponse response = httpClient.execute(request);
			entity = response.getEntity();
			if (entity != null) {
				Attachment a = attachmentDeserializer.parseAttachment(entity.getContent());
				attachment.setContentType(a.getContentType());
				attachment.setName(a.getName());
				attachment.setRemoteId(a.getRemoteId());
				attachment.setRemotePath(a.getRemotePath());
				attachment.setSize(a.getSize());
				attachment.setUrl(a.getUrl());
				attachment.setDirty(a.isDirty());

				// TODO go save this attachment again
				DaoStore.getInstance(context).getAttachmentDao().update(attachment);
			}

		} catch (Exception e) {
			Log.e(LOG_NAME, "Failure pushing attachment: " + attachment.getLocalPath(), e);
		} finally {
			try {
				if (entity != null) {
					entity.consumeContent();
				}
			} catch (Exception e) {
                Log.w(LOG_NAME, "Trouble cleaning up after POST request.", e);
			}
		}
		return attachment;
	}

	public static User postProfilePicture(User user, String absolutePath, Context context) {
		DefaultHttpClient httpClient = HttpClientManager.getInstance(context).getHttpClient();
		HttpEntity entity = null;
		try {
			Log.d(LOG_NAME, "Pushing profile picture for  " + user.getRemoteId());
			URL serverURL = new URL(PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.serverURLKey), context.getString(R.string.serverURLDefaultValue)));
			URL endpoint = new URL(serverURL + "/api/users/" + user.getRemoteId());
			
			HttpPut request = new HttpPut(endpoint.toURI());
			String mimeType = MediaUtility.getMimeType(absolutePath);
			
			Log.d(LOG_NAME, "Mime type is: " + mimeType);

			FileBody fileBody = new FileBody(new File(absolutePath), mimeType);
			FormBodyPart fbp = new FormBodyPart("avatar", fileBody);

			MultipartEntity reqEntity = new MultipartEntity();
			reqEntity.addPart(fbp);

			request.setEntity(reqEntity);
			HttpResponse response = httpClient.execute(request);
			entity = response.getEntity();
			if (entity != null) {
				final Gson userDeserializer = UserDeserializer.getGsonBuilder(context);
				String entityString = EntityUtils.toString(entity);
				
				JSONObject userJson = new JSONObject(entityString);
				if (userJson != null) {
					User newUser = userDeserializer.fromJson(userJson.toString(), User.class);
					UserHelper userHelper = UserHelper.getInstance(context);
					if (newUser != null) {
						User oldUser = userHelper.read(newUser.getRemoteId());
						if (oldUser == null) {
							newUser.setCurrentUser(user.isCurrentUser());
							newUser.setFetchedDate(new Date());
							newUser = userHelper.create(newUser);
							Log.d(LOG_NAME, "Created user with remote_id " + user.getRemoteId());
						} else {
							// perform update?
							newUser.setId(oldUser.getId());
							newUser.setCurrentUser(user.isCurrentUser());
							newUser.setFetchedDate(new Date());
							userHelper.update(newUser);
							Log.d(LOG_NAME, "Updated user with remote_id " + newUser.getRemoteId());
						}
						return newUser;
					}
				}
				return user;
			} else {
				Log.e(LOG_NAME, "Unable to save profile picture.");
				return user;
			}

		} catch (Exception e) {
			Log.e(LOG_NAME, "Failure pushing profile picture: " + absolutePath, e);
		} finally {
			try {
				if (entity != null) {
					entity.consumeContent();
				}
			} catch (Exception e) {
                Log.w(LOG_NAME, "Trouble cleaning up after POST request.", e);
			}
		}
		return user;
	}

    public static Boolean postCurrentUsersRecentEvent(Event event, Context context) {
        Boolean status = false;
        HttpEntity entity = null;
        try {
            UserHelper userHelper = UserHelper.getInstance(context);
            User currentUser = userHelper.readCurrentUser();

            URL serverURL = new URL(PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.serverURLKey), context.getString(R.string.serverURLDefaultValue)));
            URI endpointUri = new URL(serverURL + "/api/users/" + currentUser.getRemoteId() + "/events/" + event.getRemoteId() + "/recent").toURI();

            DefaultHttpClient httpClient = HttpClientManager.getInstance(context).getHttpClient();
            HttpPost request = new HttpPost(endpointUri);
            request.addHeader("Content-Type", "application/json; charset=utf-8");

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                status = true;
            } else {
                entity = response.getEntity();
                String error = EntityUtils.toString(entity);
                Log.e(LOG_NAME, "Bad request.");
                Log.e(LOG_NAME, error);
            }
        } catch (Exception e) {
            Log.e(LOG_NAME, "Failure posting user's recent event.", e);
        } finally {
            try {
                if (entity != null) {
                    entity.consumeContent();
                }
            } catch (Exception e) {
                Log.w(LOG_NAME, "Trouble cleaning up after POST request.", e);
            }
        }
        return status;
    }


    public static Boolean logout(Context context) {
        Boolean status = false;
        HttpEntity entity = null;
        try {

            URL serverURL = new URL(PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.serverURLKey), context.getString(R.string.serverURLDefaultValue)));
            URI endpointUri = new URL(serverURL + "/api/logout").toURI();

            DefaultHttpClient httpClient = HttpClientManager.getInstance(context).getHttpClient();
            HttpPost request = new HttpPost(endpointUri);
            request.addHeader("Content-Type", "application/json; charset=utf-8");

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                status = true;
            } else {
                entity = response.getEntity();
                String error = EntityUtils.toString(entity);
                Log.e(LOG_NAME, "Bad request.");
                Log.e(LOG_NAME, error);
            }
        } catch (Exception e) {
            Log.e(LOG_NAME, "Failure logging out of server.", e);
        } finally {
            try {
                if (entity != null) {
                    entity.consumeContent();
                }
            } catch (Exception e) {
                Log.w(LOG_NAME, "Trouble cleaning up after POST request.", e);
            }
        }
        return status;
    }
}
