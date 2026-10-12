package local.quiettv.launcher;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Device profile and system-state rules isolated from Android and UI code. Unknown devices get no guessed ports. */
final class DevicePolicy {
    /**
     * Matches one flat component in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
     * Accepts the "pkg/.Class" shorthand and the full "pkg/pkg.Class" form; anything
     * else, including lookalike class names, never reports the service as enabled.
     */
    static boolean accessibilityServiceEnabled(String enabled,String pkg,String cls){
        if(enabled==null||pkg==null||cls==null||pkg.isEmpty()||pkg.indexOf('/')>=0)return false;
        String simple=cls.startsWith(".")?cls.substring(1):cls;
        int dot=simple.lastIndexOf('.');
        if(dot>=0)simple=simple.substring(dot+1);
        if(simple.isEmpty())return false;
        String full=pkg+"."+simple;
        for(String entry:enabled.split(":")){
            String value=entry.trim();int slash=value.indexOf('/');
            if(slash<=0||slash==value.length()-1)continue;
            if(!value.substring(0,slash).equals(pkg))continue;
            String name=value.substring(slash+1).trim();
            if(name.equals("."+simple)||name.equals(full))return true;
        }
        return false;
    }
    static LinkedHashSet<String> tclPorts(String configured,String legacy,String model){
        LinkedHashSet<String> ports=new LinkedHashSet<>();
        String value=configured==null?"":configured.trim();
        if(value.isEmpty())value=legacy==null?"":legacy;
        Matcher matcher=Pattern.compile("(?i)(?<![A-Z0-9])(HDMI\\s*[1-4]|AV\\s*[1-2]|DTV|ATV|TV)(?![A-Z0-9])").matcher(value);
        while(matcher.find())ports.add(matcher.group(1).replaceAll("\\s+","").toUpperCase(Locale.ROOT));
        if(ports.isEmpty()&&"50F295C-JN".equalsIgnoreCase(model)){
            ports.add("DTV");ports.add("HDMI1");ports.add("HDMI2");ports.add("AV1");
        }
        return ports;
    }
    private DevicePolicy(){}
}
