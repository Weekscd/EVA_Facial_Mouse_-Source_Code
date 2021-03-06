/*
 * Enable Viacam for Android, a camera based mouse emulator
 *
 * Copyright (C) 2015 Cesar Mauri Loba (CREA Software Systems)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.crea_si.eviacam.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;

import com.crea_si.eviacam.BuildConfig;
import com.crea_si.eviacam.R;
import com.crea_si.input_method_aidl.IClickableIME;

/**
 * Handles the communication with the IME
 */

public class InputMethodAction implements ServiceConnection {
    
    private static final String REMOTE_PACKAGE= "com.crea_si.eviacam.service";
    private static final String REMOTE_ACTION= "com.crea_si.softkeyboard.RemoteBinderService";
    private static final String IME_NAME= REMOTE_PACKAGE + "/com.crea_si.softkeyboard.SoftKeyboard";
    
    // period (in milliseconds) to try to rebind again to the IME
    private static final int BIND_RETRY_PERIOD = 2000;
    
    private final Context mContext;

    // binder (proxy) with the remote input method service
    private IClickableIME mRemoteService;
    
    // time stamp of the last time the thread ran
    private long mLastBindAttemptTimeStamp = 0;

    private final Handler mHandler= new Handler();

    public InputMethodAction(@NonNull Context c) {
        mContext= c;

        // attempt to bind with IME
        keepBindAlive();
    }
    
    public void cleanup() {
        if (mRemoteService == null) return;
        
        mContext.unbindService(this);
        mRemoteService= null;
    }
    
    /**
     * Bind to the remote IME when needed
     * 
     * TODO: support multiple compatible IMEs
     * TODO: provide feedback to the user 
     */
    private void keepBindAlive() {
        if (mRemoteService != null) return;
        
        /*
          no bind available, try to establish it if enough
          time passed since the last attempt
         */
        long tstamp= System.currentTimeMillis();
        
        if (tstamp - mLastBindAttemptTimeStamp < BIND_RETRY_PERIOD) {
            return;
        }

        mLastBindAttemptTimeStamp = tstamp;

        if (BuildConfig.DEBUG) Log.d(EVIACAM.TAG, "Attempt to bind to remote IME");
        Intent intent= new Intent(REMOTE_ACTION);
        intent.setPackage(REMOTE_PACKAGE);
        try {
            if (!mContext.bindService(intent, this, Context.BIND_AUTO_CREATE)) {
                if (BuildConfig.DEBUG) Log.d(EVIACAM.TAG, "Cannot bind remote IME");
            }
        }
        catch(SecurityException e) {
            Log.e(EVIACAM.TAG, "Cannot bind remote IME. Security exception.");
        }
    }
    
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        // This is called when the connection with the service has been
        // established, giving us the object we can use to
        // interact with the service.
        Log.i(EVIACAM.TAG, "remoteIME:onServiceConnected: " + className.toString());
        mRemoteService = IClickableIME.Stub.asInterface(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        // This is called when the connection with the service has been
        // unexpectedly disconnected -- that is, its process crashed.
        Log.i(EVIACAM.TAG, "remoteIME:onServiceDisconnected");
        mContext.unbindService(this);
        mRemoteService = null;
        keepBindAlive();
    }
    
    public boolean click(int x, int y) {
        if (mRemoteService == null) {
            if (BuildConfig.DEBUG) {
                Log.d(EVIACAM.TAG, "InputMethodAction: click: no remote service available");
            }
            return false;
        }

        try {
            return mRemoteService.click(x, y);
        } catch (RemoteException e) {
            return false;
        }
    }

    private boolean IMEPrereq () {
        if (!isEnabledCustomKeyboard(mContext)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    EVIACAM.LongToast(mContext, R.string.service_toast_keyboard_not_enabled_toast);
                }
            });

            return false;
        }

        if (mRemoteService == null) {
            if (BuildConfig.DEBUG) {
                Log.d(EVIACAM.TAG, "InputMethodAction: IMEPrereq: no remote service available");
            }
            keepBindAlive();
            return false;
        }

        return true;
    }

    public void openIME() {
        if (!IMEPrereq()) return;

        try {
            mRemoteService.openIME();
        } catch (RemoteException e) {
            // Nothing to be done
            Log.e(EVIACAM.TAG, "InputMethodAction: exception while trying to open IME");
        }
    }

    public void closeIME() {
        if (mRemoteService == null) {
            if (BuildConfig.DEBUG) {
                if (BuildConfig.DEBUG) {
                    Log.d(EVIACAM.TAG, "InputMethodAction: closeIME: no remote service available");
                }
            }
            keepBindAlive();
            return;
        }

        // Does not check mInputMethodManager.isActive because does not mean IME is open
        try {
            mRemoteService.closeIME();
        } catch (RemoteException e) {
            // Nothing to be done
            Log.i(EVIACAM.TAG, "InputMethodAction: exception while trying to close IME");
        }
    }

    public void toggleIME() {
        if (!IMEPrereq()) return;

        // Does not check mInputMethodManager.isActive because does not mean IME is open
        try {
            mRemoteService.toggleIME();
        } catch (RemoteException e) {
            // Nothing to be done
            Log.e(EVIACAM.TAG, "InputMethodAction: exception while trying to toggle IME");
        }
    }

    /**
     * Check if the custom keyboard is enabled and is the default one
     * @param c context
     * @return true if enabled
     */
    public static boolean isEnabledCustomKeyboard (Context c) {
        String pkgName= Settings.Secure.getString(c.getContentResolver(),
                                                  Settings.Secure.DEFAULT_INPUT_METHOD);
        return pkgName.contentEquals(IME_NAME);
    }
}
