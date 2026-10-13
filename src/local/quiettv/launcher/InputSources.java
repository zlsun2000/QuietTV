package local.quiettv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.media.tv.*;
import android.os.Build;
import android.os.Handler;
import java.util.*;
import java.lang.reflect.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reads TCL input metadata only; switching is delegated to the exported TV activity. */
final class InputSources {
    interface ScanCallback {void complete(ArrayList<Port> ports);}
    static final class Port {
        int id,number,state=-1; String name,device="",enumName,inputId=""; Object source;Intent intent;
        String key(){return "input:"+(inputId.isEmpty()?String.valueOf(id):inputId);}
    }
    static DeviceCapability capability(int visiblePorts){return visiblePorts>0?DeviceCapability.available("tv_inputs",visiblePorts+" 个可启动入口"):DeviceCapability.optional("tv_inputs","系统未报告可启动输入源");}
    private final Context context;
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanPending=new AtomicBoolean();
    private final AtomicBoolean scanAgain=new AtomicBoolean();
    private TvInputManager tvInputManager;
    private TvInputManager.TvInputCallback tvInputCallback;
    private Class<?> sourceClass;
    private Object common,properties;
    private final StringBuilder diagnostics=new StringBuilder();
    InputSources(Context c){context=c.getApplicationContext();}
    void observe(ScanCallback callback){
        try{tvInputManager=(TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);if(tvInputManager==null)return;
            tvInputCallback=new TvInputManager.TvInputCallback(){
                @Override public void onInputAdded(String inputId){scanAsync(callback);}
                @Override public void onInputRemoved(String inputId){scanAsync(callback);}
                @Override public void onInputUpdated(String inputId){scanAsync(callback);}
                @Override public void onInputStateChanged(String inputId,int state){scanAsync(callback);}
            };tvInputManager.registerCallback(tvInputCallback,main);
        }catch(Exception ignored){}
    }
    void scanAsync(final ScanCallback callback){if(!scanPending.compareAndSet(false,true)){scanAgain.set(true);return;}try{worker.execute(()->{final ArrayList<Port> result=scan();scanPending.set(false);main.post(()->callback.complete(result));if(scanAgain.getAndSet(false))scanAsync(callback);});}catch(Exception ignored){scanPending.set(false);scanAgain.set(false);}}
    void dispose(){try{if(tvInputManager!=null&&tvInputCallback!=null)tvInputManager.unregisterCallback(tvInputCallback);}catch(Exception ignored){}worker.shutdownNow();}
    private Object call(Object target,String method,Class<?>[] types,Object...args) throws Exception {
        Method m=target.getClass().getMethod(method,types);m.setAccessible(true);return m.invoke(target,args);
    }
    private Object manager(String name) throws Exception {
        Class<?> cls=Class.forName(name);return cls.getMethod("getInstance",Context.class).invoke(null,context);
    }
    synchronized ArrayList<Port> scan(){
        ArrayList<Port> result=new ArrayList<>();diagnostics.setLength(0);
        try{
            Intent tv=new Intent().setComponent(new ComponentName("com.tcl.tv","com.tcl.tv.TVActivity"));
            if(!SystemEntrypoints.available(context,tv))return standardInputs();
            sourceClass=Class.forName("com.tcl.tvmanager.vo.EnTCLInputSource");
            if(common==null)common=manager("com.tcl.tvmanager.TTvCommonManager");
            if(properties==null)properties=call(manager("com.tcl.tvmanager.TvManager"),"getProperty",new Class<?>[0]);
            Object[] constants=sourceClass.getEnumConstants();HashMap<String,Object> byName=new HashMap<>();
            for(Object o:constants){Enum<?> e=(Enum<?>)o;byName.put(e.name(),o);diagnostics.append(e.name()).append('=').append(e.ordinal()).append('\n');}
            String configured=property("ro.sita.cfg.model.Apps.SOURCE_SUPPORT");
            String legacy=property("ro.sita.model.SourceManager.SourceName");
            diagnostics.append("SOURCE_SUPPORT=").append(configured).append("\nSourceName=").append(legacy).append('\n');
            LinkedHashSet<String> allowed=DevicePolicy.tclPorts(configured,legacy,Build.MODEL);
            for(String name:allowed){
                String enumName="EN_TCL_"+(name.equals("TV")?"DTV":name);
                Object source=byName.get(enumName);if(source==null)continue;
                Port p=new Port();p.source=source;p.enumName=enumName;p.id=((Enum<?>)source).ordinal();p.name=name.equals("DTV")||name.equals("TV")?"TV":name.equals("ATV")?"模拟 TV":name.replace("HDMI","HDMI ").replace("AV","AV ");
                if(name.startsWith("HDMI"))p.number=Integer.parseInt(name.substring(4));
                p.intent=new Intent(tv).putExtra("targetSourceId",p.id).putExtra("referer",context.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try{Object state=call(common,"isInserted",new Class<?>[]{sourceClass},source);p.state=Boolean.TRUE.equals(state)?1:0;}
                catch(Exception e){diagnostics.append("status ").append(name).append(": ").append(error(e)).append('\n');}
                result.add(p);
            }
            readDeviceNames(result);
            for(Port p:result)diagnostics.append(p.name).append(" id=").append(p.id).append(" state=").append(p.state).append(" device=").append(p.device).append('\n');
        }catch(Throwable e){diagnostics.append("Initialization: ").append(error(e)).append('\n');}
        return result.isEmpty()?standardInputs():result;
    }
    private ArrayList<Port> standardInputs(){
        ArrayList<Port> result=new ArrayList<>();
        try{TvInputManager manager=(TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);if(manager==null)return result;
            for(TvInputInfo info:manager.getTvInputList()){
                try{
                // A registered input is usable here only when a TV player can handle its URI.
                if(!info.isPassthroughInput())continue;
                Intent view=new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(info.getId()));
                if(!SystemEntrypoints.available(context,view))continue;
                Port p=new Port();p.inputId=info.getId();p.enumName=info.getType()==TvInputInfo.TYPE_HDMI?"ANDROID_HDMI":"ANDROID_INPUT";
                CharSequence label=info.loadCustomLabel(context);if(label==null||label.length()==0)label=info.loadLabel(context);p.name=label==null?"输入源":label.toString();
                int state=manager.getInputState(info.getId());p.state=state==TvInputManager.INPUT_STATE_CONNECTED?1:state==TvInputManager.INPUT_STATE_DISCONNECTED?0:-1;
                // Public TvInputInfo does not guarantee a CEC device name or physical port number.
                // Keep them unknown rather than depending on a hidden system API.
                p.intent=view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);result.add(p);
                }catch(Exception e){diagnostics.append("input item: ").append(error(e)).append('\n');}
            }
        }catch(Exception ignored){}return result;
    }
    private String property(String key){
        try{String v=String.valueOf(call(properties,"get",new Class<?>[]{String.class,String.class},key,""));return v.equals("null")?"":v.trim();}
        catch(Exception e){diagnostics.append(key).append(": ").append(error(e)).append('\n');return "";}
    }
    private void readDeviceNames(ArrayList<Port> ports){
        try{
            Object current=call(common,"getCurrentInputSource",new Class<?>[0]);
            Object spd=call(common,"getHDMIDeviceInfo",new Class<?>[0]);
            if(spd!=null&&current instanceof Enum){String description=String.valueOf(spd.getClass().getField("productDescription").get(spd)).trim();
                for(Port p:ports)if(p.state==1&&p.enumName.equals(((Enum<?>)current).name())&&!description.equals("null"))p.device=description;
            }
        }catch(Exception e){diagnostics.append("SPD: ").append(error(e)).append('\n');}
        try{
            Object hdmi=context.getSystemService("hdmi_control");if(hdmi==null)return;
            Object client=call(hdmi,"getTvClient",new Class<?>[0]);if(client==null)return;
            Object devices=call(client,"getDeviceList",new Class<?>[0]);if(!(devices instanceof List))return;
            for(Object device:(List<?>)devices){int n=((Number)call(device,"getPortId",new Class<?>[0])).intValue();String label=String.valueOf(call(device,"getDisplayName",new Class<?>[0])).trim();
                if(label.isEmpty()||label.equals("null")||label.startsWith("Playback_")||label.startsWith("HDMI"))continue;
                for(Port p:ports)if(p.number==n&&p.state==1)p.device=label;
            }
        }catch(Exception e){diagnostics.append("CEC: ").append(error(e)).append('\n');}
    }
    private String error(Throwable e){if(e instanceof InvocationTargetException&&e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName()+": "+e.getMessage();}
    private void writeDiagnostics(){
        try{File dir=context.getExternalFilesDir(null);if(dir!=null){Writer out=new OutputStreamWriter(new FileOutputStream(new File(dir,"input-diagnostics.txt")),"UTF-8");out.write(diagnostics.toString());out.close();}}catch(Exception ignored){}
    }
}
