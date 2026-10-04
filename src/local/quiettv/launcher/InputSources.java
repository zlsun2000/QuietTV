package local.quiettv.launcher;

import android.content.Context;
import java.util.*;
import java.lang.reflect.*;
import java.io.*;

/** Reads TCL input metadata only; switching is delegated to the exported TV activity. */
final class InputSources {
    static final class Port {
        int id,number,state=-1; String name,device="",enumName; Object source;
    }
    private final Context context;
    private Class<?> sourceClass;
    private Object common,properties;
    private final StringBuilder diagnostics=new StringBuilder();
    InputSources(Context c){context=c.getApplicationContext();}
    private Object call(Object target,String method,Class<?>[] types,Object...args) throws Exception {
        Method m=target.getClass().getMethod(method,types);m.setAccessible(true);return m.invoke(target,args);
    }
    private Object manager(String name) throws Exception {
        Class<?> cls=Class.forName(name);return cls.getMethod("getInstance",Context.class).invoke(null,context);
    }
    ArrayList<Port> scan(){
        ArrayList<Port> result=new ArrayList<>();diagnostics.setLength(0);
        try{
            sourceClass=Class.forName("com.tcl.tvmanager.vo.EnTCLInputSource");
            if(common==null)common=manager("com.tcl.tvmanager.TTvCommonManager");
            if(properties==null)properties=call(manager("com.tcl.tvmanager.TvManager"),"getProperty",new Class<?>[0]);
            Object[] constants=sourceClass.getEnumConstants();HashMap<String,Object> byName=new HashMap<>();
            for(Object o:constants){Enum<?> e=(Enum<?>)o;byName.put(e.name(),o);diagnostics.append(e.name()).append('=').append(e.ordinal()).append('\n');}
            String configured=property("ro.sita.cfg.model.Apps.SOURCE_SUPPORT");
            String legacy=property("ro.sita.model.SourceManager.SourceName");
            diagnostics.append("SOURCE_SUPPORT=").append(configured).append("\nSourceName=").append(legacy).append('\n');
            LinkedHashSet<String> allowed=new LinkedHashSet<>();
            String config=!configured.isEmpty()?configured:legacy;
            java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?i)(HDMI\\s*[1-4]|AV\\s*[1-2]|DTV|ATV|\\bTV\\b)").matcher(config);
            while(match.find())allowed.add(match.group(1).replace(" ","").toUpperCase(Locale.ROOT));
            // Verified 50F295C-JN ports; never invent an extra HDMI port if metadata is absent.
            if(allowed.isEmpty()) {allowed.add("DTV");allowed.add("HDMI1");allowed.add("HDMI2");allowed.add("AV1");diagnostics.append("Using verified 50F295C-JN source list\n");}
            for(String name:allowed){
                String enumName="EN_TCL_"+(name.equals("TV")?"DTV":name);
                Object source=byName.get(enumName);if(source==null)continue;
                Port p=new Port();p.source=source;p.enumName=enumName;p.id=((Enum<?>)source).ordinal();p.name=name.equals("DTV")||name.equals("TV")?"TV":name.equals("ATV")?"模拟 TV":name.replace("HDMI","HDMI ").replace("AV","AV ");
                if(name.startsWith("HDMI"))p.number=Integer.parseInt(name.substring(4));
                try{Object state=call(common,"isInserted",new Class<?>[]{sourceClass},source);p.state=Boolean.TRUE.equals(state)?1:0;}
                catch(Exception e){diagnostics.append("status ").append(name).append(": ").append(error(e)).append('\n');}
                result.add(p);
            }
            readDeviceNames(result);
            for(Port p:result)diagnostics.append(p.name).append(" id=").append(p.id).append(" state=").append(p.state).append(" device=").append(p.device).append('\n');
        }catch(Throwable e){diagnostics.append("Initialization: ").append(error(e)).append('\n');}
        writeDiagnostics();return result;
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
