/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.ase.activity;

import java.net.InetSocketAddress;
import java.util.UUID;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.widget.RemoteViews;

import com.google.ase.AndroidProxy;
import com.google.ase.AseApplication;
import com.google.ase.AseLog;
import com.google.ase.Constants;
import com.google.ase.R;
import com.google.ase.ScriptLauncher;
import com.google.ase.exception.AseException;
import com.google.ase.terminal.Terminal;
import com.google.ase.trigger.Trigger;

/**
 * A service that allows scripts and the RPC server to run in the background.
 * 
 * @author Damon Kohler (damonkohler@gmail.com)
 */
public class AseService extends Service {

  private AndroidProxy mAndroidProxy;
  private ScriptLauncher mLauncher;
  private final StringBuilder mNotificationMessage;
  private Trigger mTrigger;
  private int mNotificationId;

  public AseService() {
    mNotificationMessage = new StringBuilder();
  }

  @Override
  public void onCreate() {
    AseApplication application = (AseApplication) getApplication();
    mNotificationId = application.getNewNotificationId();
  }

  @Override
  public void onStart(Intent intent, int startId) {
    super.onStart(intent, startId);

    // TODO: Right now, only one interpreter execution is supported concurrently.
    // When this changes, we need to support multiple trigger notifications as well.
    if (mTrigger == null) {
      mTrigger = getTrigger(intent);
      notifyTriggerOfStart();
    }

    if (intent.getAction().equals(Constants.ACTION_LAUNCH_SERVER)) {
      launchServer(intent);
    } else if (intent.getAction().equals(Constants.ACTION_LAUNCH_SCRIPT)) {
      launchServer(intent);
      launchInterpreter(intent);
    } else if (intent.getAction().equals(Constants.ACTION_LAUNCH_TERMINAL)) {
      launchServer(intent);
      launchTerminal(intent);
    } else if (intent.getAction().equals(Constants.ACTION_KILL_SERVICE)) {
      stopSelf();
      return;
    }

    ServiceUtils.setForeground(this, mNotificationId, createNotification());
  }

  private Notification createNotification() {
    Notification notification =
        new Notification(R.drawable.ase_logo_48, "ASE is running...", System.currentTimeMillis());
    notification.contentView = new RemoteViews(getPackageName(), R.layout.notification);
    notification.contentView.setTextViewText(R.id.notification_title, "ASE Service");
    notification.contentView.setTextViewText(R.id.notification_message, mNotificationMessage
        .toString());
    Intent notificationIntent = new Intent(this, AseService.class);
    notificationIntent.setAction(Constants.ACTION_KILL_SERVICE);
    notification.contentIntent = PendingIntent.getService(this, 0, notificationIntent, 0);
    notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
    return notification;
  }

  private void launchTerminal(Intent intent) {
    Intent i = new Intent(this, Terminal.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    i.putExtras(intent);
    i.putExtra(Constants.EXTRA_PROXY_PORT, mAndroidProxy.getAddress().getPort());
    startActivity(i);
  }

  private void launchInterpreter(Intent intent) {
    if (mLauncher != null) {
      return;
    }
    InetSocketAddress address = mAndroidProxy.getAddress();
    mLauncher = new ScriptLauncher(intent, address);
    try {
      mLauncher.launch();
    } catch (AseException e) {
      AseLog.e(this, e.getMessage(), e);
      stopSelf();
      return;
    }
    mNotificationMessage.append("\nRunning script service: " + mLauncher.getScriptName());
  }

  private void launchServer(Intent intent) {
    if (mAndroidProxy != null) {
      return;
    }
    mAndroidProxy = new AndroidProxy(this, intent);
    boolean usePublicIp = intent.getBooleanExtra(Constants.EXTRA_USE_EXTERNAL_IP, false);
    if (usePublicIp) {
      mAndroidProxy.startPublic();
    } else {
      mAndroidProxy.startLocal();
    }
    InetSocketAddress address = mAndroidProxy.getAddress();
    mNotificationMessage.append(String.format("Running network service on: %s:%d", address
        .getHostName(), address.getPort()));
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (mLauncher != null) {
      mLauncher.getProcess().kill();
    }
    notifyTriggerOfShutDown();
    if (mAndroidProxy != null) {
      mAndroidProxy.shutdown();
    }
    NotificationManager manager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    manager.cancel(mNotificationId);
  }

  /** Returns the {@link TriggerInfo} for the given intent, or null if none exists. */
  private Trigger getTrigger(Intent intent) {
    final AseApplication application = (AseApplication) getApplication();
    final String triggerIdExtra = intent.getStringExtra(Constants.EXTRA_TRIGGER_ID);
    if (triggerIdExtra == null) {
      return null;
    }

    try {
      final UUID triggerId = UUID.fromString(triggerIdExtra);
      return application.getTriggerRepository().getById(triggerId);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void notifyTriggerOfShutDown() {
    if (mTrigger != null) {
      mTrigger.afterTrigger(this);
    }
  }

  private void notifyTriggerOfStart() {
    if (mTrigger != null) {
      mTrigger.beforeTrigger(this);
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
