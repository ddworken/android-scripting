package com.google.ase.jsonrpc;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.ase.AseLog;

public abstract class RpcReceiverManager {

  private final Map<Class<? extends RpcReceiver>, RpcReceiver> mReceivers;

  public RpcReceiverManager(Collection<Class<? extends RpcReceiver>> classList) {
    mReceivers = new HashMap<Class<? extends RpcReceiver>, RpcReceiver>();
    for (Class<? extends RpcReceiver> receiverClass : classList) {
      mReceivers.put(receiverClass, null);
    }
  }

  public Collection<Class<? extends RpcReceiver>> getRpcReceiverClasses() {
    return mReceivers.keySet();
  }

  public RpcReceiver getReceiver(Class<? extends RpcReceiver> clazz) {
    RpcReceiver object = mReceivers.get(clazz);
    if (object != null) {
      return object;
    }

    Constructor<? extends RpcReceiver> constructor;
    try {
      constructor = clazz.getConstructor(getClass());
      object = constructor.newInstance(this);
      mReceivers.put(clazz, object);
    } catch (Exception e) {
      AseLog.e(e);
    }

    return object;
  }
}
