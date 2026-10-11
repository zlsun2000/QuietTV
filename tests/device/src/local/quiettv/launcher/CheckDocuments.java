package local.quiettv.launcher;

import android.content.*;
import android.database.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Build;
import android.provider.OpenableColumns;
import java.io.*;

/** Synthetic documents in the test APK; never part of the production desktop. */
public final class CheckDocuments extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "image/png";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});c.addRow(new Object[]{"../../private.png"});return c;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        boolean virtual=Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk")||Build.MODEL.contains("Emulator");
        if(!virtual)throw new FileNotFoundException("Only a synthetic emulator may use test documents");
        if(!"/image".equals(uri.getPath())||!"r".equals(mode))throw new FileNotFoundException("Unknown test document");
        File f=new File(getContext().getCacheDir(),"synthetic.png");
        try{Bitmap image=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);image.eraseColor(0xff285a46);try(OutputStream out=new FileOutputStream(f)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}catch(IOException e){throw new FileNotFoundException("Synthetic document creation failed");}
        return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
