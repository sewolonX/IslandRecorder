package com.android.internal.app;

import android.os.IBinder;
import android.os.IInterface;

public interface IAppOpsService extends IInterface {
    int checkOperation(int code, int uid, String packageName);

    void setMode(int code, int uid, String packageName, int mode);

    abstract class Stub {
        public static IAppOpsService asInterface(IBinder binder) {
            throw new RuntimeException("Stub");
        }
    }
}
