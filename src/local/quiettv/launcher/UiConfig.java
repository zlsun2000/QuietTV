package local.quiettv.launcher;

import java.util.Properties;
import java.io.InputStream;

/** Safe customization knobs; invalid settings fall back to the tested layout. */
final class UiConfig {
    final int columns,clockSize,brandSize,weatherWidth;
    final float favoriteRatio;
    UiConfig(Properties p){columns=integer(p,"grid.columns",5,4,6);clockSize=integer(p,"clock.size",54,40,60);
        brandSize=integer(p,"brand.size",18,16,22);weatherWidth=integer(p,"weather.width",205,160,250);
        favoriteRatio=decimal(p,"favorites.ratio",.60f,.55f,.65f);}
    static UiConfig load(android.content.Context c){Properties p=new Properties();try(InputStream in=c.getResources().openRawResource(R.raw.ui_defaults)){p.load(in);}catch(Exception ignored){}return new UiConfig(p);}
    private static int integer(Properties p,String key,int fallback,int min,int max){try{int n=Integer.parseInt(p.getProperty(key));return n>=min&&n<=max?n:fallback;}catch(Exception e){return fallback;}}
    private static float decimal(Properties p,String key,float fallback,float min,float max){try{float n=Float.parseFloat(p.getProperty(key));return n>=min&&n<=max?n:fallback;}catch(Exception e){return fallback;}}
}
