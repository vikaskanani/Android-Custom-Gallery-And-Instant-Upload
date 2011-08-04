package com.isummation.customgallery;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.os.AsyncTask.Status;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class UploadQueue extends Activity {
	private QueueAdapter queueAdapter;
	private ListView UploadList;

	private static final String LINE_START = "--";
	private static final String LINE_END = "\r\n";
	private static final String BOUNDRY = "*****";

	private SSLSocketFactory defaultSSLSocketFactory = null;
	private HostnameVerifier defaultHostnameVerifier = null;
	
	private ProgressDialog progressDialog;
	private static final int PROGRESSDIALOG_ID = 0;
	
	private static final int CANCELED = -4;
	private static final int OTHER_INTERNAL_ERROR = -3; //part of internal error
	private static final int SECURITY_ERROR = -2; //part of internal error
	private static final int SERVER_STATUS_FAILED = -1; //server side failed
	private static final int SERVER_STATUS_DEFAULT = 0; //pending
	private static final int SERVER_STATUS_UPLOADED = 1; //success
	
	private UploadTask uploadTask;
	private int uploadCounter;
	private boolean uploadFlag;
	private String PhotoId;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.uploadqueue);
		
		PhotoId = getIntent().getStringExtra("photoId");
		
		queueAdapter = new QueueAdapter();
		String ids = getIntent().getStringExtra("Ids");
		queueAdapter.initialize(ids);
		UploadList = (ListView) findViewById(R.id.UploadList);
		UploadList.setItemsCanFocus(true);
		UploadList.setAdapter(queueAdapter);
		
		Button startUploadBtn = (Button) findViewById(R.id.StartUploadBtn);
		startUploadBtn.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				showDialog(PROGRESSDIALOG_ID);
			}
		});
	}

	public class QueueAdapter extends BaseAdapter {
		private LayoutInflater mInflater;
		public ArrayList<QueueItem> queueItems = new ArrayList<QueueItem>();

		public QueueAdapter() {
			mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public void initialize(String ids) {
			queueItems.clear();
			
			String[] arrIds = ids.split(",");
			for (String item: arrIds){
				QueueItem queueItem = new QueueItem();
				queueItem.media_id = Long.parseLong(item);
				
				final String[] columns = { MediaStore.Images.Media.DATA };
				final String orderBy = MediaStore.Images.Media._ID;
				Cursor imagecursor = managedQuery(
						MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns,
						MediaStore.Images.Media._ID + " = " + queueItem.media_id + "", null,
						orderBy);
				int count = imagecursor.getCount();
				for (int i = 0; i < count; i++) {
					imagecursor.moveToPosition(i);
					int dataColumnIndex = imagecursor
							.getColumnIndex(MediaStore.Images.Media.DATA);
					queueItem.path = imagecursor.getString(dataColumnIndex);
				}
				imagecursor.close();
				
				queueItems.add(queueItem);
			}
			notifyDataSetChanged();
		}

		public int getCount() {
			return queueItems.size();
		}

		public int getUploadCount() {
			int cnt = 0;
			for (QueueItem item : queueItems) {
				if (item.uploaded != 1)
					cnt++;
			}
			return cnt;
		}

		public QueueItem getItem(int position) {
			return queueItems.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				holder = new ViewHolder();
				convertView = mInflater.inflate(R.layout.uploadqueueitem, null);
				holder.imageview = (ImageView) convertView
						.findViewById(R.id.QueueItemThumbnail);
				holder.caption = (EditText) convertView
						.findViewById(R.id.QueueItemCaption);
				holder.uploadedStatus = (TextView) convertView
						.findViewById(R.id.uploadStatus);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			QueueItem item = getItem(position);
			final String[] columns = { MediaStore.Images.Media.DATA };
			final String orderBy = MediaStore.Images.Media._ID;
			Cursor imagecursor = managedQuery(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns,
					MediaStore.Images.Media._ID + " = " + item.media_id + "", null,
					orderBy);
			int count = imagecursor.getCount();
			for (int i = 0; i < count; i++) {
				imagecursor.moveToPosition(i);
				holder.imageview.setImageBitmap(MediaStore.Images.Thumbnails.getThumbnail(
						getContentResolver(), item.media_id,
						MediaStore.Images.Thumbnails.MICRO_KIND, null));
			}
			imagecursor.close();
			
			if (item.uploaded == 1)
				holder.uploadedStatus.setText("Uploaded");
			else if (item.uploaded == 0)
				holder.uploadedStatus.setText("Pending");
			else
				holder.uploadedStatus.setText("Failed"); // value -1

			holder.caption.setId(position);
			holder.caption.setText(item.caption);
			holder.caption
					.setOnFocusChangeListener(new OnFocusChangeListener() {

						public void onFocusChange(View v, boolean hasFocus) {
							// TODO Auto-generated method stub
							if (!hasFocus) {
								final int position = v.getId();
								final EditText Caption = (EditText) v;
								queueAdapter.queueItems.get(position).caption = Caption
										.getText().toString();
							}
						}
					});

			return convertView;
		}	
	}
	
	public static class ViewHolder {
		ImageView imageview;
		EditText caption;
		TextView uploadedStatus;
	}
	class QueueItem {
		String path;
		long media_id;
		String caption;
		int uploaded;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case PROGRESSDIALOG_ID:
			removeDialog(PROGRESSDIALOG_ID);
			progressDialog = new ProgressDialog(this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMax(queueAdapter.getUploadCount());
			progressDialog.setTitle("Uploading");
			progressDialog.setCancelable(true);
			progressDialog.setMessage("Please wait...");
			progressDialog.setOnCancelListener(new OnCancelListener(){

				public void onCancel(DialogInterface dialog) {
					// TODO Auto-generated method stub
					if (uploadTask != null && uploadTask.getStatus() != AsyncTask.Status.FINISHED)
						uploadTask.cancel(true);
				}
		
			});
	    	break;
	    default:
	    	progressDialog = null;
	    }
	    return progressDialog;
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case PROGRESSDIALOG_ID:
			if (uploadTask != null && uploadTask.getStatus() != Status.FINISHED)
				uploadTask.cancel(true);
			uploadTask = new UploadTask();
			uploadTask.execute();
			break;
		}
	}
	
	class UploadTask extends AsyncTask<Void, Integer, String> {
		
		@Override
		protected String doInBackground(Void... unused) {
			uploadCounter = 0;
			for (QueueItem item:queueAdapter.queueItems){
				
				if(isCancelled())
					return (null);
				
				if (item.uploaded != 1){
					
					String uploadPath = item.path;
					
					BitmapFactory.Options o = new BitmapFactory.Options();
					o.inJustDecodeBounds = true;
					BitmapFactory.decodeFile(item.path, o);
					int owidth = o.outWidth;
					int oheight = o.outHeight;
					System.gc();
					Bitmap bitmap;
					try {
						bitmap = decodeFile(item.path);
						if (bitmap == null)
							throw new Exception();
					} catch (Exception e) {
						e.printStackTrace();
						//stop the work as there is no more memory available
						return (null);
					}
					
					if(isCancelled())
						return (null);
					
					int height = bitmap.getHeight();
					int width = bitmap.getWidth();
					
					if (owidth > width || oheight > height) {
						try{
							String oName = item.path.substring(item.path
									.lastIndexOf("/") + 1);
							File myDirectory = new File(
									Environment.getExternalStorageDirectory()
											+ "/REOAllegiance/temp/");
							if (myDirectory.isDirectory()) {
								String[] children = myDirectory.list();
								for (int j = 0; j < children.length; j++) {
									new File(myDirectory, children[j]).delete();
								}
							}
							myDirectory.mkdirs();
							File file = new File(myDirectory, "tmp_" + oName);
							uploadPath = myDirectory + "/tmp_" + oName;
							FileOutputStream fos = new FileOutputStream(file);
							bitmap.compress(CompressFormat.JPEG, 100, fos);
							bitmap.recycle();
							fos.flush();
							fos.close();
						} catch (SecurityException e) {
							e.printStackTrace();
							//STOP the work as there is some security issue that does not allow file operation
							publishProgress(SECURITY_ERROR);
							return (null);
						} catch (Exception e) {
							e.printStackTrace();
							//STOP the work as there is an IOException that does not allow file operation
							publishProgress(OTHER_INTERNAL_ERROR);
							return (null);
						}
						
						if(isCancelled())
							return (null);
						
						try{
							// copy paste exif information from original file to new
							// file
							ExifInterface oldexif = new ExifInterface(
									item.path);
							ExifInterface newexif = new ExifInterface(uploadPath);

							int build = Build.VERSION.SDK_INT;

							// From API 11
							if (build >= 11) {
								if (oldexif.getAttribute("FNumber") != null) {
									newexif.setAttribute("FNumber",
											oldexif.getAttribute("FNumber"));
								}
								if (oldexif.getAttribute("ExposureTime") != null) {
									newexif.setAttribute("ExposureTime",
											oldexif.getAttribute("ExposureTime"));
								}
								if (oldexif.getAttribute("ISOSpeedRatings") != null) {
									newexif.setAttribute("ISOSpeedRatings",
											oldexif.getAttribute("ISOSpeedRatings"));
								}
							}
							// From API 9
							if (build >= 9) {
								if (oldexif.getAttribute("GPSAltitude") != null) {
									newexif.setAttribute("GPSAltitude",
											oldexif.getAttribute("GPSAltitude"));
								}
								if (oldexif.getAttribute("GPSAltitudeRef") != null) {
									newexif.setAttribute("GPSAltitudeRef",
											oldexif.getAttribute("GPSAltitudeRef"));
								}
							}
							// From API 8
							if (build >= 8) {
								if (oldexif.getAttribute("FocalLength") != null) {
									newexif.setAttribute("FocalLength",
											oldexif.getAttribute("FocalLength"));
								}
								if (oldexif.getAttribute("GPSDateStamp") != null) {
									newexif.setAttribute("GPSDateStamp",
											oldexif.getAttribute("GPSDateStamp"));
								}
								if (oldexif.getAttribute("GPSProcessingMethod") != null) {
									newexif.setAttribute(
											"GPSProcessingMethod",
											oldexif.getAttribute("GPSProcessingMethod"));
								}
								if (oldexif.getAttribute("GPSTimeStamp") != null) {
									newexif.setAttribute("GPSTimeStamp", ""
											+ oldexif.getAttribute("GPSTimeStamp"));
								}
							}
							if (oldexif.getAttribute("DateTime") != null) {
								newexif.setAttribute("DateTime",
										oldexif.getAttribute("DateTime"));
							}
							if (oldexif.getAttribute("Flash") != null) {
								newexif.setAttribute("Flash",
										oldexif.getAttribute("Flash"));
							}
							if (oldexif.getAttribute("GPSLatitude") != null) {
								newexif.setAttribute("GPSLatitude",
										oldexif.getAttribute("GPSLatitude"));
							}
							if (oldexif.getAttribute("GPSLatitudeRef") != null) {
								newexif.setAttribute("GPSLatitudeRef",
										oldexif.getAttribute("GPSLatitudeRef"));
							}
							if (oldexif.getAttribute("GPSLongitude") != null) {
								newexif.setAttribute("GPSLongitude",
										oldexif.getAttribute("GPSLongitude"));
							}
							if (oldexif.getAttribute("GPSLatitudeRef") != null) {
								newexif.setAttribute("GPSLongitudeRef",
										oldexif.getAttribute("GPSLongitudeRef"));
							}
							newexif.setAttribute("ImageLength",
									"" + height);
							newexif.setAttribute("ImageWidth",
									"" + width);

							if (oldexif.getAttribute("Make") != null) {
								newexif.setAttribute("Make",
										oldexif.getAttribute("Make"));
							}
							if (oldexif.getAttribute("Model") != null) {
								newexif.setAttribute("Model",
										oldexif.getAttribute("Model"));
							}
							if (oldexif.getAttribute("Orientation") != null) {
								newexif.setAttribute("Orientation",
										oldexif.getAttribute("Orientation"));
							}
							if (oldexif.getAttribute("WhiteBalance") != null) {
								newexif.setAttribute("WhiteBalance",
										oldexif.getAttribute("WhiteBalance"));
							}

							newexif.saveAttributes();
							
						} catch (Exception e) {
							e.printStackTrace();
							//It's okay, if we can't manage to copy exif information
						}
					}
					
					if(isCancelled())
						return (null);
					
					HttpURLConnection conn = null;
					DataOutputStream dos = null;
					boolean trustEveryone = true;
					URL url;
					
					try {
						url = new URL(
								  getString(R.string.WebServiceURL) + "/cfc/iphonewebservice.cfc?method=uploadPhoto&returnformat=json");
						
						File file = new File(uploadPath);
						InputStream fileInputStream = new FileInputStream(file);
	
						int bytesRead, bytesAvailable, bufferSize;
						long totalBytes;
						byte[] buffer;
						final int maxBufferSize = 8096;
						
						// Open a HTTP connection to the URL based on protocol
						if (url.getProtocol().toLowerCase().equals("https")) {
							if (!trustEveryone) {
								conn = (HttpsURLConnection) url.openConnection();
							}
							// Use our HTTPS connection that blindly trusts
							// everyone.
							// This should only be used in debug environments
							else {
								// Setup the HTTPS connection class to trust
								// everyone
								trustAllHosts();
								HttpsURLConnection https = (HttpsURLConnection) url
										.openConnection();
								// Save the current hostnameVerifier
								defaultHostnameVerifier = https
										.getHostnameVerifier();
								// Setup the connection not to verify hostnames
								https.setHostnameVerifier(DO_NOT_VERIFY);
								conn = https;
							}
						} else {
							conn = (HttpURLConnection) url.openConnection();
						}
						conn.setDoInput(true);
						conn.setDoOutput(true);
						conn.setUseCaches(false);
						conn.setRequestMethod("POST");
						conn.setRequestProperty("Connection", "Keep-Alive");
						conn.setRequestProperty("Content-Type",
								"multipart/form-data;boundary=" + BOUNDRY);
	
						dos = new DataOutputStream(conn.getOutputStream());
	
						// Add photo id
						dos.writeBytes(LINE_START + BOUNDRY + LINE_END);
						dos.writeBytes("Content-Disposition: form-data; name=\"photoId\";");
						dos.writeBytes(LINE_END + LINE_END);
						dos.writeBytes(PhotoId);
						dos.writeBytes(LINE_END);
	
						// Add caption. Passed only if it is not null
						if (item.caption != null){
							dos.writeBytes(LINE_START + BOUNDRY + LINE_END);
							dos.writeBytes("Content-Disposition: form-data; name=\"photoCaption\";");
							dos.writeBytes(LINE_END + LINE_END);
							dos.writeBytes(item.caption);
							dos.writeBytes(LINE_END);
						}
	
						String fName = uploadPath.substring(uploadPath
								.lastIndexOf("/") + 1);
	
						dos.writeBytes(LINE_START + BOUNDRY + LINE_END);
						dos.writeBytes("Content-Disposition: form-data; name=\"uploaded\";"
								+ " filename=\"" + fName + "\"" + LINE_END);
						dos.writeBytes("Content-Type: image/JPEG" + LINE_END);
						dos.writeBytes(LINE_END);
	
						// create a buffer of maximum size
						bytesAvailable = fileInputStream.available();
						bufferSize = Math.min(bytesAvailable, maxBufferSize);
						buffer = new byte[bufferSize];
	
						// read file and write it into form...
						bytesRead = fileInputStream.read(buffer, 0, bufferSize);
						totalBytes = 0;
	
						while (bytesRead > 0) {
							totalBytes += bytesRead;
							dos.write(buffer, 0, bufferSize);
							bytesAvailable = fileInputStream.available();
							bufferSize = Math.min(bytesAvailable, maxBufferSize);
							bytesRead = fileInputStream.read(buffer, 0, bufferSize);
						}
	
						// send multipart form data necesssary after file data...
						dos.writeBytes(LINE_END);
						dos.writeBytes(LINE_START + BOUNDRY + LINE_START + LINE_END);
	
						// close streams
						fileInputStream.close();
						dos.flush();
						dos.close();
						if (uploadPath != item.path)
							file.delete();
					} catch(Exception e){
						e.printStackTrace();
						//STOP the work as there is a connection issue, we assume that file isn't uploaded yet as we only dealing with URLConnection
						publishProgress(SERVER_STATUS_DEFAULT);
						return (null);
					}
					
					//processing response
					try{
						switch (conn.getResponseCode()) {
						case 200:
							DataInputStream inStream;
							try {
								inStream = new DataInputStream(
										conn.getInputStream());
								StringBuffer responseString = new StringBuffer("");
								String line;
								while ((line = inStream.readLine()) != null) {
									responseString.append(line);
								}
								JSONObject JResponse = new JSONObject(
										responseString.toString());
								int success = JResponse.getInt("SUCCESS");
								// String message = JResponse.getString("MESSAGE");
								if (success == 1) {
									item.uploaded = 1;
									publishProgress(SERVER_STATUS_UPLOADED);
								} else {
									item.uploaded = -1;
								}
								inStream.close();
							} catch (Exception e) {
								e.printStackTrace();
								item.uploaded = -1;
							}
							break;
						case 500:
						default:
							item.uploaded = -1;
						}
						// Revert back to the proper verifier and socket factories
						if (trustEveryone
								&& url!= null && url.getProtocol().toLowerCase().equals("https")) {
							((HttpsURLConnection) conn)
									.setHostnameVerifier(defaultHostnameVerifier);
							HttpsURLConnection
									.setDefaultSSLSocketFactory(defaultSSLSocketFactory);
						}
					} catch (Exception e) {
						e.printStackTrace();
						//failed on server side, continue with rest of the items
						publishProgress(SERVER_STATUS_FAILED);
					}
					uploadCounter++;
					publishProgress(SERVER_STATUS_UPLOADED);
				}
			}
			return (null);
		}
		
		@Override 
		protected void onCancelled() {
			publishProgress(CANCELED);
		}
		
		@Override
		protected void onProgressUpdate(Integer... statusCode) {
			switch (statusCode[0]) {
			case CANCELED:
				removeDialog(PROGRESSDIALOG_ID);
				Toast.makeText(getApplicationContext(),
						getString(R.string.CanceledMessage),
						Toast.LENGTH_SHORT).show();
				queueAdapter.notifyDataSetChanged();
				break;
			case OTHER_INTERNAL_ERROR:
				removeDialog(PROGRESSDIALOG_ID);
				Toast.makeText(getApplicationContext(),
						getString(R.string.internal_exception_message),
						Toast.LENGTH_LONG).show();
				break;
			case SECURITY_ERROR:
				removeDialog(PROGRESSDIALOG_ID);
				Toast.makeText(getApplicationContext(),
						getString(R.string.security_exception_message),
						Toast.LENGTH_LONG).show();
				break;
			case SERVER_STATUS_UPLOADED:
				if(!uploadFlag)
					uploadFlag = true;
			default:
				progressDialog.setProgress(uploadCounter);
				queueAdapter.notifyDataSetChanged();
			}
		}

		@Override
		protected void onPostExecute(String sResponse) {
			try {
				removeDialog(PROGRESSDIALOG_ID);
				
				AlertDialog.Builder builder = new AlertDialog.Builder(
						UploadQueue.this);

				if (queueAdapter.getUploadCount() == 0) {
					String message = "";
					if (uploadCounter > 1)
						message = message + uploadCounter + " photos uploaded.";
					else if(uploadCounter == 1)
						message = message + uploadCounter + " photo uploaded.";
					
					builder.setMessage(message).setCancelable(false)
							.setPositiveButton("Ok",
									new DialogInterface.OnClickListener() {
										public void onClick(
												DialogInterface dialog, int id) {
											Intent intent = new Intent();
											if (uploadFlag)
												setResult(RESULT_OK, intent);
											else
												setResult(RESULT_CANCELED, intent);
											UploadQueue.this.finish();
										}
									});
					final AlertDialog alert = builder.create();
					
					//Extra screen wake up notification
					WindowManager.LayoutParams winParams =  alert.getWindow().getAttributes();
				    winParams.flags |= (WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
					alert.getWindow().setAttributes(winParams);
					
					alert.show();
					
					//Extra vibrate notification
					((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(1000);

				} else {
					builder.setMessage(
							"Some photos are not uploaded, Please try again.")
							.setCancelable(false)
							.setPositiveButton("Ok",
									new DialogInterface.OnClickListener() {
										public void onClick(
												DialogInterface dialog, int id) {
											dialog.cancel();
										}
									});
					final AlertDialog alert = builder.create();
					
					//Extra screen wake up notification
					WindowManager.LayoutParams winParams =  alert.getWindow().getAttributes();
				    winParams.flags |= (WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
					alert.getWindow().setAttributes(winParams);
					
					alert.show();
					
					//Extra vibrate notification
					((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(1000);
				}
				
			} catch (Exception e) {
				Toast.makeText(getApplicationContext(), getString(R.string.exception_message),
						Toast.LENGTH_LONG).show();
				e.printStackTrace();
			}
		}
	}

	public Bitmap decodeFile(String path) {
		// Decode image size
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(path, o);

		// The new size we want to scale to
		final int REQUIRED_SIZE = 1024;

		// Find the correct scale value. It should be the power of 2.
		int width_tmp = o.outWidth, height_tmp = o.outHeight;
		int scale = 1;
		while (true) {
			if (width_tmp < REQUIRED_SIZE && height_tmp < REQUIRED_SIZE)
				break;
			width_tmp /= 2;
			height_tmp /= 2;
			scale *= 2;
		}

		// Decode with inSampleSize
		BitmapFactory.Options o2 = new BitmapFactory.Options();
		o2.inSampleSize = scale;
		return BitmapFactory.decodeFile(path, o2);
	}

	private void trustAllHosts() {
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new java.security.cert.X509Certificate[] {};
			}

			public void checkClientTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}
		} };

		// Install the all-trusting trust manager
		try {
			// Backup the current SSL socket factory
			defaultSSLSocketFactory = HttpsURLConnection
					.getDefaultSSLSocketFactory();
			// Install our all trusting manager
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection
					.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// always verify the host - don't check for certificate
	final static HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	};
	
	@Override
	protected void onDestroy (){
		removeDialog(PROGRESSDIALOG_ID);
		if (uploadTask != null && uploadTask.getStatus() != Status.FINISHED)
			uploadTask.cancel(true);
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			if (uploadFlag)
				setResult(RESULT_OK, getIntent());
			else
				setResult(RESULT_CANCELED, getIntent());
			UploadQueue.this.finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}
