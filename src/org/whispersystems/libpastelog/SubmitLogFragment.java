package org.whispersystems.libpastelog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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

  private static final String API_ENDPOINT = "https://api.github.com/gists";

  private OnLogSubmittedListener mListener;

  /**
   * Use this factory method to create a new instance of
   * this fragment using the provided parameters.
   *
   * @return A new instance of fragment SubmitLogFragment.
   */
  public static SubmitLogFragment newInstance() {
    return new SubmitLogFragment();
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
      logPreview.setText(R.string.log_submit_activity__loading_logcat);
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
      super(getActivity(), R.string.log_submit_activity__submitting, R.string.log_submit_activity__posting_logs);
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

      if (response != null) {
        TextView showText = new TextView(getActivity());
        showText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        showText.setPadding(15, 30, 15, 30);
        showText.setText(getString(R.string.log_submit_activity__your_pastebin_url, response));
        showText.setAutoLinkMask(Activity.RESULT_OK);
        showText.setMovementMethod(LinkMovementMethod.getInstance());
        Linkify.addLinks(showText, Linkify.WEB_URLS);
        showText.setOnLongClickListener(new View.OnLongClickListener() {

          @Override
          public boolean onLongClick(View v) {
            // Copy the Text to the clipboard
            @SuppressWarnings("deprecation")
            ClipboardManager manager =
                (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE);
            manager.setText(response);
            Toast.makeText(getActivity().getApplicationContext(), R.string.log_submit_activity__copied_to_clipboard,
                           Toast.LENGTH_SHORT).show();
            return true;
          }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.log_submit_activity__log_submit_success_title)
               .setView(showText)
               .setCancelable(false)
               .setNeutralButton(R.string.log_submit_activity__log_got_it, new DialogInterface.OnClickListener() {
                 @Override
                 public void onClick(DialogInterface dialogInterface, int i) {
                   dialogInterface.dismiss();
                   if (mListener != null) mListener.onSuccess();
                 }
               });
        AlertDialog dialog = builder.create();
        dialog.show();
      } else {
        if (response == null) {
          Log.w(TAG, "Response was null from Pastebin API.");
        } else {
          Log.w(TAG, "Response seemed like an error: " + response);
        }
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
