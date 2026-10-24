package dev.inknfclab;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PhotoFilterUiTest {
    ActivityController<Activity> host;
    Activity activity;
    PicturePanel pictures;
    interface Check { boolean ready() throws Exception; }
    static Object field(Object object,String name)throws Exception {
        Field member=object.getClass().getDeclaredField(name);member.setAccessible(true);return member.get(object);
    }
    static void set(Object object,String name,Object value)throws Exception {
        Field member=object.getClass().getDeclaredField(name);member.setAccessible(true);member.set(object,value);
    }
    static void await(Check check)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(!check.ready()&&System.nanoTime()<end) {
            Thread.sleep(10);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        }
        assertTrue("Preview did not finish",check.ready());
    }
    static View find(View view,String text) {
        if(text.equals(view.getContentDescription())||view instanceof Button&&text.equals(((Button)view).getText().toString()))return view;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) { View result=find(group.getChildAt(i),text);if(result!=null)return result; }
        }
        return null;
    }
    static PhotoEditor canvas(View view) {
        if(view instanceof PhotoEditor)return (PhotoEditor)view;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){PhotoEditor result=canvas(group.getChildAt(i));if(result!=null)return result;}
        }
        return null;
    }
    static int[] pixels(Bitmap image) {
        int[] result=new int[image.getWidth()*image.getHeight()];
        image.getPixels(result,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());return result;
    }
    @Before public void setup() {
        host=Robolectric.buildActivity(Activity.class);activity=host.get();
        activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar);host.setup();
        activity.getSharedPreferences("photo_edit",0).edit().clear().commit();
        new File(activity.getFilesDir(),"photo-original.cache").delete();
    }
    @After public void close() {
        if(pictures!=null)pictures.close();host.pause().stop().destroy();
    }
    @Test public void previewUndoRedoCancelAndApplyKeepTheirOwnState()throws Exception {
        Bitmap source=PicturePanel.demoBitmap();PhotoEdit initial=new PhotoEdit();
        AtomicReference<PhotoEdit> saved=new AtomicReference<>();
        Dialog dialog=PhotoEditor.open(activity,source,initial,3,saved::set);
        try {
            View root=dialog.getWindow().getDecorView();PhotoEditor editor=canvas(root);
            await(()->field(editor,"colorPreview")!=null);
            int warm=Arrays.hashCode(pixels((Bitmap)field(editor,"colorPreview")));
            find(root,"经典四色").performClick();await(()->field(editor,"colorPreview")!=null);
            assertTrue(find(root,"经典四色").isSelected());assertFalse(find(root,"自然暖调").isSelected());
            assertNotEquals(warm,Arrays.hashCode(pixels((Bitmap)field(editor,"colorPreview"))));
            find(root,"撤销").performClick();assertEquals(PhotoEdit.EINKWARM,((PhotoEdit)field(editor,"edit")).colorProfile);
            find(root,"重做").performClick();assertEquals(PhotoEdit.CLASSIC,((PhotoEdit)field(editor,"edit")).colorProfile);
            find(root,"取消编辑").performClick();assertNull(saved.get());assertEquals(PhotoEdit.EINKWARM,initial.colorProfile);
            dialog=PhotoEditor.open(activity,source,initial,3,saved::set);root=dialog.getWindow().getDecorView();
            find(root,"经典四色").performClick();find(root,"完成").performClick();
            assertEquals(PhotoEdit.CLASSIC,saved.get().colorProfile);assertEquals(PhotoEdit.EINKWARM,initial.colorProfile);
        } finally { dialog.dismiss();source.recycle(); }
    }
    @Test public void committedFilterSurvivesPicturePanelRestartAndBusyGuard()throws Exception {
        pictures=new PicturePanel(activity,(a,b)->{},()->{},()->{});activity.setContentView(pictures.view);
        pictures.prepareDemo();await(()->pictures.frame()!=null);byte[] warm=pictures.frame().image.packed.clone();
        assertNull(find(pictures.view,"经典四色"));
        java.lang.reflect.Method open=PicturePanel.class.getDeclaredMethod("openEditorAt",int.class);open.setAccessible(true);
        open.invoke(pictures,3);Dialog dialog=(Dialog)field(pictures,"editorDialog");View root=dialog.getWindow().getDecorView();
        find(root,"经典四色").performClick();find(root,"完成").performClick();await(()->!(Boolean)field(pictures,"preparing"));
        assertEquals(PhotoEdit.CLASSIC,pictures.frame().metadata.getString("color_profile"));
        assertFalse(Arrays.equals(warm,pictures.frame().image.packed));
        pictures.setBusy(true);open.invoke(pictures,3);assertNull(field(pictures,"editorDialog"));pictures.setBusy(false);
        Bitmap original=(Bitmap)field(pictures,"original");
        try(FileOutputStream stream=new FileOutputStream(new File(activity.getFilesDir(),"photo-original.cache"))) {
            original.compress(Bitmap.CompressFormat.PNG,100,stream);
        }
        pictures.close();pictures=new PicturePanel(activity,(a,b)->{},()->{},()->{});activity.setContentView(pictures.view);
        await(()->pictures.frame()!=null);assertEquals(PhotoEdit.CLASSIC,pictures.frame().metadata.getString("color_profile"));
    }
    @Test public void finalPreviewWinsRapidSwitchesAndDismissReleasesIt()throws Exception {
        Bitmap source=PicturePanel.demoBitmap();Dialog dialog=PhotoEditor.open(activity,source,new PhotoEdit(),3,e->{});
        try {
            View root=dialog.getWindow().getDecorView();PhotoEditor editor=canvas(root);
            await(()->field(editor,"colorPreview")!=null);
            for(int i=0;i<4;i++){find(root,"经典四色").performClick();find(root,"自然暖调").performClick();}
            find(root,"经典四色").performClick();await(()->field(editor,"colorPreview")!=null);
            PhotoEdit state=new PhotoEdit();state.colorProfile=PhotoEdit.CLASSIC;
            ImageCodec.PreparedImage expected=ImageCodec.prepareEdited(source,state);
            try { assertArrayEquals(pixels(expected.preview),pixels((Bitmap)field(editor,"colorPreview"))); }
            finally { expected.preview.recycle();expected.originalPreview.recycle(); }
            dialog.dismiss();assertNull(field(editor,"colorPreview"));
        } finally { dialog.dismiss();source.recycle(); }
    }
    @Test public void textSelectionIsVisibleOverRawComposition()throws Exception {
        Bitmap source=Bitmap.createBitmap(80,120,Bitmap.Config.ARGB_8888);source.eraseColor(Color.WHITE);
        PhotoEdit state=new PhotoEdit();PhotoEdit.TextLayer text=new PhotoEdit.TextLayer();text.text="Selected";state.texts.add(text);
        PhotoEditor editor=new PhotoEditor(activity,source,state);Bitmap preview=Bitmap.createBitmap(552,768,Bitmap.Config.ARGB_8888);
        preview.eraseColor(Color.GREEN);set(editor,"colorPreview",preview);set(editor,"textMode",true);set(editor,"selected",0);
        editor.layout(0,0,400,650);Bitmap selected=Bitmap.createBitmap(400,650,Bitmap.Config.ARGB_8888),plain=selected.copy(Bitmap.Config.ARGB_8888,true);
        try {
            editor.draw(new Canvas(selected));set(editor,"selected",-1);editor.draw(new Canvas(plain));
            assertFalse(Arrays.equals(pixels(selected),pixels(plain)));assertNotEquals(Color.GREEN,selected.getPixel(200,250));
        } finally { selected.recycle();plain.recycle();preview.recycle();source.recycle(); }
    }
    @Test public void filterControlsFitNarrowAndWideEditors()throws Exception {
        Bitmap source=PicturePanel.demoBitmap();
        try {
            for(int width:new int[]{360,400}) {
                Dialog dialog=PhotoEditor.open(activity,source,new PhotoEdit(),3,e->{});
                try {
                    View root=dialog.getWindow().getDecorView();PhotoEditor editor=canvas(root);
                    await(()->field(editor,"colorPreview")!=null);
                    root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
                    root.layout(0,0,width,800);
                    for(String label:new String[]{"经典四色","自然暖调","完成","滤镜"}) {
                        View control=find(root,label);assertNotNull(label,control);assertTrue(label,control.getWidth()>20&&control.getHeight()>20);
                        int[] location=new int[2];control.getLocationInWindow(location);
                        assertTrue(label,location[0]>=0&&location[0]+control.getWidth()<=width);
                    }
                    String destination=System.getenv("MIAOMO_FILTER_SCREENSHOTS");
                    if(destination!=null) {
                        File folder=new File(destination);assertTrue(folder.isDirectory()||folder.mkdirs());
                        Bitmap image=Bitmap.createBitmap(width,800,Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));
                        try(FileOutputStream stream=new FileOutputStream(new File(folder,"filter-"+width+".png"))) {
                            image.compress(Bitmap.CompressFormat.PNG,100,stream);
                        } finally { image.recycle(); }
                    }
                } finally { dialog.dismiss(); }
            }
        } finally { source.recycle(); }
    }
}
