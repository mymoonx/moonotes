package dev.mymoonx.moonotesnetwork;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;

/** Bottom-right anchored offsets for the two chat buttons; GUI scale independent. */
final class ChatButtonPlacement {
    static final int PICKER=0, MANAGER=1;
    private static final Gson JSON=new Gson();
    static final class Data {
        int pickerRight=96,pickerBottom=18,managerRight=6,managerBottom=18;
    }
    private static final class Holder {
        static final ChatButtonPlacement LOCAL=new ChatButtonPlacement(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("moonotes-chat-layout.json"));
    }
    private static volatile Data current=new Data();
    static ChatButtonPlacement local(){return Holder.LOCAL;}
    static ChatOverlayGeometry.Rect picker(int w,int h){return rect(current,PICKER,w,h);}
    static ChatOverlayGeometry.Rect manager(int w,int h){return rect(current,MANAGER,w,h);}

    private final Path file;
    private Data saved;
    ChatButtonPlacement(Path file){
        this.file=file; saved=read();current=copy(saved);
    }
    private static Data copy(Data d){
        Data out=new Data();
        out.pickerRight=d.pickerRight;out.pickerBottom=d.pickerBottom;
        out.managerRight=d.managerRight;out.managerBottom=d.managerBottom;
        return out;
    }
    synchronized Data draft(){return copy(saved);}
    static ChatOverlayGeometry.Rect rect(Data d,int which,int w,int h){
        int width=which==PICKER?48:84,height=21;
        int right=which==PICKER?d.pickerRight:d.managerRight;
        int bottom=which==PICKER?d.pickerBottom:d.managerBottom;
        int x=Math.clamp(w-width-right,4,Math.max(4,w-width-4));
        int y=Math.clamp(h-height-bottom,4,Math.max(4,h-height-18));
        return new ChatOverlayGeometry.Rect(x,y,width,height);
    }
    static void place(Data d,int which,int w,int h,double x,double y){
        int width=which==PICKER?48:84,height=21;
        int bx=Math.clamp((int)Math.round(x),4,Math.max(4,w-width-4));
        int by=Math.clamp((int)Math.round(y),4,Math.max(4,h-height-18));
        int right=w-width-bx,bottom=h-height-by;
        if(which==PICKER){d.pickerRight=right;d.pickerBottom=bottom;}
        else{d.managerRight=right;d.managerBottom=bottom;}
    }
    synchronized boolean save(Data draft){
        if(draft==null)return false;
        saved=copy(draft);current=copy(saved);
        try{
            byte[] bytes=JSON.toJson(saved).getBytes(StandardCharsets.UTF_8);
            if(bytes.length>2048)throw new IOException("Invalid size");
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"moonotes-chat-layout-",".tmp");
            try{
                Files.write(tmp,bytes);
                Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(tmp);}
            return true;
        }catch(IOException ex){return false;}
    }
    synchronized void reset(){save(new Data());}
    private Data read(){
        try{
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>2048)return new Data();
            Data input=JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),Data.class);
            if(input==null)return new Data();
            for(int v:new int[]{input.pickerRight,input.pickerBottom,input.managerRight,input.managerBottom})
                if(v<0||v>4096)return new Data();
            return input;
        }catch(IOException|RuntimeException ex){return new Data();}
    }
}
