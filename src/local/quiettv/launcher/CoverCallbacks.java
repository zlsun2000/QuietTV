package local.quiettv.launcher;

import java.util.*;

/** Coalesces a bounded download while retaining each current view's repaint callback. */
final class CoverCallbacks {
    private final HashMap<String,LinkedHashSet<Runnable>> waiting=new HashMap<>();
    boolean begin(String key,Runnable ready){LinkedHashSet<Runnable> callbacks=waiting.get(key);boolean fresh=callbacks==null;if(fresh){if(waiting.size()>=16)return false;callbacks=new LinkedHashSet<>();waiting.put(key,callbacks);}if(callbacks.size()<32)callbacks.add(ready);return fresh;}
    ArrayList<Runnable> take(String key){LinkedHashSet<Runnable> callbacks=waiting.remove(key);return callbacks==null?new ArrayList<Runnable>():new ArrayList<>(callbacks);}
    void clear(){waiting.clear();}
}
