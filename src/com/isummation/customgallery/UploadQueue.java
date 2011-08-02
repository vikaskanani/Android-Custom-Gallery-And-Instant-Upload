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
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
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

public class UploadQueue extends Activity {
	private QueueAdapter queueAdapter;
	private ListView UploadList;

	private static final String LINE_START = "--";
	private static final String LINE_END = "\r\n";
	private static final String BOUNDRY = "*****";

	private SSLSocketFactory defaultSSLSocketFactory = null;
	private HostnameVerifier defaultHostnameVerifier = null;

	static final int PROGRESS_DIALOG = 0;
	private ProgressDialog progressDialog;
	ProgressThread progressThread;
	
	private boolean uploadFlag;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.uploadqueue);
		
		queueAdapter = new QueueAdapter();
		String ids = getIntent().getStringExtra("Ids");
		queueAdapter.initialize(ids);
		UploadList = (ListView) findViewById(R.id.UploadList);
		UploadList.setItemsCanFocus(true);
		UploadList.setAdapter(queueAdapter);
		
		Button startUploadBtn = (Button) findViewById(R.id.StartUploadBtn);
		startUploadBtn.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				showDialog(PROGRESS_DIALOG);
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

	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case PROGRESS_DIALOG:
			removeDialog(PROGRESS_DIALOG);
			progressDialog = new ProgressDialog(UploadQueue.this);
			progressDialog.setCancelable(false);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMessage("Uploading...");
			return progressDialog;
		default:
			return null;
		}
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case PROGRESS_DIALOG:
			progressDialog.setProgress(0);
			progressThread = new ProgressThread(handler);
			progressThread.start();
		}
	}

	final Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			int total = msg.arg1;
			int percent = total * 100 / queueAdapter.queueItems.size();
			progressDialog.setProgress(percent);
			if (total >= msg.arg2) {
				// dismissDialog(PROGRESS_DIALOG);
				removeDialog(PROGRESS_DIALOG);

				AlertDialog.Builder builder = new AlertDialog.Builder(
						UploadQueue.this);

				if (queueAdapter.getUploadCount() == 0) {

					builder.setMessage("Files uploaded successfully.")
							.setCancelable(false)
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
							"Some files are not uploaded, pressing upload button will again try to upload files which are not uploaded.")
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
				queueAdapter.notifyDataSetChanged();
			}
		}
	};

	/** Nested class that performs progress calculations (counting) */
	private class ProgressThread extends Thread {
		Handler mHandler;
		int total;

		ProgressThread(Handler h) {
			mHandler = h;
			total = 1;
		}

		public void run() {
			final int len = queueAdapter.getCount();
			final int uploadcount = queueAdapter.getUploadCount();
			String finalUploadPath;
			for (int i = 0; i < len; i++) {
				QueueItem queueItem = new QueueItem();
				queueItem = queueAdapter.getItem(i);
				if (queueItem.uploaded == 1) {
					// Update progress bar
					Message msg = mHandler.obtainMessage();
					msg.arg1 = total;
					msg.arg2 = uploadcount;
					mHandler.sendMessage(msg);
					total++;
					continue; // only process rest of the status
				}

				finalUploadPath = queueItem.path;

				BitmapFactory.Options o = new BitmapFactory.Options();
				o.inJustDecodeBounds = true;
				BitmapFactory.decodeFile(queueItem.path, o);
				int owidth = o.outWidth;
				int oheight = o.outHeight;
				System.gc();
				Bitmap bitmap = decodeFile(queueItem.path);
				int height = bitmap.getHeight();
				int width = bitmap.getWidth();

				// Here we are trying to resize the image before we upload it,
				// because slow internet connection can take too much time, as
				// eventually we also resize image on server side, so why
				// shouldn't be done here?
				boolean isResized = false;
				try {
					if (owidth > width || oheight > height) {
						isResized = true;
						String oName = queueItem.path.substring(queueItem.path
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
						String newPath = myDirectory + "/tmp_" + oName;
						FileOutputStream fos = new FileOutputStream(file);
						bitmap.compress(CompressFormat.JPEG, 100, fos);
						bitmap.recycle();
						fos.flush();
						fos.close();

						// copy paste exif information from original file to new
						// file
						ExifInterface oldexif = new ExifInterface(
								queueItem.path);
						ExifInterface newexif = new ExifInterface(newPath);

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

						finalUploadPath = newPath;
					}

					File file = new File(finalUploadPath);
					InputStream fileInputStream = new FileInputStream(file);

					HttpURLConnection conn = null;
					DataOutputStream dos = null;
					boolean trustEveryone = true;

					int bytesRead, bytesAvailable, bufferSize;
					long totalBytes;
					byte[] buffer;
					int maxBufferSize = 8096;
					// open a URL connection to the server
					URL url = new URL("http://10.0.2.2/cfc/iphonewebservice.cfc?method=uploadPhoto&returnformat=json");

					// Open a HTTP connection to the URL based on protocol
					if (url.getProtocol().toLowerCase().equals("https")) {
						// Using standard HTTPS connection. Will not allow self
						// signed
						// certificate
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
					dos.writeBytes(getIntent().getStringExtra("photoId"));
					dos.writeBytes(LINE_END);

					// Add caption. Passed only if it is not null
					if (queueItem.caption != null){
						dos.writeBytes(LINE_START + BOUNDRY + LINE_END);
						dos.writeBytes("Content-Disposition: form-data; name=\"photoCaption\";");
						dos.writeBytes(LINE_END + LINE_END);
						dos.writeBytes(queueItem.caption);
						dos.writeBytes(LINE_END);
					}

					String fName = finalUploadPath.substring(finalUploadPath
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

					// ------------------ read the SERVER RESPONSE
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
								queueAdapter.queueItems.get(i).uploaded = 1;
								uploadFlag = true;
							} else {
								queueAdapter.queueItems.get(i).uploaded = -1;
							}
							inStream.close();
						} catch (Exception e) {
							e.printStackTrace();
							queueAdapter.queueItems.get(i).uploaded = -1;
						}
						break;
					case 500:
					default:
						queueAdapter.queueItems.get(i).uploaded = -1;
					}
					// Revert back to the proper verifier and socket factories
					if (trustEveryone
							&& url.getProtocol().toLowerCase().equals("https")) {
						((HttpsURLConnection) conn)
								.setHostnameVerifier(defaultHostnameVerifier);
						HttpsURLConnection
								.setDefaultSSLSocketFactory(defaultSSLSocketFactory);
					}

					// Clean up directory if image resized
					if (isResized) {
						File tempFile = new File(finalUploadPath);
						tempFile.delete();
					}
				} catch (Exception e) {
					e.printStackTrace();
					queueAdapter.queueItems.get(i).uploaded = -1;
				} finally {
					// Update progress bar
					Message msg = mHandler.obtainMessage();
					msg.arg1 = total;
					msg.arg2 = uploadcount;
					mHandler.sendMessage(msg);
					total++;
				}
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

	/**
	 * This function will install a trust manager that will blindly trust all
	 * SSL certificates. The reason this code is being added is to enable
	 * developers to do development using self signed SSL certificates on their
	 * web server.
	 * 
	 * The standard HttpsURLConnection class will throw an exception on self
	 * signed certificates if this code is not run.
	 */
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
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			Intent intent = new Intent();
			if (uploadFlag)
				setResult(RESULT_OK, intent);
			else
				setResult(RESULT_CANCELED, intent);
			finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}
