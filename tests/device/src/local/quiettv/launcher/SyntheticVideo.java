package local.quiettv.launcher;

import android.media.*;
import java.io.File;
import java.nio.ByteBuffer;

/** Short, silent, solid-colour fixture encoded locally; contains no third-party media. */
final class SyntheticVideo {
    static File create(File file)throws Exception {
        MediaCodec codec=MediaCodec.createEncoderByType("video/avc");MediaMuxer muxer=null;
        try{
            MediaFormat format=MediaFormat.createVideoFormat("video/avc",160,96);format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);format.setInteger(MediaFormat.KEY_BIT_RATE,100000);format.setInteger(MediaFormat.KEY_FRAME_RATE,12);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();muxer=new MediaMuxer(file.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            byte[] frame=new byte[160*96*3/2];java.util.Arrays.fill(frame,0,160*96,(byte)144);java.util.Arrays.fill(frame,160*96,160*96*5/4,(byte)54);java.util.Arrays.fill(frame,160*96*5/4,frame.length,(byte)34);
            int submitted=0,track=-1;boolean eos=false;long deadline=android.os.SystemClock.uptimeMillis()+15000;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(!eos&&android.os.SystemClock.uptimeMillis()<deadline){
                if(submitted<=48){int input=codec.dequeueInputBuffer(10000);if(input>=0){ByteBuffer buffer=codec.getInputBuffer(input);buffer.clear();if(submitted<48)buffer.put(frame);codec.queueInputBuffer(input,0,submitted<48?frame.length:0,submitted*1000000L/12,submitted==48?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);submitted++;}}
                int output=codec.dequeueOutputBuffer(info,10000);
                if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=muxer.addTrack(codec.getOutputFormat());muxer.start();}
                else if(output>=0){if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){if(track<0)throw new IllegalStateException("Encoder has no format");ByteBuffer data=codec.getOutputBuffer(output);data.position(info.offset);data.limit(info.offset+info.size);muxer.writeSampleData(track,data,info);}eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(output,false);}
            }
            if(!eos)throw new IllegalStateException("Video fixture encoding timed out");muxer.stop();return file;
        }finally{try{codec.stop();}catch(Exception ignored){}codec.release();if(muxer!=null)muxer.release();}
    }
}
