package dev.inknfclab;
import java.util.*;

/** Whole edit snapshots; raw source bitmap is never duplicated in undo history. */
final class EditHistory {
    private final ArrayDeque<PhotoEdit> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    void record(PhotoEdit state){undo.addLast(state.copy());while(undo.size()>30)undo.removeFirst();redo.clear();}
    boolean canUndo(){return !undo.isEmpty();}
    boolean canRedo(){return !redo.isEmpty();}
    PhotoEdit undo(PhotoEdit current){if(undo.isEmpty())return current.copy();redo.addLast(current.copy());return undo.removeLast().copy();}
    PhotoEdit redo(PhotoEdit current){if(redo.isEmpty())return current.copy();undo.addLast(current.copy());return redo.removeLast().copy();}
}
