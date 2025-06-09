package com.example.geotax;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int CONST = 6371;
    static JSONArray taxisA, conductoresA;
    private List<Marker> marcadoresTaxis = new ArrayList<>();
    private Marker marcadorMonoblock = null;

    // coordenadas de la monoblock (UMSA)
    double latitudUMSA = -16.504791725783956;
    double longitudUMSA = -68.12996105148655;

    private MapView map;
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    // 1 = permiso concedido
    // 0 = permiso denegado

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // configuracion del user-agent
        Configuration.getInstance().load(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // permisos
        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION
        });

        // inicio del mapa
        map = findViewById(R.id.mapa);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15);
        map.getController().setCenter(new GeoPoint(latitudUMSA, longitudUMSA));

        // tarea de fondo para conectar con la pagina clasespersonales.com
        new MyAsyncTask().execute("http://www.clasespersonales.com/taxis/listaxis.php",
                "http://www.clasespersonales.com/taxis/listacon.php");

    }
    private class MyAsyncTask extends AsyncTask<String, Void, String>{
        @Override
        protected String doInBackground(String... arg0){
            String datosTaxis = "";
            String datosConductores = "";
            datosTaxis = bajarDatos(arg0[0]);
            datosConductores = bajarDatos(arg0[1]);
            return datosTaxis + "###" + datosConductores;
        }

        @Override
        protected void onPostExecute(String resultado){
            // mostrar todos los taxis en el mapa
            String[] datos = resultado.split("###");
            try{
                JSONObject jsonTaxis = new JSONObject(datos[0]);
                JSONObject jsonConductores = new JSONObject(datos[1]);
                taxisA = jsonTaxis.getJSONArray("taxis");
                conductoresA = jsonConductores.getJSONArray("conductores");

                // llamamos al metodo para mostrar los taxis en el mapa mediante marcadores
                mostrarUnidades(taxisA, conductoresA);

                // resaltar el monoblock
                resaltar(latitudUMSA, longitudUMSA);
            }
            catch(Exception e){
                Log.e("Error: ", e.getMessage(), e);
            }
        }
    }

    private static String bajarDatos(String url){
        InputStream p; // puntero
        String res = "";

        // usar un try catch para evitar errores en caso de que no haya conexion a internet
        try{
            URL paginaWeb = new URL(url);
            HttpURLConnection con = (HttpURLConnection) paginaWeb.openConnection();
            p = con.getInputStream();

            // si es que todo es se cumple, es porque tuvimos respuesta (informacion que nos llego)
            if(p != null){
                res = convierteString(p);
            }
            else{
                res = "Fallo la conexion";
            }
        }
        catch(Exception e){
            Log.e("Error", e.getMessage(), e);
        }
        return res;
    }

    private static String convierteString(InputStream b) throws IOException {
        BufferedReader puntero = new BufferedReader(new InputStreamReader(b));
        String linea;
        String resultado = "";
        while((linea = puntero.readLine()) != null){
            resultado += linea;
        }
        puntero.close();
        return resultado;
    }

    private void requestPermissionsIfNecessary(String[] permissions){
        for(String permission : permissions){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_REQUEST_CODE);
                return;
            }
        }
    }

    private void addMarker(double lat, double lon, String title, String info){
        GeoPoint point = new GeoPoint(lat, lon);
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setTitle(title);
        marker.setSubDescription(info);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(marker);
        marcadoresTaxis.add(marker);
        map.invalidate();
    }

    private String calcularDistancia(double lat, double lon){
        // usando la formula de Haversine
        double latDistancia = Math.toRadians(latitudUMSA - lat);
        double lonDistancia = Math.toRadians(longitudUMSA - lon);

        double a = Math.sin(latDistancia / 2) * Math.sin(latDistancia / 2) +
                Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(latitudUMSA)) *
                        Math.sin(lonDistancia / 2) * Math.sin(lonDistancia / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        String resultado = String.format("%.2f", CONST * c);
        return resultado.replace(",",".");
    }

    public void mostrarUnidades(JSONArray taxisA, JSONArray conductoresA){
        try{
            for(int i = 0; i < taxisA.length() ; i++){
                JSONObject taxi = taxisA.getJSONObject(i);
                String movil = taxi.getString("movil");
                String chofer = taxi.getString("chofer");

                // con el ci del chofer empezamos una busqueda para conseguir el nombre del mismo en la listaConductores
                String nombreCompleto = "";
                for(int j = 0 ; j < conductoresA.length() ; j++){
                    JSONObject conductor = conductoresA.getJSONObject(j);
                    String ci = conductor.getString("carnet");
                    if(ci.equals(chofer)){
                        String nombre = conductor.getString("nombres");
                        String paterno = conductor.getString("paterno");
                        String materno = conductor.getString("materno");

                        nombreCompleto = nombre + " " + paterno + " " + materno;
                        break;
                    }
                }

                String latitud = taxi.getString("latitud");
                String longitud = taxi.getString("longitud");
                String distancia = calcularDistancia(Double.parseDouble(latitud), Double.parseDouble(longitud));

                String texto = "Unidad: " + movil + "\n" + "Distancia: " + distancia + " Km.";

                // mostrar el marcador del taxi en el mapa
                addMarker(Double.parseDouble(latitud), Double.parseDouble(longitud), texto, "Conductor: "+nombreCompleto);
            }
        }
        catch(Exception e){
            Log.e("Error", e.getMessage(), e);
        }
    }

    public void reiniciar(View v){
        // quitar los marcadores de taxis
        for(Marker m : marcadoresTaxis){
            map.getOverlays().remove(m);
        }
        marcadoresTaxis.clear();

        map.invalidate();

        // reagregar el marcador del monoblock y de los taxis
        resaltar(latitudUMSA, longitudUMSA);
        mostrarUnidades(taxisA, conductoresA);
    }

    private void resaltar(double lat, double lon){
        // en caso de que el marcador ya exista lo eliminamos
        if(marcadorMonoblock != null){
            map.getOverlays().remove(marcadorMonoblock);
        }
        GeoPoint puntoEdificio = new GeoPoint(lat, lon);

        // crear marcador
        Marker marcadorEdificio = new Marker(map);
        marcadorEdificio.setPosition(puntoEdificio);
        marcadorEdificio.setTitle("Monoblock UMSA");
        marcadorEdificio.setSubDescription("Av. Villazón N° 1995, Plaza del Bicentenario - Zona Central.");

        // cambiar el icono y color para que se destaque
        Drawable icono = getResources().getDrawable(android.R.drawable.star_big_on).mutate();
        icono.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        marcadorEdificio.setIcon(icono);

        // agregar el nuevo marcador al mapa
        map.getOverlays().add(marcadorEdificio);

        // actualizar el mapa
        map.invalidate();

    }

    public void filtrar(View v){
        for(Marker m : marcadoresTaxis){
            map.getOverlays().remove(m);
        }
        marcadoresTaxis.clear();

        map.invalidate();

        resaltar(latitudUMSA, longitudUMSA);

        // distancias
        StringBuilder distancias = new StringBuilder();
        // tomar las distnacias entre 2 puntos en km (monoblock y el taxi)
        try{
            for(int i = 0 ; i < taxisA.length() ; i++){
                JSONObject taxi = taxisA.getJSONObject(i);
                String unidad = taxi.getString("movil");
                String carnet = taxi.getString("chofer");
                String lat = taxi.getString("latitud");
                String lon = taxi.getString("longitud");

                distancias.append(unidad).append(";")
                        .append(calcularDistancia(Double.parseDouble(lat), Double.parseDouble(lon))).append(";")
                        .append(lat).append(";")
                        .append(lon).append(";")
                        .append(carnet).append("\n");
            }
            // hallamos los 3 puntos mas cercanos a la monoblock
            // ordenamos un array de menor a mayor
            String aux = "";
            String[] listaDistancias = distancias.toString().split("\\r?\\n");
            for(int i = 0 ; i < listaDistancias.length - 1 ; i++){
                for(int j = 0 ; j < listaDistancias.length - 1 - i ; j++){
                    String[] dist1 = listaDistancias[j].split(";");
                    String[] dist2 = listaDistancias[j+1].split(";");
                    if(Double.parseDouble(dist1[1]) > Double.parseDouble(dist2[1])){
                        aux = listaDistancias[j + 1];
                        listaDistancias[j + 1] = listaDistancias[j];
                        listaDistancias[j] = aux;
                    }
                }
            }

            for(int i = 0 ; i < 3 ; i++){
                String[] datos = listaDistancias[i].split(";");
                String carnet = datos[4];
                String nombres = "";
                String texto = "";

                try{
                    for(int j = 0 ; j < conductoresA.length() ; j++){
                        JSONObject conductor = conductoresA.getJSONObject(j);
                        String ci = conductor.getString("carnet");
                        if(ci.equals(carnet)){
                            String nombre = conductor.getString("nombres");
                            String paterno = conductor.getString("paterno");
                            String materno = conductor.getString("materno");

                            nombres = nombre + " " + paterno + " " + materno;
                            break;
                        }
                    }
                    texto = "Unidad: " + datos[0] + " " + "\n" + "Distancia: " + datos[1] + "Km.";
                    addMarker(Double.parseDouble(datos[2]), Double.parseDouble(datos[3]), texto, nombres);
                }
                catch(Exception e){
                    Log.e("Error", e.getMessage(), e);
                }
            }
        }
        catch(Exception e){
            Log.e("Error", e.getMessage(), e);
        }
    }


}