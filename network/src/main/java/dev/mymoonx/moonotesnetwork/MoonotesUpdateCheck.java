package dev.mymoonx.moonotesnetwork;

import com.google.gson.JsonParser;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Manual only: checks an official GitHub release without transmitting player identifiers. */
final class MoonotesUpdateCheck {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"Moonotes-Update-Check");t.setDaemon(true);return t;
    });
    private static volatile String result="Не проверялось";
    private static volatile boolean busy;
    private MoonotesUpdateCheck() {}
    static String result(){return result;}
    static boolean busy(){return busy;}
    static synchronized void check(){
        if(busy)return;
        busy=true;result="Проверяем GitHub Releases…";
        IO.execute(()->{
            try {
                URI uri=URI.create("https://api.github.com/repos/mymoonx/moonotes/releases/latest");
                HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();
                c.setConnectTimeout(5000);c.setReadTimeout(6000);c.setInstanceFollowRedirects(false);
                c.setRequestProperty("Accept","application/vnd.github+json");
                c.setRequestProperty("User-Agent","Moonotes-updates");
                try{
                    if(c.getResponseCode()!=200){result="Проверка недоступна (HTTP "+c.getResponseCode()+")";return;}
                    byte[] bytes;
                    try(InputStream in=c.getInputStream()){bytes=in.readNBytes(16385);}
                    if(bytes.length>16384)throw new IOException("Ответ слишком большой");
                    var json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
                    String tag=json.get("tag_name").getAsString();
                    if(!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-z0-9.-]+)?"))throw new IOException("Неверная версия");
                    result="Последняя стабильная версия: "+tag;
                }finally{c.disconnect();}
            }catch(Exception ignored){result="Не удалось проверить обновления";}
            finally{busy=false;}
        });
    }
}
