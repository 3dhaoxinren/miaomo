package dev.inknfclab;
import java.io.IOException;
import java.util.Arrays;
public final class EditSceneTest {
    static void require(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        PhotoEdit state=new PhotoEdit();state.landscape=true;state.frameStyle=4;state.frameColor=0xffe8b000;state.frameWidth=.07;
        final String originalText="杭州 · 秋日\n2026.10.20 ♥";PhotoEdit.TextLayer text=new PhotoEdit.TextLayer();text.text=originalText;text.color=0xffc80000;text.background=0xffffffff;text.x=.37;text.y=.82;text.rotation=-13;text.size=.083;text.font=1;text.outline=true;state.texts.add(text);
        byte[] bytes=state.encode();PhotoEdit decoded=PhotoEdit.decode(bytes);require(Arrays.equals(bytes,decoded.encode()));require(decoded.texts.get(0).text.equals(text.text));require(decoded.landscape&&decoded.frameStyle==4&&decoded.texts.get(0).outline);
        PhotoEdit independent=state.copy();independent.texts.get(0).text="changed";require(state.texts.get(0).text.equals(originalText));independent.texts.clear();require(state.texts.size()==1);
        EditHistory history=new EditHistory();history.record(state);state.texts.get(0).text="after";history.record(state);state.frameStyle=5;
        PhotoEdit first=history.undo(state);require(first.frameStyle==4&&first.texts.get(0).text.equals("after"));PhotoEdit second=history.undo(first);require(second.texts.get(0).text.equals(originalText));PhotoEdit redo=history.redo(second);require(redo.texts.get(0).text.equals("after"));history.record(redo);require(!history.canRedo());
        PhotoEdit many=new PhotoEdit();for(int i=0;i<35;i++)many.texts.add(new PhotoEdit.TextLayer());many.normalize();require(many.texts.size()==20);many.texts.get(0).text=new String(new char[700]).replace('\0','a');many.normalize();require(many.texts.get(0).text.length()==500);
        byte[] extra=Arrays.copyOf(bytes,bytes.length+1);try{PhotoEdit.decode(extra);throw new AssertionError("trailing data accepted");}catch(IOException expected){}
        try{PhotoEdit.decode(Arrays.copyOf(bytes,10));throw new AssertionError("truncated data accepted");}catch(IOException expected){}
        byte[] corrupt=bytes.clone();corrupt[0]=0;try{PhotoEdit.decode(corrupt);throw new AssertionError("wrong magic accepted");}catch(IOException expected){}
        decoded.reset();require(decoded.texts.isEmpty()&&decoded.frameStyle==0);
        EditHistory limited=new EditHistory();for(int i=0;i<40;i++){limited.record(state);state.rotation=i;}int count=0;while(limited.canUndo()){state=limited.undo(state);count++;}require(count==30);
        System.out.println("PASS EditSceneTest: Chinese multiline scene roundtrip; deep copy; undo/redo/branch/30-state cap; layer limit; reset; corrupt/truncated/trailing rejects");
    }
}
