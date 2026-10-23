package com.example.ui

import android.content.Context
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.NordestaoStore
import org.json.JSONArray
import org.json.JSONObject

internal fun storesMapData(stores: List<NordestaoStore>): String {
    val data = JSONArray(stores.filter { it.latitude != null && it.longitude != null }.map {
        JSONObject().put("id",it.id).put("name",it.name).put("address",it.address).put("lat",it.latitude).put("lon",it.longitude)
    }).toString().replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    return data
}
internal fun storesMapUpdate(stores: List<NordestaoStore>, location: Pair<Double, Double>?, request: Int): String {
    val point = location?.takeIf { it.first.isFinite() && it.second.isFinite() && kotlin.math.abs(it.first) <= 90 && kotlin.math.abs(it.second) <= 180 }
        ?.let { "[${it.first},${it.second}]" } ?: "null"
    return "window.nrdUpdateStores(${storesMapData(stores)});window.nrdSetLocation($point,$request);"
}
internal fun storesMapHtml(stores: List<NordestaoStore>, leafletJs: String, leafletCss: String): String {
    val data = storesMapData(stores)
    return """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src https://tile.openstreetmap.org data:; connect-src 'none'; base-uri 'none'; form-action 'none'">
<style>$leafletCss
html,body,#map{height:100%;width:100%;margin:0}#map{position:absolute;inset:0}body{font-family:sans-serif}.leaflet-popup-content{white-space:pre-line}.user-pin{background:#1769e0;border:3px solid white;border-radius:50%;box-shadow:0 0 0 6px #1769e033}#map{touch-action:none}.store-pin{background:#d93826;border:3px solid white;border-radius:50%;box-shadow:0 1px 5px #333}#error{padding:20px;position:relative;background:white}</style></head><body><div id="map"></div><div id="error" hidden>Não foi possível carregar o mapa. Volte para Lista para consultar as lojas.</div>
<script>$leafletJs</script>
<script>if(typeof L==='undefined'){document.getElementById('map').hidden=true;document.getElementById('error').hidden=false;}else{
const map=L.map('map',{dragging:true,touchZoom:true,doubleClickZoom:true}).setView([-5.8,-35.3],10);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(map);
const storesLayer=L.layerGroup().addTo(map);let initialBounds=false;let userPin=null;let lastLocationRequest=-1;
window.nrdUpdateStores=function(stores){storesLayer.clearLayers();const bounds=[];for(const s of stores){const box=document.createElement('div');const title=document.createElement('strong');title.textContent=s.name;box.appendChild(title);const addr=document.createElement('p');addr.textContent=s.address;box.appendChild(addr);const a=document.createElement('a');a.href='nrd-store://'+s.id;a.textContent='Ver detalhes da loja';box.appendChild(a);L.marker([s.lat,s.lon],{icon:L.divIcon({className:'store-pin',iconSize:[18,18],iconAnchor:[9,9]})}).addTo(storesLayer).bindPopup(box);bounds.push([s.lat,s.lon]);}if(!initialBounds&&bounds.length){initialBounds=true;map.fitBounds(bounds,{padding:[25,25],maxZoom:15});}};
window.nrdSetLocation=function(point,request){if(!point)return;if(!userPin){userPin=L.marker(point,{icon:L.divIcon({className:'user-pin',iconSize:[18,18],iconAnchor:[9,9]})}).addTo(map).bindPopup('Sua localização aproximada');}else userPin.setLatLng(point);if(request!==lastLocationRequest){lastLocationRequest=request;map.setView(point,14);}};
window.nrdUpdateStores($data);
if(typeof ResizeObserver!=='undefined')new ResizeObserver(()=>map.invalidateSize()).observe(document.getElementById('map'));}

</script></body></html>"""
}
@Composable
internal fun NordestaoStoresMap(stores: List<NordestaoStore>, location: Pair<Double,Double>?, locationRequest: Int, onSelect: (Long) -> Unit, onExternal: (Uri) -> Unit) {
    val context = LocalContext.current
    val select by rememberUpdatedState(onSelect)
    val external by rememberUpdatedState(onExternal)
    val currentStores by rememberUpdatedState(stores)
    val commands by rememberUpdatedState(storesMapUpdate(stores, location, locationRequest))
    var pageReady by remember { mutableStateOf(false) }
    val webView = remember(context) { StoreMapWebView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false; settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.userAgentString += " NRDLOJAS/Stores (+https://github.com/bichocutela/NRDLOJAS-v2)"
        CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { pageReady = true }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                if(uri.scheme == "nrd-store") uri.host?.toLongOrNull()?.takeIf { id -> currentStores.any { it.id == id } }?.let { select(it) }
                else if(uri.scheme == "https" && uri.host == "www.openstreetmap.org") external(uri)
                return true
            }
        }
    } }
    val assets = remember(context) {
        listOf("leaflet.js", "leaflet.css").map { name -> context.assets.open("store-map/$name").bufferedReader().use { it.readText() } }
    }
    val html = remember(assets) { storesMapHtml(emptyList(), assets[0], assets[1]) }
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize(), update = {
        if(it.tag == null) {
            it.tag = "loading"
            it.loadDataWithBaseURL("https://appassets.androidplatform.net/nrd-stores/",html,"text/html","UTF-8",null)
        } else if(pageReady && it.tag != commands) {
            it.evaluateJavascript(commands, null)
            it.tag = commands
        }
    })
    DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
}

/** Keep drag and pinch streams inside the map instead of handing them to a parent. */
internal class StoreMapWebView(context: Context) : WebView(context) {
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return try { super.dispatchTouchEvent(event) } finally {
            if(event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL)
                parent?.requestDisallowInterceptTouchEvent(false)
        }
    }
}
