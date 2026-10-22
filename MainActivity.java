package com.hervis.diagnosticoobd;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(15,18,24), CARD=Color.rgb(29,34,43), TEXT=Color.rgb(235,239,245), MUTED=Color.rgb(155,165,180), ACCENT=Color.rgb(58,190,140);
    private LinearLayout root, grid;
    private TextView status;
    private BluetoothSocket socket;
    private InputStream in; private OutputStream out;
    private ExecutorService worker=Executors.newSingleThreadExecutor();
    private Handler handler=new Handler(Looper.getMainLooper());
    private boolean connected=false, polling=false;
    private final Map<String,TextView> values=new LinkedHashMap<>();
    private final String[] pids={"010C","010D","0105","010B","0110","0111","0142","0106","0107","010E"};
    private final String[] names={"RPM","Velocidad","Temp. motor","MAP","MAF","TPS","Voltaje ECU","STFT","LTFT","O2 B1S1"};
    private final String[] units={"rpm","km/h","°C","kPa","g/s","%","V","%","%","V"};
    private final Map<String,String> lastValues=new LinkedHashMap<>();
    private SharedPreferences prefs;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); prefs=getSharedPreferences("obd",MODE_PRIVATE); buildUi();
        if (Build.VERSION.SDK_INT>=31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_SCAN"},9);
    }
    private void buildUi(){
        root=new LinearLayout(this); root.setOrientation(1); root.setPadding(18,18,18,12); root.setBackgroundColor(BG);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); LinearLayout page=new LinearLayout(this); page.setOrientation(1); scroll.addView(page);
        TextView title=new TextView(this); title.setText("DIAGNÓSTICO OBD"); title.setTextColor(TEXT); title.setTextSize(23); title.setTypeface(null,1); page.addView(title);
        TextView sub=new TextView(this); sub.setText("Multimarca · Bluetooth ELM327 · modo oscuro"); sub.setTextColor(MUTED); sub.setTextSize(13); page.addView(sub);
        status=new TextView(this); status.setText("Desconectado · Conecta un ELM327 emparejado"); status.setTextColor(ACCENT); status.setPadding(0,16,0,12); page.addView(status);
        LinearLayout buttons=new LinearLayout(this); buttons.setOrientation(0);
        Button connect=button("Conectar ELM327"); connect.setOnClickListener(v->chooseDevice()); buttons.addView(connect,new LinearLayout.LayoutParams(0,54,1));
        Button codes=button("Códigos DTC"); codes.setOnClickListener(v->readCodes()); buttons.addView(codes,new LinearLayout.LayoutParams(0,54,1)); page.addView(buttons);
        Button clear=button("Borrar códigos"); clear.setOnClickListener(v->clearCodes()); page.addView(clear);
        TextView section=new TextView(this); section.setText("DATOS EN VIVO"); section.setTextColor(TEXT); section.setTextSize(16); section.setTypeface(null,1); section.setPadding(0,20,0,10); page.addView(section);
        grid=new LinearLayout(this); grid.setOrientation(1); page.addView(grid);
        for(int i=0;i<names.length;i+=2){
            LinearLayout row=new LinearLayout(this); row.setOrientation(0);
            for(int j=i;j<Math.min(i+2,names.length);j++){
                LinearLayout card=new LinearLayout(this); card.setOrientation(1); card.setPadding(12,12,8,12); card.setBackgroundColor(CARD);
                TextView n=new TextView(this); n.setText(names[j]); n.setTextColor(MUTED); n.setTextSize(13); card.addView(n);
                TextView val=new TextView(this); val.setText("-- "+units[j]); val.setTextColor(TEXT); val.setTextSize(21); val.setTypeface(null,1); card.addView(val); values.put(pids[j],val);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,94,1); lp.setMargins(3,3,3,3); row.addView(card,lp);
            } grid.addView(row);
        }
        Button customize=button("Personalizar medidores / vehículo"); customize.setOnClickListener(v->customize()); page.addView(customize);
        TextView note=new TextView(this); note.setText("Prototipo alfa. Los datos dependen de la ECU y del adaptador. Sensores específicos de fabricante aún no incluidos."); note.setTextColor(MUTED); note.setTextSize(12); note.setPadding(2,18,2,20); page.addView(note);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(TEXT); b.setAllCaps(false); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD)); return b; }
    private void chooseDevice(){
        try{
            BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter(); if(a==null){msg("Este teléfono no tiene Bluetooth.");return;}
            if(Build.VERSION.SDK_INT>=31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},9);return;}
            Set<BluetoothDevice> ds=a.getBondedDevices(); ArrayList<BluetoothDevice> list=new ArrayList<>();
            for(BluetoothDevice d:ds) if(d.getName()!=null && (d.getName().toLowerCase().contains("obd")||d.getName().toLowerCase().contains("elm")||d.getName().toLowerCase().contains("vlink"))) list.add(d);
            if(list.isEmpty()) list.addAll(ds);
            if(list.isEmpty()){msg("Primero empareja el ELM327 en Ajustes Bluetooth de Android.");return;}
            String[] labels=new String[list.size()]; for(int i=0;i<list.size();i++) labels[i]=list.get(i).getName()+"\n"+list.get(i).getAddress();
            new AlertDialog.Builder(this).setTitle("Selecciona el adaptador").setItems(labels,(d,w)->connect(list.get(w))).setNegativeButton("Cancelar",null).show();
        }catch(SecurityException e){msg("Permiso Bluetooth denegado.");}
    }
    private void connect(BluetoothDevice device){
        status.setText("Conectando con "+device.getName()+"…");
        worker.execute(()->{
            try{
                BluetoothSocket s=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                s.connect(); socket=s; in=s.getInputStream(); out=s.getOutputStream();
                String init=cmd("ATZ"); cmd("ATE0"); cmd("ATL0"); cmd("ATS0"); cmd("ATH0"); cmd("ATSP0");
                connected=true; handler.post(()->{status.setText("Conectado · detectando protocolo"); polling=true; pollNext(0);});
            }catch(Exception e){connected=false; handler.post(()->status.setText("Error de conexión: "+e.getMessage())); closeSocket();}
        });
    }
    private String cmd(String command)throws Exception{
        out.write((command+"\r").getBytes("US-ASCII")); out.flush();
        StringBuilder sb=new StringBuilder(); long end=System.currentTimeMillis()+1800;
        while(System.currentTimeMillis()<end){ while(in.available()>0){int c=in.read(); if(c=='>' ) return sb.toString().replace("\r"," ").replace("\n"," ").trim(); if(c>=0)sb.append((char)c);} Thread.sleep(20);}
        return sb.toString().replace("\r"," ").replace("\n"," ").trim();
    }
    private void pollNext(int idx){
        if(!polling||!connected)return;
        String pid=pids[idx%pids.length];
        worker.execute(()->{
            String raw=""; try{raw=cmd(pid); String value=parse(pid,raw); if(value!=null){String v=value; handler.post(()->{TextView t=values.get(pid); if(t!=null)t.setText(v+" "+units[Arrays.asList(pids).indexOf(pid)]);});}}
            catch(Exception e){handler.post(()->status.setText("Comunicación interrumpida")); polling=false; closeSocket();}
            handler.postDelayed(()->pollNext((idx+1)%pids.length),120);
        });
    }
    private String parse(String pid,String raw){
        String s=raw.replace(" ","").toUpperCase(Locale.US); int pos=s.indexOf(pid.substring(2));
        if(pos<0)return null; s=s.substring(pos+2); if(s.length()<2)return null;
        try{
            switch(pid){
                case "010C": return String.valueOf((Integer.parseInt(s.substring(0,4),16))/4);
                case "010D": return String.valueOf(Integer.parseInt(s.substring(0,2),16));
                case "0105": return String.valueOf(Integer.parseInt(s.substring(0,2),16)-40);
                case "010B": return String.valueOf(Integer.parseInt(s.substring(0,2),16));
                case "0110": return String.format(Locale.US,"%.2f",Integer.parseInt(s.substring(0,4),16)/100.0);
                case "0111": return String.valueOf(Integer.parseInt(s.substring(0,2),16)*100/255);
                case "0142": return String.format(Locale.US,"%.2f",Integer.parseInt(s.substring(0,4),16)/1000.0);
                case "0106": case "0107": return String.valueOf((Integer.parseInt(s.substring(0,2),16)-128)*100/128);
                case "010E": return String.format(Locale.US,"%.3f",Integer.parseInt(s.substring(0,2),16)/200.0);
            }
        }catch(Exception ignored){} return null;
    }
    private void readCodes(){ if(!connected){msg("Conecta primero el ELM327.");return;} worker.execute(()->{try{String r=cmd("03");handler.post(()->msg("Respuesta de códigos DTC:\n"+r));}catch(Exception e){handler.post(()->msg("Error: "+e.getMessage()));}}); }
    private void clearCodes(){ if(!connected){msg("Conecta primero el ELM327.");return;} new AlertDialog.Builder(this).setTitle("Borrar códigos").setMessage("Esto solicita borrar los códigos de la ECU. ¿Continuar?").setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(d,w)->worker.execute(()->{try{String r=cmd("04");handler.post(()->msg("Respuesta ECU: "+r));}catch(Exception e){handler.post(()->msg("Error: "+e.getMessage()));}})).show(); }
    private void customize(){
        EditText vehicle=new EditText(this); vehicle.setSingleLine(); vehicle.setHint("Vehículo (ej. Renault Duster 2.0 2015)"); vehicle.setText(prefs.getString("vehicle",""));
        EditText profile=new EditText(this); profile.setSingleLine(); profile.setHint("Nombre de perfil de medidores"); profile.setText(prefs.getString("profile","Predeterminado"));
        LinearLayout l=new LinearLayout(this); l.setPadding(24,8,24,8); l.setOrientation(1); l.addView(vehicle); l.addView(profile);
        new AlertDialog.Builder(this).setTitle("Guardar configuración").setView(l).setPositiveButton("Guardar",(d,w)->{prefs.edit().putString("vehicle",vehicle.getText().toString()).putString("profile",profile.getText().toString()).apply(); status.setText("Perfil guardado: "+profile.getText());}).setNegativeButton("Cancelar",null).show();
    }
    private void msg(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("Aceptar",null).show();}
    private void closeSocket(){try{if(socket!=null)socket.close();}catch(Exception ignored){} connected=false;}
    @Override protected void onDestroy(){polling=false; closeSocket(); worker.shutdownNow(); super.onDestroy();}
}
