// Copyright (c) 2009, Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.tawacentral.roger.secrets;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This activity handles logging into the application.  It prompts the user for
 * his password, or guides him through the process of creating one.  This
 * activity also permits the user to reset his password, which essentially
 * means deletes all his secrets.
 *
 * @author rogerta
 */
public class LoginActivity extends Activity {
  /** Dialog Id for resetting password. */
  private static final int DIALOG_RESET_PASSWORD = 1;
  /** Tag for logging purposes. */
  public static final String LOG_TAG = "Secrets";

  /**
   * This is the global list of the user's secrets.  This list is accessed
   * from other parts of the program. */
  private static ArrayList<Secret> secrets = null;

  private boolean isFirstRun;
  private boolean isValidatingPassword;
  private String passwordString;
  private Toast toast;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.login);

    // Setup the behaviour of the password edit view.
    View password = findViewById(R.id.login_password);
    password.setOnKeyListener(new View.OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        // In the Android 1.1 platform, I used to use a click listener, since
        // pressing the Enter key would generate that event.  However, in
        // Android 1.5 (cupcake) that no longer works.  Therefore I trap the
        // Enter key here with a key listener, and try handling the password
        // when the Enter key is released (action up).  Note that in 1.1 though,
        // if I completely ignore the Enter key action down, I get a popup menu
        // with a paste command.  To get around this, I always return true if
        // the Enter key is pressed or released, but I only try to handle the
        // password on release.  This works for both 1.1 and 1.5.  The Enter
        // does not become part of the password.
        if (KeyEvent.KEYCODE_ENTER == keyCode) {
          if (KeyEvent.ACTION_UP == event.getAction())
            handlePasswordClick((TextView) v);
          
          return true;
        }
        
        return false;
      }
    });
    
    Log.d(LOG_TAG, "LoginActivity.onCreate");
  }

  /**
   * Reset the activity's UI when we come back from any other activity on
   * the phone.
   */
  @Override
  protected void onResume() {
    super.onResume();

    // NOTE: don't reset the static secrets member here.  I used to do that,
    // and then discovered that an orientation change, at the wrong moment,
    // would cause all secrets to be lost.  The scenario was as follows:
    //
    //  . the user start secrets, enters his password, and presses enter
    //  . while the secrets are being loaded and decrypted, he changes the
    //    orientation (i.e. opens or closes the keyboard)
    //  . once the secrets are loaded, an intent to start the
    //    SecretsListActivity is queued up with startActivity()
    //  . before switching to the new activity, the system destroys this
    //    activity and then recreates it.  onResume() is called, which used to
    //    set the static secrets member to null
    //  . the SecretsListActivity is finally started, and when it tries to get
    //    the secrets list, its null
    //  . when the user leaves the SecretsListActivity, the list of secrets
    //    is overwritten by an empty list

    passwordString = null;
    isValidatingPassword = false;

    // If there is no existing secrets file, this is a first-run scenario.
    // (Its also possible that the user started the app but never got passed
    // entering his password)  In this case, we will show a special first time
    // message with instructions about entering a password, followed by a
    // validation pass to get him to enter the password again.
    isFirstRun = isFirstRun();
    if (isFirstRun) {
      TextView text = (TextView)findViewById(R.id.login_instructions);
      text.setText(R.string.login_instruction_1);
      text = (TextView)findViewById(R.id.login_second_line);
      text.setText("");
    } else {
      TextView text = (TextView)findViewById(R.id.login_instructions);
      text.setText("");
      text = (TextView)findViewById(R.id.login_second_line);
      text.setText(R.string.login_second_line);
    }

    // Clear the password.  The user always needs to type it again when this
    // activity is started.
    TextView password = (TextView) findViewById(R.id.login_password);
    password.setText("");
    Log.d(LOG_TAG, "LoginActivity.onResume");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.login_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    boolean handled = false;

    switch(item.getItemId()) {
      case R.id.reset_password:
        showDialog(DIALOG_RESET_PASSWORD);
        handled = true;
        break;
      default:
        break;
    }

    return handled;
  }

  @Override
  public Dialog onCreateDialog(int id) {
    Dialog dialog = null;

    switch (id) {
      case DIALOG_RESET_PASSWORD: {
        DialogInterface.OnClickListener listener =
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                if (DialogInterface.BUTTON1 == which) {
                  // If we delete the secrets from disk, make sure to also
                  // clear them from memory too.  This is to handle the case
                  // where the user knows his password and has logged in, but
                  // he returned to the login screen and asked to reset his
                  // password.
                  if (!FileUtils.deleteSecrets(LoginActivity.this))
                    showToast(R.string.error_reset_password, Toast.LENGTH_LONG);
                  else
                    secrets = null;

                  onResume();
                }
              }
            };
        dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.login_menu_reset_password)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.login_menu_reset_password_message)
            .setPositiveButton(R.string.login_reset_password_pos, listener)
            .setNegativeButton(R.string.login_reset_password_neg, null)
            .create();
        }
        break;
      default:
        break;
    }

    return dialog;
  }

  /**
   * Determines if this is the first run of the program.  A first run is
   * detected if there is no existing secrets file.
   */
  private boolean isFirstRun() {
    return !FileUtils.secretsExist(this);
  }

  /**
   * Handle a user click on the password view.
   *
   * @param passwordView The password view holding the entered password.
   */
  private void handlePasswordClick(TextView passwordView) {
    // The program tries to minimize the amount of the time the users password
    // is held in memory.  The password edit field is cleared immediately
    // after getting the value, and the password string is held only as long as
    // required to generated the ciphers.
    String passwordString = passwordView.getText().toString();
    passwordView.setText("");

    if (isFirstRun) {
      TextView instructions = (TextView)findViewById(R.id.login_instructions);

      if (!isValidatingPassword) {
        // This is the first run, and the user has created his password for the
        // first time.  We need to get him to validate it, so show the second
        // set of instructions, remember the password, clear the password field,
        // and wait for him to enter it again.
        instructions.setText(R.string.login_instruction_2);

        this.passwordString = passwordString;
        isValidatingPassword = true;
        return;
      } else {
        // This is the first run, and the user is validating his password.
        // If they are the same, continue to the next activity.  If not,
        // display an error message and go back to creating a brand new
        // password.
        if (!passwordString.equals(this.passwordString)) {
          instructions.setText(R.string.login_instruction_1);
          showToast(R.string.invalid_password, Toast.LENGTH_SHORT);

          this.passwordString = null;
          isValidatingPassword = false;
          return;
        }
      }
    }

    // Lets not save the password in memory anywhere.  Create all the ciphers
    // we will need based on the password and save those.
    SecurityUtils.createCiphers(passwordString);
    passwordString = null;

    if (isFirstRun) {
      secrets = new ArrayList<Secret>();
    } else {
      secrets = FileUtils.loadSecrets(this);
      if (null == secrets) {
        // TODO(rogerta): need better error message here.  There are probably
        // many reasons that we might not be able to open the file.
        showToast(R.string.invalid_password, Toast.LENGTH_LONG);
        return;
      }
    }

    Intent intent = new Intent(LoginActivity.this, SecretsListActivity.class);
    startActivity(intent);
    Log.d(LOG_TAG, "LoginActivity.handlePasswordClick");
  }

  /**
   * Show a toast on the screen with the given message.  If a toast is already
   * being displayed, the message is replaced and timer is restarted.
   *
   * @param message Resource id of tText to display in the toast.
   * @param length Length of time to show toast.
   */
  private void showToast(int message, int length) {
    if (null == toast) {
      toast = Toast.makeText(LoginActivity.this, message, length);
      toast.setGravity(Gravity.CENTER, 0, 0);
    } else {
      toast.setText(message);
    }

    toast.show();
  }

  /** Gets the global list if the user's secrets. */
  public static ArrayList<Secret> getSecrets() {
    return secrets;
  }

  /** Overwrite the current secrets with the given list. */
  public static void restoreSecrets(ArrayList<Secret> secrets) {
    // I don't want to change the actual instance of the global array that
    // holds the secrets, since this array is referred to from other places
    // in the code.  I will simply replace the existing array with the entries
    // from the new one.
    LoginActivity.secrets.clear();
    LoginActivity.secrets.addAll(secrets);
  }
}