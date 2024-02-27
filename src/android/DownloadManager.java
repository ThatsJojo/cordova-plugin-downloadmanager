package downloadmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PluginResult;

/**
 * This class echoes a string called from JavaScript.
 */
public class DownloadManager extends CordovaPlugin {

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("download")) {
            String url = args.getString(0);
            String fileName = args.getString(1);
            String des = args.getString(2);
            JSONObject options = args.getJSONObject(3);

            try {
                this.startDownload(url, fileName, des, options, callbackContext);
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
            return true;
        }
        if (action.equals("addCompletedDownload")) {
            String title = args.getString(0);
            String description = args.getString(1);
            String mimeType = args.getString(2);
            String path = args.getString(3);
            long length = args.getLong(4);
            this.addCompletedDownload(title, description, mimeType, path, length, callbackContext);
            return true;
        }
        return false;
    }

    private void addCompletedDownload(String title, String description, String mimeType, String path, long length, CallbackContext callbackContext) {

        if(title != null && title.length() > 0 && description != null && description.length() > 0 && mimeType != null && mimeType.length() > 0 && path != null && path.length() > 0 && length > 0) {

            android.app.DownloadManager downloadManager = (android.app.DownloadManager) cordova.getActivity().getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);

            try {
                downloadManager.addCompletedDownload(title, description, false, mimeType, path, length, true);
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }

            callbackContext.success(title);
        } else {
            callbackContext.error("Invalid one or more arguments.");
        }
    }

    private void startDownload(String url, String fileName, String description, JSONObject options, CallbackContext callbackContext) throws UnsupportedEncodingException, JSONException {
        if (url != null && url.length() > 0) {
            boolean useIncomingFileName = options.getBoolean("useIncomingFileName");
            boolean usePublic = options.getBoolean("setDestinationInExternalPublicDir");
            boolean openAfterDownload = options.getBoolean("openAfterDownload");
            String finalFileName = useIncomingFileName
                    ? URLUtil.guessFileName(url, null, MimeTypeMap.getFileExtensionFromUrl(url))
                    : URLDecoder.decode(fileName,"UTF-8");

            android.app.DownloadManager downloadManager = (android.app.DownloadManager) cordova.getActivity().getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);
            Uri Download_Uri = Uri.parse(url);
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Download_Uri);
            //Restrict the types of networks over which this download may proceed.
            request.setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI | android.app.DownloadManager.Request.NETWORK_MOBILE);
            //Set whether this download may proceed over a roaming connection.
            request.setAllowedOverRoaming(false);
            //Set the title of this download, to be displayed in notifications (if enabled).
            request.setTitle(finalFileName);
            //Set a description of this download, to be displayed in notifications (if enabled)
            request.setDescription(description);
            //Set the local destination for the downloaded file to a path within the application's external files directory
            //If usePublic is true, use setDestinationInExternalPublicDir().
            if (usePublic) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName);
            } else {
                request.setDestinationInExternalFilesDir(cordova.getActivity().getApplicationContext(), Environment.DIRECTORY_DOWNLOADS, finalFileName);
            }
            //Set visiblity after download is complete
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            long downloadID = downloadManager.enqueue(request);

            if (!openAfterDownload) {
                callbackContext.success(Download_Uri.toString());
                return;
            }

            BroadcastReceiver onComplete = new BroadcastReceiver() {
                public void onReceive(Context ctxt, Intent intent) {
                    long referenceId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    
                    if (downloadID == referenceId) {
                        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), finalFileName);
                        String mime = _getMimeType(file.getName());
                        Intent newIntent = new Intent(Intent.ACTION_VIEW);
                        newIntent.setDataAndType(Download_Uri, mime);
                        newIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        
                        if (newIntent.resolveActivity(cordova.getContext().getPackageManager()) != null) {
                            cordova.getActivity().startActivity(newIntent);
                        }

                        callbackContext.success(Download_Uri.toString());
                    }
                }
            };

            cordova.getActivity().registerReceiver(onComplete, new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }

    private String _getMimeType(String url) {
        String mimeType = "*/*";
        int extensionIndex = url.lastIndexOf('.');

        if (extensionIndex > 0) {
            String extMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(url.substring(extensionIndex+1));
            if (extMimeType != null) {
                mimeType = extMimeType;
            }
        }

        return mimeType;
    }
}
