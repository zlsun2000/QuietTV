package local.quiettv.launcher;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Device profile rules isolated from Android and UI code. Unknown devices get no guessed ports. */
final class DevicePolicy {
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
