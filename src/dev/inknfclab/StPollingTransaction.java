package dev.inknfclab;

import java.io.IOException;
import java.util.Arrays;

/** ST54L custom-frame transaction. A saved baseline is required before the first write. */
final class StPollingTransaction {
    interface Link {
        void pause() throws Exception;
        void resume() throws Exception;
        byte[] exchange(byte[] command) throws Exception;
    }
    interface Store {
        byte[] load() throws Exception;
        void save(byte[] frames) throws Exception;
        void clear() throws Exception;
    }
    private final Link link;
    private final Store store;
    private byte[] original;
    private boolean dirty;
    StPollingTransaction(Link link,Store store){this.link=link;this.store=store;}
    static byte[] response(int command,byte[] payload)throws IOException{
        if(payload==null||payload.length<2||(payload[0]&255)!=command||payload[1]!=0)
            throw new IOException("ST command 0x"+Integer.toHexString(command)+" response rejected: "+Protocol.hex(payload));
        return Arrays.copyOfRange(payload,2,payload.length);
    }
    private byte[] read()throws Exception{
        byte[] frames=response(0x15,link.exchange(new byte[]{0x15}));Protocol.validatePolling(frames);
        if(frames.length>250)throw new IOException("ST polling snapshot is too large for a bounded restore command");return frames;
    }
    private void write(byte[] frames)throws Exception{
        Protocol.validatePolling(frames);byte[] command=new byte[frames.length+1];command[0]=0x13;System.arraycopy(frames,0,command,1,frames.length);
        response(0x13,link.exchange(command));
    }
    private void restorePaused()throws Exception{
        if(!dirty)return;
        write(original);
        if(!Arrays.equals(read(),original))throw new IOException("ST original polling-frame readback mismatch; recovery snapshot retained");
        store.clear();dirty=false;
    }
    synchronized byte[] start()throws Exception{
        if(dirty)throw new IOException("ST polling transaction requires recovery before another start");
        link.pause();
        try{
            byte[] saved=store.load();
            if(saved!=null){Protocol.validatePolling(saved);original=saved.clone();dirty=true;restorePaused();}
            original=read();
            if(Arrays.equals(original,Protocol.polling7A()))throw new IOException("ST custom 7A frame is already active without an owned snapshot; left unchanged");
            store.save(original.clone()); // Must finish before any setter, including failed setters.
            dirty=true;
            try{
                write(Protocol.polling7A());
                if(!Arrays.equals(read(),Protocol.polling7A()))throw new IOException("ST 7A polling-frame readback mismatch");
            }catch(Exception failed){
                try{restorePaused();}catch(Exception recovery){failed.addSuppressed(recovery);}
                throw failed;
            }
            return original.clone();
        }finally{link.resume();}
    }
    synchronized boolean restore()throws Exception{
        if(!dirty)return true;
        link.pause();try{restorePaused();return true;}finally{link.resume();}
    }
    synchronized boolean needsRecovery(){return dirty;}
    synchronized byte[] baseline(){return original==null?null:original.clone();}
}
