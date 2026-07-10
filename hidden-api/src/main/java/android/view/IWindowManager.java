package android.view;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.List;

public interface IWindowManager extends IInterface {

    void setScreenShareProjectBlackList(List<String> blacklist) throws RemoteException;

    abstract class Stub extends Binder implements IWindowManager {

        public static IWindowManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
