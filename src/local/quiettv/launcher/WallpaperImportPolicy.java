package local.quiettv.launcher;

import java.util.Locale;

final class WallpaperImportPolicy {
    static String extension(String mime,String name){
        if(mime!=null){switch(mime.toLowerCase(Locale.ROOT)){
            case "image/jpeg":return "jpg";case "image/png":return "png";case "image/webp":return "webp";case "image/bmp":return "bmp";
            case "video/mp4":return "mp4";case "video/x-m4v":return "m4v";case "video/webm":return "webm";case "video/quicktime":return "mov";case "video/x-matroska":return "mkv";
        }}
        if(name==null)return "";int dot=name.lastIndexOf('.');String ext=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
        return ext.matches("jpg|jpeg|png|webp|bmp|mp4|m4v|webm|mov|mkv")?ext:"";
    }
    static boolean video(String extension){return extension.matches("mp4|m4v|webm|mov|mkv");}
    static long limit(String extension){return (video(extension)?400L:50L)*1024*1024;}
    private WallpaperImportPolicy(){}
}
