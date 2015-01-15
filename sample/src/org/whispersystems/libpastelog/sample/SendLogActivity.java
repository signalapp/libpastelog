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

package org.whispersystems.libpastelog.sample;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import org.whispersystems.libpastelog.SubmitLogFragment;

/**
 * rhodey
 */
public class SendLogActivity extends FragmentActivity implements SubmitLogFragment.OnLogSubmittedListener {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.simple_fragment_activity);
    getActionBar().setTitle(R.string.send_debug_log);

    for (int i=0; i<10; i++) {
      Log.i("SendLog", "debug line " + i);
    }

    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
    Fragment            submitLogFragment   = SubmitLogFragment.newInstance(getString(R.string.support_email_address),
                                                                            getString(R.string.support_email_subject));

    fragmentTransaction.replace(R.id.fragment_view, submitLogFragment);
    fragmentTransaction.commit();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        break;
    }

    return false;
  }

  @Override
  public void onSuccess() {
    Toast.makeText(getBaseContext(), R.string.succeeded, Toast.LENGTH_LONG).show();
    finish();
  }

  @Override
  public void onFailure() {
    Toast.makeText(getBaseContext(), R.string.failed, Toast.LENGTH_LONG).show();
    finish();
  }

  @Override
  public void onCancel() {
    Toast.makeText(getBaseContext(), R.string.cancelled, Toast.LENGTH_LONG).show();
    finish();
  }
}
