package com.island.recorder;

interface IPrivilegedService {
    boolean setPackageNetworkingEnabled(int uid, boolean enabled);
    boolean setShowTouches(boolean enabled);
    boolean setProjectMediaAllowed(String packageName, int uid, boolean allowed);
    boolean isProjectMediaAllowed(String packageName, int uid);
}
