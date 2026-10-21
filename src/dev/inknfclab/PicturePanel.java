package dev.inknfclab;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Album selection, local originals, composition editing and InkSight preview. */
final class PicturePanel {
    static final int PICK=301,SAVE=302;
    interface Events{void log(String type,String value);}
    static final class Frame{final ImageCodec.PreparedImage image;final JSONObject metadata;Frame(ImageCodec.PreparedImage image,JSONObject metadata){this.image=image;this.metadata=metadata;}}
    final LinearLayout view;
    private final Activity activity;private final Events events;
    private final ExecutorService images=Executors.newSingleThreadExecutor();
    private final ImageView preview;private final TextView summary,photoInfo,previewLabel;
    private final ProgressBar progress;private final Button pick,filePick,editButton,compare,upload,cancel;
    private final LinearLayout selectedActions;private final View empty;
    private Bitmap original,displayBitmap;private PhotoEdit edit=new PhotoEdit();private Dialog editorDialog;
    private boolean busy,preparing,editing,closed,originalShown;private int generation;private String sourceKind="photo_picker",pendingSource="photo_picker";
    private volatile Frame frame;
    PicturePanel(Activity activity,Events events,Runnable start,Runnable stop){
        this.activity=activity;this.events=events;view=AppUi.column(activity);
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);LinearLayout headingText=AppUi.column(activity);headingText.addView(AppUi.text(activity,"照片工作室",25,AppUi.INK,true));AppUi.gap(headingText,3);headingText.addView(AppUi.text(activity,"让喜欢的画面，成为日常。",12,AppUi.MUTED,false));heading.addView(headingText,new LinearLayout.LayoutParams(0,-2,1));
        View menu=UiIcon.iconButton(activity,"more","更多选图方式",false,()->new AlertDialog.Builder(activity).setItems(new String[]{"从文件选择","保存四色预览","体验测试图"},(d,which)->{if(which==0)pickFile();else if(which==1)savePreview();else prepareDemo();}).show());heading.addView(menu,new LinearLayout.LayoutParams(AppUi.dp(activity,42),AppUi.dp(activity,42)));view.addView(heading);AppUi.gap(view,19);
        LinearLayout canvasCard=AppUi.column(activity);canvasCard.setBackground(AppUi.shape(0xffe9e9e4,16,activity));canvasCard.setPadding(AppUi.dp(activity,18),AppUi.dp(activity,18),AppUi.dp(activity,18),AppUi.dp(activity,18));
        FrameLayout imageArea=new FrameLayout(activity);
        preview=new ImageView(activity);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setContentDescription("四色照片预览");imageArea.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout placeholder=AppUi.column(activity);placeholder.setGravity(Gravity.CENTER);UiIcon glyph=new UiIcon(activity,"image",0xff9a9b96);placeholder.addView(glyph,new LinearLayout.LayoutParams(AppUi.dp(activity,50),AppUi.dp(activity,50)));AppUi.gap(placeholder,16);placeholder.addView(AppUi.text(activity,"选择你的第一张照片",16,AppUi.INK,true));AppUi.gap(placeholder,6);placeholder.addView(AppUi.text(activity,"相册 · 文件 · 随时开始",12,AppUi.MUTED,false));imageArea.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));empty=placeholder;canvasCard.addView(imageArea,new LinearLayout.LayoutParams(-1,AppUi.dp(activity,318)));view.addView(canvasCard);AppUi.gap(view,10);
        LinearLayout caption=new LinearLayout(activity);caption.setGravity(Gravity.CENTER_VERTICAL);previewLabel=AppUi.text(activity,"四色预览",11,AppUi.MUTED,false);caption.addView(previewLabel,new LinearLayout.LayoutParams(0,-2,1));for(int color:InkSightQuantizer.COLORS){View dot=new View(activity);android.graphics.drawable.GradientDrawable bg=AppUi.shape(color,6,activity);bg.setStroke(AppUi.dp(activity,.5f),AppUi.LINE);dot.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(AppUi.dp(activity,9),AppUi.dp(activity,9));lp.leftMargin=AppUi.dp(activity,5);caption.addView(dot,lp);}caption.addView(AppUi.text(activity,"  552 × 768",10,AppUi.MUTED,false));view.addView(caption);AppUi.gap(view,17);
        pick=AppUi.button(activity,"＋ 选择照片",true,this::pick);filePick=AppUi.ghost(activity,"文件",false,this::pickFile);view.addView(pick,new LinearLayout.LayoutParams(-1,AppUi.dp(activity,44)));
        selectedActions=AppUi.column(activity);LinearLayout toolbar=new LinearLayout(activity);
        editButton=AppUi.button(activity,"编辑照片",false,this::openEditor);compare=AppUi.button(activity,"原图对比",false,()->{originalShown=!originalShown;showFrame();});
        toolbar.addView(UiIcon.tool(activity,"crop","构图",false,false,()->openEditorAt(0)),new LinearLayout.LayoutParams(0,AppUi.dp(activity,62),1));toolbar.addView(UiIcon.tool(activity,"text","文字",false,false,()->openEditorAt(1)),new LinearLayout.LayoutParams(0,AppUi.dp(activity,62),1));toolbar.addView(UiIcon.tool(activity,"frame","相框",false,false,()->openEditorAt(2)),new LinearLayout.LayoutParams(0,AppUi.dp(activity,62),1));toolbar.addView(UiIcon.tool(activity,"compare","对比",false,false,()->{originalShown=!originalShown;showFrame();}),new LinearLayout.LayoutParams(0,AppUi.dp(activity,62),1));selectedActions.addView(toolbar);AppUi.gap(selectedActions,12);
        photoInfo=AppUi.text(activity,"",11,AppUi.MUTED,false);photoInfo.setGravity(Gravity.CENTER);selectedActions.addView(photoInfo);view.addView(selectedActions);AppUi.gap(view,20);
        LinearLayout line=new LinearLayout(activity);line.setGravity(Gravity.CENTER_VERTICAL);summary=AppUi.text(activity,"选好照片，预览后即可推送。",12,AppUi.MUTED,false);line.addView(summary,new LinearLayout.LayoutParams(0,-2,1));view.addView(line);AppUi.gap(view,10);
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(AppUi.INK));view.addView(progress,new LinearLayout.LayoutParams(-1,AppUi.dp(activity,3)));AppUi.gap(view,12);
        upload=AppUi.button(activity,"推送到墨水屏  →",true,start);view.addView(upload,new LinearLayout.LayoutParams(-1,AppUi.dp(activity,48)));cancel=AppUi.ghost(activity,"停止并保存",false,stop);view.addView(cancel);AppUi.gap(view,9);TextView note=AppUi.text(activity,"INKSIGHT  /  本地处理 · 原图保留",10,AppUi.MUTED,false);note.setGravity(Gravity.CENTER);view.addView(note);
        controls();activity.getWindow().getDecorView().post(()->{if(!closed&&cacheFile().isFile())loadCached(false);});
    }
    private File cacheFile(){return new File(activity.getFilesDir(),"photo-original.cache");}
    private void controls(){
        if(upload==null)return;boolean enabled=!busy&&!preparing&&!editing;
        pick.setEnabled(enabled);filePick.setEnabled(enabled);editButton.setEnabled(enabled&&original!=null);compare.setEnabled(enabled&&frame!=null);upload.setEnabled(enabled&&frame!=null);
        selectedActions.setVisibility(original==null?View.GONE:View.VISIBLE);pick.setText(original==null?"＋ 选择照片":"更换照片");pick.setBackground(AppUi.shape(original==null?AppUi.INK:AppUi.SURFACE,9,activity));pick.setTextColor(original==null?AppUi.SURFACE:AppUi.INK);upload.setAlpha(upload.isEnabled()?1:.3f);
        cancel.setVisibility(busy?View.VISIBLE:View.GONE);
    }
    void setBusy(boolean value){busy=value;controls();}
    Frame frame(){return frame;}
    int powerWaitMs(){return activity.getSharedPreferences("refresh",0).getInt("power_wait_ms",5000);}
    void transfer(String message,int completed,int total){summary.setText(message+"\n"+completed+" / "+total+" 个数据包");boolean wait=total>0&&completed==total&&!message.equals("屏幕刷新完成");progress.setIndeterminate(wait);if(!wait)progress.setProgress(completed*90/Math.max(1,total));}
    void result(String message){progress.setIndeterminate(false);summary.setText(message);if(message.contains("设备报告空闲")||message.equals("屏幕刷新完成"))progress.setProgress(100);}
    void failed(Throwable e){String detail=e.getMessage()==null?e.toString():e.getMessage();result(detail.contains("68CA")?"屏幕供电不足 · 68CA\n请检查供电与贴合位置，再手动重试。":"推送未完成\n"+detail);progress.setProgress(0);events.log("image_transfer_failed",detail);}
    private void pick(){if(busy||preparing||editing)return;if(Build.VERSION.SDK_INT>=33)try{openPicker(new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"),"photo_picker");return;}catch(ActivityNotFoundException ignored){}try{openPicker(new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI).setType("image/*"),"gallery");}catch(ActivityNotFoundException ignored){pickFile();}}
    private void pickFile(){if(busy||preparing||editing)return;try{openPicker(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),"document_picker");}catch(ActivityNotFoundException e){result("没有可用的图片选择器");}}
    private void openPicker(Intent intent,String kind){activity.startActivityForResult(intent,PICK);pendingSource=kind;events.log("image_picker_opened",kind+" / "+intent.getAction());}
    boolean onResult(int request,int result,Intent data){
        if(request==SAVE){if(result==Activity.RESULT_OK&&data!=null&&data.getData()!=null&&frame!=null){Uri uri=data.getData();Bitmap image=frame.image.preview;images.execute(()->{try(OutputStream out=activity.getContentResolver().openOutputStream(uri)){if(out==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("保存失败");activity.runOnUiThread(()->Toast.makeText(activity,"四色预览已保存",Toast.LENGTH_SHORT).show());}catch(Exception e){events.log("image_save_error",e.toString());}});}return true;}
        if(request!=PICK)return false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null){events.log("image_picker_cancelled",pendingSource);return true;}
        Uri uri=data.getData();final String kind=pendingSource;preparing=true;controls();summary.setText("正在读取照片…");progress.setIndeterminate(true);int token=++generation;
        images.execute(()->{File temporary=new File(activity.getFilesDir(),"photo-original.tmp");Bitmap bitmap=null;
            try(InputStream input=activity.getContentResolver().openInputStream(uri);OutputStream output=new FileOutputStream(temporary)){
                if(input==null)throw new IOException("无法读取照片");byte[] b=new byte[8192];int n,total=0;while((n=input.read(b))!=-1){total+=n;if(total>64*1024*1024)throw new IOException("照片文件超过64MB，请先缩小");output.write(b,0,n);}
            }catch(Exception e){temporary.delete();imageError(token,e);return;}
            try{bitmap=decode(temporary);if(!temporary.renameTo(cacheFile()))throw new IOException("照片缓存保存失败");final Bitmap loaded=bitmap;
                activity.runOnUiThread(()->{if(closed||generation!=token){loaded.recycle();return;}Bitmap previous=original;original=loaded;if(previous!=null)images.execute(previous::recycle);edit=new PhotoEdit();persistEdit();sourceKind=kind;preparing=false;progress.setIndeterminate(false);events.log("image_picker_selected",kind);controls();prepare();openEditor();});
            }catch(Exception e){if(bitmap!=null)bitmap.recycle();temporary.delete();imageError(token,e);}
        });return true;
    }
    private void imageError(int token,Throwable error){activity.runOnUiThread(()->{if(closed||generation!=token)return;preparing=false;progress.setIndeterminate(false);summary.setText("照片处理失败："+error.getMessage());controls();events.log("image_prepare_error",error.toString());});}
    private Bitmap decode(File file)throws Exception{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.toString(),bounds);if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>100000000L)throw new IOException("照片尺寸无效或过大");int sample=1;while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>2048)sample*=2;final int factor=sample;
        if(Build.VERSION.SDK_INT>=28)return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file),(decoder,info,src)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetSampleSize(factor);});BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=factor;Bitmap bitmap=BitmapFactory.decodeFile(file.toString(),options);if(bitmap==null)throw new IOException("无法解码照片");return bitmap;}
    private void loadCached(boolean showEditor){if(busy||preparing||editing)return;int token=++generation;preparing=true;controls();images.execute(()->{try{Bitmap bitmap=decode(cacheFile());activity.runOnUiThread(()->{if(closed||generation!=token){bitmap.recycle();return;}original=bitmap;edit=readEdit();sourceKind="local_original";preparing=false;controls();prepare();if(showEditor)openEditor();});}catch(Exception e){imageError(token,e);}});}
    private PhotoEdit readEdit(){android.content.SharedPreferences p=activity.getSharedPreferences("photo_edit",0);String encoded=p.getString("scene_v1",null);if(encoded!=null)try{return PhotoEdit.decode(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT));}catch(Exception error){events.log("edit_restore_error",error.toString());}PhotoEdit e=new PhotoEdit();e.landscape=p.getBoolean("landscape",false);e.cover=p.getBoolean("cover",true);e.rotation=p.getInt("rotation",0);e.mirror=p.getBoolean("mirror",false);e.zoom=p.getFloat("zoom",1);e.offsetX=p.getFloat("x",0);e.offsetY=p.getFloat("y",0);e.normalize();return e;}
    private void persistEdit(){try{activity.getSharedPreferences("photo_edit",0).edit().putString("scene_v1",android.util.Base64.encodeToString(edit.encode(),android.util.Base64.NO_WRAP)).apply();}catch(IOException error){events.log("edit_save_error",error.toString());}}
    private void openEditor(){openEditorAt(0);}
    private void openEditorAt(int tab){if(busy||editing||original==null)return;editing=true;controls();editorDialog=PhotoEditor.open(activity,original,edit,tab,e->{edit=e;persistEdit();originalShown=false;prepare();});editorDialog.setOnDismissListener(d->{editing=false;editorDialog=null;controls();});}
    private void prepare(){if(busy||closed||original==null)return;final int token=++generation;final Bitmap input=original;final PhotoEdit state=edit.copy();final String kind=sourceKind;preparing=true;frame=null;controls();summary.setText("正在生成四色预览…");progress.setIndeterminate(true);
        images.execute(()->{try{long started=SystemClock.elapsedRealtime();ImageCodec.PreparedImage image=ImageCodec.prepareEdited(input,state);JSONObject meta=new JSONObject().put("width",552).put("height",768).put("packed_bytes",105984).put("colors",4).put("algorithm","inksight_atkinson").put("photo_enhance",true).put("palette","000000,FFFFFF,E8B000,C80000").put("resample","pillow_lanczos").put("source",kind).put("orientation",state.landscape?"landscape":"portrait").put("rotation",state.rotation).put("mirror",state.mirror).put("cover",state.cover).put("zoom",state.zoom).put("offset_x",state.offsetX).put("offset_y",state.offsetY).put("text_layers",state.texts.size()).put("frame_style",PhotoDecorations.FRAMES[state.frameStyle]).put("edit_sha256",Protocol.hex(MessageDigest.getInstance("SHA-256").digest(state.encode()))).put("prepare_ms",SystemClock.elapsedRealtime()-started).put("sha256",Protocol.hex(MessageDigest.getInstance("SHA-256").digest(image.packed)));
            activity.runOnUiThread(()->{if(closed||generation!=token){image.preview.recycle();image.originalPreview.recycle();return;}frame=new Frame(image,meta);preparing=false;progress.setIndeterminate(false);progress.setProgress(0);showFrame();photoInfo.setText((state.landscape?"横向构图":"竖向构图")+"  ·  "+state.texts.size()+" 个文字图层  ·  "+PhotoDecorations.FRAMES[state.frameStyle]);summary.setText("预览就绪，贴近屏幕即可推送。");controls();events.log("image_prepared",meta.toString());});
        }catch(Throwable e){imageError(token,e);}});
    }
    private void showFrame(){Frame f=frame;if(f==null)return;Bitmap bitmap=originalShown?f.image.originalPreview:f.image.preview;if(edit.landscape){Matrix m=new Matrix();m.postRotate(-90);Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,false);preview.setImageBitmap(rotated);if(displayBitmap!=null)displayBitmap.recycle();displayBitmap=rotated;}else{preview.setImageBitmap(bitmap);if(displayBitmap!=null){displayBitmap.recycle();displayBitmap=null;}}empty.setVisibility(View.GONE);previewLabel.setText(originalShown?"原图 · 未量化":"InkSight 四色预览");compare.setText(originalShown?"查看四色":"查看原图");}
    private void savePreview(){if(frame==null||busy||preparing){Toast.makeText(activity,"请先选择照片",Toast.LENGTH_SHORT).show();return;}activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png").putExtra(Intent.EXTRA_TITLE,"喵墨-四色预览.png"),SAVE);}
    void prepareDemo(){if(busy||editing||closed)return;original=demoBitmap();sourceKind="generated_test_card";edit=new PhotoEdit();originalShown=false;prepare();}
    static Bitmap demoBitmap(){Bitmap b=Bitmap.createBitmap(552,768,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(0xfff9f8ef);Paint p=new Paint(3);p.setColor(0xff1c3029);p.setTextSize(49);p.setTypeface(Typeface.create("sans-serif-medium",0));c.drawText("INK / MOMENTS",35,92,p);p.setTextSize(23);c.drawText("Keep a little color.",38,134,p);for(int i=0;i<4;i++){p.setColor(InkSightQuantizer.COLORS[i]);c.drawRect(35+i*121,190,35+(i+1)*121,440,p);}for(int i=0;i<16;i++){p.setColor(InkSightQuantizer.COLORS[i%4]);c.drawCircle(70+(i%4)*136,505+(i/4)*49,17,p);}p.setColor(0xff1c3029);p.setTextSize(20);c.drawText("552 × 768  /  InkSight",38,740,p);return b;}
    void close(){closed=true;generation++;if(editorDialog!=null)editorDialog.dismiss();images.shutdownNow();}
}
