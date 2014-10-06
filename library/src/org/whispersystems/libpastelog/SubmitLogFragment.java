/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.whispersystems.libpastelog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.JsonElement;
import com.google.thoughtcrimegson.JsonParser;
import com.google.thoughtcrimegson.annotations.SerializedName;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import org.whispersystems.libpastelog.util.ProgressDialogAsyncTask;
import org.whispersystems.libpastelog.util.Scrubber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper {@link Fragment} to preview and submit logcat information to a public pastebin.
 * Activities that contain this fragment must implement the
 * {@link SubmitLogFragment.OnLogSubmittedListener} interface
 * to handle interaction events.
 * Use the {@link SubmitLogFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class SubmitLogFragment extends Fragment {
  private static final String TAG = SubmitLogFragment.class.getSimpleName();

  private EditText logPreview;
  private Button   okButton;
  private Button   cancelButton;
  private String   supportEmailAddress;
  private String   supportEmailSubject;
  private String   hackSavedLogUrl;
  private boolean  emailActivityWasStarted = false;

  private static final String API_ENDPOINT = "https://api.github.com/gists";

  private OnLogSubmittedListener mListener;

  /**
   * Use this factory method to create a new instance of
   * this fragment using the provided parameters.
   *
   * @return A new instance of fragment SubmitLogFragment.
   */
  public static SubmitLogFragment newInstance(String supportEmailAddress,
                                              String supportEmailSubject)
  {
    SubmitLogFragment fragment = new SubmitLogFragment();

    fragment.supportEmailAddress = supportEmailAddress;
    fragment.supportEmailSubject = supportEmailSubject;

    return fragment;
  }

  public SubmitLogFragment() { }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.fragment_submit_log, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initializeResources();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      mListener = (OnLogSubmittedListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString() + " must implement OnFragmentInteractionListener");
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    if (emailActivityWasStarted && mListener != null)
      mListener.onSuccess();
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mListener = null;
  }

  private void initializeResources() {
    logPreview =   (EditText) getView().findViewById(R.id.log_preview);
    okButton =     (Button)   getView().findViewById(R.id.ok);
    cancelButton = (Button)   getView().findViewById(R.id.cancel);

    okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        new SubmitToPastebinAsyncTask(logPreview.getText().toString()).execute();
      }
    });

    cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (mListener != null) mListener.onCancel();
      }
    });
    new PopulateLogcatAsyncTask().execute();
  }

  private static String grabLogcat() {
    try {
      final Process         process        = Runtime.getRuntime().exec("logcat -d");
      final BufferedReader  bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      final StringBuilder   log            = new StringBuilder();
      final String          separator      = System.getProperty("line.separator");

      String line;
      while ((line = bufferedReader.readLine()) != null) {
        log.append(line);
        log.append(separator);
      }
      return log.toString();
    } catch (IOException ioe) {
      Log.w(TAG, "IOException when trying to read logcat.", ioe);
      return null;
    }
  }

  private Intent getIntentForSupportEmail(String logUrl) {
    Intent emailSendIntent = new Intent(Intent.ACTION_SEND);

    emailSendIntent.putExtra(Intent.EXTRA_EMAIL,   new String[] { supportEmailAddress });
    emailSendIntent.putExtra(Intent.EXTRA_SUBJECT, supportEmailSubject);
    emailSendIntent.putExtra(
        Intent.EXTRA_TEXT,
        getString(R.string.log_submit_activity__please_review_this_log_from_my_app, logUrl)
    );
    emailSendIntent.setType("message/rfc822");

    return emailSendIntent;
  }

  private void handleShowChooserForIntent(final Intent intent, String chooserTitle) {
    final AlertDialog.Builder    builder = new AlertDialog.Builder(getActivity());
    final ShareIntentListAdapter adapter = ShareIntentListAdapter.getAdapterForIntent(getActivity(), intent);

    builder.setTitle(chooserTitle)
           .setAdapter(adapter, new DialogInterface.OnClickListener() {

             @Override
             public void onClick(DialogInterface dialog, int which) {
               ActivityInfo info = adapter.getItem(which).activityInfo;
               intent.setClassName(info.packageName, info.name);
               startActivity(intent);

               emailActivityWasStarted = true;
             }

           })
           .setOnCancelListener(new DialogInterface.OnCancelListener() {

             @Override
             public void onCancel(DialogInterface dialogInterface) {
               if (hackSavedLogUrl != null)
                 handleShowSuccessDialog(hackSavedLogUrl);
             }

           })
           .create().show();
  }

  private TextView handleBuildSuccessTextView(final String logUrl) {
    TextView showText = new TextView(getActivity());

    showText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    showText.setPadding(15, 30, 15, 30);
    showText.setText(getString(R.string.log_submit_activity__copy_this_url_and_add_it_to_your_issue, logUrl));
    showText.setAutoLinkMask(Activity.RESULT_OK);
    showText.setMovementMethod(LinkMovementMethod.getInstance());
    showText.setOnLongClickListener(new View.OnLongClickListener() {

      @Override
      public boolean onLongClick(View v) {
        @SuppressWarnings("deprecation")
        ClipboardManager manager =
            (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE);
        manager.setText(logUrl);
        Toast.makeText(getActivity(),
                       R.string.log_submit_activity__copied_to_clipboard,
                       Toast.LENGTH_SHORT).show();
        return true;
      }
    });

    Linkify.addLinks(showText, Linkify.WEB_URLS);
    return showText;
  }

  private void handleShowSuccessDialog(final String logUrl) {
    TextView            showText = handleBuildSuccessTextView(logUrl);
    AlertDialog.Builder builder  = new AlertDialog.Builder(getActivity());

    builder.setTitle(R.string.log_submit_activity__success)
           .setView(showText)
           .setCancelable(false)
           .setNeutralButton(R.string.log_submit_activity__button_got_it, new DialogInterface.OnClickListener() {
             @Override
             public void onClick(DialogInterface dialogInterface, int i) {
               dialogInterface.dismiss();
               if (mListener != null) mListener.onSuccess();
             }
           })
           .setPositiveButton(R.string.log_submit_activity__button_compose_email, new DialogInterface.OnClickListener() {
             @Override
             public void onClick(DialogInterface dialogInterface, int i) {
               handleShowChooserForIntent(
                   getIntentForSupportEmail(logUrl),
                   getString(R.string.log_submit_activity__choose_email_app)
               );
             }
           });

    builder.create().show();
    hackSavedLogUrl = logUrl;
  }

  private class PopulateLogcatAsyncTask extends AsyncTask<Void,Void,String> {

    @Override
    protected String doInBackground(Void... voids) {
      final StringBuilder builder = new StringBuilder(buildDescription(getActivity()));
      builder.append("\n");
      builder.append(new Scrubber().scrub(grabLogcat()));
      return builder.toString();
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      logPreview.setText(R.string.log_submit_activity__loading_logs);
      okButton.setEnabled(false);
    }

    @Override
    protected void onPostExecute(String logcat) {
      super.onPostExecute(logcat);
      if (TextUtils.isEmpty(logcat)) {
        if (mListener != null) mListener.onFailure();
        return;
      }
      logPreview.setText(logcat);
      okButton.setEnabled(true);
    }
  }

  private class SubmitToPastebinAsyncTask extends ProgressDialogAsyncTask<Void,Void,String> {
    private final String         paste;

    public SubmitToPastebinAsyncTask(String paste) {
      super(getActivity(), R.string.log_submit_activity__submitting, R.string.log_submit_activity__uploading_logs);
      this.paste = paste;
    }

    @Override
    protected String doInBackground(Void... voids) {
      final Gson gson = new Gson();
      HttpURLConnection connection = null;
      try {
        Map<String,GistPost.File> files = new HashMap<String, GistPost.File>();
        files.put("cat.log", new GistPost.File(paste));
        final byte[] post = gson.toJson(new GistPost(files)).getBytes();

        HttpPost request = new HttpPost(API_ENDPOINT);

        request.setEntity(new ByteArrayEntity(post));

        HttpClient httpclient = new DefaultHttpClient();
        HttpResponse response = httpclient.execute(request);

        HttpEntity entity = response.getEntity();
        BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(entity);
        InputStream input = bufHttpEntity.getContent();

        input.close();

        JsonElement element = new JsonParser().parse(new InputStreamReader(input));
        if (element.isJsonObject()) {
          JsonElement url = element.getAsJsonObject().get("html_url");
          if (url.isJsonPrimitive()) {
            return url.getAsString();
          }
        }

        throw new IOException("Gist's response was malformed");

      } catch (MalformedURLException e) {
        Log.e("ImageActivity", "bad url", e);
      } catch (IOException e) {
        Log.e("ImageActivity", "io error", e);
      } finally {
        if (connection != null) connection.disconnect();
      }
      return null;
    }

    @Override
    protected void onPostExecute(final String response) {
      super.onPostExecute(response);

      if (response != null)
        handleShowSuccessDialog(response);
      else {
        Log.w(TAG, "Response was null from Pastebin API.");
        if (mListener != null) mListener.onFailure();
      }
    }
  }

  private static String buildDescription(Context context) {
    final PackageManager pm      = context.getPackageManager();
    final StringBuilder  builder = new StringBuilder();

    builder.append("Device : ")
           .append(Build.MANUFACTURER).append(" ")
           .append(Build.MODEL).append(" (")
           .append(Build.PRODUCT).append(")\n");
    builder.append("Android: ").append(Build.DISPLAY).append("\n");
    builder.append("OS Host: ").append(Build.HOST).append("\n");
    builder.append("App    : ");
    try {
      builder.append(pm.getApplicationLabel(pm.getApplicationInfo(context.getPackageName(), 0)))
             .append(" ")
             .append(pm.getPackageInfo(context.getPackageName(), 0).versionName)
             .append("\n");
    } catch (PackageManager.NameNotFoundException nnfe) {
      builder.append("Unknown\n");
    }

    return builder.toString();
  }

  @SuppressWarnings("unused")
  private static class GistPost {
    private static class File {
      private final String content;
      public File(final String content) {
        this.content = content;
      }
    }
    private final String description;
    private final Map<String,File> files;
    @SerializedName("public")
    private final boolean isPublic = false;

    public GistPost(String description, Map<String,File> files) {
      this.description = description;
      this.files       = files;
    }

    public GistPost(Map<String,File> files) {
      this(null, files);
    }
  }

  /**
   * This interface must be implemented by activities that contain this
   * fragment to allow an interaction in this fragment to be communicated
   * to the activity and potentially other fragments contained in that
   * activity.
   * <p>
   * See the Android Training lesson <a href=
   * "http://developer.android.com/training/basics/fragments/communicating.html"
   * >Communicating with Other Fragments</a> for more information.
   */
  public interface OnLogSubmittedListener {
    public void onSuccess();
    public void onFailure();
    public void onCancel();
  }
}
