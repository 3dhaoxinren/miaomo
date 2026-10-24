package dev.inknfclab;

import java.io.*;
import java.util.Arrays;

public final class PhotoProfileTest {
    private static void require(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        PhotoEdit state=new PhotoEdit();require(PhotoEdit.EINKWARM.equals(state.colorProfile));
        state.colorProfile=PhotoEdit.CLASSIC;state.landscape=true;state.zoom=1.7;state.texts.add(new PhotoEdit.TextLayer());
        byte[] encoded=state.encode();PhotoEdit restored=PhotoEdit.decode(encoded);
        require(PhotoEdit.CLASSIC.equals(restored.colorProfile));require(Arrays.equals(encoded,restored.encode()));
        PhotoEdit copy=state.copy();copy.colorProfile=PhotoEdit.EINKWARM;require(PhotoEdit.CLASSIC.equals(state.colorProfile));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream legacy=new DataOutputStream(bytes);legacy.writeInt(0x494E4B45);legacy.writeInt(1);
        int length=((encoded[8]&255)<<8)|(encoded[9]&255);legacy.write(encoded,10+length,encoded.length-10-length);
        PhotoEdit old=PhotoEdit.decode(bytes.toByteArray());require(PhotoEdit.EINKWARM.equals(old.colorProfile));require(old.landscape&&old.zoom==1.7&&old.texts.size()==1);
        EditHistory history=new EditHistory();history.record(state);state.colorProfile=PhotoEdit.EINKWARM;PhotoEdit undone=history.undo(state);require(PhotoEdit.CLASSIC.equals(undone.colorProfile));require(PhotoEdit.EINKWARM.equals(history.redo(undone).colorProfile));
        restored.reset();require(PhotoEdit.EINKWARM.equals(restored.colorProfile));restored.colorProfile="unknown";restored.normalize();require(PhotoEdit.EINKWARM.equals(restored.colorProfile));
        System.out.println("PASS PhotoProfileTest: v2 roundtrip, v1 warm migration, copy/reset/normalize, undo/redo");
    }
}
