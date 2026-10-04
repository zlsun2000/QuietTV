package local.quiettv.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original, static vector landscape: gradients and paths remain sharp at any buffer size. */
final class LandscapeView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape=new Path();
    private final int theme;
    LandscapeView(Context context,int theme){super(context);this.theme=Math.max(0,Math.min(theme,3));}
    private void ridge(Canvas canvas,float[] points,int color){
        float w=getWidth(),h=getHeight();shape.reset();shape.moveTo(0,h);
        for(int i=0;i<points.length;i+=2)shape.lineTo(points[i]*w,points[i+1]*h);
        shape.lineTo(w,h);shape.close();paint.setColor(color);canvas.drawPath(shape,paint);
    }
    @Override protected void onDraw(Canvas canvas){
        float w=getWidth(),h=getHeight();
        int[][] palette={
            {0xff102c47,0xff6c96a7,0xff496d80,0xff314f67,0xff21394c,0xff102b3c,0xffffd2a6},
            {0xff272544,0xffaa8e9f,0xff777e99,0xff515b7e,0xff343d5c,0xff202942,0xffffd8bf},
            {0xff161d28,0xff637480,0xff475965,0xff344650,0xff263640,0xff172630,0xffcbd5d8},
            {0xff153943,0xff7aaca7,0xff5d8c8c,0xff3c6b70,0xff244d54,0xff14343c,0xffe3d7ac}
        };
        int[] colors=palette[theme];
        paint.setShader(new LinearGradient(0,0,w*.25f,h*.72f,new int[]{colors[0],colors[1],colors[2]},new float[]{0,.6f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        paint.setShader(new RadialGradient(w*.78f,h*.19f,w*.38f,new int[]{0x5ff4d4ab,0x00f4d4ab},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        paint.setColor(colors[6]);paint.setAlpha(145);canvas.drawCircle(w*.80f,h*.19f,h*.044f,paint);paint.setAlpha(255);
        ridge(canvas,new float[]{0,.42f,.07f,.39f,.13f,.43f,.22f,.30f,.26f,.35f,.33f,.28f,.41f,.40f,.47f,.34f,.57f,.39f,.67f,.27f,.72f,.33f,.81f,.25f,.90f,.37f,1,.30f},colors[2]);
        ridge(canvas,new float[]{0,.53f,.09f,.46f,.18f,.50f,.26f,.39f,.32f,.44f,.40f,.53f,.49f,.44f,.58f,.50f,.65f,.41f,.72f,.49f,.80f,.35f,.87f,.43f,.93f,.37f,1,.48f},colors[3]);
        // Angular, softly lit rock faces give the ridges depth without bitmap assets.
        paint.setColor(0x225fe1da);shape.reset();shape.moveTo(w*.80f,h*.35f);shape.lineTo(w*.76f,h*.49f);shape.lineTo(w*.87f,h*.54f);shape.close();canvas.drawPath(shape,paint);
        paint.setColor(0x18ffe3bd);shape.reset();shape.moveTo(w*.26f,h*.39f);shape.lineTo(w*.32f,h*.44f);shape.lineTo(w*.39f,h*.59f);shape.lineTo(w*.29f,h*.53f);shape.close();canvas.drawPath(shape,paint);
        ridge(canvas,new float[]{0,.63f,.11f,.59f,.22f,.65f,.31f,.62f,.42f,.67f,.56f,.60f,.66f,.63f,.78f,.54f,.87f,.61f,1,.56f},colors[4]);
        // Still lake and a near shoreline, with subtle horizon reflections.
        paint.setShader(new LinearGradient(0,h*.61f,0,h,new int[]{colors[3],colors[5]},null,Shader.TileMode.CLAMP));canvas.drawRect(0,h*.67f,w,h,paint);paint.setShader(null);
        paint.setStrokeWidth(h*.001f);for(int i=0;i<18;i++){float y=h*(.69f+i*.012f);paint.setColor(i%3==0?0x105fd7d0:0x08d1e3df);canvas.drawLine(w*(.12f+i%4*.035f),y,w*(.95f-i%5*.04f),y,paint);}
        ridge(canvas,new float[]{0,.71f,.09f,.75f,.18f,.74f,.29f,.84f,.42f,.87f,.56f,.94f,.65f,1,1,1},colors[5]);
        // Readability scrims, strongest behind the cards and at the TV safe margins.
        paint.setShader(new LinearGradient(0,0,0,h,new int[]{0x38050c15,0x12050c15,0x70050c15,0xdd050c15},new float[]{0,.21f,.50f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);
        paint.setShader(new LinearGradient(0,0,w,0,new int[]{0x32040b13,0x00040b13,0x20040b13},new float[]{0,.45f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    }
}
